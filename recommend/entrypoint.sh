#!/usr/bin/env bash
set -e

echo "[entrypoint] CWD: $(pwd)"
echo "[entrypoint] Python: $(python --version)"

# 권한 정리(컨테이너 내부 경로에 한함)
chown -R root:root /app || true

# 전처리 입력/출력 경로 (환경변수로 덮어쓰기 가능)
: "${RECO_INPUT:=/app/data/bakeries_raw.csv}"
: "${RECO_OUTPUT:=/app/data/bakeries_clean.csv}"

if [ -f "$RECO_INPUT" ]; then
  echo "[entrypoint] Preprocess start → $RECO_INPUT"
  python /app/preprocess.py || { echo "[entrypoint] preprocess failed"; exit 1; }
else
  echo "[entrypoint] Skip preprocess: input not found ($RECO_INPUT)"
fi

echo "[entrypoint] Launch FastAPI"
exec uvicorn app:app \
  --host 0.0.0.0 \
  --port 8000 \
  --log-level debug \
  --access-log