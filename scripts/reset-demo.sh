#!/usr/bin/env sh
set -eu

env_file="${1:-.env.demo}"

if [ ! -f "$env_file" ]; then
  echo "Demo environment file not found: $env_file" >&2
  exit 1
fi

set -a
. "$env_file"
set +a

require_value() {
  eval "value=\${$1:-}"
  if [ -z "$value" ]; then
    echo "Missing $1 in $env_file" >&2
    exit 1
  fi
}

require_value DEMO_MODE
require_value DEMO_DATA_ENABLED
require_value DEMO_DATABASE_USERNAME
require_value DEMO_DATABASE_URL

[ "$DEMO_MODE" = "true" ] || { echo "Reset requires DEMO_MODE=true" >&2; exit 1; }
[ "$DEMO_DATA_ENABLED" = "true" ] || { echo "Reset requires DEMO_DATA_ENABLED=true" >&2; exit 1; }
[ "$DEMO_DATABASE_USERNAME" = "clinora_demo" ] || { echo "Reset requires clinora_demo user" >&2; exit 1; }
case "$DEMO_DATABASE_URL" in
  jdbc:postgresql://*/clinora_demo|jdbc:postgresql://*/clinora_demo\?*) ;;
  *) echo "Reset refused: DEMO_DATABASE_URL must target only clinora_demo" >&2; exit 1 ;;
esac
case "$DEMO_DATABASE_URL" in
  *prod*|*PROD*) echo "Reset refused: production-looking URL" >&2; exit 1 ;;
esac

docker compose --env-file "$env_file" -f docker-compose.demo.yml -p clinora-demo down -v
docker compose --env-file "$env_file" -f docker-compose.demo.yml -p clinora-demo up -d --build

port="${DEMO_BACKEND_PORT:-18080}"
deadline=$(( $(date +%s) + 120 ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  if curl --fail --silent --show-error "http://127.0.0.1:${port}/actuator/health" >/dev/null; then
    echo "Demo reset complete: backend healthy on port $port"
    exit 0
  fi
  sleep 2
done

echo "Demo backend did not become healthy within 120 seconds" >&2
exit 1
