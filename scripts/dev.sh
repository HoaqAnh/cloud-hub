#!/usr/bin/env bash
set -Eeuo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

for command in docker pnpm; do
  if ! command -v "$command" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$command" >&2
    exit 1
  fi
done

docker compose --env-file .env.example -f infra/docker-compose.yml up -d

(
  cd "$root/apps/backend"
  exec bash ./mvnw spring-boot:run
) &
backend_pid=$!

(
  cd "$root/apps/frontend"
  exec pnpm dev
) &
frontend_pid=$!

stop_services() {
  trap - EXIT INT TERM
  kill "$backend_pid" "$frontend_pid" 2>/dev/null || true
  wait "$backend_pid" "$frontend_pid" 2>/dev/null || true
}
trap stop_services EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf 'Frontend: http://localhost:5173\n'
printf 'Backend:  http://localhost:8080\n'
printf 'Press Ctrl+C to stop the frontend and backend.\n'

wait "$backend_pid" "$frontend_pid"
