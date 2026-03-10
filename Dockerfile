# =============================================================================
# CareCircle Backend — Dockerfile
# Multi-stage build: fat Maven image for compile, tiny Alpine JRE for runtime.
#
# 🧠 WHY MULTI-STAGE?
# The Maven build image (~700MB) contains compilers, source code, and build tools
# that have zero place in production. The final image only needs the JRE + the
# compiled JAR. Multi-stage drops the runtime image from ~700MB to ~120MB and
# removes the entire attack surface of the build toolchain.
#
# Build:  docker build -t carecircle-backend .
# Run:    docker compose up   (preferred — see docker-compose.yml)
# =============================================================================

# ── Stage 1: BUILD ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# 🧠 Copy pom.xml FIRST, then download dependencies in a separate layer.
# Docker layer cache means: if pom.xml hasn't changed, Maven skips the
# dependency download on every rebuild. This turns a 3-minute build into
# a 10-second one during active development.
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

# Now copy source and build. This layer only rebuilds when src/ changes.
COPY src ./src
RUN mvn package -DskipTests -B --no-transfer-progress

# ── Stage 2: RUNTIME ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

# 🧠 NON-ROOT USER — a security baseline requirement for production containers.
# If an attacker exploits the app, they land as 'appuser' (no sudo, no shell
# access to sensitive OS files) instead of root (owns everything).
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the final JAR from the build stage — nothing else
COPY --from=build /workspace/target/*.jar app.jar

# Hand ownership to the non-root user
RUN chown appuser:appgroup app.jar

USER appuser

# 8080 = Spring Boot default. Documented here so 'docker inspect' shows it.
EXPOSE 8080

# 🧠 JVM tuning flags for containers:
#   -XX:+UseContainerSupport          Read CPU/RAM limits from cgroup, not host hardware
#   -XX:MaxRAMPercentage=75.0         Use up to 75% of container memory for heap
#                                     (leaves 25% for Metaspace, threads, off-heap caches)
#   -Djava.security.egd=...urandom    Faster startup — /dev/random blocks until enough
#                                     entropy; /dev/urandom doesn't block
ENTRYPOINT ["java", \
            "-XX:+UseContainerSupport", \
            "-XX:MaxRAMPercentage=75.0", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-jar", "app.jar"]