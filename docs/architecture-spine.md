---
name: JobStream Architecture Spine
type: architecture-spine
purpose: build-substrate
altitude: feature
paradigm: event-driven microservices
scope: Full-stack job-search & AI-assisted candidature platform
status: final
created: 2026-07-30
updated: 2026-07-30
binds: []
sources:
  - docs/architecture-summary.md
companions: []
---

# Architecture Spine — JobStream

## Design Paradigm

**Event-driven microservices** communicating through Apache Kafka. Each service owns its read/write tables in a shared PostgreSQL. The frontend (Angular) communicates with the jobstream-api via REST and receives real-time events from the ai-analyzer-service via SSE. Each service follows a **hexagonal (ports & adapters)** internal structure (AD-10): use cases depend on ports, adapters wrap external systems.

```mermaid
flowchart LR
    subgraph Frontend
        ANG[Angular SPA]
    end

    subgraph Backend
        DASH[jobstream-api<br/>WebMVC :8081]
        ANALYZER[ai-analyzer-service<br/>WebFlux :8082]
    end

    subgraph Messaging
        KAFKA[Kafka<br/>analysis.request<br/>cover-letter.request]
    end

    subgraph Storage
        PG[(PostgreSQL)]
        REDIS[(Redis Cache)]
    end

    subgraph External
        JS[JSearch API]
        AZ[Adzuna API]
        OAI[OpenAI API]
    end

    ANG -->|REST| DASH
    ANG -->|SSE| ANALYZER
    DASH -->|produce| KAFKA
    ANALYZER -->|consume| KAFKA
    DASH -->|CRUD| PG
    DASH -->|cache| REDIS
    ANALYZER -->|R2DBC| PG
    DASH -->|WebClient| JS
    DASH -->|WebClient| AZ
    ANALYZER -->|WebClient| OAI
```

### Service Boundaries

```mermaid
flowchart LR
    subgraph jobstream-api
        direction LR
        subgraph app_dash[Application - Use Cases]
            JOB_SVC[JobSearchService]
            SAVED_SVC[SavedJobService]
            CACHE_SVC[CacheService]
        end
        subgraph ports_dash[Ports]
            JOB_PROVIDER[JobProvider]
            CACHE_PORT[CachePort]
            SAVED_REPO[SavedJobRepository]
            BUS_PORT_DASH[MessageBusPort]
        end
        subgraph in_dash[In Adapters]
            JOB_CTRL[JobController]
            SAVED_CTRL[SavedJobController]
            CV_CTRL[CvController]
        end
        subgraph out_dash[Out Adapters]
            JS_ADP[JSearchAdapter]
            AZ_ADP[AdzunaAdapter]
            REDIS_ADP[RedisCacheAdapter]
            JPA_ADP[JpaRepositoryAdapter]
            KAFKA_PROD_ADP[KafkaProducerAdapter]
        end
    end

    subgraph ai-analyzer-service
        direction LR
        subgraph app_ana[Application - Use Cases]
            ANALYSIS_SVC[ReactiveAnalysisService]
            COVER_SVC[CoverLetterService]
        end
        subgraph ports_ana[Ports]
            AI_PROVIDER[AiProvider]
            ANALYSIS_REPO[AnalysisRepository]
            BUS_PORT_ANA[MessageBusPort]
        end
        subgraph in_ana[In Adapters]
            SSE_CTRL[SseController]
            KAFKA_CONS_ADP[KafkaConsumerAdapter]
        end
        subgraph out_ana[Out Adapters]
            OPENAI_ADP[OpenAiAdapter]
            R2DBC_ADP[R2dbcAnalysisRepository]
        end
    end

    JOB_CTRL --> JOB_SVC
    SAVED_CTRL --> SAVED_SVC
    CV_CTRL --> SAVED_SVC
    JOB_SVC --> JOB_PROVIDER
    JOB_SVC --> CACHE_PORT
    JOB_SVC --> SAVED_REPO
    JOB_SVC --> BUS_PORT_DASH
    SAVED_SVC --> SAVED_REPO
    SAVED_SVC --> BUS_PORT_DASH
    JOB_PROVIDER --> JS_ADP
    JOB_PROVIDER --> AZ_ADP
    CACHE_PORT --> REDIS_ADP
    SAVED_REPO --> JPA_ADP
    BUS_PORT_DASH --> KAFKA_PROD_ADP
    KAFKA_PROD_ADP --> KAFKA
    KAFKA --> KAFKA_CONS_ADP
    KAFKA_CONS_ADP --> ANALYSIS_SVC
    KAFKA_CONS_ADP --> COVER_SVC
    SSE_CTRL --> ANALYSIS_SVC
    SSE_CTRL --> COVER_SVC
    ANALYSIS_SVC --> AI_PROVIDER
    ANALYSIS_SVC --> ANALYSIS_REPO
    COVER_SVC --> AI_PROVIDER
    COVER_SVC --> ANALYSIS_REPO
    AI_PROVIDER --> OPENAI_ADP
    ANALYSIS_REPO --> R2DBC_ADP
    JS_ADP --> JS
    AZ_ADP --> AZ
    OPENAI_ADP --> OAI
```

## Invariants & Rules

### AD-1 — Event-Driven Service Boundary

- **Binds:** `all`
- **Prevents:** Synchronous cross-service calls (no REST between services)
- **Rule:** Services communicate exclusively through Kafka topics. The only allowed synchronous calls are REST from frontend → jobstream-api, and SSE from analyzer → frontend.

### AD-2 — Shared Database, Isolated Table Ownership

- **Binds:** `all`
- **Prevents:** Two services writing to the same table
- **Rule:** Each service writes exclusively to its own tables. The other service may read but never write. Ownership:

| Table | Owner |
|---|---|
| `saved_jobs` | jobstream-api |
| `cv` | jobstream-api |
| `analysis_results` | ai-analyzer-service |

### AD-3 — Two Kafka Topics

- **Binds:** `all`
- **Prevents:** A single topic carrying heterogeneous message types
- **Rule:** Each async workflow owns a dedicated topic:
  - `analysis.request` — job analysis trigger
  - `cover-letter.request` — cover letter generation trigger
  - Each topic has a corresponding DL topic (`*.DLT`) for failed messages after retry exhaustion

### AD-4 — Cover Letter Depends on Analysis

- **Binds:** `ai-analyzer-service`
- **Prevents:** Generating a cover letter before an analysis result exists
- **Rule:** The ai-analyzer-service must verify `analysis_results` exists for the given `saved_job_id` before processing a `cover-letter.request`. If missing, reject the message (→ DLT).

### AD-5 — Kanban Status in saved_jobs

- **Binds:** `jobstream-api`
- **Prevents:** A separate kanban/state management service or table
- **Rule:** `saved_jobs.status` is an enum: `SAVED → ANALYZED → COVER_LETTER → APPLIED → POSITIVE | NEGATIVE`. Transitions are forward-only; `status` is mutated by the service that owns the preceding step.

### AD-6 — CV Stored in PostgreSQL

- **Binds:** `jobstream-api`
- **Prevents:** Filesystem storage (no local file coupling), object storage (no S3 infra)
- **Rule:** CV content stored as TEXT in table `cv`. One row (single CV for single user). Uploaded via `POST /api/cv` to jobstream-api.

### AD-7 — Redis for Job Search Cache

- **Binds:** `jobstream-api`
- **Prevents:** Repeated identical API calls within cache TTL
- **Rule:** `GET /api/jobs?q=...` results are cached in Redis with a TTL of 30 minutes. Cache key is the query string. Miss → call external API → store in Redis → return. Hit → return immediately.

### AD-8 — SSE from ai-analyzer-service

- **Binds:** `ai-analyzer-service`
- **Prevents:** Polling from frontend, jobstream-api as SSE intermediary
- **Rule:** Real-time events (`analysis-completed`, `cover-letter-completed`) are pushed exclusively by the ai-analyzer-service via `GET /api/events` (Flux SSE). Single-instance SSE is sufficient (single-user project).

### AD-9 — DLT per Topic

- **Binds:** `ai-analyzer-service`
- **Prevents:** Silent message loss on processing failure
- **Rule:** Each consumer has a dead-letter topic with retry policy (3 retries, exponential backoff). Failed messages land in `*.DLT` for manual inspection / replay.

### AD-10 — Hexagonal Architecture (Ports & Adapters)

- **Binds:** `all`
- **Prevents:** Domain/application coupling to infrastructure (WebClient, Kafka, Redis, JPA/R2DBC, Spring beans)
- **Rule:** Each service is structured in three layers:
  - **Domain/Application** — entities (`SavedJob`, `Cv`, `JobOffer`, `AnalysisResult`) + use cases (`JobSearchService`, `SavedJobService`, `ReactiveAnalysisService`, `CoverLetterService`). Framework-free.
  - **Ports** — interfaces owned by the application layer: `JobProvider`, `CachePort`, `MessageBusPort`, `SavedJobRepository`, `AnalysisRepository`, `AiProvider`.
  - **Adapters** — one implementation per external system. In adapters: REST controllers, Kafka consumers. Out adapters: `JSearchAdapter`, `AdzunaAdapter`, `RedisCacheAdapter`, `JpaRepositoryAdapter`, `R2dbcAnalysisRepository`, `OpenAiAdapter`, `KafkaProducerAdapter`.
- **Rule:** `JobSearchService` depends on the `JobProvider` port only — never on JSearch/Adzuna directly. Provider aggregation or fallback is a wiring/config decision, not a code change.

## Consistency Conventions

| Concern | Convention |
|---|---|
| Naming (entities) | Singular PascalCase (`SavedJob`, `AnalysisResult`) |
| Naming (topics) | kebab-case (`analysis.request`, `cover-letter.request`) |
| Naming (endpoints) | kebab-case plural (`/api/saved-jobs`, `/api/events`) |
| IDs | UUID v4 (all entities) |
| Dates | ISO-8601 UTC (`yyyy-MM-dd'T'HH:mm:ss'Z'`) |
| Error shape | `{ "error": "...", "status": N }` |
| Config | Spring application.yml per service, environment vars for secrets |
| Logging | Structured JSON (Logback), traceable via correlation ID |
| API contracts | DTOs generated by OpenAPI in each service (`target/generated-sources`); shared contract = OpenAPI spec |

## Stack

| Name | Version |
|---|---|
| Java | 25 |
| Spring Boot | 3.4.x |
| Spring WebMVC | jobstream-api |
| Spring WebFlux | ai-analyzer-service |
| JPA / Hibernate | jobstream-api |
| R2DBC | ai-analyzer-service |
| Apache Kafka | 3.8.x |
| PostgreSQL | 16 |
| Redis | 7.x |
| Angular | 22 |
| Docker | latest |
| Kubernetes | 1.30 (local: kind/minikube) |

## Structural Seed

### System Context

```mermaid
C4Context
    Person(user, "User (Julien)", "Single user, tech recruiter target")
    System_Boundary(jobstream, "JobStream Platform") {
        System(dashboard, "jobstream-api", "Spring Boot WebMVC :8081")
        System(analyzer, "ai-analyzer-service", "Spring Boot WebFlux :8082")
        SystemDb(pg, "PostgreSQL", "Shared database")
        SystemDb(redis, "Redis", "Job search cache")
        SystemQueue(kafka, "Kafka", "Message broker")
    }
    System_Ext(js, "JSearch API", "Job search external API")
    System_Ext(az, "Adzuna API", "Job search external API")
    System_Ext(oai, "OpenAI API", "AI analysis & generation")

    Rel(user, dashboard, "REST", "HTTPS")
    Rel(user, analyzer, "SSE", "HTTPS")
    Rel(dashboard, pg, "JPA read/write")
    Rel(analyzer, pg, "R2DBC read/write")
    Rel(dashboard, redis, "cache read/write")
    Rel(dashboard, kafka, "produce")
    Rel(analyzer, kafka, "consume")
    Rel(dashboard, js, "WebClient", "HTTPS")
    Rel(dashboard, az, "WebClient", "HTTPS")
    Rel(analyzer, oai, "WebClient", "HTTPS")
```

### Core Entity Model

```mermaid
erDiagram
    saved_jobs {
        uuid id PK
        varchar external_id
        varchar title
        varchar company
        text url
        text description
        varchar status "SAVED | ANALYZED | COVER_LETTER | APPLIED | POSITIVE | NEGATIVE"
        text cover_letter "nullable, generated by AI"
        timestamp created_at
        timestamp updated_at
    }

    analysis_results {
        uuid id PK
        uuid saved_job_id FK
        int score "0-100"
        text summary
        jsonb strengths
        jsonb weaknesses
        timestamp created_at
    }

    cv {
        uuid id PK
        varchar filename
        text content "markdown CV content"
        timestamp uploaded_at
    }

    saved_jobs ||--o| analysis_results : "has one"
    saved_jobs ||--o| cv : "references for analysis context"
```

### Source Tree

```text
backend/
├── jobstream-api/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/jobstream/
│       ├── adapter/in/                  # Driving adapters (REST)
│       │   ├── JobController.java
│       │   ├── SavedJobController.java
│       │   └── CvController.java        # NEW — POST /api/cv
│       ├── application/                 # Use cases — depend only on ports
│       │   ├── JobSearchService.java
│       │   ├── SavedJobService.java
│       │   └── CacheService.java        # NEW — cache-aside orchestration
│       ├── domain/
│       │   ├── SavedJob.java
│       │   ├── Cv.java                  # NEW
│       │   └── JobOffer.java            # NEW — normalized offer model
│       ├── port/                        # NEW — interfaces owned by application
│       │   ├── JobProvider.java         # NEW — job sources (JSearch/Adzuna)
│       │   ├── CachePort.java           # NEW
│       │   ├── SavedJobRepository.java  # NEW
│       │   └── MessageBusPort.java      # NEW
│       ├── adapter/out/                 # Driven adapters — one per external system
│       │   ├── jobprovider/
│       │   │   ├── JSearchAdapter.java  # NEW
│       │   │   └── AdzunaAdapter.java   # NEW
│       │   ├── cache/
│       │   │   └── RedisCacheAdapter.java      # NEW
│       │   ├── persistence/
│       │   │   └── JpaSavedJobRepository.java   # NEW
│       │   └── messaging/
│       │       └── KafkaProducerAdapter.java    # NEW
│       └── config/
│           ├── KafkaConfig.java
│           ├── RedisConfig.java         # NEW
│           └── WebClientConfig.java
└── ai-analyzer-service/
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/com/jobstream/analyzer/
        ├── adapter/in/
        │   ├── AnalysisEventController.java   # SSE
        │   ├── AnalysisRequestConsumer.java
        │   └── CoverLetterConsumer.java       # NEW
        ├── application/
        │   ├── ReactiveAnalysisService.java
        │   ├── CoverLetterService.java        # NEW
        │   └── SseBroadcaster.java
        ├── domain/
        │   └── AnalysisResult.java
        ├── port/                              # NEW
        │   ├── AiProvider.java                # NEW — OpenAI
        │   ├── AnalysisRepository.java        # NEW
        │   └── MessageBusPort.java            # NEW
        ├── adapter/out/
        │   ├── ai/
        │   │   └── OpenAiAdapter.java         # NEW
        │   └── persistence/
        │       └── R2dbcAnalysisRepository.java # NEW
        └── config/
            ├── KafkaConfig.java
            └── OpenAiConfig.java
```

### Event Flow

```mermaid
sequenceDiagram
    participant F as Angular Frontend
    participant D as jobstream-api
    participant K as Kafka
    participant A as ai-analyzer-service
    participant O as OpenAI
    participant P as PostgreSQL
    participant R as Redis

    Note over F,R: Job Search (with cache)
    F->>D: GET /api/jobs?q=java
    D->>R: check cache
    alt cache miss
        D->>JS: WebClient call
        D->>R: store result (TTL 30min)
    end
    D->>F: job list

    Note over F,R: Save Job
    F->>D: POST /api/saved-jobs
    D->>P: INSERT saved_jobs (status=SAVED)
    D->>F: 201 Created

    Note over F,R: Upload CV
    F->>D: POST /api/cv
    D->>P: INSERT cv (upsert)
    D->>F: 200 OK

    Note over F,R: Analyze Job
    F->>D: POST /api/saved-jobs/{id}/analyze
    D->>P: UPDATE status=IN_PROGRESS
    D->>K: publish analysis.request
    K->>A: consume
    A->>P: read cv + saved_jobs
    A->>O: analysis request (CV + job)
    O->>A: analysis result
    A->>P: INSERT analysis_results + UPDATE saved_jobs.status=ANALYZED
    A->>F: SSE event analysis-completed

    Note over F,R: Generate Cover Letter
    F->>D: POST /api/saved-jobs/{id}/cover-letter
    D->>K: publish cover-letter.request
    K->>A: consume
    A->>P: verify analysis_results exists
    A->>O: cover letter request (analysis + job)
    O->>A: cover letter text
    A->>P: UPDATE saved_jobs.cover_letter + status=COVER_LETTER
    A->>F: SSE event cover-letter-completed

    Note over F,R: Kanban Status Update
    F->>D: PATCH /api/saved-jobs/{id}/status
    D->>P: UPDATE status=APPLIED|POSITIVE|NEGATIVE
    D->>F: 200 OK
```

## Capability → Architecture Map

| Capability | Lives in | Governed by |
|---|---|---|
| Job search (external APIs) | jobstream-api | AD-7, AD-3, AD-10 |
| Saved jobs CRUD | jobstream-api | AD-2, AD-10 |
| Kanban status management | jobstream-api | AD-5, AD-10 |
| CV upload & storage | jobstream-api | AD-6, AD-2, AD-10 |
| Job analysis (AI) | ai-analyzer-service | AD-1, AD-3, AD-4, AD-10 |
| Cover letter generation | ai-analyzer-service | AD-1, AD-3, AD-4, AD-10 |
| Real-time SSE notifications | ai-analyzer-service | AD-8, AD-10 |
| Job search caching | jobstream-api | AD-7, AD-10 |
| Message resilience | ai-analyzer-service | AD-9, AD-10 |

## Deferred

| Item | Why deferred |
|---|---|
| Multi-instance SSE scaling | Single-user project, not needed until multi-user or cloud deployment |
| Authentication / Authorization | Vitrine project, single user. Revisit if open to public |
| Cloud deployment (Azure) | Post-MVP. Docker Compose serves local dev and vitrine demo |
| CI/CD pipeline | Post-MVP. Manual build until code stabilizes |
| Monitoring & alerting | Prometheus/Grafana scaffold exists in infra/ but not wired until deployment |
| CV versioning / history | Single CV, overwrite-only. Revisit if multiple CVs needed |
| Report export (PDF) | Copy-paste from UI covers MVP needs |
| Testing strategy | To be defined during implementation |
