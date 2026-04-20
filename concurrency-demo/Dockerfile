# ---- Build stage ----
# Uses the official Gradle image so we don't need to ship a wrapper JAR.
FROM gradle:8.10-jdk21 AS build
WORKDIR /app

# Copy build files first for better layer caching
COPY settings.gradle build.gradle ./

# Warm up dependency cache (best-effort)
RUN gradle --no-daemon dependencies > /dev/null 2>&1 || true

# Copy source and build
COPY src ./src
RUN gradle --no-daemon bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Non-root user
RUN groupadd -r app && useradd -r -g app app
USER app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

# JVM flags worth knowing for a concurrency demo:
#   -XX:+UseZGC          low-latency GC, scales well with many threads
#   -XX:+AlwaysPreTouch  avoid page-fault hiccups under load
# Keep these conservative by default; override via JAVA_OPTS if you want to experiment.
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
