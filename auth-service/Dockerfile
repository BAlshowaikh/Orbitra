# Dockerfile
# Multi-stage build for auth-service: stage 1 compiles the jar with a full
# Maven+JDK image; stage 2 runs it on a minimal JRE-only image. The final
# image never contains Maven, the JDK compiler, or source code - only the
# built jar and a JRE to run it.

# ---------- Stage 1: build ----------
# Pinned to a specific version, never :latest - reproducible builds matter
# more than always having the newest tag.
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy only pom.xml first and download dependencies as their own layer.
# Docker caches layers - as long as pom.xml doesn't change, this dependency
# download is skipped on rebuilds, and only the (much faster) source compile
# below re-runs when you edit code.
COPY pom.xml .
# The below line check the copied pom file and download all dependencies 
# to the local Maven repository, so that the next step can build the project 
# without needing to download dependencies again and internet access
RUN mvn dependency:go-offline 

# Now copy the actual source and build.
COPY src ./src
# The below line skipped tests for build speed 
RUN mvn clean package -DskipTests

# ---------- Stage 2: run ----------
# Alpine-based JRE (no JDK, no Maven, no compiler) - smaller image, smaller
# attack surface than shipping the build stage's tools to production.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Below Two Lines for security reasons: Don't run as root inside the container 
#- if the app is ever compromised, it isn't running with root privileges.
RUN addgroup -S spring && adduser -S spring -G spring
# The below means from this point on, everything
# runs as this unprivileged spring user instead.
USER spring:spring

# Copy just the built jar from stage 1 - none of Maven, the JDK, or the
# source tree make it into this final image.
COPY --from=build /app/target/*.jar app.jar

# Documents that the container listens on port 8081 - matches auth-service's
# fixed server.port (application.properties), not Docker's old default of 8080.
EXPOSE 8081

# Exec form (JSON array), not shell form ("java -jar app.jar") - this makes
# java PID 1 directly, so it receives SIGTERM from `docker stop` and can shut
# down gracefully instead of being killed abruptly after a timeout.
ENTRYPOINT ["java", "-jar", "app.jar"]
