# ── Stage 1: Build ──────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Cache dependency layer: copy POM first, download deps, then copy source
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f pom.xml dependency:go-offline -B 2>/dev/null || true

# Install Maven (Alpine ships without it)
RUN apk add --no-cache maven

COPY src ./src
RUN mvn -f pom.xml clean package -DskipTests -B

# ── Stage 2: Runtime ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security (Zero-Trust)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

COPY --from=builder /app/target/journaling-backend-1.0.0.jar app.jar

# JVM flags tuned for containers + Virtual Threads:
#   -XX:+UseContainerSupport   — respect cgroup memory limits
#   -XX:MaxRAMPercentage=75    — use 75% of container RAM for heap
#   -Djdk.tracePinnedThreads   — warn on Virtual Thread pinning (debug aid)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -Dspring.profiles.active=docker"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
