# recommend/utils/google_places.py
import os
import functools
import httpx

GOOGLE_KEY = os.getenv("GOOGLE_PLACES_API_KEY", "")
LANG = os.getenv("GOOGLE_PLACES_LANGUAGE", "ko")
REGION = os.getenv("GOOGLE_PLACES_REGION", "kr")

FIND_URL = "https://maps.googleapis.com/maps/api/place/findplacefromtext/json"
DETAILS_URL = "https://maps.googleapis.com/maps/api/place/details/json"

def _client():
    return httpx.Client(timeout=8)

@functools.lru_cache(maxsize=5000)
def find_place_id(query: str) -> str | None:
    """문자열(query)로 place_id 조회 (Find Place from Text)"""
    if not GOOGLE_KEY or not query:
        return None
    with _client() as c:
        r = c.get(FIND_URL, params={
            "key": GOOGLE_KEY,
            "input": query,
            "inputtype": "textquery",
            "language": LANG,
            "region": REGION,
            "fields": "place_id"
        })
        r.raise_for_status()
        cands = r.json().get("candidates", [])
        if not cands:
            return None
        return cands[0].get("place_id")

@functools.lru_cache(maxsize=5000)
def get_photo_reference(place_id: str) -> str | None:
    """place_id로 photo_reference 1개 획득 (Place Details)"""
    if not GOOGLE_KEY or not place_id:
        return None
    with _client() as c:
        r = c.get(DETAILS_URL, params={
            "key": GOOGLE_KEY,
            "place_id": place_id,
            "fields": "photos"
        })
        r.raise_for_status()
        photos = r.json().get("result", {}).get("photos", [])
        if not photos:
            return None
        return photos[0].get("photo_reference")

def get_photo_reference_by_name_addr(name: str, address: str) -> str | None:
    """이름+주소 → place_id → photo_reference (모두 캐시됨)"""
    q = f"{(name or '').strip()} {(address or '').strip()}".strip()
    if not q:
        return None
    pid = find_place_id(q)
    if not pid:
        return None
    return get_photo_reference(pid)
