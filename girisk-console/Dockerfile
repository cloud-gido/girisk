FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV SERVER_PORT=18088
EXPOSE 18088

# 由宿主机 mvn package 预构建（./start.sh --docker 会自动执行）
COPY target/girisk-1.0.0.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
