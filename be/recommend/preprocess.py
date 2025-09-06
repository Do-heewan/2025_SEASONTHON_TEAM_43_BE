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

    # 괄호 제거
    s = re.sub(r"\([^)]*\)", "", s)

    # 층/호/범위 제거: "1,2층", "1~2층", "101호", "108, 109호", "101~104호", "1층", "B1층"
    s = re.sub(r"\d+\s*,\s*\d+\s*층", "", s)          # "1,2층"
    s = re.sub(r"\d+\s*~\s*\d+\s*층", "", s)          # "1~2층"
    s = re.sub(r"\b\d+\s*호\b", "", s)                # "101호"
    s = re.sub(r"\d+\s*,\s*\d+\s*호", "", s)          # "108, 109호"
    s = re.sub(r"\d+\s*~\s*\d+\s*호", "", s)          # "101~104호"
    s = re.sub(r"\b[0-9A-Za-z]*\d+\s*층\b", "", s)    # "1층", "B1층"

    # 쉼표 뒤 불필요 텍스트(상호 등) 잘라내기 (주소가 먼저일 때만)
    # 예: "대전 ... 1,2층 꾸드뱅베이커리" → "대전 ... "
    # 단, 번지 형태는 유지하기 위해 '번지/번길/로/길' 등의 주소 키워드가 나오면 그대로 둠
    parts = [p.strip() for p in s.split(",")]
    if len(parts) > 1:
        left = parts[0]
        # 왼쪽 파트가 주소의 주요 형태를 포함하면 오른쪽은 버림
        if re.search(r"(로|길|번길|로\d+|길\d+|동|구|군|시|도)\b", left):
            s = left

    # 다중 특수문자 제거/정리
    s = re.sub(r"[#·•…]+", " ", s)
    return s.strip()

async def fill_missing_coords(df: pd.DataFrame, extras: dict) -> pd.DataFrame:
    """1) 주소 검색 2) 키워드 검색(address) 3) 키워드 검색(구+빵집명)"""
    idxs_addr = df[(df["address"].astype(str)!="") & (df["lat"].isna() | df["lng"].isna())].index.tolist()
    print(f"[geocode] need coords for {len(idxs_addr)} rows (address-present) / total {len(df)}")

    failures = []
    filled_total = 0

    # attempt 1~2: 주소/키워드 (geocode_address_async 내부에서 처리)
    for attempt in range(2):
        if not idxs_addr: break
        tasks = [geocode_address_async(tidy_address(str(df.at[i,"address"]))) for i in idxs_addr]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        new_idxs = []
        filled_now = 0
        for k, res in enumerate(results):
            i = idxs_addr[k]
            if isinstance(res, Exception):
                new_idxs.append(i); continue
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

    # attempt 3: '{구} {빵집명}' 키워드 검색 (★ 좌표 없는 인덱스만)
    if idxs_addr:
        tasks = []
        for i in idxs_addr:
            gu = (extras.get(int(i), {}).get("gu") or "").strip()
            nm = (extras.get(int(i), {}).get("bakery") or "").strip()
            q = f"{gu} {nm}".strip() if gu or nm else ""
            if q:
                tasks.append(_get_json("https://dapi.kakao.com/v2/local/search/keyword.json", {"query": q}))
            else:
                tasks.append({"_error": {"reason": "no_query"}})
        results = await asyncio.gather(*tasks, return_exceptions=True)
        filled_now = 0
        for k, data in enumerate(results):
            i = idxs_addr[k]
            if isinstance(data, Exception):
                continue
            if "_error" not in data:
                docs = data.get("documents", [])
                if docs:
                    x = float(docs[0]["x"]); y = float(docs[0]["y"])
                    df.at[i, "lat"] = y
                    df.at[i, "lng"] = x
                    filled_now += 1
        filled_total += filled_now
        print(f"[geocode] attempt 3: filled {filled_now} (total {filled_total})")

    # 실패 샘플 출력
    if failures:
        print("[geocode] sample failures (up to 5):")
        for f in random.sample(failures, k=min(5, len(failures))):
            print("  ->", f)

    return df

def extract_gu_from_address(addr: str) -> str:
    """주소 문자열에서 'XX구' 패턴을 추출 (없으면 빈문자열)"""
    if not addr:
        return ""
    m = re.search(r"([가-힣A-Za-z]+구)", addr)
    return m.group(1) if m else ""

def main():
    print(f"[preprocess] input={INPUT}")
    raw = pd.read_csv(INPUT, sep=None, engine="python", encoding="utf-8-sig")
    print(f"[preprocess] raw columns: {list(raw.columns)}  rows={len(raw)}")

    # 원본에서 힌트 컬럼 확보
    raw_gu = raw.get("구")
    raw_nm = raw.get("빵집명")

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

    # extras 구성: 주소에서 구 추출 + 이름
    extras = {
        int(i): {
            "gu": extract_gu_from_address(str(df.at[i, "address"])),
            "bakery": str(df.at[i, "name"]).strip(),
       }
        for i in df.index
    }

    if not SKIP_GEOCODE:
        try:
            df = asyncio.run(fill_missing_coords(df, extras))
        except Exception as e:
            print("[preprocess] geocode failed:", e)
    else:
        print("[preprocess] SKIP_GEOCODE=true → geocoding skipped")

    print(f"[preprocess] missing coords (after): lat NaN={df['lat'].isna().sum()} lng NaN={df['lng'].isna().sum()}")

    # 좌표 있는 것만 유지
    kept = df[df["lat"].notna() & df["lng"].notna()].copy()

    print(f"[preprocess] keep rows with coords: {len(kept)} / {len(df)}")
    kept.to_csv(OUTPUT, index=False, quoting=csv.QUOTE_MINIMAL)
    print(f"Preprocessed: {len(kept)} rows → {OUTPUT}")

if __name__ == "__main__":
    main()
