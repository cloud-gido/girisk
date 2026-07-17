#!/usr/bin/env bash
# Germany vs Paraguay → 本地回放写 Redis → GiRisk Console 敞口看板
#
#   ./scripts/demo-germany-exposure.sh
#   ./scripts/demo-germany-exposure.sh --xlsx "/path/to/Germany vs Paraguay.xlsx"
#   ./scripts/demo-germany-exposure.sh --kafka-dry-run
#
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

XLSX=""
KAFKA_DRY=false
SKIP_CONSOLE=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --xlsx)
      XLSX="${2:-}"
      shift 2
      ;;
    --xlsx=*)
      XLSX="${1#*=}"
      shift
      ;;
    --kafka-dry-run)
      KAFKA_DRY=true
      shift
      ;;
    --skip-console)
      SKIP_CONSOLE=true
      shift
      ;;
    *)
      echo "未知参数: $1" >&2
      exit 1
      ;;
  esac
done

CSV="$ROOT/girisk-engine/src/test/resources/germany-vs-paraguay-orders.csv"
WORKDIR="$ROOT/target/demo-exposure"
SEED_JSON="$WORKDIR/sports-seed.json"
mkdir -p "$WORKDIR"

if [[ -n "$XLSX" ]]; then
  if [[ ! -f "$XLSX" ]]; then
    echo "错误: xlsx 不存在: $XLSX" >&2
    exit 1
  fi
  echo ">>> xlsx → CSV ..."
  python3 - <<PY
import openpyxl, csv
from pathlib import Path
xlsx = Path(r"""$XLSX""")
out = Path(r"""$WORKDIR/germany-vs-paraguay-orders.csv""")
wb = openpyxl.load_workbook(xlsx, data_only=True)
ws = wb.active
n = 0
with out.open("w", newline="", encoding="utf-8") as f:
    w = csv.writer(f)
    w.writerow(["seq", "orderId", "selection", "odds", "stakeYuan"])
    for i, row in enumerate(ws.iter_rows(values_only=True), 1):
        if i == 1:
            continue
        if row[1] is None:
            continue
        seq, oid, _play, _mkt, sel, _line, odds, stake = row[:8]
        w.writerow([int(seq), str(oid), sel, float(odds), f"{float(stake):.2f}"])
        n += 1
print("wrote", out, "orders", n)
PY
  CSV="$WORKDIR/germany-vs-paraguay-orders.csv"
fi

echo ">>> 启动 Redis ..."
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  docker compose up -d redis
else
  echo "警告: Docker 不可用，假定本机 6379 已有 Redis" >&2
fi

for _ in $(seq 1 40); do
  if command -v nc >/dev/null 2>&1 && nc -z 127.0.0.1 "${REDIS_PORT:-6379}" 2>/dev/null; then
    break
  fi
  sleep 0.5
done

echo ">>> 构建 girisk-engine ..."
mvn -q -pl girisk-engine -am -DskipTests package
JAR="$ROOT/girisk-engine/target/girisk-engine-1.0.0.jar"

echo ">>> LocalExposureReplay → Redis + sports-seed.json ..."
java -cp "$JAR" com.girisk.flink.risk.demo.LocalExposureReplayMain \
  --file "$CSV" \
  --redis-host 127.0.0.1 \
  --redis-port "${REDIS_PORT:-6379}" \
  --fixture-id germany-paraguay \
  --home Germany \
  --away Paraguay \
  --delta 0.2 \
  --seed 5000 \
  --max-worst-loss 200000 \
  --seed-out "$SEED_JSON"

if [[ "$KAFKA_DRY" == "true" ]]; then
  echo ">>> OrderFileKafkaPublisher --dry-run（样例 JSON）..."
  java -cp "$JAR" com.girisk.flink.risk.demo.OrderFileKafkaPublisher \
    --file "$CSV" --dry-run --limit 2
fi

if [[ "$SKIP_CONSOLE" == "true" ]]; then
  echo "跳过 Console 启动。访问时请: ./start.sh --exposure-demo --background"
  exit 0
fi

echo ">>> 启动 GiRisk Console (exposure-demo) ..."
./stop.sh 2>/dev/null || true
export REDIS_HOST=127.0.0.1
export REDIS_PORT="${REDIS_PORT:-6379}"
export REDIS_ENABLED=true
./start.sh --exposure-demo --background

PORT="${GIRISK_PORT:-18088}"
echo ">>> 等待 Console 就绪 ..."
for _ in $(seq 1 60); do
  if curl -sf "http://127.0.0.1:${PORT}/api/v1/auth/login" -H 'Content-Type: application/json' \
      -d '{"username":"admin","password":"admin123"}' >/dev/null 2>&1; then
    break
  fi
  sleep 0.5
done

echo ">>> 灌入敞口赛事库 (sports_match + 盘口) ..."
TOKEN=$(curl -sf -X POST "http://127.0.0.1:${PORT}/api/v1/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("data",{}).get("token",""))')
if [[ -z "$TOKEN" || ! -f "$SEED_JSON" ]]; then
  echo "错误: 无法登录或缺少 $SEED_JSON" >&2
  exit 1
fi
curl -sf -X POST "http://127.0.0.1:${PORT}/api/v1/sports/replay/seed" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H 'Content-Type: application/json' \
  --data-binary @"$SEED_JSON" | python3 -m json.tool

echo ""
echo "========================================"
echo " 打开: http://localhost:${PORT}/girisk/exposure"
echo " 账号: admin / admin123"
echo " 高危赛事: Germany vs Paraguay → 可点进盘口下钻"
echo "========================================"
