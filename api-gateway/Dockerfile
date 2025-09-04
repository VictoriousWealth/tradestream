FROM eclipse-temurin:21-jre
WORKDIR /app
ARG JAR_FILE=target/api-gateway-*.jar
COPY ${JAR_FILE} app.jar
HEALTHCHECK --interval=30s --timeout=3s --retries=5 CMD curl -fsS http://localhost:8080/actuator/health || exit 1
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]

