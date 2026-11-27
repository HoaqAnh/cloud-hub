# CloudHub

CloudHub is a local multi-cloud file portal. Sprint 0 provides the monorepo, database, backend health endpoint, frontend health UI, and initial migrations.

## Prerequisites

- Docker
- JDK 21 or later
- Node.js 24 LTS
- pnpm 11

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

The dev scripts load `.env` into the backend process. If `.env` is absent, they use `.env.example`. Set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` for Google Drive OAuth, `DROPBOX_CLIENT_ID` and `DROPBOX_CLIENT_SECRET` for Dropbox OAuth, or `ONEDRIVE_CLIENT_ID` and `ONEDRIVE_CLIENT_SECRET` for Microsoft personal-account OneDrive OAuth. Configure the matching redirect URI in each provider app; keep secrets out of frontend variables.

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

## Frontend workspace packages

- `packages/ui`: shared shadcn/ui primitives, added with the shadcn CLI
- `packages/contracts`: shared TypeScript API/domain interfaces
- `packages/api-client`: cookie-aware API transport and typed errors
- `packages/i18n`: English and Vietnamese translations

Frontend features are organized under `apps/frontend/src/features/<feature>` with separate `api`, `hooks`, and `components` layers. See [the frontend README](apps/frontend/README.md) for the development commands and component conventions.

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
- Vite React TypeScript frontend with feature-based layers, shadcn/ui, Tailwind, and TanStack Query
- PostgreSQL Docker Compose service
- Initial database migration
- Editor, environment, workspace, and ADR configuration
