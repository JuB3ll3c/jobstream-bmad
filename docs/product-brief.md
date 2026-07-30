---
title: 'Product Brief: JobStream'
status: final
created: 2026-07-30
updated: 2026-07-30
---

# Product Brief: JobStream

## Executive Summary

JobStream is an AI-assisted job candidature platform that helps tech professionals streamline their job search. Search and save job offers from multiple aggregators, analyze how well they match your CV using AI, generate tailored cover letters, and track every application through a kanban board — all in one place.

Built as a microservices architecture (Spring Boot, Kafka, PostgreSQL, Redis, Angular), JobStream is also a technical showcase demonstrating mastery of modern backend stacks, event-driven design, AI integration, and DevOps practices.

## The Problem

Job hunting is fragmented. Offers are scattered across platforms (LinkedIn, Indeed, company career pages), each with its own workflow. Analyzing a job description against your CV means manual comparison. Writing a cover letter is a time-consuming chore done from scratch every time. Tracking where each application stands — analyzed, applied, waiting for feedback — quickly becomes unmanageable.

The result: missed opportunities, duplicated effort, and a stressful, disorganized process.

## The Solution

JobStream is a single dashboard where a tech candidate can:

- **Search** job offers from multiple external APIs with transparent caching
- **Save** relevant offers to a personal database with kanban status tracking
- **Analyze** each offer against their CV using OpenAI — receiving a structured report (score, strengths, weaknesses, summary)
- **Generate** a tailored cover letter from the analysis context
- **Track** every application through a visual kanban board: Saved → Analyzed → Cover Letter → Applied → Positive / Negative

## What Makes This Different

- **AI-first workflow**: analysis and cover letter generation are core features, not afterthoughts — the analysis result feeds directly into the cover letter context
- **Event-driven architecture**: async processing via Kafka keeps the UI responsive and demonstrates production-grade patterns even on a single-user project
- **Two Spring stacks in one project**: WebMVC (dashboard) and WebFlux (analyzer) show deliberate stack choice based on workload characteristics
- **Full local DevOps pipeline**: Docker Compose → Kubernetes manifests → CI/CD, designed to scale to Azure

## Who This Serves

**Primary user:** a tech professional (the developer) actively job-hunting, who wants one tool to manage the entire candidature pipeline — search, analyze, write, track — without juggling tabs and documents.

**Secondary audience:** technical recruiters and hiring managers evaluating the developer's engineering skills through the project's code quality, architecture decisions, and DevOps practices.

## Success Criteria

- A candidate can search, save, analyze, and generate a cover letter without leaving the app
- Kanban board accurately reflects real application status at a glance
- Analysis report is structured (score, strengths, weaknesses) and actionable
- Cover letter is contextualized by the existing analysis, not generic
- Project compiles, runs locally via Docker Compose, and passes lint/type checks
- GitHub repository is well-structured, documented, and presents a coherent architecture

## Scope

### In (MVP)

- Job search via JSearch and Adzuna APIs with Redis caching
- Save jobs with kanban status tracking (SAVED → ANALYZED → COVER_LETTER → APPLIED → POSITIVE/NEGATIVE)
- Single CV upload (markdown, stored in PostgreSQL)
- AI-powered job-offer analysis via OpenAI (structured report: score, summary, strengths, weaknesses)
- AI-powered cover letter generation (contextualized by existing analysis, overwrite on regeneration)
- Real-time SSE updates for analysis and cover letter completion
- Single-user, no authentication
- Angular frontend
- Docker Compose local deployment
- Kafka with dead-letter topics for resilience

### Out (first version)

- Multi-user / auth
- Cloud deployment (Azure) — planned as a separate learning phase
- CV versioning or multiple CVs
- PDF export of reports
- CI/CD pipeline — manual build until code stabilizes
- Monitoring dashboards — scaffold exists but not wired
- Mobile app — web-only

## Vision

JobStream evolves from a personal tool into a reference open-source project that demonstrates how modern Java microservices, event-driven architecture, and AI can be combined into a coherent, practical application. The architecture is designed to scale to real-world conditions — multi-user, cloud-deployed, fully monitored — and the project serves as both a useful job-search companion and a compelling engineering portfolio.
