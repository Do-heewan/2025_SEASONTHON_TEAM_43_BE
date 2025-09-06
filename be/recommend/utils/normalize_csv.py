# recommend/utils/normalize_csv.py
import re
import math

def _is_nan(x) -> bool:
    # pandas NaN, float('nan') 모두 안전 체크
    return isinstance(x, float) and math.isnan(x)

def normalize_text(s) -> str:
    """
    어떤 타입이 와도 안전하게 문자열로 정규화.
    - None, NaN -> ""
    - 양끝 공백 제거, 다중 공백을 단일 공백으로
    """
    if s is None or _is_nan(s):
        return ""
    # 숫자/기타 타입도 문자열로 변환
    s = str(s).strip()
    s = re.sub(r"\s+", " ", s)
    return s
