# Runway Migration Review Agent

Runway Migration Review Agent reviews SQL migration history with Runway's impact analysis, then writes a cited report for a human reviewer.

## Existing Runway Work And Agent Additions

Already provided by Runway:

- Runway migration engine
- `runway-core`
- `runway-codegen`
- SQL parsing and impact analysis
- HTML migration impact report
- Existing integration-test migrations
- Native-image and reflection-free functionality

Added in this directory:

- Migration-review agent flow
- Controlled tools exposed to the agent
- Evidence-verification layer
- Baseline workflow
- Evaluation dataset and scoring
- Agent trajectories
- Improvement changelog
- Reproduction and demo scripts
- Agent-specific documentation

## Problem

Database migration histories become difficult to understand after years of incremental changes. Backend engineers must determine which migrations still matter, which objects replaced earlier ones and whether a long history can safely be consolidated. Reviewing every SQL file manually is slow, while a general-purpose AI review may produce convincing recommendations without reliable evidence.

Intended user: a backend engineer maintaining a long history of SQL database migrations.

Practical value: the agent turns Runway impact facts plus selected SQL into a maintainer action guide. It calls out likely replacements, consolidation candidates, dependencies, uncertainty, follow-up checks and the operating steps a maintainer should take before making any migration-history decision.

Central insight: Agents are most reliable when they do not replace deterministic analysis. Runway establishes what changed; the agent explains how to respond; verification prevents the guidance from outrunning the evidence.

## Architecture

Flow:

1. `list_migrations(directory)` lists approved SQL migrations.
2. `run_runway_analysis(directory, dialect)` runs the existing `RunwayCodegen --analysis-only` command and writes an HTML impact report.
3. The workflow extracts concise impact data from the unchanged HTML report.
4. `read_migration(path)` reads only selected approved SQL files.
5. The model returns structured JSON recommendations with maintainer actions.
6. `schema.py` checks the review shape and allowed enum values.
7. `verify.py` deterministically checks evidence and safety.
8. `render.py` writes a human-readable Markdown report.

The agent has no unrestricted shell tool and no tool that writes migration files.
It does not replace Runway's analysis. Runway produces deterministic facts; the agent reads selected SQL only for evidence excerpts, interpretation and maintainer guidance.

## Safety Model

The workflow never modifies, deletes, combines, rewrites, applies or executes migrations. `SAFE_TO_CONSIDER` means safe for a human to investigate, not safe to execute. Applied production history still needs normal engineering review, environment checks, backups and release controls.

Findings use one of:

- `SAFE_TO_CONSIDER`
- `NEEDS_HUMAN_REVIEW`
- `INSUFFICIENT_EVIDENCE`

The verifier downgrades unsupported findings and shows changed or rejected findings in the final report.

## Baseline

The baseline is deliberately simple. It sends the same SQL files to the same configured model with this prompt:

> Review these SQL migrations and identify obsolete migrations, replacement relationships, dependencies and possible consolidation candidates. Explain every recommendation.

It does not receive Runway impact analysis, controlled tools, iterative source inspection or deterministic verification.

## Evaluation

`evaluation/cases.json` contains 10 fixed cases built from the existing PostgreSQL synthetic integration-test migrations. They cover replacement candidates, ordinary migrations that should not be flagged, dependency-sensitive relationships, incomplete analysis and one challenging mixed-object case.

Primary metric: correctly classified cases out of 10.

Additional signals: unsupported claims, critical misses, human-review escalations, evidence coverage, runtime and usage data when the API returns it.

Current results are generated under `outputs/evaluation/results.md`.

## Setup From Clean Clone

```bash
git clone https://github.com/absketches/runway.git
cd runway
cd runway-agent
python3 -m pip install -r requirements.txt
cp .env.example .env
```

Edit `.env` and set `OPENAI_API_KEY`.

Environment variables:

- `OPENAI_API_KEY`: required for baseline, agent, demo and full evaluation.
- `RUNWAY_AGENT_MODEL`: model name, default `gpt-5`.
- `RUNWAY_AGENT_TEMPERATURE`: default `0`.
- `RUNWAY_AGENT_MIGRATIONS`: migration directory; default `../runway-integration-tests/src/test/runway/postgresql`. This can be an absolute path outside the Runway repository.
- `RUNWAY_AGENT_DIALECT`: default `postgresql`.

The OpenAI SDK reads `OPENAI_API_KEY` from the environment.

## Commands

From `runway-agent/`:

```bash
./scripts/analyze.sh
./scripts/run-baseline.sh
./scripts/run-agent.sh
./scripts/evaluate.sh
./scripts/demo.sh
```

`analyze.sh` does not require an API key. The other scripts do.
`run-agent.sh` is the full review and spends OpenAI API tokens. It runs local Runway analysis first, sends selected evidence to the configured model, verifies the response and renders the final report.
`evaluate.sh` runs baseline and agent child processes for each case. It creates `outputs/evaluation/results.md` immediately, updates it after each completed case and applies a per-child timeout, defaulting to 120 seconds.

For running the agent on your own migration history with your own OpenAI API key, see `USAGE.md`.

## Tested Versions

- Java: requires JDK 21 because the parent Maven build sets `maven.compiler.release` to `21`.
- Maven: required to build `runway-codegen` if the jar is not already present.
- Python: tested with Python 3 style code; Python 3.10+ is recommended.
- OpenAI Python SDK: `openai>=1.40.0`.

## Runtime And Cost

Expected local analysis runtime is usually seconds after Maven dependencies are cached. Agent and baseline runtime depends on the selected model and API latency. Approximate API cost is reported only if usage data is available from the SDK response; otherwise it is marked unavailable rather than estimated.

## Known Limitations

- The impact report remains HTML; this workflow extracts row data from report attributes instead of changing Runway's existing report format.
- Evaluation scoring uses deterministic text matching and is intentionally conservative.
- SQL history alone cannot prove application usage, production deployment state or business safety.
- Incomplete Runway analysis forces uncertainty.
- Files in `examples/` are placeholders or sanitized examples until live API outputs are generated.

## Troubleshooting OpenAI Calls

If the model call fails, the agent and baseline print a direct error:

```text
OpenAI API call failed. Check OPENAI_API_KEY, network/DNS access, model name and account quota.
```

For agent runs, the same failure is also written to `outputs/agent-trajectory.jsonl` as an `openai_error` event. During evaluation, each case writes `run-diagnostics.json` with child process stdout, stderr and exit codes.

## Trajectories

Inspect live trajectories at `outputs/agent-trajectory.jsonl` after running the agent. Each JSONL event records the initial instruction, tool requests, tool responses, selected files, draft output, verification feedback and final output locations. Sanitized examples live in `examples/trajectories/`.

## Demo

Run:

```bash
./scripts/demo.sh
```

The demo reviews a small demo-only migration pair under `examples/demo-migrations/`, then prints the Runway analysis location, agent summary, verification result and final Markdown report.

The demo intentionally runs the agent in concise mode with a capped response budget so it demonstrates the workflow without using the full evaluation budget.

## Main Failure Mode

Main failure mode: the model can overinterpret related SQL objects as replacements. The verifier catches unsupported evidence and unsafe action language, but it cannot prove production safety.

## Changelog

See `CHANGELOG.md`.
