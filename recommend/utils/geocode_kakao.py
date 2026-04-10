import math
import os
import time
import httpx

RAW_KEY = os.environ.get("KAKAO_REST_API_KEY", "").strip()
KAKAO_KEY = RAW_KEY if RAW_KEY.startswith("KakaoAK ") else f"KakaoAK {RAW_KEY}"

def haversine(lat1, lon1, lat2, lon2):
    R = 6371000
    dlat = math.radians(lat2-lat1); dlon = math.radians(lon2-lon1)
    a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1))*math.cos(math.radians(lat2))*math.sin(dlon/2)**2
    return 2*R*math.asin(math.sqrt(a))

async def _get_json(url, params):
    headers = {"Authorization": KAKAO_KEY}
    async with httpx.AsyncClient(timeout=10) as client:
        r = await client.get(url, headers=headers, params=params)
        # 디버깅을 위해 비정상인 경우 간단 로그 반환
        if r.status_code != 200:
            try:
                body = r.json()
            except Exception:
                body = {"text": r.text[:200]}
            return {"_error": {"status": r.status_code, "body": body}}
        return r.json()

async def geocode_address_async(address: str) -> tuple[float | None, float | None, dict]:
    """
    1차: 주소검색 /v2/local/search/address.json
    실패 시 2차: 키워드검색 /v2/local/search/keyword.json (주소 문자열 그대로)
    """
    if not RAW_KEY or not address:
        return None, None, {"reason": "no_key_or_address"}

    # 1) 주소 검색
    url_addr = "https://dapi.kakao.com/v2/local/search/address.json"
    data = await _get_json(url_addr, {"query": address})
    if "_error" not in data:
        docs = data.get("documents", [])
        if docs:
            x = float(docs[0]["x"]); y = float(docs[0]["y"])
            return y, x, {"method": "address"}

    # 2) 키워드 검색(주소 그대로 시도)
    url_kw = "https://dapi.kakao.com/v2/local/search/keyword.json"
    data2 = await _get_json(url_kw, {"query": address})
    if "_error" not in data2:
        docs = data2.get("documents", [])
        if docs:
            x = float(docs[0]["x"]); y = float(docs[0]["y"])
            return y, x, {"method": "keyword"} # x=lng, y=lat

    # 실패 로그 리턴
    reason = {"reason": "kakao_no_match", "addr_err": data.get("_error"), "kw_err": data2.get("_error")}
    return None, None, reason

def backoff_sleep(i):
    time.sleep(min(1.0 * (2 ** i), 5.0))
