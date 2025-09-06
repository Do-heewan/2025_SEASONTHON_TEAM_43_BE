import os, math
from fastapi import FastAPI, Query
from pydantic import BaseModel
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from utils.geocode_kakao import haversine

DATA_PATH = os.environ.get("RECO_DATA", "data/bakeries_clean.csv")
RADIUS_M = int(os.environ.get("RECO_RADIUS_M", "5000"))             # 반경 5km 고정
LIMIT = int(os.environ.get("RECO_LIMIT", "10"))                     # 상위 10개 고정

app = FastAPI(title="Bread Reco", version="1.0.0")

DF: pd.DataFrame | None = None      # pandas DataFrame
VEC: TfidfVectorizer | None = None  # TfidfVectorizer
MAT = None                          # TF-IDF sparse matrix


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

@app.get("/health")
def health():
    return {"ok": True, "rows": None if DF is None else len(DF)}

@app.post("/reload")
def reload():
    global DF, VEC, MAT
    DF, VEC, MAT = load_data()
    return {"ok": True, "rows": len(DF or [])}

@app.get("/recommend")
def recommend(
        lat: float, lng: float,
        keywords: str = Query("", description="쉼표로 구분된 키워드"),
        exclude: str = Query("", description="쉼표로 구분된 bakeryId 목록")
):
    if DF is None:
        return []
    df = DF.copy()

    # 제외
    if exclude:
        try:
            ex = set(int(x) for x in exclude.split(",") if x)
            df = df[~df["id"].isin(ex)]
        except ValueError:
            pass

    # 거리계산 + 반경 필터
    def dist(row):
        return haversine(lat, lng, float(row["lat"]), float(row["lng"]))
    df["distance"] = df.apply(dist, axis=1)
    df = df[df["distance"] <= RADIUS_M]

    # 키워드
    ks = [k.strip() for k in keywords.split(",") if k.strip()]

    # 점수 계산
    if ks and VEC is not None and MAT is not None and len(DF) == MAT.shape[0]:
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

    # 응답 생성 (itertuples로 안정/빠르게)
    return [
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
        }
        for r in out.itertuples(index=False)
    ]