---
title: 'Epics: JobStream'
status: final
created: 2026-07-31
updated: 2026-07-31
stepsCompleted: [1, 2, 3, 4]
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-jobstream-bmad-2026-07-31/prd.md
  - _bmad-output/planning-artifacts/architecture/architecture-jobstream-bmad-2026-07-30/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/ux-designs/ux-jobstream-bmad-2026-07-31/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-jobstream-bmad-2026-07-31/EXPERIENCE.md
  - _bmad-output/planning-artifacts/ux-designs/ux-jobstream-bmad-2026-07-31/mockups/*.html
---

# JobStream — Epic Breakdown

## Overview

This document provides the epic breakdown for JobStream, decomposing the requirements from the PRD, Architecture, and UX design into implementable epics and stories. Full story detail (with acceptance criteria) lives in `stories/` (one file per epic, backend + frontend stories together); this file owns the requirements inventory, coverage map, and epic list.

## Requirements Inventory

### Functional Requirements

- **FR-1 — Search across providers.** The user queries job offers by keyword and receives a single merged list from JSearch + Adzuna, deduplicated by offer URL. A single Provider outage degrades to the other Provider's results. No cross-provider relevance ranking (ordering = provider order / posted date).
- **FR-2 — Cache search results.** Identical queries within the TTL (30 min) are served from Redis without external API calls; cache key is the normalized query string.
- **FR-3 — Save a job offer.** The user saves any returned Job Offer to Saved Jobs with initial status `SAVED`. Duplicate saves are rejected (by `external_id` + URL).
- **FR-4 — Upload CV.** The user uploads one markdown CV via `POST /api/cv`, stored in PostgreSQL, replacing any prior CV (overwrite-only, single CV invariant).
- **FR-5 — Trigger analysis.** The user requests an analysis for a Saved Job; the request is queued (`analysis.request`), not synchronous. No `IN_PROGRESS` status — the UI derives processing state from absence of report / pending SSE; `APPLIED` is the human in-progress stage.
- **FR-6 — CV context reuse.** The analyzer extracts the CV's key information once and reuses it across analyses (OpenAI sends only CV context + job description). Model configured via `application.yml`.
- **FR-7 — Structured analysis report.** The analysis produces score (0-100), summary, strengths, weaknesses — persisted in `analysis_results` (strengths/weaknesses as `{ title, detail }` objects, JSONB) and linked to the Saved Job; `saved_jobs.status` becomes `ANALYZED`.
- **FR-8 — SSE completion event.** On completion, an `analysis-completed` SSE event (carrying the report reference) is delivered via `GET /api/events`.
- **FR-9 — Generate cover letter.** The user generates a cover letter for a Saved Job that has an Analysis Report; a new letter overwrites the previous one. Requires an existing report (else reject to DLT, no silent drop). On completion, `cover_letter` is populated, status becomes `COVER_LETTER`, and a `cover-letter-completed` SSE event is delivered.
- **FR-10 — Track status.** The user moves a Saved Job forward in the status flow (`SAVED → ANALYZED → COVER_LETTER → APPLIED → POSITIVE | NEGATIVE`); backward transitions are rejected (`PATCH /api/saved-jobs/{id}/status`). The board always reflects persisted status.
- **FR-11 — Dead-letter handling.** Failed messages are retried (3 attempts, exponential backoff) then routed to the topic's DLT (`analysis.request.DLT` / `cover-letter.request.DLT`) for manual replay.

### NonFunctional Requirements

- **NFR-1 — Reliability.** Async workloads survive consumer failures via DLT (FR-11); no synchronous cross-service calls (ADR-1).
- **NFR-2 — Performance.** Search served from cache within TTL (FR-2); no hard latency SLA on AI (async, OpenAI-dependent); SSE keeps the UI responsive.
- **NFR-3 — Security.** Single-user, no auth; secrets (API keys) only via environment variables; CV is personal data stored locally in the app DB, never transmitted beyond OpenAI for the analysis feature.
- **NFR-4 — Observability.** Structured JSON logging with correlation IDs tracing a message from publish to SSE delivery.
- **NFR-5 — Cost.** OpenAI usage is budget-conscious: CV context extracted once and reused (FR-6), bounded retries (FR-11), no unbounded LLM loops.
- **NFR-6 — Portability.** Full stack runs locally via Docker Compose on Windows/macOS/Linux.

### Additional Requirements (Architecture)

- **Greenfield scaffolding** — no starter template specified. Epic 1 Story 1 = backend project scaffolding: Maven multi-module (jobstream-api + ai-analyzer-service), Java 25, Spring Boot 4.1.0.
- **AD-1 — Event-driven boundary.** No REST between services; only REST frontend → jobstream-api and SSE analyzer → frontend.
- **AD-2 — Shared PostgreSQL, isolated table ownership.** `saved_jobs` + `cv` owned by jobstream-api; `analysis_results` owned by ai-analyzer-service. Each service reads but never writes the other's tables.
- **AD-3 — Two Kafka topics.** `analysis.request`, `cover-letter.request`, each with a matching `*.DLT`.
- **AD-4 — Cover letter depends on analysis.** ai-analyzer-service verifies `analysis_results` exists before processing `cover-letter.request`; missing → reject to DLT.
- **AD-5 — Kanban status in saved_jobs.** Enum `SAVED → ANALYZED → COVER_LETTER → APPLIED → POSITIVE | NEGATIVE`; forward-only; status mutated by the service owning the preceding step.
- **AD-6 — CV stored in PostgreSQL.** CV content as TEXT in `cv` table, one row (single CV). Upload via `POST /api/cv` to jobstream-api.
- **AD-7 — Redis for job search cache.** TTL 30 min; cache key = query string; miss → external call → store → return.
- **AD-8 — SSE from ai-analyzer-service.** Real-time events (`analysis-completed`, `cover-letter-completed`) pushed exclusively by the analyzer via `GET /api/events`. Single-instance SSE sufficient.
- **AD-9 — DLT per topic.** Retry policy (3 retries, exponential backoff); failed messages land in `*.DLT`.
- **AD-10 — Hexagonal architecture.** Each service in three layers — domain/application (framework-free), ports (interfaces owned by application), adapters (one per external system). `JobSearchService` depends on the `JobProvider` port only.
- **Stack versions.** Java 25, Spring Boot 4.1.0, WebMVC (api), WebFlux (analyzer), JPA/Hibernate (api), R2DBC (analyzer), Apache Kafka 4.3.1, PostgreSQL 18, Redis 8.2.x, Angular 22, Docker.
- **Docker Compose** local stack (postgres, redis, kafka, both services, frontend).
- **OpenAPI contract.** DTOs generated per service (`target/generated-sources`); shared contract = OpenAPI spec.
- **Consistency conventions.** Kebab-case endpoints/topics; UUID v4 IDs; ISO-8601 UTC dates; error shape `{ "error": "...", "status": N }`; secrets via environment variables; structured JSON logging with correlation IDs.

> ⚠️ **Architecture inconsistency to fix during implementation:** the Architecture Spine sequence diagram (Analyze Job) shows `UPDATE status=IN_PROGRESS`, contradicting the canonical decision (PRD FR-5, AD-5): there is **no `IN_PROGRESS` status**. Processing state is derived from absence of report / pending SSE. Fix the sequence diagram when implementing the analyze flow.

### UX Design Requirements

- **UX-DR1 — Design token layer.** Implement the Tailwind CSS design system tokens per DESIGN.md frontmatter (colors, typography, rounded, spacing). Light theme only for V1; dark tokens deferred to V2.
- **UX-DR2 — Component layer.** Build reusable components: Button (primary/secondary), Input (2px `ring` focus halo), Card, Modal (single-level stack), Kanban column, Status badge (pill), Score badge, Analysis panel, Toast, Pagination.
- **UX-DR3 — Score ramp.** ≥80 green, 50–79 amber, <50 red; number and label always agree.
- **UX-DR4 — Navigation.** Top bar with three tabs (Search · Board · CV); job detail and cover letter layer as panels over Search/Board; modal stacks one level deep.
- **UX-DR5 — Kanban board.** 5 columns with a shared terminal `POSITIVE | NEGATIVE` column (pill carries the verdict); forward-only; drag & drop after `COVER_LETTER`; keyboard fallback (arrows + Enter + Esc); board reflects persisted status.
- **UX-DR6 — SSE in-progress states.** Analysis panel renders skeleton + live status line ("Extracting CV context… Analyzing…"); completion swaps content in place via SSE, no page refresh, `aria-live` announcement.
- **UX-DR7 — Accessibility floor.** WCAG 2.2 AA; 2px focus ring at AA contrast; keyboard-operable board; `prefers-reduced-motion` respected (static status lines, no shimmer).
- **UX-DR8 — Microcopy.** English, professional/human voice per EXPERIENCE.md Voice and Tone table (e.g., "Analyze this offer", "Report ready", "That move isn't allowed — status can only move forward.").
- **UX-DR9 — Interaction primitives.** `/` focuses search, `Enter` submits/confirms, `s` saves job row, `Esc` closes panels; board keyboard-operable. Mouse-first; drag & drop is the board's centerpiece gesture.
- **UX-DR10 — Pagination.** Footer under search results, 20 per page; backward/forward + page numbers. Never infinite scroll.
- **UX-DR11 — Backward moves rejected.** Toast + card snap-back; no animation of the invalid state.
- **UX-DR12 — CV overwrite confirm.** Upload replaces the previous CV after an explicit modal confirm ("Replace current CV?"); destructive-tone confirm.
- **UX-DR13 — Responsive.** Desktop-first; board degrades to a single-column forward status list below `lg`; no mobile app in V1.
- **UX-DR14 — Empty states.** Always an action: "No results. Try another keyword." / "No saved jobs yet. Save offers from Search to start tracking." / degraded provider notice + Retry.
- **UX-DR15 — Mock composition reference.** 5 mockups (`mockups/*.html`) illustrate IA surfaces; spine wins on conflict.

### FR Coverage Map

- **FR-1** — Epic 1 (Job Search & Save) — Search across providers
- **FR-2** — Epic 1 (Job Search & Save) — Cache search results
- **FR-3** — Epic 1 (Job Search & Save) — Save a job offer
- **FR-4** — Epic 2 (AI Analysis) — Upload CV
- **FR-5** — Epic 2 (AI Analysis) — Trigger analysis
- **FR-6** — Epic 2 (AI Analysis) — CV context reuse
- **FR-7** — Epic 2 (AI Analysis) — Structured analysis report
- **FR-8** — Epic 2 (AI Analysis) — SSE completion event
- **FR-9** — Epic 3 (Cover Letter) — Generate cover letter
- **FR-10** — Epic 4 (Application Tracking) — Track status
- **FR-11** — Epic 2 + Epic 3 (AI Analysis, Cover Letter) — Dead-letter handling

## Epic List

### Epic 1: Job Search & Save

The user searches job offers from JSearch + Adzuna in one merged, URL-deduplicated, cached list and saves the relevant ones to their board.

**FRs covered:** FR-1, FR-2, FR-3

### Epic 2: AI Analysis

The user uploads their single markdown CV and gets an objective AI match assessment (score, strengths, weaknesses) for any saved offer, delivered asynchronously via SSE with resilient retries (DLT).

**FRs covered:** FR-4, FR-5, FR-6, FR-7, FR-8, FR-11

### Epic 3: Cover Letter

The user generates a contextual cover letter from the analysis report of a saved offer; regeneration overwrites the previous letter.

**FRs covered:** FR-9, FR-11

### Epic 4: Application Tracking

The user tracks every application on a kanban board through its forward-only lifecycle (`SAVED → ANALYZED → COVER_LETTER → APPLIED → POSITIVE | NEGATIVE`) with drag & drop and a shared terminal column.

**FRs covered:** FR-10
