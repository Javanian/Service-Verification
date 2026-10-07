#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${DB_PASSWORD:?Set a disposable PostgreSQL password}"
: "${OWNER_USERNAME:?Set a test owner username}"
: "${OWNER_PASSWORD:?Set a test owner password}"
(cd frontend && npm ci && npm run typecheck && npm test && npm run build)
rm -rf backend/src/main/resources/static
mkdir -p backend/src/main/resources/static
cp -R frontend/dist/browser/. backend/src/main/resources/static/
(cd backend && ./mvnw verify)
