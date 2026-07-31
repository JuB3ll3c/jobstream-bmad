---
title: 'Epic 2 — AI Analysis — Stories'
status: in-progress
created: 2026-07-31
updated: 2026-07-31
epic: 2
fr: [FR-4, FR-5, FR-6, FR-7, FR-8, FR-11]
uxdr: [UX-DR2, UX-DR3, UX-DR6, UX-DR7, UX-DR12, UX-DR14]
---

# Epic 2: AI Analysis — Stories

> **Goal:** The user uploads their single markdown CV and gets an objective AI match assessment (score, strengths, weaknesses) for any saved offer, delivered asynchronously via SSE with resilient retries (DLT).
> Epic list and requirements inventory in `../epics.md`. Backend stories 2.1, 2.3–2.5, 2.7; frontend stories 2.2, 2.6.

## Backend

### Story 2.1: CV upload backend

As a job hunter,
I want to upload my CV once,
So that the app knows who I am (FR-4, AD-6).

**Acceptance Criteria:**

**Given** the `cv` table (single row) with `CvController` and `POST /api/cv`
**When** I upload a markdown CV
**Then** it is stored in PostgreSQL and replaces any prior CV (overwrite-only)
**And** the latest CV is returned on read

### Story 2.3: Analysis request pipeline

As a job hunter,
I want to trigger an analysis,
So that the job is queued and I get an immediate response (FR-5, AD-3).

**Acceptance Criteria:**

**Given** `POST /api/saved-jobs/{id}/analyze` publishing `analysis.request` and the ai-analyzer-service consuming it
**When** I trigger analysis on a saved job
**Then** the request returns immediately and the message reaches the consumer
**And** there is no `IN_PROGRESS` status — processing state is derived from absence of report / pending SSE (also fix the architecture sequence diagram's IN_PROGRESS step)

### Story 2.4: Structured analysis execution + CV context reuse

As a job hunter,
I want an AI report,
So that I judge my fit objectively (FR-6, FR-7; NFR-5).

**Acceptance Criteria:**

**Given** the `AiProvider` port + `OpenAiAdapter`, with CV key-information extracted once and reused across analyses
**When** the consumer processes `analysis.request`
**Then** it calls OpenAI with only CV context + job description, and persists an `AnalysisResult` (score 0-100, summary, strengths/weaknesses as `{ title, detail }` objects stored as JSONB) in `analysis_results`
**And** `saved_jobs.status` becomes `ANALYZED` (`analysis_results` owned by ai-analyzer-service, AD-2)

### Story 2.5: SSE completion event

As a job hunter,
I want live notification,
So that the report appears without refreshing (FR-8, AD-8).

**Acceptance Criteria:**

**Given** `GET /api/events` (SSE) with an `SseBroadcaster`
**When** an analysis completes
**Then** an `analysis-completed` event carrying the report reference is pushed to the frontend

### Story 2.7: DLT & retry policy

As a developer,
I want failed messages recoverable,
So that no work is silently lost (FR-11, AD-9).

**Acceptance Criteria:**

**Given** the consumer retry policy (3 attempts, exponential backoff)
**When** retry exhaustion occurs
**Then** the message lands in `analysis.request.DLT` and is visible for manual replay

## Frontend

### Story 2.2: CV upload UI

As a job hunter,
I want a CV screen,
So that I drop my resume with a clear overwrite guard (FR-4 UI; UX-DR2, UX-DR12, UX-DR14).

**Acceptance Criteria:**

**Given** the CV tab with a dropzone and current-CV preview
**When** I upload a new CV
**Then** an overwrite confirm modal appears ("Replace current CV?") and the preview updates after confirm
**And** cancel keeps the previous CV

### Story 2.6: Analysis UI

As a job hunter,
I want the report rendered,
So that I read my score and strengths at a glance (FR-5, FR-7, FR-8 UI; UX-DR3, UX-DR6, UX-DR7).

**Acceptance Criteria:**

**Given** the analysis panel on Job detail with an in-progress state (skeleton + status line "Extracting CV context… Analyzing…")
**When** the SSE `analysis-completed` event lands
**Then** the panel swaps to the full report (score badge with ramp ≥80 green / 50–79 amber / <50 red, summary, strengths/weaknesses) in place, no refresh, with `aria-live` announcement
**And** `prefers-reduced-motion` renders static status lines (no shimmer)
