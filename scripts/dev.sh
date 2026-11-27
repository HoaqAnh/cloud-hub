#!/usr/bin/env bash
set -Eeuo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

env_file="$root/.env"
if [[ ! -f "$env_file" ]]; then
  env_file="$root/.env.example"
fi

line_number=0
while IFS= read -r line || [[ -n "$line" ]]; do
  line_number=$((line_number + 1))
  line="${line%$'\r'}"
  [[ "$line" =~ ^[[:space:]]*(#.*)?$ ]] && continue
  if [[ ! "$line" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
    printf 'Invalid env entry at %s:%d\n' "$env_file" "$line_number" >&2
    exit 1
  fi

  key="${BASH_REMATCH[1]}"
  value="${BASH_REMATCH[2]}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  if [[ "$value" =~ ^\"(.*)\"$ ]] || [[ "$value" =~ ^\'(.*)\'$ ]]; then
    value="${BASH_REMATCH[1]}"
  fi
  export "$key=$value"
done < "$env_file"

for command in docker pnpm; do
  if ! command -v "$command" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$command" >&2
    exit 1
  fi
done

docker compose --env-file "$env_file" -f infra/docker-compose.yml up -d

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
printf 'Backend:  http://localhost:%s\n' "${SERVER_PORT:-8080}"
printf 'Press Ctrl+C to stop the frontend and backend.\n'

wait "$backend_pid" "$frontend_pid"
