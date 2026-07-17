#!/usr/bin/env bash
# GiRisk Console 一键启动（运营台 girisk-console）
#
#   ./start.sh                 自动：MySQL 可达用 mysql profile，否则 H2 local
#   ./start.sh --local           强制 H2 + 关 Redis/Kafka（纯前端联调）
#   ./start.sh --exposure-demo   H2 + Redis（本地敞口回放看板）
#   ./start.sh --mysql           强制 MySQL profile
#   ./start.sh --background      本地后台启动
#   ./start.sh --docker          Docker Compose 部署
#
# Flink 决策引擎见 girisk-engine / docs / deploy/flink
# 敞口回放演示见 scripts/demo-germany-exposure.sh

set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"
APP_DIR="$ROOT/girisk-console"

PROFILE=""
BACKGROUND=false
DOCKER=false
DEMO=false
for arg in "$@"; do
  case "$arg" in
    --local|--no-redis) PROFILE="local" ;;
    --exposure-demo) PROFILE="exposure-demo" ;;
    --mysql) PROFILE="mysql" ;;
    --demo) DEMO=true ;;
    --background|-d) BACKGROUND=true ;;
    --docker) DOCKER=true ;;
  esac
done

GIRISK_PORT="${GIRISK_PORT:-18088}"
REDIS_PORT="${REDIS_PORT:-6379}"
PID_FILE=".girisk.pid"
LOG_DIR="logs"
LOG_FILE="${LOG_DIR}/app.log"
JAR="$APP_DIR/target/girisk-console-1.0.0.jar"

if [[ "${DOCKER}" == "true" ]]; then
  if ! command -v docker >/dev/null 2>&1; then
    echo "错误: 未找到 docker" >&2
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "错误: Docker 未运行，请先启动 Docker Desktop" >&2
    exit 1
  fi

  echo "========================================"
  echo " GiRisk Console Docker 部署"
  echo "========================================"

  if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)"
    export JAVA_HOME
  fi
  if ! command -v mvn >/dev/null 2>&1; then
    echo "错误: Docker 部署需先在宿主机安装 mvn" >&2
    exit 1
  fi

  echo ">>> 宿主机预构建..."
  if command -v npm >/dev/null 2>&1; then
    (cd "$APP_DIR/frontend" && npm install --silent && npm run build)
  else
    echo "错误: 未找到 npm" >&2
    exit 1
  fi
  mvn -q -pl girisk-console -am -DskipTests package
  cp -f "$JAR" "$ROOT/target/girisk-console-1.0.0.jar" 2>/dev/null || mkdir -p "$ROOT/target" && cp -f "$JAR" "$ROOT/target/girisk-console-1.0.0.jar"

  echo ">>> 构建 Docker 镜像并启动容器..."
  export GIRISK_PORT
  docker compose up -d --build

  echo ""
  echo ">>> 平台: http://localhost:${GIRISK_PORT}"
  echo ">>> 账号: admin / admin123"
  echo ">>> 停止: ./stop.sh --docker"
  exit 0
fi

redis_ok=0
if command -v nc >/dev/null 2>&1 && nc -z localhost "${REDIS_PORT}" 2>/dev/null; then
  redis_ok=1
fi

mysql_ok=0
if command -v nc >/dev/null 2>&1 && nc -z localhost "${MYSQL_PORT:-3306}" 2>/dev/null; then
  mysql_ok=1
fi

if [[ -z "${PROFILE}" ]]; then
  if [[ "$mysql_ok" -eq 1 ]]; then
    PROFILE="mysql"
  else
    PROFILE="local"
  fi
fi

for arg in "$@"; do
  case "$arg" in
    --mysql) PROFILE="mysql" ;;
  esac
done

# exposure-demo 依赖 Redis；探测失败时仍强制开启（profile yml 也会开）
if [[ "${PROFILE}" == "exposure-demo" ]]; then
  export REDIS_ENABLED=true
  export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
fi

if [[ "${PROFILE}" == "mysql" && "$mysql_ok" -ne 1 ]]; then
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    echo ">>> MySQL 未就绪，尝试 docker compose up -d mysql ..."
    docker compose up -d mysql
    for _ in $(seq 1 30); do
      if nc -z localhost "${MYSQL_PORT:-3306}" 2>/dev/null; then
        mysql_ok=1
        break
      fi
      sleep 1
    done
  fi
  if [[ "$mysql_ok" -ne 1 ]]; then
    echo "错误: --mysql 需要本机 ${MYSQL_PORT:-3306} 可用" >&2
    exit 1
  fi
fi

echo "========================================"
echo " GiRisk Console（风控决策引擎 · 运营台）"
echo "========================================"
echo " 平台:  http://localhost:${GIRISK_PORT}"
echo " Profile: ${PROFILE}"
echo " Redis: localhost:${REDIS_PORT} $([ "$redis_ok" -eq 1 ] && echo '(可达)' || echo '(不可用→内存)')"
echo " MySQL: localhost:${MYSQL_PORT:-3306} $([ "$mysql_ok" -eq 1 ] && echo '(可达)' || echo '(不可用→H2 local)')"
echo " 账号:  admin / admin123"
echo " Flink:  mvn -pl girisk-engine -am package && flink run girisk-engine/target/girisk-engine-1.0.0.jar"
echo "========================================"

if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)"
  export JAVA_HOME
fi
if [[ -z "${JAVA_HOME:-}" ]] || ! command -v java >/dev/null 2>&1; then
  echo "错误: 未找到 Java，请安装 JDK 21+" >&2
  exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "错误: 未找到 mvn" >&2
  exit 1
fi

echo ""
echo ">>> 构建前端 ..."
if command -v npm >/dev/null 2>&1; then
  (cd "$APP_DIR/frontend" && npm install --silent && npm run build)
fi

echo ">>> 编译后端 (girisk-console) ..."
mvn -q -pl girisk-console -am -DskipTests package

if command -v lsof >/dev/null 2>&1; then
  lsof -ti:"${GIRISK_PORT}" | xargs kill -9 2>/dev/null || true
fi
if [[ -f "${PID_FILE}" ]]; then
  old_pid="$(cat "${PID_FILE}" 2>/dev/null || true)"
  if [[ -n "${old_pid}" ]] && kill -0 "${old_pid}" 2>/dev/null; then
    kill "${old_pid}" 2>/dev/null || true
    sleep 1
  fi
  rm -f "${PID_FILE}"
fi

export SERVER_PORT="${GIRISK_PORT}"
export REDIS_HOST="${REDIS_HOST:-localhost}"
export REDIS_PORT="${REDIS_PORT}"
if [[ "${PROFILE}" == "exposure-demo" ]]; then
  export REDIS_ENABLED=true
elif [[ "$redis_ok" -eq 1 ]]; then
  export REDIS_ENABLED=true
else
  export REDIS_ENABLED=false
fi
ACTIVE_PROFILES="${PROFILE}"
if [[ "${DEMO}" == "true" ]]; then
  if [[ -n "${ACTIVE_PROFILES}" ]]; then
    ACTIVE_PROFILES="${ACTIVE_PROFILES},demo"
  else
    ACTIVE_PROFILES="demo"
  fi
fi
if [[ -n "${ACTIVE_PROFILES}" ]]; then
  export SPRING_PROFILES_ACTIVE="${ACTIVE_PROFILES}"
fi

if [[ "${BACKGROUND}" == "true" ]]; then
  mkdir -p "${LOG_DIR}"
  echo ""
  echo ">>> 模式: 本地后台运行"
  nohup java -jar "${JAR}" >> "${LOG_FILE}" 2>&1 &
  echo $! > "${PID_FILE}"
  echo ">>> 已启动 PID=$(cat "${PID_FILE}")"
  echo ">>> 日志: ${LOG_FILE}"
  echo ">>> 访问 http://localhost:${GIRISK_PORT}"
  echo ">>> 停止: ./stop.sh"
  exit 0
fi

echo ""
echo ">>> 模式: 本地前台运行"
echo ">>> 访问 http://localhost:${GIRISK_PORT}"
echo ""

exec java -jar "${JAR}"
