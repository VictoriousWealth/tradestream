# 1. Base image with Java
FROM eclipse-temurin:17-jdk

# 2. Install curl + unzip, set workdir, copy Gradle wrapper, clear caches, and set permissions
WORKDIR /app
COPY gradlew .
COPY gradle gradle
RUN apt-get update && apt-get install -y curl unzip \
    && rm -rf /var/lib/apt/lists/* \
    && chmod +x gradlew \
    && rm -rf /root/.gradle/wrapper/dists

# 3. Copy the rest of the project
COPY . .

# 4. Pre-download dependencies (cached if only code changes)
RUN ./gradlew dependencies --no-daemon || true

# 5. Build app (skip tests for speed)
RUN ./gradlew clean build -x test --no-daemon

# 6. Expose port used by Spring Boot
EXPOSE 8082

# 7. Run the application using the compiled JAR
CMD ["java", "-jar", "build/libs/auth-0.0.1-SNAPSHOT.jar"]
