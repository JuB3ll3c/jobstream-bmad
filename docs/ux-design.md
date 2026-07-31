---
title: 'UX Design: JobStream'
status: final
created: 2026-07-31
updated: 2026-07-31
---

# UX Design: JobStream

> Recap of the UX design work. Sources of truth live in the planning artifacts:
> - `_bmad-output/planning-artifacts/ux-designs/ux-jobstream-bmad-2026-07-31/DESIGN.md`
> - `_bmad-output/planning-artifacts/ux-designs/ux-jobstream-bmad-2026-07-31/EXPERIENCE.md`

## Design (DESIGN.md)

**UI system:** custom Tailwind CSS tokens, no component library. Light theme only for V1; dark mode deferred to V2. Desktop-first web, single-tenant, no login, English UI.

### Colors

- **Primary** — blue-600 `#2563EB` is the only action color (buttons, links, focus rings, active nav). `primary-hover` `#1D4ED8`, `primary-soft` `#EFF6FF` for selected chips and score-badge backgrounds.
- **Neutrals** — slate ramp: `background` `#F8FAFC`, `surface` `#FFFFFF`, `border` `#E2E8F0`, `muted-foreground` `#64748B`.
- **Verdict colors** (AI judgement + status only, never chrome) — green `#16A34A`, amber `#D97706`, red `#DC2626`.
- **Score ramp** — ≥80 green, 50–79 amber, <50 red. The number and the label always agree.

### Typography

Inter everywhere — no display font. Hierarchy by weight/size: `display` 24px/600, `heading` 20px/600, `body-lg` 16px, `body` 14px, `label` 13px/500, `caption` 12px.

### Layout, elevation, shapes

- Search at `max-w-3xl`, Board full-width (`max-w-7xl`). Top bar with tabs (Search · Board · CV), no sidebar.
- Border-dominant: cards separate by 1px border, never shadow. Elevation only for drag, modals, toasts.
- Tighter corners than Tailwind: 4px inputs, 6px cards/buttons, 8px panels/toasts; `rounded/full` only on status badges.

### Components

- **Button primary/secondary**, **Input** (2px `ring` focus halo), **Card**, **Kanban column** (subtle field), **Status badge** (pill), **Score badge**, **Analysis panel** (SSE report surface), **Toast**.

## Experience (EXPERIENCE.md)

### Information architecture

| Surface | Reached from | Purpose |
|---|---|---|
| Search (home) | App open / tab | Merged, deduplicated results (JSearch + Adzuna), save per row |
| Board | Tab | Kanban: `SAVED → ANALYZED → COVER_LETTER → APPLIED → POSITIVE | NEGATIVE`, forward-only |
| Job detail | Search row / Board card | Description + Analysis Report |
| CV | Tab | Single markdown CV, overwrite-only |
| Cover letter | Job detail | Generate / view / regenerate |

**No `IN_PROGRESS` status** — `APPLIED` is the human in-progress stage (per PRD glossary). Panels layer over Search/Board; modal stacks stay one level deep.

### Key states & rules

- **SSE-driven async**: analysis and cover letters render "in progress → done" in place, no refresh, `aria-live` announcement.
- **Backward moves rejected** with a toast + card snap-back; board always reflects persisted status.
- **Cover letter before analysis**: generate button disabled, raced requests rejected to DLT.
- Cold load → skeletons; empty states always have an action; provider quota → degraded notice + Retry.

### Interaction primitives

Mouse-first (drag & drop is the centerpiece), with `/` (search), `Enter`, `s` (save), `Esc`, and full keyboard fallback for the board (not mouse-only).

### Accessibility floor

WCAG 2.2 AA, 2px focus ring at AA contrast, `aria-live` on SSE completions, keyboard-operable board, `prefers-reduced-motion` respected.

### Key flows

- **UJ-1 — The single-session hunt**: search → merge → save 2 → upload CV → trigger analysis → SSE report renders in place → generate cover letter → drag to APPLIED.
- **UJ-2 — The fresh-clone demo**: clone → `docker compose up` → full pipeline works in minutes; provider rate-limit degrades gracefully without breaking the demo.
