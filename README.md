# CloudHub

CloudHub is a local multi-cloud file portal. Explorer supports copying and moving individual files between connected accounts. Choose a destination account, then use its root folder or browse to a subfolder.

## Prerequisites

- Docker
- JDK 21 or later
- Node.js 24 LTS
- pnpm 11

## Start locally

Create `.env` from `.env.example`. Set `CLOUDHUB_ADMIN_PASSWORD` to your local admin password and `CLOUDHUB_TOKEN_KEY` to a random secret (for example, generate one with `openssl rand -hex 32`). Keep the token key stable across restarts. On startup, the configured password sets or updates the configured local admin account. If upgrading from the old default token key, disconnect and reconnect existing cloud accounts after setting the new key. The backend and PostgreSQL bind to loopback.

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

## Backend restart during development

Spring Boot DevTools restarts the backend when Java classes are compiled into `apps/backend/target/classes`. Enable build on save in your IDE, or run `bash ./mvnw compile` (`.\mvnw.cmd compile` on Windows) from `apps/backend` in a separate terminal. Vite and PostgreSQL stay running. After editing `.env` or changing dependencies, restart the affected process. The packaged backend excludes DevTools.

## HTTP request logs

The backend prints a request summary to stdout and returns an `X-Request-ID` response header. The dev scripts also write rotating logs to `apps/backend/logs/http.log`. Use `tail -f apps/backend/logs/http.log` on Bash or `Get-Content apps/backend/logs/http.log -Wait` on PowerShell. Search for the response ID to find the method, route, status, and duration. The summary excludes request and response content, query parameters, and credentials.

## Frontend workspace packages

- `packages/ui`: shared shadcn/ui primitives, added with the shadcn CLI
- `packages/contracts`: shared TypeScript API/domain interfaces
- `packages/api-client`: cookie-aware API transport and typed errors
- `packages/i18n`: [English and Vietnamese translations](packages/i18n/README.md)

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

## Development stack

- Maven Spring Boot backend with Flyway and a public `/api/health` endpoint
- Vite React TypeScript frontend with feature-based layers, shadcn/ui, Tailwind, and TanStack Query
- PostgreSQL Docker Compose service
- Initial database migration
- Editor, environment, workspace, and ADR configuration
