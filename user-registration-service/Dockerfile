# 1. Base image with Java
FROM eclipse-temurin:17-jdk

# 2. Set working directory
WORKDIR /app

# 3. Copy everything from the context (user-registration service files)
COPY . .

# 4. Grant Gradle permission (good for Alpine-based images)
# 5. Build app (skip tests for speed)
RUN chmod +x ./gradlew && ./gradlew clean build -x test

# 6. Expose port used by Spring Boot
EXPOSE 8081

# 7. Run the application using the compiled JAR
CMD ["java", "-jar", "build/libs/user-registration-service-0.0.1-SNAPSHOT.jar"]

