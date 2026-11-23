# ADR 0001: Sprint 0 Foundation Decisions

Status: accepted
Date: 2026-11-23

## Decisions

- Backend: Java LTS with Spring Boot and Maven.
- Frontend: React, TypeScript, Vite, Tailwind CSS, shadcn-style Radix primitives, TanStack Router and Query.
- Database: PostgreSQL managed locally by Docker Compose.
- Portal authentication direction: JWT (open decision resolved by the tech stack).
- Provider integrations: official SDK or REST adapter where an SDK does not provide a clear benefit; provider access is isolated behind backend adapters.
- API contract: REST + OpenAPI, with generated TypeScript client in a later sprint.
- Local development origins: frontend `localhost:5173`, backend `localhost:8080`, PostgreSQL `localhost:5432`.

## Consequences

Sprint 0 is a runnable foundation rather than a product feature. OAuth, portal login, provider adapters, and token encryption remain scoped to later sprints.
