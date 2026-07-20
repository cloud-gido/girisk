# 推荐：仓库根多阶段构建（CI / 本地）
#   docker build -f deploy/Dockerfile.engine -t girisk-engine:local .
#   → ghcr.io/<owner>/girisk/girisk-engine:latest
#
# 本文件仅保留「宿主机已打 jar」的快速打包（路径已对齐 monorepo）：
#   mvn -pl girisk-engine -am -DskipTests package
#   docker build -f deploy/flink/prod/Dockerfile -t girisk-engine:local ../..

FROM flink:2.0.1-java17
COPY girisk-engine/target/girisk-engine-1.0.0.jar /opt/flink/usrlib/app.jar
USER flink
