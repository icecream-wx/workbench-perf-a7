#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v node >/dev/null 2>&1; then
  echo "[ERROR] node not found in PATH. Install Node.js 20.19+ first." >&2
  exit 1
fi

exec node scripts/app.mjs stop
