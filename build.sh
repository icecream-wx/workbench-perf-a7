#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v mvn >/dev/null 2>&1; then
  echo "[ERROR] mvn not found in PATH." >&2
  exit 1
fi
if ! command -v npm >/dev/null 2>&1; then
  echo "[ERROR] npm not found in PATH. Install Node.js 20.19+ first." >&2
  exit 1
fi

echo "[INFO] backend: mvn -q -DskipTests package"
(
  cd backend
  mvn -q -DskipTests package
)

echo "[INFO] frontend dependencies"
(
  cd frontend
  if [ ! -d node_modules ]; then
    if [ -f package-lock.json ]; then
      echo "[INFO] npm ci"
      npm ci
    else
      echo "[INFO] npm install"
      npm install
    fi
  fi
  echo "[INFO] frontend: npm run build"
  npm run build
)

echo "[INFO] artifacts:"
for jar in backend/target/*.jar; do
  [ -e "$jar" ] && case "$jar" in *.original) ;; *) echo "  $jar" ;; esac
done
[ -f frontend/dist/index.html ] && echo "  frontend/dist/index.html"
