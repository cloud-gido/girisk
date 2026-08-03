#!/usr/bin/env bash
# GiRisk Console 一键启动（运营台 girisk-console）
#
#   ./start.sh                 自动：PostgreSQL 可达用 postgres profile，否则 H2 local
#   ./start.sh --local           强制 H2 + 关 Redis/Kafka（纯前端联调）
#   ./start.sh --exposure-demo   H2 + Redis（本地敞口回放看板）
#   ./start.sh --postgres        强制 PostgreSQL profile（对齐 GIDO PG16）
#   ./start.sh --background      本地后台启动
#   ./start.sh --docker          Docker Compose 部署 Console 容器
#   ./start.sh --stack           一键：PG+Kafka+Redis+Doris + Console（对齐 gido 家族镜像）
#
# Engine 在同仓 girisk-engine/（GitLab 全量；公开 GitHub 用 sync-github 排除）
# 敞口演示：./start.sh --exposure-demo

set -euo pipefail
cd "$(dirname "$0")"
ROOT="$(pwd)"
APP_DIR="$ROOT/girisk-console"

PROFILE=""
BACKGROUND=false
DOCKER=false
DEMO=false
STACK=false
for arg in "$@"; do
  case "$arg" in
    --local|--no-redis) PROFILE="local" ;;
    --exposure-demo) PROFILE="exposure-demo" ;;
    --postgres|--mysql) PROFILE="postgres" ;; # --mysql 兼容旧习惯，实际为 PG
    --demo) DEMO=true ;;
    --background|-d) BACKGROUND=true ;;
    --docker) DOCKER=true ;;
    --stack|--full-stack) STACK=true ;;
  esac
done

GIRISK_PORT="${GIRISK_PORT:-18088}"
REDIS_PORT="${REDIS_PORT:-6379}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
KAFKA_EXTERNAL_PORT="${KAFKA_EXTERNAL_PORT:-9094}"
PID_FILE=".girisk.pid"
LOG_DIR="logs"
LOG_FILE="${LOG_DIR}/app.log"
JAR="$APP_DIR/target/girisk-console-1.0.0.jar"

if [[ "${STACK}" == "true" ]]; then
  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo "错误: --stack 需要 Docker 运行中" >&2
    exit 1
  fi
  if [[ -z "${JAVA_HOME:-}" ]] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
    JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null || true)"
    export JAVA_HOME
  fi
  if ! command -v java >/dev/null 2>&1 || ! command -v mvn >/dev/null 2>&1; then
    echo "错误: --stack 需要本机 JDK 21+ 与 Maven" >&2
    exit 1
  fi

  echo "========================================"
  echo " GiRisk 全栈一键启动（对齐 GIDO 家族镜像）"
  echo "========================================"
  echo ">>> Docker: PostgreSQL 16 + Kafka + Redis 7 + Doris FE/BE + Routine Load"
  docker compose --profile doris up -d

  echo ">>> 等待 PostgreSQL / Kafka / Redis ..."
  for _ in $(seq 1 60); do
    pg_h=0; kafka_h=0; redis_h=0
    docker compose ps --format json 2>/dev/null | grep -q '"Service":"postgres".*"Health":"healthy"' && pg_h=1 || true
    docker inspect -f '{{.State.Health.Status}}' girisk-postgres 2>/dev/null | grep -qx healthy && pg_h=1 || true
    docker inspect -f '{{.State.Health.Status}}' girisk-kafka 2>/dev/null | grep -qx healthy && kafka_h=1 || true
    docker inspect -f '{{.State.Health.Status}}' girisk-redis 2>/dev/null | grep -qx healthy && redis_h=1 || true
    if [[ "$pg_h" -eq 1 && "$kafka_h" -eq 1 && "$redis_h" -eq 1 ]]; then
      break
    fi
    sleep 2
  done

  echo ">>> 确保审计 Topic 存在 ..."
  for t in girisk.decision.v1 girisk.config.v1 girisk.trading.order.risk-check.post.v1 girisk.order.event; do
    docker exec girisk-kafka /opt/kafka/bin/kafka-topics.sh \
      --bootstrap-server localhost:9092 --create --if-not-exists \
      --topic "$t" --partitions 3 --replication-factor 1 >/dev/null 2>&1 || true
  done

  echo ">>> 构建 Console ..."
  if command -v npm >/dev/null 2>&1; then
    (cd "$APP_DIR/frontend" && npm install --silent && npm run build)
  fi
  mvn -q -pl girisk-console -am -DskipTests package

  if command -v lsof >/dev/null 2>&1; then
    lsof -ti:"${GIRISK_PORT}" | xargs kill -9 2>/dev/null || true
  fi
  [[ -f "${PID_FILE}" ]] && rm -f "${PID_FILE}"

  export SERVER_PORT="${GIRISK_PORT}"
  export SPRING_PROFILES_ACTIVE=postgres
  export SQL_INIT_MODE="${SQL_INIT_MODE:-always}"
  export POSTGRES_HOST=127.0.0.1
  export POSTGRES_PORT="${POSTGRES_PORT}"
  export POSTGRES_DB=girisk
  export POSTGRES_USER=girisk
  export POSTGRES_PASSWORD=girisk
  export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT}/girisk"
  export SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
  export SPRING_DATASOURCE_USERNAME=girisk
  export SPRING_DATASOURCE_PASSWORD=girisk
  export REDIS_ENABLED=true
  export REDIS_HOST=127.0.0.1
  export REDIS_PORT="${REDIS_PORT}"
  export KAFKA_ENABLED=true
  export KAFKA_BOOTSTRAP="127.0.0.1:${KAFKA_EXTERNAL_PORT}"
  export GIRISK_AUDIT_DORIS_ENABLED=true
  export GIRISK_AUDIT_DORIS_JDBC_URL='jdbc:mysql://127.0.0.1:9030/girisk?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
  export GIRISK_AUDIT_DORIS_USERNAME=root
  export GIRISK_AUDIT_DORIS_PASSWORD=

  mkdir -p "${LOG_DIR}"
  nohup java -jar "${JAR}" >> "${LOG_FILE}" 2>&1 &
  echo $! > "${PID_FILE}"

  echo ""
  echo ">>> 已启动"
  echo "    Console  http://localhost:${GIRISK_PORT}   (admin / admin123)"
  echo "    Postgres localhost:${POSTGRES_PORT}  · Redis :${REDIS_PORT}"
  echo "    Doris FE http://localhost:8030  · MySQL协议 :9030（审计）"
  echo "    Kafka    宿主机 127.0.0.1:${KAFKA_EXTERNAL_PORT}（容器内 kafka:9092）"
  echo "    PID=$(cat "${PID_FILE}")  日志=${LOG_FILE}"
  echo ">>> 停止: ./stop.sh --stack"
  echo ">>> Doris BE 冷启动较慢时，可稍后执行: ./scripts/verify-doris-audit.sh"
  exit 0
fi

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
  echo ">>> 多阶段构建镜像（Node + Maven 在 Docker 内，无需宿主机 mvn）..."
  export GIRISK_PORT
  docker compose --profile full up -d --build

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

pg_ok=0
if command -v nc >/dev/null 2>&1 && nc -z localhost "${POSTGRES_PORT}" 2>/dev/null; then
  pg_ok=1
fi

if [[ -z "${PROFILE}" ]]; then
  if [[ "$pg_ok" -eq 1 ]]; then
    PROFILE="postgres"
  else
    PROFILE="local"
  fi
fi

for arg in "$@"; do
  case "$arg" in
    --postgres|--mysql) PROFILE="postgres" ;;
  esac
done

# exposure-demo 依赖 Redis；探测失败时仍强制开启（profile yml 也会开）
if [[ "${PROFILE}" == "exposure-demo" ]]; then
  export REDIS_ENABLED=true
  export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
fi

if [[ "${PROFILE}" == "postgres" && "$pg_ok" -ne 1 ]]; then
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    echo ">>> PostgreSQL 未就绪，尝试 docker compose up -d postgres ..."
    docker compose up -d postgres
    for _ in $(seq 1 30); do
      if nc -z localhost "${POSTGRES_PORT}" 2>/dev/null; then
        pg_ok=1
        break
      fi
      sleep 1
    done
  fi
  if [[ "$pg_ok" -ne 1 ]]; then
    echo "错误: --postgres 需要本机 ${POSTGRES_PORT} 可用" >&2
    exit 1
  fi
fi

echo "========================================"
echo " GiRisk Console（风控运营台）"
echo "========================================"
echo " 平台:  http://localhost:${GIRISK_PORT}"
echo " Profile: ${PROFILE}"
echo " Redis: localhost:${REDIS_PORT} $([ "$redis_ok" -eq 1 ] && echo '(可达 · 7.0-alpine)' || echo '(不可用→内存)')"
echo " Postgres: localhost:${POSTGRES_PORT} $([ "$pg_ok" -eq 1 ] && echo '(可达 · 16)' || echo '(不可用→H2 local)')"
echo " 账号:  admin / admin123"
echo " Engine: mvn -pl girisk-engine -am package → gido 实时作业"
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
if [[ "${PROFILE}" == "postgres" ]]; then
  export POSTGRES_HOST="${POSTGRES_HOST:-127.0.0.1}"
  export POSTGRES_PORT="${POSTGRES_PORT}"
  export POSTGRES_DB="${POSTGRES_DB:-girisk}"
  export POSTGRES_USER="${POSTGRES_USER:-girisk}"
  export POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-girisk}"
  # 覆盖可能残留的 MySQL JDBC，避免 Driver 拒连
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"
  export SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
  export SPRING_DATASOURCE_USERNAME="${POSTGRES_USER}"
  export SPRING_DATASOURCE_PASSWORD="${POSTGRES_PASSWORD}"
fi
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
