import os
from fastapi import FastAPI, Query, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from utils.geocode_kakao import haversine

# 썸네일용 구글 Places
from utils.google_places import get_photo_reference_by_name_addr  # 없으면 이 줄/관련 부분만 빼세요
import httpx  # 위와 동일

DATA_PATH = os.environ.get("RECO_DATA", "data/bakeries_clean.csv")
RADIUS_M = int(os.environ.get("RECO_RADIUS_M", "5000"))             # 반경 5km
LIMIT = int(os.environ.get("RECO_LIMIT", "10"))                     # 상위 10개
PUBLIC_HOST = os.getenv("PUBLIC_HOST_FASTAPI", "localhost:8004")    # /photo 프록시 URL용
GOOGLE_KEY = os.getenv("GOOGLE_PLACES_API_KEY", "")                 # 썸네일 사용 시 필요

app = FastAPI(title="Bread Reco", version="1.0.0")

DF: pd.DataFrame | None = None      # pandas DataFrame
VEC: TfidfVectorizer | None = None  # TfidfVectorizer
MAT = None                          # TF-IDF sparse matrix


def load_data(path=DATA_PATH):
    """CSV 로드 + 결측 보정 + TF-IDF 벡터라이저/행렬 준비"""
    import os

    if not os.path.exists(path):
        df = pd.DataFrame(columns=["id","name","address","intro","signature","lat","lng","text"])
        return df, None, None

    df = pd.read_csv(path)

    # id 보정 - 없거나 NaN 있으면 재생성(1..N) + 항상 int로
    if "id" not in df.columns or df["id"].isna().any():
        df = df.reset_index(drop=True)
        df["id"] = df.index + 1
    else:
        df["id"] = pd.to_numeric(df["id"], errors="coerce")
        if df["id"].isna().any():
            df = df.reset_index(drop=True)
            df["id"] = df.index + 1
        df["id"] = df["id"].astype(int)

    # 텍스트 컬럼들 기본값/문자열화
    for col in ["name","intro","signature","address"]:
        if col not in df.columns:
            df[col] = ""
        df[col] = df[col].fillna("").astype(str)

    if len(df) == 0:
        return df.assign(text=""), None, None

    # 검색용 텍스트
    df["text"] = (df["name"] + " " + df["intro"] + " " + df["signature"]).astype(str)

    # TF-IDF (문자 n-gram)
    vec = TfidfVectorizer(analyzer="char", ngram_range=(2,4), min_df=1)
    try:
        mat = vec.fit_transform(df["text"])
    except ValueError:
        # 전부 공백/정지어 등으로 비어버리는 경우
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
    """CSV를 다시 읽고 TF-IDF 재구축"""
    global DF, VEC, MAT
    DF, VEC, MAT = load_data()
    return {"ok": True, "rows": 0 if DF is None else int(len(DF))}


# (선택) 썸네일 프록시: 구글 키 노출 없이 이미지 전달
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
def recommend(
        lat: float,
        lng: float,
        keywords: str = Query("", description="쉼표로 구분된 키워드"),
        exclude: str = Query("", description="쉼표로 구분된 bakeryId 목록")
):
    """
    - 키워드가 있으면: TF-IDF 유사도 내림차순 → 거리 오름차순
    - 키워드가 없으면: 거리 오름차순
    - 항상 radius(미터) 내에서만 추천
    - exclude(id 목록)는 제외
    """
    if DF is None:
        return []

    # 원본 DF 보존
    df = DF.copy()

    # 제외
    if exclude:
        try:
            ex = {int(x) for x in exclude.split(",") if x}
            df = df[~df["id"].isin(ex)]
        except ValueError:
            pass

    # 거리 계산 + 반경 필터
    def dist(row):
        return haversine(lat, lng, float(row["lat"]), float(row["lng"]))
    df["distance"] = df.apply(dist, axis=1)
    df = df[df["distance"] <= RADIUS_M]

    # 키워드 파싱
    ks = [k.strip() for k in keywords.split(",") if k.strip()]

    # 점수 계산
    use_text = bool(ks) and (VEC is not None) and (MAT is not None) and (len(DF) == MAT.shape[0])
    if use_text:
        # 전체 DF 기준으로 sims 계산 → DF 인덱스를 기준으로 Series 생성 → 부분 df에 안전 매핑
        qv = VEC.transform([" ".join(ks)])
        sims = cosine_similarity(qv, MAT).ravel()
        score_full = pd.Series(sims, index=DF.index)
        df["score"] = score_full.reindex(df.index).fillna(0.0)
        out = df.sort_values(["score", "distance"], ascending=[False, True]).head(LIMIT)
    else:
        # 텍스트 매칭 못 쓰는 경우(키워드 없음/행렬 없음 등) → 거리 우선
        df["score"] = 0.0
        out = df.sort_values(["distance"], ascending=True).head(LIMIT)

    items = []
    for _, r in out.iterrows():
        # (선택) 썸네일
        thumb = None
        if GOOGLE_KEY:
            try:
                ref = get_photo_reference_by_name_addr(str(r["name"]), str(r["address"]))
                if ref:
                    thumb = f"http://{PUBLIC_HOST}/photo?photo_reference={ref}"
            except Exception:
                thumb = None

        items.append({
            "id": int(r["id"]),
            "name": str(r["name"]),
            "address": str(r["address"]),
            "intro": str(r["intro"]),
            "signature": str(r["signature"]),
            "lat": float(r["lat"]),
            "lng": float(r["lng"]),
            "score": float(r["score"]),
            "distance": float(r["distance"]),
            "thumbnailUrl": thumb,
        })

    return items