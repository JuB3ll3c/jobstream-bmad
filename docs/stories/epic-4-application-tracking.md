---
title: 'Epic 4 — Application Tracking — Stories'
status: in-progress
created: 2026-07-31
updated: 2026-07-31
epic: 4
fr: [FR-10]
uxdr: [UX-DR2, UX-DR5, UX-DR7, UX-DR9, UX-DR11, UX-DR13]
---

# Epic 4: Application Tracking — Stories

> **Goal:** The user tracks every application on a kanban board through its forward-only lifecycle (`SAVED → ANALYZED → COVER_LETTER → APPLIED → POSITIVE | NEGATIVE`) with drag & drop and a shared terminal column.
> Epic list and requirements inventory in `../epics.md`. Backend story 4.1, frontend story 4.2.

## Backend

### Story 4.1: Kanban status update endpoint

As a job hunter,
I want to move a saved job forward,
So that my board reflects where each application stands (FR-10, AD-5).

**Acceptance Criteria:**

**Given** `PATCH /api/saved-jobs/{id}/status` with the enum `SAVED → ANALYZED → COVER_LETTER → APPLIED → POSITIVE | NEGATIVE`
**When** I move a Saved Job forward (e.g. `APPLIED`, `POSITIVE`, `NEGATIVE` from `COVER_LETTER`, or forward from any earlier state)
**Then** the status is updated and persisted, and the board always reflects persisted status
**And** a backward transition returns an error (forward-only enforced)

## Frontend

### Story 4.2: Kanban board UI

As a job hunter,
I want a visual board,
So that I see every application's stage at a glance and move it forward (FR-10 UI; UX-DR2, UX-DR5, UX-DR7, UX-DR9, UX-DR11, UX-DR13).

**Acceptance Criteria:**

**Given** the Board tab with 5 columns (`SAVED`, `ANALYZED`, `COVER_LETTER`, `APPLIED`, terminal `POSITIVE | NEGATIVE`) and cards showing title, company, status pill, score badge when analyzed
**When** I drag a card (drag & drop enabled from `COVER_LETTER` forward) or move it via keyboard (arrows + Enter + Esc)
**Then** the card lands and the status persists; the terminal column stays neutral with the pill carrying the verdict
**And** a backward move attempt is rejected with a toast ("That move isn't allowed — status can only move forward.") and the card snaps back; below `lg` the board degrades to a single-column status list
