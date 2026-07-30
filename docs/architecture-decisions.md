# Architecture Decisions — JobStream

All architectural decisions made for JobStream, with context, alternatives considered, and consequences.

---

## AD-1 — Event-Driven Microservices Paradigm

**Context:** JobStream has two distinct workloads: a classic CRUD (job saving, CV management, kanban) and a reactive I/O-bound pipeline (OpenAI calls, SSE streaming). Putting them in the same service would mix WebMVC and WebFlux or force a stack compromise.

**Decision:** Two Spring Boot services communicating via Kafka. dashboard-service uses WebMVC/JPA for CRUD, ai-analyzer-service uses WebFlux/R2DBC for reactive processing. No direct REST calls between services — everything goes through Kafka.

**Alternatives:**
- Single Spring Boot monolith: simpler to deploy, but mixes stacks and doesn't show microservices mastery
- Single service with modules: doesn't resolve the WebMVC/WebFlux conflict

**Consequences:** +infra complexity (Kafka, 2 services), +architectural clarity, richer technical showcase.

---

## AD-2 — Shared Database, Isolated Tables

**Context:** Both services need access to the same data (dashboard reads analysis results, analyzer reads CV and job offers).

**Decision:** Single PostgreSQL instance, but each service writes exclusively to its own tables. Dashboard owns `saved_jobs` and `cv`. Analyzer owns `analysis_results`. Each can read the other's tables.

**Alternatives:**
- Two separate databases: cleaner microservices practice, but complicates business joins and adds infra for a solo project
- Single database without ownership rules: risk of accidental cross-service writes

**Consequences:** Loose schema coupling (a column change in `analysis_results` can impact dashboard queries). Acceptable for a solo project — migrate to two databases if the project becomes multi-team.

---

## AD-3 — Two Kafka Topics

**Context:** The initial architecture had a single `analysis.request` topic. Adding cover letter generation (triggered separately, after analysis) would require a `type` field in the message to distinguish intent.

**Decision:** Two dedicated topics: `analysis.request` and `cover-letter.request`. Each with its own consumer, DLT (`.DLT`), and retry policy.

**Alternatives:**
- Single topic with `type` field: simpler but mixes responsibilities, makes per-flow retry policies harder
- Direct REST call for cover letter: breaks event-driven decoupling

**Consequences:** Two topics to manage, but clear flow isolation. Each topic can evolve independently.

---

## AD-4 — Cover Letter Depends on Analysis

**Context:** The cover letter must be contextual — using strengths/weaknesses identified by the analysis to personalize the message.

**Decision:** The analyzer verifies `analysis_results` exists for the given `saved_job_id` before processing a cover letter request. If missing, the message is rejected to the DLT.

**Alternatives:**
- Cover letter without dependency: simpler but generates generic letters
- Enforce order in the frontend (disable button until analysis is done): sufficient in UI but not guaranteed at message level

**Consequences:** Safety at consumer level. The frontend can also disable the button for better UX.

---

## AD-5 — Kanban Status in saved_jobs

**Context:** Application tracking follows a state cycle (saved → analyzed → cover letter → applied → feedback). A storage decision was needed.

**Decision:** A `status` column in the `saved_jobs` table. Enum: `SAVED → ANALYZED → COVER_LETTER → APPLIED → POSITIVE | NEGATIVE`. Forward-only transitions. Dashboard owns and mutates this field.

**Alternatives:**
- Dedicated `application_status` table with history: more flexible (full history, custom states), but overkill for this need
- Dedicated state machine service: unnecessary technical exercise

**Consequences:** Simple, single source of truth. No transition history — add a log table later if needed.

---

## AD-6 — CV Stored in PostgreSQL

**Context:** The CV (markdown format) must be accessible by the analyzer for OpenAI calls. On a PC it could be a local file, but the analyzer inside its Docker container wouldn't have access.

**Decision:** `cv` table in PostgreSQL. Single row (single CV). Upload via `POST /api/cv` on the dashboard. Content stored as TEXT.

**Alternatives:**
- Filesystem with shared Docker volume: more complex setup, less portable across OS/hosts
- Object storage (S3): unjustified extra infra for a solo project

**Consequences:** DB dependency for a file. If the CV grows large (images), reconsider storage. Easy to migrate later (upload interface is HTTP, storage backend is encapsulated).

---

## AD-7 — Redis Cache for Job Offers

**Context:** API calls to JSearch and Adzuna have quotas and latency. Identical searches can be repeated.

**Decision:** Redis cache-aside on the dashboard-service. 30-minute TTL. Key = query string. Cache miss → API call → store → return. Cache hit → immediate return.

**Alternatives:**
- In-memory cache (Caffeine): simpler, no Redis infra, but lost on restart and not shareable
- PostgreSQL cache table: functional but slower, Redis adds a valuable tech badge

**Consequences:** Extra infra (Redis), demonstrates an in-demand skill. Easy to remove if the project stays solo and quotas suffice.

---

## AD-8 — SSE from the Analyzer

**Context:** The frontend needs real-time delivery of analysis and cover letter results. A notification channel was needed.

**Decision:** The ai-analyzer-service exposes a single SSE endpoint `GET /api/events`. Two event types: `analysis-completed` and `cover-letter-completed`. Single-instance (no multi-instance scaling needed for a solo project).

**Alternatives:**
- WebSocket: bidirectional, heavier to implement. SSE suffices (unidirectional server → client)
- HTTP polling: simple but not real-time, wastes resources
- Dashboard as relay: would route through a non-reactive service, adding latency

**Consequences:** SSE is simple, unidirectional, natively supported by browsers and WebFlux. If multi-instance scaling is needed later, add a `sse.events` Kafka topic as a bus.

---

## AD-9 — Dead-Letter Topic per Topic

**Context:** Kafka messages can fail (OpenAI API down, invalid request, missing analysis). Without a retry mechanism, messages are silently lost.

**Decision:** Each topic has a dedicated `.DLT` topic. Retry policy: 3 attempts with exponential backoff. After exhaustion, the message lands in the DLT for manual inspection and replay.

**Alternatives:**
- Infinite retry: can block the consumer indefinitely
- Log and drop: silent data loss
- No DLT (Kafka default): message lost on first failure

**Consequences:** Resilience. A message in DLT requires manual action — acceptable for a solo project. For multi-user, an automatic replay mechanism would be needed.
