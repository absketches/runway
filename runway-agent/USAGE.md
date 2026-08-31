# Using Runway Migration Review Agent On Your Own Migrations

This guide shows how to run the agent against your own SQL migrations with your own OpenAI API key.

The agent is advisory only. It does not modify, delete, combine, rewrite, apply or execute migrations.

## 1. Prepare The Project

From a clean clone:

```bash
git clone https://github.com/absketches/runway.git
cd runway/runway-agent
python3 -m pip install -r requirements.txt
cp .env.example .env
```

Edit `.env`:

```bash
OPENAI_API_KEY=your_api_key_here
RUNWAY_AGENT_MODEL=gpt-5
RUNWAY_AGENT_TEMPERATURE=0
RUNWAY_AGENT_MIGRATIONS=../path/to/your/migrations
RUNWAY_AGENT_DIALECT=postgresql
```

Supported dialect values are the existing Runway codegen dialects:

- `postgresql`
- `mysql`
- `mariadb`
- `sqlite`

## 2. Migration Directory Requirements

Your migration directory must:

- contain versioned SQL files such as `V1__create_users.sql`;
- be readable by the local Python process;
- use SQL compatible with the configured dialect.

The migration directory can be anywhere on your local filesystem. The directory you configure becomes the boundary: the agent can list and read SQL migration files inside that directory only.
Generated output stays inside the Runway repository, normally under `runway-agent/outputs/`.

For example, if your migrations live in another project:

```bash
RUNWAY_AGENT_MIGRATIONS=/path/to/your/service/src/main/resources/db/migration
```

Or run a one-off smoke test directly:

```bash
/path/to/runway/runway-agent/scripts/run-agent.sh \
  --migrations /path/to/your/service/src/main/resources/db/migration \
  --dialect postgresql \
  --output-dir outputs/my-service-smoke \
  --concise \
  --max-output-tokens 3000 \
  --files V1__create_users.sql V2__add_email.sql
```

## 3. Run Deterministic Runway Analysis First

```bash
./scripts/analyze.sh
```

This generates an HTML impact report under:

```text
runway-agent/outputs/
```

This step does not use the OpenAI API and does not spend model tokens. It only runs the existing local Runway analysis.

## 4. Run The Full Agent Flow

```bash
./scripts/run-agent.sh
```

This is the normal end-to-end agent run. It uses your `OPENAI_API_KEY` and spends model tokens.

What it does:

1. Loads `.env`.
2. Lists the configured migration files.
3. Runs local Runway analysis-only mode.
4. Extracts impact facts from the generated Runway HTML report.
5. Selects relevant SQL files for closer inspection.
6. Sends the impact facts and inspected SQL excerpts to the configured model.
7. Checks the model output against the review schema.
8. Verifies evidence and safety deterministically.
9. Renders the final Markdown report.

The agent is not meant to redo Runway's impact analysis. Runway remains the source of truth for migration order, touched objects, statuses, merge candidates, data changes and incomplete-analysis warnings. The agent reads SQL files only to attach short excerpts, explain why a Runway fact may matter and turn it into maintainer operating steps.

The generated files are:

- `outputs/agent-review.json`: verified structured JSON;
- `outputs/agent-review.md`: human-readable Markdown report;
- `outputs/agent-trajectory.jsonl`: sanitized tool and verification trace;
- `outputs/runway-impact-*.html`: Runway impact report.

Use this when you want the actual review report for the configured migration directory.

## 5. Run The Baseline Flow

```bash
./scripts/run-baseline.sh
```

This also uses your `OPENAI_API_KEY` and spends model tokens. It sends migration SQL directly to the model with a simple prompt. It does not use Runway impact analysis, controlled tools, iterative source inspection or deterministic verification.

Use this only when you want to compare the full agent against a simpler AI-only review.

## 6. Run A Lower-Cost Smoke Test

For a small test, pass a subset of filenames directly:

```bash
/path/to/runway/runway-agent/scripts/run-agent.sh \
  --migrations /path/to/your/service/src/main/resources/db/migration \
  --dialect postgresql \
  --output-dir outputs/smoke \
  --concise \
  --max-output-tokens 3000 \
  --files V1__create_users.sql V2__add_email.sql
```

This still uses your API key but keeps the model response short.

## 7. Which Commands Spend Tokens?

| Command | Uses OpenAI API? | Spends model tokens? | Purpose |
| --- | --- | --- | --- |
| `./scripts/analyze.sh` | No | No | Local Runway impact analysis only |
| `./scripts/run-agent.sh` | Yes | Yes | Full verified agent review |
| `./scripts/run-baseline.sh` | Yes | Yes | Direct-prompt comparison baseline |
| `./scripts/evaluate.sh` | Yes, if `OPENAI_API_KEY` is set | Yes, if run with credentials | Runs baseline and agent over 10 cases |
| `./scripts/demo.sh` | Yes | Yes, capped | Small concise demo case using demo-only migrations |

## 8. Read The Output

Open:

```bash
sed -n '1,220p' outputs/agent-review.md
```

Each finding includes:

- decision;
- confidence;
- migrations;
- database objects;
- the Runway signal being addressed;
- a maintainer action plan;
- explanation;
- evidence excerpts;
- risk;
- human-review instructions;
- verification notes if the verifier changed the finding.

Decision meanings:

- `SAFE_TO_CONSIDER`: safe for a human to investigate, not safe to execute automatically.
- `NEEDS_HUMAN_REVIEW`: plausible relationship, but a developer must inspect dependencies, environment state or incomplete facts.
- `INSUFFICIENT_EVIDENCE`: the workflow could not support the claim.

## 9. Token And Cost Controls

Use these controls when testing with your own API key:

```bash
RUNWAY_AGENT_MODEL=gpt-5
RUNWAY_AGENT_TEMPERATURE=0
```

For cheaper smoke tests, prefer:

```bash
./scripts/run-agent.sh --concise --max-output-tokens 3000 --files ...
```

For full reviews, omit `--concise` and `--max-output-tokens` or choose a higher cap:

```bash
./scripts/run-agent.sh --max-output-tokens 2000
```

The run summary prints token usage when the OpenAI API returns it:

```json
{
  "usage": {
    "input_tokens": 864,
    "output_tokens": 2310,
    "total_tokens": 3174
  }
}
```

## 10. Evaluate The Sample Dataset

The bundled evaluation dataset uses Runway's synthetic PostgreSQL integration-test migrations:

```bash
./scripts/evaluate.sh
```

The evaluation runs both the baseline and the agent for each case, so a full run can take several minutes depending on model latency. The command creates `outputs/evaluation/results.md` when it starts, updates it after each completed case and stops any single baseline or agent child after 120 seconds by default.

For a quick check:

```bash
./scripts/evaluate.sh --stop-after 1 --timeout-seconds 60
```

This is mainly for reproducing the bundled comparison dataset. For your own migrations, start with `analyze.sh`, `run-agent.sh` and targeted smoke tests.

## 11. Troubleshooting

Missing API key:

```text
OPENAI_API_KEY is required.
```

Fix: set `OPENAI_API_KEY` in `runway-agent/.env`.

OpenAI call failed:

```text
OpenAI API call failed. Check OPENAI_API_KEY, network/DNS access, model name and account quota.
```

Fix: check that the API key is valid, your network can reach the OpenAI API, the configured model exists for your account and your account has quota. Agent runs also log this as an `openai_error` event in `outputs/agent-trajectory.jsonl`. Evaluation runs write per-case details to `outputs/evaluation/<case-id>/run-diagnostics.json`.

Missing OpenAI package:

```text
Missing Python dependency: openai.
```

Fix:

```bash
python3 -m pip install -r requirements.txt
```

Missing Java or Maven:

```text
Missing required command: java
Missing required command: mvn
```

Fix: install JDK 21 and Maven.

Migration directory rejected:

```text
Migration directory does not exist
```

Fix: point `RUNWAY_AGENT_MIGRATIONS` at an existing directory containing versioned SQL migrations.

## 12. What To Share In A Review

For a human review, share:

- `outputs/agent-review.md`
- the matching `outputs/runway-impact-*.html`
- the relevant migration SQL files
- `outputs/agent-trajectory.jsonl` if you want to inspect tool use and verification behavior

Do not share `.env` or API keys.
