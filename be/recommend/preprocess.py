import os, asyncio, csv, re, random
import pandas as pd
from utils.normalize_csv import normalize_text
from utils.geocode_kakao import geocode_address_async, backoff_sleep, _get_json

INPUT = os.environ.get("RECO_INPUT", "data/bakeries_raw.csv")
OUTPUT = os.environ.get("RECO_OUTPUT", "data/bakeries_clean.csv")
SKIP_GEOCODE = os.getenv("RECO_SKIP_GEOCODE", "").lower() in ("1","true","yes")

REQUIRED_COLS = ["id","name","address","intro","signature","lat","lng"]
COLMAP_VARIANTS = {
    "id": {"id","빵집id","bakery_id","place_id"},
    "name": {"name","빵집명","상호명","place_name"},
    "address": {"address","위치","주소","지번주소","도로명주소","road_address","road_address_name"},
    "intro": {"intro","한줄소개","소개","description","desc","소개글"},
    "signature": {"signature","대표메뉴","시그니처","메인메뉴"},
    "lat": {"lat","위도","y","latitude"},
    "lng": {"lng","경도","x","longitude","lon"},
}

def normalize_header(col: str) -> str:
    col = (col or "").replace("\xa0", " ")
    col = col.strip().lower()
    col = re.sub(r"\s+", "", col)
    return col

def map_headers(df: pd.DataFrame) -> pd.DataFrame:
    raw_cols = list(df.columns)
    df.columns = [normalize_header(c) for c in raw_cols]
    rename = {}
    all_variants = {k: {normalize_header(v) for v in vs} for k,vs in COLMAP_VARIANTS.items()}
    for std, variants in all_variants.items():
        for c in df.columns:
            if c in variants:
                rename[c] = std
    df.rename(columns=rename, inplace=True)
    for c in REQUIRED_COLS:
        if c not in df.columns:
            df[c] = ""
    return df[REQUIRED_COLS].copy()

def coerce_number(s):
    if pd.isna(s) or s is None: return None
    t = str(s).strip().replace(",", "")
    if t == "": return None
    try:
        return float(t)
    except Exception:
        return None

def tidy_address(s: str) -> str:
    s = (s or "").replace("\xa0", " ")
    s = re.sub(r"\s+", " ", s).strip()
    s = re.sub(r"\([^)]*\)", "", s)   # 괄호 정보 제거
    s = re.sub(r"[#·•…]+", " ", s)
    return s.strip()

async def fill_missing_coords(df: pd.DataFrame, extras: dict) -> pd.DataFrame:
    """1) 주소검색 2) 키워드검색(address) 3) 키워드검색(구+빵집명)"""
    idxs_addr = df[(df["address"].astype(str)!="") & (df["lat"].isna() | df["lng"].isna())].index.tolist()
    all_idxs = df.index.tolist()
    print(f"[geocode] need coords for {len(idxs_addr)} rows (address-present) / total {len(all_idxs)}")

    failures = []
    filled_total = 0

    # attempt 1~2: 주소/키워드 (geocode_address_async 내부에서 처리)
    for attempt in range(2):
        target = idxs_addr
        if not target: break
        tasks = [geocode_address_async(tidy_address(str(df.at[i,"address"]))) for i in target]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        new_idxs = []
        filled_now = 0
        for k, res in enumerate(results):
            i = target[k]
            if isinstance(res, Exception):
                new_idxs.append(i)
                continue
            lat, lng, meta = res
            if lat is not None and lng is not None:
                df.at[i, "lat"] = lat
                df.at[i, "lng"] = lng
                filled_now += 1
            else:
                failures.append({"row": int(i), "address": df.at[i,"address"], **(meta or {})})
                new_idxs.append(i)
        filled_total += filled_now
        idxs_addr = new_idxs
        print(f"[geocode] attempt {attempt+1}: filled {filled_now} (total {filled_total}) remaining {len(idxs_addr)}")
        if idxs_addr:
            backoff_sleep(attempt)

    # attempt 3: '{구} {빵집명}' 키워드 직접 시도
    if idxs_addr:
        tasks = []
        for i in all_idxs:  # 주소가 없어도 전체에서 시도
            gu = extras.get(i, {}).get("gu", "")
            nm = extras.get(i, {}).get("bakery", "")
            q = f"{gu} {nm}".strip()
            if q:
                tasks.append(_get_json("https://dapi.kakao.com/v2/local/search/keyword.json", {"query": q}))
            else:
                tasks.append({"_error": {"reason": "no_query"}})

        results = await asyncio.gather(*tasks, return_exceptions=True)
        filled_now = 0
        for i, data in enumerate(results):
            if isinstance(data, Exception):
                continue
            if "_error" not in data:
                docs = data.get("documents", [])
                if docs:
                    x = float(docs[0]["x"]); y = float(docs[0]["y"])
                    df.at[i, "lat"] = y
                    df.at[i, "lng"] = x
                    filled_now += 1
            else:
                # 실패는 이미 위 failures에 address 기반으로 쌓였으므로 생략 가능
                pass
        filled_total += filled_now
        print(f"[geocode] attempt 3: filled {filled_now} (total {filled_total})")

    # 실패 샘플 출력
    if failures:
        print("[geocode] sample failures (up to 5):")
        for f in random.sample(failures, k=min(5, len(failures))):
            print("  ->", f)

    return df

def main():
    print(f"[preprocess] input={INPUT}")
    raw = pd.read_csv(INPUT, sep=None, engine="python", encoding="utf-8-sig")
    print(f"[preprocess] raw columns: {list(raw.columns)}  rows={len(raw)}")

    # 보조 컬럼 저장(구/빵집명)
    extras = {}
    raw_gu = raw.get("구")
    raw_nm = raw.get("빵집명")
    for i in range(len(raw)):
        extras[i] = {
            "gu": normalize_text(str(raw_gu.iloc[i])) if raw_gu is not None else "",
            "bakery": normalize_text(str(raw_nm.iloc[i])) if raw_nm is not None else "",
        }

    df = map_headers(raw)

    # 문자열 정리
    for col in ["name","address","intro","signature"]:
        df[col] = df[col].fillna("").astype(str).str.replace("\xa0"," ", regex=False).apply(normalize_text)

    # id 없으면 1..N 부여
    if df["id"].isna().all():
        df["id"] = range(1, len(df)+1)
    df["id"] = pd.to_numeric(df["id"], errors="coerce").astype("Int64")

    # 숫자화
    df["lat"] = df["lat"].apply(coerce_number)
    df["lng"] = df["lng"].apply(coerce_number)

    # name 비어있는 행 제거
    before = len(df)
    df = df[df["name"]!=""].copy()
    print(f"[preprocess] drop empty name: {before} -> {len(df)}")

    # 중복 제거
    before = len(df)
    df.drop_duplicates(subset=["name","address"], keep="first", inplace=True)
    print(f"[preprocess] dedup: {before} -> {len(df)}")

    print(f"[preprocess] missing coords (before): lat NaN={df['lat'].isna().sum()} lng NaN={df['lng'].isna().sum()}")

    if not SKIP_GEOCODE:
        try:
            df = asyncio.run(fill_missing_coords(df, extras))
        except Exception as e:
            print("[preprocess] geocode failed:", e)
    else:
        print("[preprocess] SKIP_GEOCODE=true → geocoding skipped")

    print(f"[preprocess] missing coords (after): lat NaN={df['lat'].isna().sum()} lng NaN={df['lng'].isna().sum()}")

    # ↓↓↓ 디버그용으로 좌표 없는 행도 살리고 싶으면 아래 두 줄 중 위를 주석 처리하고 아래를 사용
    kept = df[df["lat"].notna() & df["lng"].notna()].copy()
    # kept = df.copy()

    print(f"[preprocess] keep rows with coords: {len(kept)} / {len(df)}")
    kept.to_csv(OUTPUT, index=False, quoting=csv.QUOTE_MINIMAL)
    print(f"Preprocessed: {len(kept)} rows → {OUTPUT}")

if __name__ == "__main__":
    main()