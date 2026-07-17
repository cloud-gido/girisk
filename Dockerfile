FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV SERVER_PORT=18088
EXPOSE 18088

# 由宿主机 mvn -pl girisk-console -am package 预构建（./start.sh --docker）
COPY target/girisk-console-1.0.0.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
