# syntax=docker/dockerfile:1

# ---------- build ----------
# Samakan versi JDK dengan java.version di pom.xml.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependensi diunduh di layer terpisah agar cache terpakai selama pom.xml tidak berubah.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B dependency:go-offline

COPY src src
# Test dijalankan di CI (butuh Docker untuk Testcontainers), bukan di dalam build image.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -DskipTests package

# ---------- runtime ----------
FROM eclipse-temurin:21-jre
# curl hanya untuk HEALTHCHECK
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --no-create-home interval

WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar

USER interval
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fs http://localhost:8080/actuator/health/readiness || exit 1

# MaxRAMPercentage: JVM menghormati batas memori container. UTC agar log dan jam konsisten.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=UTC", "-jar", "app.jar"]
