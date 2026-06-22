# Build stage: compile the Spring Boot app
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Build the JAR (skip tests for speed in CI)
RUN mvn clean package -DskipTests

# Runtime stage: lightweight JDK + the compiled JAR
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copy the JAR from the builder stage
COPY --from=builder /app/target/orderms-*.jar app.jar

# Expose the port (must match your application.yml server.port)
EXPOSE 8020

# Health check (Spring Boot Actuator)
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8020/api/actuator/health || exit 1

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
