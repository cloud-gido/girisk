#!/usr/bin/env bash
# 向本地 Kafka 投递 decision.v1 / 风控请求样例（不跑 Flink 时也可喂 Doris/Console）
#
#   ./scripts/demo-decision-kafka.sh                 # 打印样例路径
#   ./scripts/demo-decision-kafka.sh --produce       # 写入 Kafka（默认 127.0.0.1:9094）
#   ./scripts/demo-decision-kafka.sh --produce --bootstrap 127.0.0.1:9092
#
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BOOTSTRAP="${GIRISK_KAFKA_BOOTSTRAP:-127.0.0.1:9094}"
PRODUCE=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --produce) PRODUCE=true; shift ;;
    --bootstrap) BOOTSTRAP="${2:-}"; shift 2 ;;
    --bootstrap=*) BOOTSTRAP="${1#*=}"; shift ;;
    -h|--help)
      sed -n '2,8p' "$0"
      exit 0
      ;;
    *) echo "未知参数: $1" >&2; exit 1 ;;
  esac
done

EX="$ROOT/docs/examples"
echo "样例文件:"
echo "  入站 CSV : $EX/order-risk-check-csv.demo.txt"
echo "  入站 JSON: $EX/order-risk-check-event.json"
echo "  出站 REJECT: $EX/decision-v1-reject-limit.json"
echo "  出站 PASS  : $EX/decision-v1-pass.json"
echo
echo "样例 JSON 见 docs/examples/（不依赖本仓 Engine 源码）"

if [[ "$PRODUCE" != true ]]; then
  exit 0
fi

produce_one() {
  local topic="$1" key="$2" file="$3"
  if command -v kcat >/dev/null 2>&1; then
    kcat -P -b "$BOOTSTRAP" -t "$topic" -k "$key" "$file"
  elif command -v kafka-console-producer.sh >/dev/null 2>&1; then
    # key|value 需自行改；此处仅 value
    kafka-console-producer.sh --bootstrap-server "$BOOTSTRAP" --topic "$topic" <"$file"
  elif docker compose -f "$ROOT/docker-compose.yml" ps kafka >/dev/null 2>&1; then
    docker compose -f "$ROOT/docker-compose.yml" exec -T kafka \
      kafka-console-producer --bootstrap-server kafka:9092 --topic "$topic" <"$file"
  else
    echo "需要 kcat / kafka-console-producer，或 compose 内 kafka 容器" >&2
    exit 1
  fi
  echo "OK → $topic key=$key ($(wc -c <"$file") bytes)"
}

echo "bootstrap=$BOOTSTRAP"
produce_one "girisk.decision.v1" "O10" "$EX/decision-v1-reject-limit.json"
produce_one "girisk.decision.v1" "O20" "$EX/decision-v1-pass.json"
produce_one "girisk.trading.order.risk-check.v1" "O10" "$EX/order-risk-check-event.json"
echo "完成。消费决策可用:"
echo "  kcat -C -b $BOOTSTRAP -t girisk.decision.v1 -o beginning -e"
