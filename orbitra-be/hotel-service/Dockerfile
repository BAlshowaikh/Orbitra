# Dockerfile
# Multi-stage build for hotel-service: stage 1 compiles the jar with a full
# Maven+JDK image; stage 2 runs it on a minimal JRE-only image.

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# ---------- Stage 2: run ----------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

# Matches hotel-service's fixed server.port (application.properties).
EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
