# Runway Migration Review Agent Changelog

This changelog covers the migration-review agent only. Runway's migration engine, SQL parsing, impact analysis, HTML report, codegen, native-image behavior and existing integration-test migrations already exist outside this directory.

## 1. Baseline: Direct Prompt Over Migration SQL

What changed: Added a baseline runner that sends the selected SQL files and a direct review prompt to the same configured model.

Why: The baseline shows what a general-purpose AI review sees without deterministic Runway facts, controlled tools, iterative source selection or verification.

Observed evidence: Not measured until `OPENAI_API_KEY` is available and `./scripts/evaluate.sh` is run.

Decision or learning: Keep it simple and fair. It is useful as a comparison point, not as a safety mechanism.

## 2. Iteration 1: Provide Runway Impact Analysis

What changed: Added `run_runway_analysis(directory, dialect)`, which invokes the existing `RunwayCodegen --analysis-only` command and extracts concise table-row impact data from the HTML report.

Why: Runway establishes deterministic migration-impact facts before the model interprets relationships.

Observed evidence: Local analysis can be run with `./scripts/analyze.sh`.

Decision or learning: The agent should not parse SQL history in isolation when Runway already provides impact facts.

## 3. Iteration 2: Require File and Object Evidence

What changed: The system prompt and review runner require exact migration filenames, object names, short SQL excerpts, risk and human-review instructions.

Why: Every recommendation should be inspectable by a backend engineer.

Observed evidence: Representative examples are in `examples/`.

Decision or learning: Evidence makes uncertainty visible and keeps recommendations reviewable.

## 4. Iteration 3: Add Deterministic Verification

What changed: Added `agent/schema.py` for structured output validation and `agent/verify.py` to check file existence, path boundaries, inspected-file coverage, evidence excerpts, object support, incomplete-analysis handling and unsafe action language.

Why: The final report should not allow explanation to outrun the evidence.

Observed evidence: Unit tests cover accepted, downgraded and path-escape cases.

Decision or learning: Unsupported findings are downgraded to `INSUFFICIENT_EVIDENCE` instead of silently omitted.

## 5. Removed Experiment: Automatic Migration Rewriting

What changed: Automatic rewrite/delete/combine behavior was rejected at design time and not implemented.

Why: Applied migration history is operationally consequential. The agent must produce recommendations only.

Observed evidence: Tooling exposes no unrestricted shell to the model and no write path for migrations.

Decision or learning: Human approval is a product requirement, not a missing feature.

## 6. Final Workflow

What changed: The final workflow is deterministic Runway analysis, agent interpretation, evidence verification and Markdown rendering.

Why: Agents are most reliable when paired with deterministic systems that define the factual boundary.

Observed evidence: Run `./scripts/run-agent.sh` and inspect `outputs/agent-review.md`, `outputs/agent-review.json` and `outputs/agent-trajectory.jsonl`.

Decision or learning: The useful agent is a reviewer and explainer, not an autonomous migration editor.
