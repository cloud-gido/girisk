#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

GIRISK_PORT="${GIRISK_PORT:-18088}"
PID_FILE=".girisk.pid"
DOCKER_ONLY=false

ALL_DOCKER=false
for arg in "$@"; do
  case "$arg" in
    --docker) DOCKER_ONLY=true ;;
    --all-docker) ALL_DOCKER=true ;;
  esac
done

stop_docker() {
  # 默认只停 Console 容器，保留 redis/kafka/mysql 以便本地演示
  if command -v docker >/dev/null 2>&1 && [[ -f docker-compose.yml ]]; then
    if docker compose ps -q girisk 2>/dev/null | grep -q .; then
      docker compose stop girisk >/dev/null 2>&1 || true
      echo "已停止 Docker 服务 girisk"
      return 0
    fi
  fi
  return 1
}

stop_docker_all() {
  if command -v docker >/dev/null 2>&1 && [[ -f docker-compose.yml ]]; then
    if docker compose ps -q 2>/dev/null | grep -q .; then
      docker compose down
      echo "已停止全部 Docker 容器"
      return 0
    fi
  fi
  return 1
}

stop_local() {
  local stopped=false
  if [[ -f "${PID_FILE}" ]]; then
    pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
    if [[ -n "${pid}" ]] && kill -0 "${pid}" 2>/dev/null; then
      kill "${pid}" 2>/dev/null || true
      echo "已停止后台进程 PID=${pid}"
      stopped=true
    fi
    rm -f "${PID_FILE}"
  fi
  if command -v lsof >/dev/null 2>&1; then
    pids="$(lsof -ti:"${GIRISK_PORT}" 2>/dev/null || true)"
    if [[ -n "${pids}" ]]; then
      echo "${pids}" | xargs kill -9 2>/dev/null || true
      echo "已停止占用 ${GIRISK_PORT} 端口的进程"
      stopped=true
    fi
  fi
  [[ "${stopped}" == "true" ]]
}

if [[ "${ALL_DOCKER}" == "true" ]]; then
  stop_docker_all || echo "没有运行中的 Docker 容器"
  exit 0
fi

if [[ "${DOCKER_ONLY}" == "true" ]]; then
  stop_docker || echo "没有运行中的 girisk 容器"
  exit 0
fi

docker_stopped=false
stop_docker && docker_stopped=true
local_stopped=false
stop_local && local_stopped=true

if [[ "${docker_stopped}" == "false" && "${local_stopped}" == "false" ]]; then
  echo "没有运行中的 GiRisk Console 进程"
else
  echo "GiRisk Console 已停止"
fi
