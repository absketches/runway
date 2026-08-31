#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "${ROOT_DIR}/.." && pwd)"

load_env() {
  if [[ -f "${ROOT_DIR}/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "${ROOT_DIR}/.env"
    set +a
  fi
}

require_command() {
  local name="$1"
  if ! command -v "${name}" >/dev/null 2>&1; then
    echo "Missing required command: ${name}" >&2
    exit 2
  fi
}

require_python_deps() {
  require_command python3
  if ! python3 -c "import openai" >/dev/null 2>&1; then
    echo "Missing Python dependency: openai. Run: cd runway-agent && python3 -m pip install -r requirements.txt" >&2
    exit 2
  fi
}

require_openai_key() {
  load_env
  if [[ -z "${OPENAI_API_KEY:-}" ]]; then
    echo "OPENAI_API_KEY is required. Run: cp .env.example .env, then add your key." >&2
    exit 2
  fi
}

require_java_maven() {
  require_command java
  require_command mvn
}
