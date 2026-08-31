#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_java_maven
load_env
if [[ -n "${OPENAI_API_KEY:-}" ]]; then
  require_python_deps
fi

cd "${ROOT_DIR}"
python3 -m evaluation.evaluate "$@"
