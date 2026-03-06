# =============================================================================
# hack-vp-processor-worker — Dockerfile
# Spring Boot 4 + Java 21 + FFmpeg (extração de frames) + S3 + Kafka consumer
# Multi-stage build: build → runtime
# =============================================================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x ./mvnw \
    && ./mvnw dependency:go-offline -B --no-transfer-progress

COPY src/ ./src/

RUN ./mvnw package -DskipTests -B --no-transfer-progress

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="FIAP Hackathon"
LABEL app="hack-vp-processor-worker"
LABEL version="0.0.1-SNAPSHOT"
LABEL description="Worker de processamento de vídeo — extração de frames via FFmpeg e upload para S3"

RUN apk add --no-cache dumb-init ffmpeg

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENV FFMPEG_PATH=/usr/bin/ffmpeg \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["dumb-init", "--"]
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
