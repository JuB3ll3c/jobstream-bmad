# JobStream

[![Status](https://img.shields.io/badge/Status-Development-yellow)]()
[![Java](https://img.shields.io/badge/Java-25-blue)]()

> **Microservices project showcasing Spring Boot, Kafka, AI, and DevOps** — built as a technical portfolio piece.

AI-assisted job candidature platform. Search job offers, analyze their fit with your CV, generate tailored cover letters, and track applications with a kanban board.

## Stack

- **Backend :** Java 25, Spring Boot (WebMVC + WebFlux)
- **Messaging :** Apache Kafka
- **Database :** PostgreSQL + Redis (cache)
- **Frontend :** Angular 22
- **Infra :** Docker, Kubernetes, Prometheus/Grafana

## Architecture

```mermaid
flowchart LR
    ANG[Angular SPA]
    DASH[jobstream-api<br/>:8081]
    ANL[ai-analyzer-service<br/>:8082]
    KF[Kafka]
    PG[(PostgreSQL)]
    RD[(Redis)]
    JS[JSearch API]
    AZ[Adzuna API]
    OAI[OpenAI]

    ANG -->|REST| DASH
    ANG -->|SSE| ANL
    DASH -->|produce| KF
    ANL -->|consume| KF
    DASH -->|JPA| PG
    DASH -->|cache| RD
    ANL -->|R2DBC| PG
    DASH -->|WebClient| JS
    DASH -->|WebClient| AZ
    ANL -->|WebClient| OAI
```

2 event-driven microservices :

| Service | Stack | Port | Role |
|---|---|---|---|
| jobstream-api | Spring WebMVC + JPA | 8081 | Job CRUD, Redis cache, CV upload |
| ai-analyzer-service | Spring WebFlux + R2DBC | 8082 | AI analysis, cover letter, SSE real-time |

### Infrastructure

```mermaid
flowchart TB
    subgraph DockerLocal[Docker Compose]
        DASH[jobstream-api<br/>:8081]
        ANL[ai-analyzer-service<br/>:8082]
        PG[(PostgreSQL<br/>:5432)]
        RD[(Redis<br/>:6379)]
        KF[Kafka KRaft<br/>:9092]
    end

    subgraph External[External APIs]
        JS[JSearch API]
        AZ[Adzuna API]
        OAI[OpenAI API]
    end

    DASH --> PG & RD & KF
    ANL --> PG & KF
    DASH --> JS & AZ
    ANL --> OAI
```

## Method

Built with the [BMad Method](https://github.com/bmad-code-org/BMAD-METHOD) — AI-driven agile development workflow (agents, phases, ADR-tracked architecture).

## Docs

- [Product Brief](docs/product-brief.md)
- [Architecture Spine](docs/architecture-spine.md)
- [Architecture Decisions](docs/architecture-decisions.md)
