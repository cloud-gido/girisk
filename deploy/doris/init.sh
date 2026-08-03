#!/usr/bin/env bash
# 等待 Doris FE/BE 就绪，建表并启动 Routine Load（消费 Kafka）
set -euo pipefail

FE_HOST="${DORIS_FE_HOST:-doris-fe}"
FE_PORT="${DORIS_FE_PORT:-9030}"
BE_IP="${DORIS_BE_IP:-172.28.0.3}"
BE_PORT="${DORIS_BE_PORT:-9050}"
BE_WAIT_MAX="${DORIS_BE_WAIT_MAX:-360}"
BE_WAIT_INTERVAL="${DORIS_BE_WAIT_INTERVAL:-5}"
BE_STABLE_ROUNDS="${DORIS_BE_STABLE_ROUNDS:-24}"
CREATE_TABLE_RETRIES="${DORIS_CREATE_TABLE_RETRIES:-15}"

mysql_fe() {
  mysql -h "$FE_HOST" -P "$FE_PORT" -uroot "$@"
}

count_alive_backends() {
  mysql_fe -e "SHOW BACKENDS\G" 2>/dev/null | grep -c "Alive: true" || true
}

echo "[doris-init] waiting FE ${FE_HOST}:${FE_PORT}..."
for i in $(seq 1 90); do
  if mysql_fe --connect-timeout=3 -e "SELECT 1" >/dev/null 2>&1; then
    break
  fi
  sleep 3
done
mysql_fe -e "SELECT 1"

echo "[doris-init] ensure BE ${BE_IP}:${BE_PORT}..."
if ! mysql_fe -N -e "SHOW BACKENDS" 2>/dev/null | grep -q "${BE_IP}"; then
  mysql_fe -e "ALTER SYSTEM ADD BACKEND \"${BE_IP}:${BE_PORT}\";" || true
fi

echo "[doris-init] waiting BE alive (max ${BE_WAIT_MAX}×${BE_WAIT_INTERVAL}s)..."
ALIVE=0
for i in $(seq 1 "$BE_WAIT_MAX"); do
  ALIVE=$(count_alive_backends)
  if [[ "$ALIVE" -ge 1 ]]; then
    echo "[doris-init] BE alive (count=$ALIVE)"
    break
  fi
  if (( i % 12 == 0 )); then
    echo "[doris-init] waiting BE ($i/${BE_WAIT_MAX}, alive=$ALIVE)..."
  fi
  sleep "$BE_WAIT_INTERVAL"
done

if [[ "$ALIVE" -lt 1 ]]; then
  echo "[doris-init] ERROR: no alive backend after wait" >&2
  mysql_fe -e "SHOW BACKENDS\G" || true
  exit 1
fi

echo "[doris-init] wait BE stable (${BE_STABLE_ROUNDS}×${BE_WAIT_INTERVAL}s)..."
for i in $(seq 1 "$BE_STABLE_ROUNDS"); do
  sleep "$BE_WAIT_INTERVAL"
  ALIVE=$(count_alive_backends)
  if [[ "$ALIVE" -lt 1 ]]; then
    echo "[doris-init] BE lost during stabilize ($i/${BE_STABLE_ROUNDS})" >&2
    exit 1
  fi
done

echo "[doris-init] create tables..."
for i in $(seq 1 "$CREATE_TABLE_RETRIES"); do
  if mysql_fe < /sql/01_create_tables.sql 2>/dev/null; then
    echo "[doris-init] tables ok"
    break
  fi
  echo "[doris-init] create tables retry ($i/${CREATE_TABLE_RETRIES})..."
  sleep 10
  if [[ "$i" == "$CREATE_TABLE_RETRIES" ]]; then
    mysql_fe < /sql/01_create_tables.sql
  fi
done

routine_exists() {
  mysql_fe girisk -N -e "SHOW ROUTINE LOAD;" 2>/dev/null | grep -q "$1"
}

echo "[doris-init] start routine load..."
if ! routine_exists load_risk_decision; then
  mysql_fe < /sql/02_routine_load.docker.sql
else
  echo "[doris-init] routine loads already exist"
fi

echo "[doris-init] routine load status:"
mysql_fe girisk -e "SHOW ROUTINE LOAD\G" 2>/dev/null | head -80 || true
echo "[doris-init] done"
