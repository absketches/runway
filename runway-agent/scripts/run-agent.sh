#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_java_maven
require_openai_key
require_python_deps

cd "${ROOT_DIR}"
python3 -m agent.review "$@"
