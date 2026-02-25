# =============================================================================
# Stage 1 — Build
# =============================================================================
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder

WORKDIR /workspace

# Cache de dependências antes de copiar o código-fonte
COPY pom.xml .
RUN mvn dependency:go-offline -B --no-transfer-progress

COPY src ./src
RUN mvn package -DskipTests --no-transfer-progress

# =============================================================================
# Stage 2 — Runtime
# =============================================================================
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="FIAP" \
      application="vp-processor-worker" \
      version="0.0.1" \
      description="Worker de processamento de vídeo — extração de frames via FFmpeg e upload para S3"

# Instala FFmpeg e utilitários mínimos
RUN apk add --no-cache ffmpeg tini

# Usuário não-root para execução segura
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /workspace/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

# Variáveis de ambiente padrão
ENV FFMPEG_PATH=/usr/bin/ffmpeg \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Tini como PID 1 — garante repasse correto de sinais para a JVM
ENTRYPOINT ["/sbin/tini", "--"]
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
