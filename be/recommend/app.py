import logging, os, math, time, traceback
from fastapi import Request, FastAPI, Query, HTTPException
from fastapi.responses import StreamingResponse, JSONResponse
from pydantic import BaseModel
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from utils.geocode_kakao import haversine
from starlette.middleware.base import BaseHTTPMiddleware

import anyio, asyncio
from utils.google_places import get_photo_reference_by_name_addr
import httpx  # /photo 프록시용

# ---- 로깅 기본 설정 ----
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
logging.basicConfig(
    level=LOG_LEVEL,
    format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
)
logger = logging.getLogger("bread-reco")

# 환경 변수
DATA_PATH = os.environ.get("RECO_DATA", "data/bakeries_clean.csv")
RADIUS_M = int(os.environ.get("RECO_RADIUS_M", "2000"))             # 반경 2km 고정
LIMIT = int(os.environ.get("RECO_LIMIT", "10"))                     # 상위 10개 고정

FEATURE_THUMBS      = os.getenv("RECO_THUMBNAILS", "0") == "1"  # 기본 OFF
THUMB_TIMEOUT_SEC   = float(os.getenv("RECO_THUMB_TIMEOUT_SEC", "0.6"))
THUMB_CONCURRENCY   = int(os.getenv("RECO_THUMB_CONCURRENCY", "4"))
GOOGLE_KEY          = os.getenv("GOOGLE_PLACES_API_KEY", "")
PUBLIC_HOST_FASTAPI = os.getenv("PUBLIC_HOST_FASTAPI", "localhost:8000")  # 프록시 URL 구성용

app = FastAPI(title="Bread Reco", version="1.0.0")

DF: pd.DataFrame | None = None      # pandas DataFrame
VEC: TfidfVectorizer | None = None  # TfidfVectorizer
MAT = None                          # TF-IDF sparse matrix

# ---- 요청/응답 간단 로깅 미들웨어 ----
class SimpleAccessLogMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        start = time.time()
        try:
            response = await call_next(request)
            return response
        finally:
            dur_ms = int((time.time() - start) * 1000)
            logger.info(
                "REQ %s %s -> %s %dms",
                request.method, request.url.path, request.client.host if request.client else "-",
                dur_ms,
            )

app.add_middleware(SimpleAccessLogMiddleware)

# ---- 전역 예외 핸들러 (스택 로그는 찍되, 응답은 안전하게) ----
@app.exception_handler(Exception)
async def unhandled_exception_handler(request: Request, exc: Exception):
    logger.exception("UNHANDLED ERROR on %s %s", request.method, request.url.path)
    # 스택은 로그에만 남기고, 클라이언트로는 안전한 메시지 반환
    return JSONResponse(status_code=500, content={"detail": "internal server error"})

def load_data(path=DATA_PATH):
    import pandas as pd, os

    if not os.path.exists(path):
        df = pd.DataFrame(columns=["id","name","address","intro","signature","lat","lng","text"])
        return df, None, None

    df = pd.read_csv(path)

    # id 보정 - 없거나 NaN 있으면 재생성(1..N)
    if "id" not in df.columns or df["id"].isna().any():
        df = df.reset_index(drop=True)
        df["id"] = df.index + 1
    else:
        df["id"] = pd.to_numeric(df["id"], errors="coerce")
        if df["id"].isna().any():
            df = df.reset_index(drop=True)
            df["id"] = df.index + 1
        else:
            df["id"] = df["id"].astype(int)

    for col in ["name","intro","signature","address"]:
        if col not in df.columns: df[col] = ""
        df[col] = df[col].fillna("")

    if len(df) == 0:
        return df.assign(text=""), None, None

    df["text"] = (df["name"] + " " + df["intro"] + " " + df["signature"]).astype(str)

    from sklearn.feature_extraction.text import TfidfVectorizer
    vec = TfidfVectorizer(analyzer="char", ngram_range=(2,4), min_df=1)
    try:
        mat = vec.fit_transform(df["text"])
    except ValueError:
        return df, None, None

    return df, vec, mat

DF, VEC, MAT = load_data()

class Item(BaseModel):
    id: int
    name: str
    address: str
    lat: float
    lng: float
    intro: str
    distance: float
    score: float
    thumbnailUrl: str | None = None

@app.get("/health")
def health():
    return {"ok": True, "rows": None if DF is None else len(DF)}

@app.post("/reload")
def reload():
    global DF, VEC, MAT
    DF, VEC, MAT = load_data()
    return {"ok": True, "rows": len(DF or [])}

# 구글 사진 프록시
@app.get("/photo")
def photo(photo_reference: str, maxwidth: int = 400):
    if not GOOGLE_KEY or not photo_reference:
        raise HTTPException(status_code=400, detail="missing key or photo_reference")
    url = (
        "https://maps.googleapis.com/maps/api/place/photo"
        f"?maxwidth={maxwidth}&photo_reference={photo_reference}&key={GOOGLE_KEY}"
    )
    try:
        r = httpx.get(url, timeout=10, follow_redirects=True)
        r.raise_for_status()
        return StreamingResponse(
            r.iter_bytes(),
            media_type=r.headers.get("content-type", "image/jpeg"),
            headers={"Cache-Control": "public, max-age=86400"}
        )
    except httpx.HTTPError:
        raise HTTPException(status_code=502, detail="google photo fetch failed")

@app.get("/recommend")
async def recommend(
        lat: float, lng: float,
        keywords: str = Query("", description="쉼표로 구분된 키워드"),
        exclude: str = Query("", description="쉼표로 구분된 bakeryId 목록"),
        thumbnails: bool = Query(False, description="썸네일 포함 여부")
):
    logger.info("[recommend] lat=%s lng=%s keywords=%s exclude=%s", lat, lng, keywords, exclude)

    if DF is None:
        logger.warning("[recommend] DF is None")
        return []

    df = DF.copy()
    before_len = len(df)

    # 제외
    if exclude:
        try:
            ex = set(int(x) for x in exclude.split(",") if x)
            df = df[~df["id"].isin(ex)]
        except ValueError:
            logger.warning("[recommend] exclude parse error: %s", exclude)

    # 거리계산 + 반경 필터
    def dist(row):
        return haversine(lat, lng, float(row["lat"]), float(row["lng"]))
    df["distance"] = df.apply(dist, axis=1)
    df = df[df["distance"] <= RADIUS_M]
    logger.debug("[recommend] filtered by radius: %d -> %d rows", before_len, len(df))

    # 키워드
    ks = [k.strip() for k in keywords.split(",") if k.strip()]

    # 점수 계산
    use_text = bool(ks) and (VEC is not None) and (MAT is not None) and (len(DF) == MAT.shape[0])
    logger.debug("[recommend] use_text=%s  ks=%s  MAT.shape=%s  DF.len=%s",
                 use_text, ks, getattr(MAT, "shape", None), len(DF))

    if use_text:
        # 키워드가 있으면: TF-IDF 유사도 → 거리 tie-break
        # 전체 DF 기준으로 sims 계산 → Series로 만들고 인덱스 정렬
        qv = VEC.transform([" ".join(ks)])
        sims = cosine_similarity(qv, MAT).ravel()
        score_full = pd.Series(sims, index=DF.index)   # DF 인덱스에 정렬
        # 부분 df에는 reindex로 안전 매핑
        df["score"] = score_full.reindex(df.index).fillna(0.0)
        # 유사도 우선, 같은 유사도에서는 거리 가까운 순
        out = df.sort_values(["score", "distance"], ascending=[False, True]).head(LIMIT)
    else:
        # 키워드가 없으면: 거리 가까운 순 Top-N
        df["score"] = 0.0
        out = df.sort_values(["distance"], ascending=True).head(LIMIT)

    logger.info("[recommend] returning %d items", len(out))

    # 1) 썸네일 없는 기본 응답 생성
    items = [
        {
            "id": int(r.id),
            "name": str(r.name),
            "address": str(r.address),
            "intro": str(r.intro),
            "signature": str(r.signature),
            "lat": float(r.lat),
            "lng": float(r.lng),
            "score": float(r.score),
            "distance": float(r.distance),
            "thumbnailUrl": None,   # 기본값
        }
        for r in out.itertuples(index=False)
    ]

    # 2) 썸네일 옵션이 켜졌고, 키도 있고, 결과가 있을 때만 “최대 0.6초 내에서” 병렬 수집
    if thumbnails and FEATURE_THUMBS and GOOGLE_KEY and items:
        sem = asyncio.Semaphore(THUMB_CONCURRENCY)

        async def _thumb_for(it):
            # 동기 LRU 캐시 함수 → 스레드로 실행
            async with sem:
                try:
                    # 스레드에서 동기 httpx 호출 실행 (최대 2초 내 응답 목표)
                    ref = await anyio.to_thread.run_sync(
                            get_photo_reference_by_name_addr,
                            it["name"], it["address"]
                    )
                    return f"http://{PUBLIC_HOST_FASTAPI}/photo?photo_reference={ref}" if ref else None
                except Exception:
                    return None

        thumbs = await asyncio.gather(*(_thumb_for(it) for it in items), return_exceptions=True)
        for it, t in zip(items, thumbs):
            it["thumbnailUrl"] = None if isinstance(t, Exception) else t

    return items
