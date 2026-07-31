---
title: 'Epic 3 — Cover Letter — Stories'
status: in-progress
created: 2026-07-31
updated: 2026-07-31
epic: 3
fr: [FR-9, FR-11]
uxdr: [UX-DR2, UX-DR6, UX-DR8]
---

# Epic 3: Cover Letter — Stories

> **Goal:** The user generates a contextual cover letter from the analysis report of a saved offer; regeneration overwrites the previous letter.
> Epic list and requirements inventory in `../epics.md`. Backend story 3.1, frontend story 3.2.

## Backend

### Story 3.1: Cover letter generation pipeline

As a job hunter,
I want a letter generated from my analysis,
So that I apply with context (FR-9, FR-11; AD-3, AD-4).

**Acceptance Criteria:**

**Given** `POST /api/saved-jobs/{id}/cover-letter` publishing `cover-letter.request`, and the consumer verifying an Analysis Report exists for that saved job
**When** I generate a letter
**Then** the letter is written via OpenAI (analysis context + job description), `saved_jobs.cover_letter` is populated, status becomes `COVER_LETTER`, and a `cover-letter-completed` SSE event is delivered (reusing the SSE broadcaster)
**And** if no report exists, the message is rejected to `cover-letter.request.DLT` (retry 3×, exponential backoff) — no silent drop

## Frontend

### Story 3.2: Cover letter UI

As a job hunter,
I want the letter rendered inline,
So that I read and regenerate it without leaving the job (FR-9 UI; UX-DR2, UX-DR6, UX-DR8).

**Acceptance Criteria:**

**Given** the cover letter panel on Job detail with Generate disabled until an Analysis Report exists (helper text explains why)
**When** the SSE `cover-letter-completed` event lands
**Then** the letter renders inline, with a Regenerate action that overwrites (in-progress state during generation, `aria-live` on completion)
**And** microcopy follows the voice rules ("The letter is saved on this offer. You can regenerate it anytime.")
