#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${SCRIPT_DIR}/common.sh"

require_java_maven
require_command python3
load_env

cd "${ROOT_DIR}"
python3 - <<'PY'
import json
import os
from agent.tools import ToolContext, list_migrations, run_runway_analysis

ctx = ToolContext.create(os.environ.get("RUNWAY_AGENT_MIGRATIONS"))
print("Migration directory:", ctx.migration_dir)
print("Migration files:", len(list_migrations(context=ctx)))
result = run_runway_analysis(str(ctx.migration_dir), os.environ.get("RUNWAY_AGENT_DIALECT", "postgresql"), ctx)
print(json.dumps(result, indent=2))
PY
