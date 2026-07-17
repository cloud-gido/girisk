FROM flink:2.0.1-java17

# 构建: docker build -f k8s/prod/Dockerfile -t harbor.yfls.internal/flink/football-order-kafka:1.0.0 .
COPY target/flink-kafka-print-1.0-SNAPSHOT.jar /opt/flink/usrlib/app.jar

USER flink
