---
title: 'Epic 1 — Job Search & Save — Stories'
status: in-progress
created: 2026-07-31
updated: 2026-07-31
epic: 1
fr: [FR-1, FR-2, FR-3]
uxdr: [UX-DR1, UX-DR2, UX-DR4, UX-DR8, UX-DR9, UX-DR10, UX-DR14]
---

# Epic 1: Job Search & Save — Stories

> **Goal:** The user searches job offers from JSearch + Adzuna in one merged, URL-deduplicated, cached list and saves the relevant ones to their board.
> Epic list and requirements inventory in `../epics.md`. Backend stories 1.1–1.5, frontend stories 1.6–1.7.

## Backend

### Story 1.1: Backend scaffolding & local infra

As a developer,
I want the repo booted with both services and local infra,
So that the project runs via `docker compose up`.

**Acceptance Criteria:**

**Given** a fresh clone with Java 25 + Maven multi-module (jobstream-api, ai-analyzer-service) and Docker Compose (PostgreSQL 18, Redis 8.2, Kafka 4.3, both services)
**When** I run `docker compose up`
**Then** all containers start and both services report healthy via actuator health endpoints
**And** secrets are configurable via environment variables only (no secrets in config files)

### Story 1.2: JobProvider port + provider adapters

As a developer,
I want the provider boundary in place,
So that job sources plug in behind one port (AD-10).

**Acceptance Criteria:**

**Given** the `JobProvider` port and `JobOffer` domain model (title, company, URL, description)
**When** I implement `JSearchAdapter` and `AdzunaAdapter`
**Then** each adapter maps its provider response to a normalized `JobOffer` retaining provider + external_id
**And** provider credentials are read from environment variables

### Story 1.3: Merged search endpoint

As a job hunter,
I want one search that merges both providers,
So that I scan a single list instead of two sites (FR-1).

**Acceptance Criteria:**

**Given** `GET /api/jobs?q=<keyword>` and both provider adapters
**When** I search a keyword
**Then** results are merged and deduplicated by offer URL, ordered by provider order / posted date
**And** if one provider fails, the request still returns the other provider's results (no total failure)

### Story 1.4: Search result caching

As a job hunter,
I want repeat searches served fast,
So that identical queries within 30 minutes don't hit the external APIs (FR-2).

**Acceptance Criteria:**

**Given** `CachePort` + `RedisCacheAdapter` with TTL 30 minutes (cache key = normalized query string)
**When** I repeat an identical query within the TTL
**Then** results are served from Redis (verifiable via logs/metrics — no external API call)
**And** a cache miss calls the external APIs then stores the result in Redis

### Story 1.5: Save a job offer

As a job hunter,
I want to save any offer,
So that I can track it later on my board (FR-3).

**Acceptance Criteria:**

**Given** the `saved_jobs` table (UUID, external_id, title, company, url, description, status) and `SavedJobRepository`
**When** I `POST /api/saved-jobs`
**Then** a Saved Job is created with status `SAVED`
**And** a duplicate save (same external_id + URL) is rejected

## Frontend

### Story 1.6: Angular scaffold + design token layer

As a developer,
I want the frontend scaffolded with the design system,
So that every screen shares one visual language (UX-DR1, UX-DR2).

**Acceptance Criteria:**

**Given** Angular 22 + Tailwind CSS configured with the DESIGN.md tokens (colors, typography, rounded, spacing; light theme V1)
**When** I build the base components (Button primary/secondary, Input with focus ring, Card, Status badge, Score badge, Modal, Toast, Pagination)
**Then** they render per DESIGN.md specs and are reusable across surfaces
**And** dark tokens are explicitly not introduced (deferred to V2)

### Story 1.7: Search surface UI

As a job hunter,
I want the search screen,
So that I scan merged results and save offers without leaving the app (FR-1, FR-2, FR-3 UI; UX-DR4, UX-DR8, UX-DR9, UX-DR10, UX-DR14).

**Acceptance Criteria:**

**Given** the top bar with tabs (Search · Board · CV) and the merged search endpoint
**When** I open the app and search a keyword
**Then** results render with provider badges, a per-row Save action, and a pagination footer (20 per page, no infinite scroll)
**And** empty/degraded states show an action ("No results. Try another keyword." / provider notice + Retry); `/` focuses search, `s` saves the focused row
