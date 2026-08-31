#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_java_maven
require_openai_key
require_python_deps

cd "${ROOT_DIR}"
DEMO_OUT="${ROOT_DIR}/outputs/demo"
DEMO_MIGRATIONS="${ROOT_DIR}/examples/demo-migrations"
mkdir -p "${DEMO_OUT}"

python3 -m agent.review \
  --migrations "${DEMO_MIGRATIONS}" \
  --output-dir "${DEMO_OUT}" \
  --trajectory "${DEMO_OUT}/trajectory.jsonl" \
  --concise \
  --max-output-tokens 3000 \
  --files V1__create_users.sql V20__create_active_user_ids_legacy.sql V40__replace_active_user_ids_same_definition.sql

echo
echo "Runway analysis and review summary:"
cat "${DEMO_OUT}/agent-run-summary.json"
echo
echo "Final Markdown report:"
sed -n '1,220p' "${DEMO_OUT}/agent-review.md"
