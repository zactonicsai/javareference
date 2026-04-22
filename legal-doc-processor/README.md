# Legal Document Processor

End-to-end example stack that uploads a document, stores it in S3 (LocalStack), sends an SQS message, and runs a Temporal workflow to extract text, score legal/crime keywords via a simple TF-IDF, and index the result into Elasticsearch — with a Tailwind-styled HTML form that uses an OpenLayers USA map for location pinning and a WebSocket-driven "service unavailable" banner.

## Stack

- **Java 21** + **Spring Boot 3.5.13** (drop to the nearest 3.5.x if `.13` isn't published when you build)
- **LocalStack 3.8** for S3 + SQS
- **Temporal 1.25** with Postgres 16 and the Temporal UI
- **Elasticsearch 8.15** + Kibana
- **Apache Tika 3** for text extraction
- **OpenLayers 9**, **Tailwind CSS** (via CDN), plain JS
- **JUnit 5**, **Mockito**, **Testcontainers**, **Temporal test environment**

## Architecture

```
┌──────────────┐  multipart POST  ┌──────────────┐  putObject   ┌──────────┐
│  index.html  │ ───────────────▶ │ UploadCtrl   │ ───────────▶ │ S3       │
│ (Tailwind +  │                  │              │              │ (Local-  │
│  OpenLayers) │                  │              │ sendMessage  │  Stack)  │
│              │                  │              │ ───────────▶ │          │
│              │                  └──────────────┘              │ SQS      │
│              │                                                 └────┬─────┘
│              │                                                      │
│  WS /ws/     │  ServiceHealth                                       │ poll
│  health ◀────┼──── every 5s                                         ▼
└──────────────┘                                              ┌──────────────┐
                                                              │ SqsPoller    │
                                                              │ Service      │
                                                              └──────┬───────┘
                                                                     │ start
                                                                     ▼
                                                          ┌──────────────────┐
                                                          │ Temporal Workflow│
                                                          │                  │
                                                          │ 1. extractText   │
                                                          │ 2. computeTF-IDF │
                                                          │ 3. indexToES     │
                                                          └──────────────────┘
```

## Quick start

```bash
docker compose up --build
```

Once everything is healthy (Elasticsearch and LocalStack take the longest):

- App UI:        http://localhost:8080
- Temporal UI:   http://localhost:8088
- Kibana:        http://localhost:5601
- Elasticsearch: http://localhost:9200
- LocalStack:    http://localhost:4566

To stop:

```bash
docker compose down -v
```

## Running locally without Docker

You still need LocalStack, Temporal, and Elasticsearch running somewhere. Easiest is to bring up just those via compose:

```bash
docker compose up -d localstack postgresql temporal elasticsearch
# then:
mvn spring-boot:run
```

The app reads its endpoints from environment variables (see `application.yml`) and defaults to `localhost` for each.

## Using it

1. Open http://localhost:8080.
2. Type a subject (e.g., `Case 2025-001 - Evidence packet`).
3. Pick a file (PDF, DOCX, TXT, image — anything Tika parses).
4. Click a point on the USA map to pin a location; optionally add a description.
5. Hit **Upload & Process**.

The response comes back immediately with a `documentId` and `sqsMessageId` — the workflow runs asynchronously. Watch it live in the Temporal UI at http://localhost:8088 or query Elasticsearch directly:

```bash
curl http://localhost:9200/legal-documents/_search?pretty
```

## Searching by keyword

```bash
curl "http://localhost:8080/api/search/keyword?q=homicide"
```

## Service health

- REST: `GET /api/health/services`
- WebSocket: `ws://localhost:8080/ws/health` (pushes the same payload every 5s)
- Spring Boot actuator: `GET /actuator/health`

The frontend subscribes to the WebSocket and:
- Shows a green/red dot per service (S3, SQS, Temporal, Elasticsearch)
- Shows a red banner and disables the Upload button when any service is down
- Falls back to HTTP polling if the WebSocket drops

## TF-IDF keyword list

Static legal/crime vocabulary in `src/main/resources/keywords/legal-keywords.txt` (~120 terms). Since there's no real corpus, IDF is synthesized from term length with a manual down-weight for common terms like `crime` and `evidence`. For anything production-bound you'd replace this with a real IDF over your document set.

## Tests

```bash
mvn test
```

What's covered:

| Test | What it exercises |
|---|---|
| `TfIdfServiceTest` | Keyword loading, scoring, ranking, top-N |
| `TextExtractionServiceTest` | Tika plain-text extraction + content-type detection |
| `UploadControllerTest` | Happy path, missing subject, empty file (MockMvc + Mockito) |
| `HealthCheckServiceTest` | Health flag aggregation |
| `DocumentProcessingWorkflowTest` | Temporal `TestWorkflowExtension` with mocked activities |
| `FullStackIntegrationTest` | **@Disabled by default.** Spins up LocalStack + Elasticsearch via Testcontainers. Enable locally when Docker is available. |

## Project layout

```
legal-doc-processor/
├── docker-compose.yml          # app + localstack + temporal + postgres + es + kibana
├── Dockerfile                  # multi-stage, maven:3.9-eclipse-temurin-21
├── pom.xml
├── localstack-init/
│   └── init-resources.sh       # creates bucket and queue
├── temporal-config/
│   └── development-sql.yaml
└── src/
    ├── main/
    │   ├── java/com/example/legaldoc/
    │   │   ├── LegalDocApplication.java
    │   │   ├── config/         # AWS, ES, Temporal, WebSocket
    │   │   ├── controller/     # Upload, Health, Search
    │   │   ├── health/         # HealthCheckService
    │   │   ├── model/          # DocumentMetadata, KeywordScore, etc.
    │   │   ├── service/        # S3, SQS, Tika, TF-IDF, ES, SqsPoller
    │   │   ├── websocket/      # HealthWebSocketHandler
    │   │   └── workflow/       # Temporal workflow + activities
    │   └── resources/
    │       ├── application.yml
    │       ├── keywords/legal-keywords.txt
    │       └── static/
    │           ├── index.html
    │           └── app.js
    └── test/java/...
```

## Known gaps & caveats

This is an **unverified** example scaffold — I could not compile it in my build environment (no Maven Central access). Treat the following as likely-to-bite:

1. **Spring Boot 3.5.13** — if that exact patch isn't published, drop to the nearest 3.5.x in `pom.xml`.
2. **Elasticsearch 8.15 fluent builders** in `ElasticsearchService` (`p -> p.keyword(k -> k)`, nested mappings, etc.) were written from memory and are the most likely place for compile errors.
3. **Temporal `WorkflowServiceStubs.blockingStub()` + `GetSystemInfoRequest`** in `HealthCheckService` — the import path is correct for recent 1.2x releases but has moved historically.
4. **SQS consumer is a simple `@Scheduled` poller**, not production-grade. No dead-letter queue handling, no visibility-timeout tuning.
5. **No Temporal auth** — uses the default namespace with no TLS. Fine for local; not fine for anywhere else.
6. **CORS is open on the WebSocket** (`setAllowedOrigins("*")`). Close it down for non-local deployments.
7. **Frontend loads Tailwind and OpenLayers from CDNs** — fine for a demo, replace with a real build pipeline for production.
8. **Execute-bit on `localstack-init/init-resources.sh`** may be lost depending on how you extract the archive; `chmod +x localstack-init/*.sh` if LocalStack init doesn't run.

## License

MIT — example code, use as you like.
