#!/usr/bin/env bash
# 校验本地 Doris 审计三表与 Routine Load 状态
set -euo pipefail

FE_HOST="${DORIS_FE_HOST:-127.0.0.1}"
FE_PORT="${DORIS_FE_PORT:-9030}"

mysql_fe() {
  mysql -h "$FE_HOST" -P "$FE_PORT" -uroot "$@"
}

echo "== FE connectivity =="
mysql_fe -e "SELECT 1 AS ok;"

echo "== BACKENDS =="
mysql_fe -e "SHOW BACKENDS\G" | grep -E 'Host|Alive|ErrMsg' || true

echo "== ROUTINE LOAD =="
mysql_fe girisk -e "SHOW ROUTINE LOAD\G" | grep -E 'Name:|State:|TopicName:|LoadedRows:|ErrorLogUrls:' || true

echo "== row counts =="
mysql_fe girisk -e "
SELECT 'risk_decision_log' AS tbl, COUNT(*) AS cnt FROM risk_decision_log
UNION ALL
SELECT 'risk_config_log', COUNT(*) FROM risk_config_log
UNION ALL
SELECT 'risk_order_status_log', COUNT(*) FROM risk_order_status_log;
"

echo "== sample decisions (latest 5) =="
mysql_fe girisk -e "
SELECT decision_date, order_id, decision, left(raw, 80) AS raw_prefix
FROM risk_decision_log
ORDER BY decision_time DESC
LIMIT 5;
" 2>/dev/null || echo "(empty or query failed)"

echo "verify-doris-audit: done"
