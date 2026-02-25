# vp-processor-worker

Worker responsável pelo processamento assíncrono de vídeos: extração de frames via **FFmpeg**, compactação em ZIP e upload para **AWS S3**.

---

## Sumário

1. [Visão Geral](#visão-geral)
2. [Arquitetura](#arquitetura)
3. [Stack Tecnológica](#stack-tecnológica)
4. [Estrutura do Projeto](#estrutura-do-projeto)
5. [Contrato de Mensagens (Kafka)](#contrato-de-mensagens-kafka)
6. [Variáveis de Ambiente](#variáveis-de-ambiente)
7. [Instruções de Execução](#instruções-de-execução)

---

## Visão Geral

O `vp-processor-worker` é um microsserviço **orientado a eventos** que:

1. Consome mensagens de processamento de um tópico Kafka.
2. Verifica a existência do vídeo no bucket S3.
3. Gera uma **presigned URL** (validade de 6 horas) para leitura segura do vídeo.
4. Executa o **FFmpeg** localmente (1 frame/segundo, formato MJPEG).
5. Detecta limites de quadros JPEG no stream binário (marcadores `0xFFD8` / `0xFFD9`).
6. Escreve cada frame em um arquivo ZIP via streaming.
7. Realiza upload multipart do ZIP para o S3 (partes de 8 MB).
8. Utiliza **Virtual Threads** (Java 21) para processamento concorrente sem bloqueio de threads de plataforma.

---

## Arquitetura

### Padrão: Hexagonal (Ports & Adapters)

```
╔══════════════════════════════════════════════════════════════════╗
║                    vp-processor-worker                           ║
║                                                                  ║
║  ┌──────────────────────────────────────────────────────────┐   ║
║  │  Infrastructure — Adapters                               │   ║
║  │                                                          │   ║
║  │  INPUT                          OUTPUT                   │   ║
║  │  ┌─────────────────────┐       ┌────────────────────┐   │   ║
║  │  │  ProcessorListener  │       │   S3OutputImpl     │   │   ║
║  │  │  (Kafka Consumer)   │       │  (FFmpeg + S3)     │   │   ║
║  │  └────────┬────────────┘       └────────────────────┘   │   ║
║  └───────────┼────────────────────────────────────────────  ┘   ║
║              │                                                   ║
║  ┌───────────▼──────────────────────────────────────────────┐   ║
║  │  Application — Use Cases & Ports                         │   ║
║  │                                                          │   ║
║  │  ┌────────────────────────┐   ┌──────────────────────┐  │   ║
║  │  │ VideoProcessorInputPort│   │     S3Output         │  │   ║
║  │  │  (Port In — executa    │   │  (Port Out — upload) │  │   ║
║  │  │   em virtual thread)   │   └──────────────────────┘  │   ║
║  │  └────────────┬───────────┘                              │   ║
║  └───────────────┼──────────────────────────────────────────┘   ║
║                  │                                               ║
║  ┌───────────────▼──────────────────────────────────────────┐   ║
║  │  Domain — Business Logic                                  │   ║
║  │                                                          │   ║
║  │  ┌────────────────────────────────────────────────────┐  │   ║
║  │  │            VideoProcessorServiceImpl               │  │   ║
║  │  │  1. Verifica existência do vídeo no S3             │  │   ║
║  │  │  2. Delega extração de frames para S3OutputImpl    │  │   ║
║  │  └────────────────────────────────────────────────────┘  │   ║
║  └──────────────────────────────────────────────────────────┘   ║
╚══════════════════════════════════════════════════════════════════╝
```

### Fluxo de Dados

```
 Produtor Externo
      │
      │  JSON: { uploadId, key }
      ▼
 ┌──────────┐      consume      ┌──────────────────────┐
 │  Kafka   │ ───────────────► │  ProcessorListener   │
 │  Topic   │                  │  (grupo: video-       │
 └──────────┘                  │   processor)          │
                               └──────────┬───────────┘
                                          │ ProcessRequest
                                          ▼
                               ┌──────────────────────┐
                               │VideoProcessorInput   │
                               │Port.execute()        │
                               │ (Virtual Thread)     │
                               └──────────┬───────────┘
                                          │
                                          ▼
                               ┌──────────────────────┐
                               │VideoProcessorService │
                               │Impl.execute()        │
                               │ ✓ exists() em S3     │
                               └──────────┬───────────┘
                                          │
                                          ▼
                               ┌──────────────────────┐
                               │  S3OutputImpl        │
                               │  1. Presigned URL    │
                               │  2. FFmpeg process   │
                               │  3. Frame -> ZIP     │
                               │  4. Multipart Upload │
                               └──────────┬───────────┘
                                          │
                                          ▼
                                   ┌────────────┐
                                   │  AWS S3    │
                                   │  (ZIP)     │
                                   └────────────┘
```

### Concorrência

```
 Kafka Listener Thread
        │
        ▼
 ExecutorService (Virtual Threads — Java 21)
        │
        ├── Video 1 ──► VideoProcessorServiceImpl ──► FFmpeg ──► S3
        ├── Video 2 ──► VideoProcessorServiceImpl ──► FFmpeg ──► S3
        └── Video N ──► VideoProcessorServiceImpl ──► FFmpeg ──► S3
```

---

## Stack Tecnológica

| Camada              | Tecnologia                          | Versão    |
|---------------------|-------------------------------------|-----------|
| Linguagem           | Java                                | 21        |
| Framework           | Spring Boot                         | 4.0.3     |
| Build               | Maven                               | 3.9.x     |
| Message Broker      | Apache Kafka                        | 3.x       |
| Cache / Estado      | Redis (Lettuce)                     | 7.x       |
| Cloud Storage       | AWS SDK v2 — S3                     | 2.41.31   |
| Processamento Video | FFmpeg                              | 6.x+      |
| Serialização JSON   | Google Gson                         | 2.x       |
| Concorrência        | Java Virtual Threads (Project Loom) | JDK 21    |
| Container Runtime   | Docker / Docker Compose             | —         |

---

## Estrutura do Projeto

```
hack-vp-processor-worker/
├── infra/
│   ├── Dockerfile              # Imagem multi-stage com FFmpeg
│   ├── docker-compose.yml      # Stack completa (Kafka, Redis, Worker)
│   └── .env.example            # Template de variáveis de ambiente
├── src/
│   └── main/
│       ├── java/com/fiap/vp_processor_worker/
│       │   ├── VpProcessorWorkerApplication.java
│       │   ├── application/
│       │   │   ├── ports/
│       │   │   │   ├── input/
│       │   │   │   │   └── VideoProcessorInputPort.java   # Port de entrada (use case)
│       │   │   │   └── output/
│       │   │   │       └── S3Output.java                  # Port de saída (contrato S3)
│       │   │   └── usecase/
│       │   │       └── VideoProcessorUseCase.java         # Interface do caso de uso
│       │   ├── domain/
│       │   │   └── service/
│       │   │       ├── VideoProcessorService.java         # Interface de domínio
│       │   │       ├── model/
│       │   │       │   └── ProcessRequest.java            # Modelo de domínio
│       │   │       └── impl/
│       │   │           └── VideoProcessorServiceImpl.java # Lógica de negócio
│       │   └── infrastructure/
│       │       ├── adapter/
│       │       │   ├── input/
│       │       │   │   └── ProcessorListener.java         # Consumidor Kafka
│       │       │   └── output/
│       │       │       ├── S3OutputImpl.java              # FFmpeg + S3 Multipart
│       │       │       └── entities/
│       │       │           └── MultipartUploadOutputStream.java
│       │       └── config/
│       │           ├── AsyncConfig.java                   # Virtual Threads Executor
│       │           ├── GsonConfig.java
│       │           ├── KafkaConfig.java
│       │           ├── RedisConfig.java
│       │           └── S3Config.java
│       └── resources/
│           └── application.yaml
└── pom.xml
```

---

## Contrato de Mensagens (Kafka)

> Este serviço não expõe endpoints HTTP. A interface de entrada é exclusivamente via **Apache Kafka**.

### Tópico de Consumo

| Propriedade    | Valor                                                   |
|----------------|---------------------------------------------------------|
| Tópico         | `video-processor-topic` (env: `VIDEO_PROCESSOR_TOPIC`) |
| Grupo          | `video-processor` (env: `VIDEO_PROCESSOR_GROUP_ID`)    |
| Auto-offset    | `earliest` (env: `AUTO_OFFSET_RESET_CONFIG`)           |
| Deserialização | JSON via Google Gson                                    |

### Schema da Mensagem — `ProcessRequest`

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "ProcessRequest",
  "description": "Solicitação de processamento de vídeo",
  "type": "object",
  "required": ["uploadId", "key"],
  "properties": {
    "uploadId": {
      "type": "string",
      "format": "uuid",
      "description": "Identificador único do upload",
      "example": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
    },
    "key": {
      "type": "string",
      "description": "Chave do objeto de vídeo no bucket S3",
      "example": "videos/3fa85f64-5717-4562-b3fc-2c963f66afa6/input.mp4"
    }
  }
}
```

#### Exemplo de Payload

```json
{
  "uploadId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "key": "videos/3fa85f64-5717-4562-b3fc-2c963f66afa6/input.mp4"
}
```

### Comportamento por Cenário

| Cenário                          | Comportamento                                              |
|----------------------------------|------------------------------------------------------------|
| Vídeo encontrado no S3           | Extrai frames, compacta ZIP, faz upload para S3            |
| Vídeo **não encontrado** no S3   | Lança `RuntimeException` — Kafka pode reprocessar          |
| Falha no FFmpeg                  | Aborta multipart upload; lança `RuntimeException`          |
| Falha no upload S3               | Aborta multipart upload; lança `RuntimeException`          |

### Objeto Gerado no S3

| Propriedade   | Detalhes                                              |
|---------------|-------------------------------------------------------|
| Bucket        | `${S3_VIDEO_BUCKET}` (mesmo bucket do vídeo original) |
| Key           | Derivada do campo `key` da mensagem                   |
| Formato       | ZIP contendo frames JPEG (1 frame/segundo)            |
| Upload        | Multipart (partes de 8 MB)                            |

---

## Variáveis de Ambiente

| Variável                   | Obrigatório | Padrão                  | Descrição                                   |
|----------------------------|:-----------:|-------------------------|---------------------------------------------|
| `ACCESS_KEY`               | **Sim**     | —                       | AWS Access Key ID                           |
| `ACCESS_SECRET`            | **Sim**     | —                       | AWS Secret Access Key                       |
| `AWS_REGION`               | Não         | `sa-east-1`             | Região AWS                                  |
| `S3_VIDEO_BUCKET`          | Não         | `hack-vp-videos`        | Bucket S3 de vídeos                         |
| `KAFKA_BOOTSTRAP_SERVERS`  | Não         | `localhost:9092`        | Endereço(s) do broker Kafka                 |
| `VIDEO_PROCESSOR_TOPIC`    | Não         | `video-processor-topic` | Tópico Kafka de entrada                     |
| `VIDEO_PROCESSOR_GROUP_ID` | Não         | `video-processor`       | Consumer group ID                           |
| `AUTO_OFFSET_RESET_CONFIG` | Não         | `earliest`              | Política de offset inicial                  |
| `REDIS_HOST`               | Não         | `localhost`             | Host do Redis                               |
| `REDIS_PORT`               | Não         | `6379`                  | Porta do Redis                              |
| `REDIS_TIMEOUT`            | Não         | `60000`                 | Timeout de conexão Redis (ms)               |
| `FFMPEG_PATH`              | Não         | *(usa o PATH)*          | Caminho absoluto do binário FFmpeg          |
| `JAVA_OPTS`                | Não         | *(JVM padrão)*          | Flags adicionais para a JVM                 |

---

## Instruções de Execução

### Pré-requisitos

| Ferramenta     | Versão mínima |
|----------------|---------------|
| Java (JDK)     | 21            |
| Maven          | 3.9           |
| Docker         | 24            |
| Docker Compose | 2.24          |
| FFmpeg         | 6.x (local)   |

---

### Opção 1 — Docker Compose (recomendado)

Todos os serviços (Kafka, Redis, Worker) são orquestrados pelo Compose.

```bash
# 1. Clone o repositório
git clone <repo-url>
cd hack-vp-processor-worker

# 2. Configure as credenciais AWS
cp infra/.env.example infra/.env
# Edite infra/.env e preencha ACCESS_KEY e ACCESS_SECRET

# 3. Suba a stack completa
docker compose -f infra/docker-compose.yml --env-file infra/.env up --build -d

# 4. Acompanhe os logs do worker
docker compose -f infra/docker-compose.yml logs -f vp-processor-worker

docker compose -f infra/docker-compose.yml logs -f vp-upload
# 5. Para derrubar a stack
docker compose -f infra/docker-compose.yml down -v
```

#### Verificar saúde dos serviços

```bash
docker compose -f infra/docker-compose.yml ps
```

#### Publicar uma mensagem de teste no Kafka

```bash
# Acesse o container do Kafka
docker exec -it kafka bash

# Publique uma mensagem no tópico
kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic video-processor-topic

# Cole o payload e pressione Enter:
{"uploadId":"8db938a6-baa7-437c-b1b4-65a848ac23f3","key":"videos/meu-video.mp4"}
```

---

### Opção 2 — Execução Local (sem Docker)

```bash
# 1. Instale o FFmpeg

# Ubuntu/Debian
sudo apt-get update && sudo apt-get install -y ffmpeg

# macOS
brew install ffmpeg

# 2. Exporte as variáveis de ambiente
export ACCESS_KEY=your-key
export ACCESS_SECRET=your-secret
export AWS_REGION=sa-east-1
export S3_VIDEO_BUCKET=hack-vp-videos
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
export REDIS_HOST=localhost
export FFMPEG_PATH=$(which ffmpeg)

# 3. Build e execução
./mvnw clean package -DskipTests
java -jar target/vp-processor-worker-0.0.1-SNAPSHOT.jar
```

---

### Opção 3 — Build da Imagem Docker isolada

```bash
# Build a partir da raiz do projeto
docker build -f infra/Dockerfile -t vp-processor-worker:latest .

# Execução manual (requer Kafka e Redis externos)
docker run --rm \
  -e ACCESS_KEY=your-key \
  -e ACCESS_SECRET=your-secret \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9094 \
  -e REDIS_HOST=host.docker.internal \
  vp-processor-worker:latest
```

---

### Logs esperados

| Evento                          | Mensagem de log esperada            |
|---------------------------------|-------------------------------------|
| Vídeo não encontrado no S3      | `RuntimeException` + stack trace    |
| Processamento concluído         | `Video processado com sucesso`      |
| Falha no FFmpeg                 | `RuntimeException` + abort multipart|

---

### Estrutura do `infra/`

```
infra/
├── Dockerfile          # Multi-stage: Maven build + JRE Alpine + FFmpeg
├── docker-compose.yml  # Kafka (KRaft) + Redis + Worker
└── .env.example        # Template de variaveis — copie para .env
```
