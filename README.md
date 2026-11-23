# CloudHub

CloudHub is a local multi-cloud file portal. Sprint 0 provides the monorepo, database, backend health endpoint, frontend health UI, and initial migrations.

## Prerequisites

- Docker
- JDK 21 or later
- Node.js LTS
- pnpm

## Start locally

Create `.env` from `.env.example` and fill in any local settings before starting.

PowerShell:

```powershell
Copy-Item .env.example .env
```

Linux, macOS, or Bash:

```bash
cp .env.example .env
```

The dev scripts load `.env` into the backend process. If `.env` is absent, they use `.env.example`. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` in `.env` to enable Google Drive OAuth; keep those secrets out of frontend variables.

PowerShell:

```powershell
.\scripts\dev.ps1
```

Linux, macOS, or Bash:

```bash
bash scripts/dev.sh
```

Services:

- Frontend: http://localhost:5173
- Backend: http://localhost:8080
- PostgreSQL: localhost:55432

## Validate

```powershell
docker compose --env-file .env.example -f infra/docker-compose.yml up -d
Set-Location apps/backend
.\mvnw.cmd test
Set-Location ../frontend
pnpm lint
pnpm build
```

## Sprint 0 deliverables

- Maven Spring Boot backend with Flyway and a public `/api/health` endpoint
- Vite React TypeScript frontend with Tailwind, TanStack Query, and a health UI
- PostgreSQL Docker Compose service
- Initial database migration
- Editor, environment, workspace, and ADR configuration
