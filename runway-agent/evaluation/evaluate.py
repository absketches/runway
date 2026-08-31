from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
CASES = ROOT / "evaluation" / "cases.json"
RESULTS = ROOT / "outputs" / "evaluation" / "results.md"


def main() -> int:
    args = _parse_args()
    _load_dotenv(ROOT / ".env")
    cases = json.loads(CASES.read_text(encoding="utf-8"))
    if not os.environ.get("OPENAI_API_KEY"):
        _write_results(_not_run(cases))
        print(f"OPENAI_API_KEY not found; wrote not-run results to {RESULTS}")
        return 0

    rows = []
    started_all = time.monotonic()
    selected_cases = cases[: args.stop_after] if args.stop_after else cases
    _write_results(_running_results(selected_cases, rows, 0.0))
    print(f"Writing evaluation results to {RESULTS}", flush=True)
    for index, case in enumerate(selected_cases, start=1):
        case_dir = ROOT / "outputs" / "evaluation" / case["id"]
        case_dir.mkdir(parents=True, exist_ok=True)
        print(f"[{index}/{len(selected_cases)}] {case['id']}: baseline", flush=True)
        baseline = _run(
            [sys.executable, "-m", "baseline.run", "--output-dir", str(case_dir / "baseline"), "--files", *case["files"]],
            ROOT,
            args.timeout_seconds,
        )
        print(f"[{index}/{len(selected_cases)}] {case['id']}: agent", flush=True)
        agent = _run(
            [sys.executable, "-m", "agent.review", "--output-dir", str(case_dir / "agent"), "--files", *case["files"]],
            ROOT,
            args.timeout_seconds,
        )
        (case_dir / "run-diagnostics.json").write_text(
            json.dumps({"baseline": baseline, "agent": agent}, indent=2) + "\n",
            encoding="utf-8",
        )
        baseline_text = _read(case_dir / "baseline" / "baseline-review.md")
        agent_text = _read(case_dir / "agent" / "agent-review.md")
        rows.append(
            {
                "case": case,
                "baseline": _score_text(case, baseline_text, baseline),
                "agent": _score_text(case, agent_text, agent),
            }
        )
        print(f"[{index}/{len(selected_cases)}] {case['id']}: done", flush=True)
        _write_results(_running_results(selected_cases, rows, round(time.monotonic() - started_all, 3)))

    _write_results(_render_results(rows, round(time.monotonic() - started_all, 3)))
    _print_result_location(RESULTS)
    return 0


def _run(cmd: list[str], cwd: Path, timeout_seconds: int) -> dict[str, Any]:
    started = time.monotonic()
    try:
        completed = subprocess.run(
            cmd,
            cwd=cwd,
            text=True,
            capture_output=True,
            check=False,
            timeout=timeout_seconds,
        )
        return {
            "exitCode": completed.returncode,
            "runtimeSeconds": round(time.monotonic() - started, 3),
            "stdout": completed.stdout[-2000:],
            "stderr": completed.stderr[-2000:],
            "timedOut": False,
        }
    except subprocess.TimeoutExpired as exc:
        stdout = _decode_timeout_output(exc.stdout)
        stderr = _decode_timeout_output(exc.stderr)
        return {
            "exitCode": 124,
            "runtimeSeconds": round(time.monotonic() - started, 3),
            "stdout": stdout[-2000:],
            "stderr": (stderr + f"\nTimed out after {timeout_seconds}s.").strip()[-2000:],
            "timedOut": True,
        }


def _score_text(case: dict[str, Any], text: str, run: dict[str, Any]) -> dict[str, Any]:
    if run["exitCode"] != 0 or not text.strip():
        return {
            "exitCode": run["exitCode"],
            "runtimeSeconds": run["runtimeSeconds"],
            "correct": False,
            "unsupportedClaims": [],
            "criticalMiss": True,
            "evidenceCoverage": 0.0,
            "runFailed": True,
            "error": _clean_error(run["stderr"] or run["stdout"]),
        }
    lowered = text.lower()
    expected = case["expectedClassification"].lower()
    forbidden = [claim for claim in case["forbiddenClaims"] if claim.lower() in lowered]
    evidence_hits = [term for term in case["requiredEvidence"] if term.lower() in lowered]
    correct = expected in lowered and not forbidden and bool(evidence_hits)
    if case["expectedClassification"] == "SAFE_TO_CONSIDER":
        correct = not forbidden and len(evidence_hits) >= max(1, len(case["requiredEvidence"]) // 2)
    return {
        "exitCode": run["exitCode"],
        "runtimeSeconds": run["runtimeSeconds"],
        "correct": correct,
        "unsupportedClaims": forbidden,
        "criticalMiss": len(evidence_hits) == 0,
        "evidenceCoverage": round(len(evidence_hits) / len(case["requiredEvidence"]), 2),
        "runFailed": False,
        "error": "",
    }


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8") if path.exists() else ""


def _render_results(rows: list[dict[str, Any]], runtime: float) -> str:
    baseline_scored = [row for row in rows if not row["baseline"].get("runFailed")]
    agent_scored = [row for row in rows if not row["agent"].get("runFailed")]
    baseline_correct = sum(1 for row in baseline_scored if row["baseline"]["correct"])
    agent_correct = sum(1 for row in agent_scored if row["agent"]["correct"])
    failed_runs = sum(
        1
        for row in rows
        for name in ("baseline", "agent")
        if row[name].get("runFailed")
    )
    lines = [
        "# Evaluation Results",
        "",
        f"Executed cases: {len(rows)}",
        f"Total runtime: {runtime}s",
        _score_line("Baseline correct", baseline_correct, len(baseline_scored)),
        _score_line("Agent correct", agent_correct, len(agent_scored)),
    ]
    if failed_runs:
        lines.extend(
            [
                "",
                f"Run failures: {failed_runs}",
                "",
                "At least one child run failed. Scores for failed rows are not meaningful; the table keeps the failure visible.",
            ]
        )
    lines.extend(
        [
            "",
            "| Case | Expected | Baseline | Agent | Agent Evidence | Notes |",
            "| --- | --- | --- | --- | --- | --- |",
        ]
    )
    for row in rows:
        case = row["case"]
        b = _result_cell(row["baseline"])
        a = _result_cell(row["agent"])
        notes = []
        if row["agent"]["unsupportedClaims"]:
            notes.append("unsupported agent claim")
        if row["agent"]["criticalMiss"] and not row["agent"].get("runFailed"):
            notes.append("agent critical miss")
        if row["baseline"].get("runFailed"):
            notes.append(f"baseline failed: {row['baseline']['error']}")
        if row["agent"].get("runFailed"):
            notes.append(f"agent failed: {row['agent']['error']}")
        lines.append(
            f"| {case['id']} | {case['expectedClassification']} | {b} | {a} | "
            f"{row['agent']['evidenceCoverage']} | {', '.join(notes) or '-'} |"
        )
    lines.append("")
    lines.append("Scoring is deterministic string matching against the fixed case rubric.")
    return "\n".join(lines) + "\n"


def _running_results(cases: list[dict[str, Any]], rows: list[dict[str, Any]], runtime: float) -> str:
    lines = [
        "# Evaluation Results",
        "",
        "Status: running.",
        "",
        f"Completed cases: {len(rows)}/{len(cases)}",
        f"Elapsed runtime: {runtime}s",
        "",
    ]
    if rows:
        lines.extend(_render_results(rows, runtime).splitlines()[7:])
    else:
        lines.append("No cases have completed yet.")
    lines.append("")
    lines.append("This file is updated after each completed case.")
    lines.append("")
    return "\n".join(lines)


def _score_line(label: str, correct: int, scored: int) -> str:
    if scored == 0:
        return f"{label}: not scored"
    return f"{label}: {correct}/{scored} scored"


def _result_cell(result: dict[str, Any]) -> str:
    if result.get("runFailed"):
        return "failed"
    return "yes" if result["correct"] else "no"


def _clean_error(value: str) -> str:
    lines = [line.strip() for line in value.splitlines() if line.strip()]
    if not lines:
        return "no output"
    important_prefixes = (
        "OpenAI API call failed",
        "Model response could not be parsed",
        "OPENAI_API_KEY is required",
        "Timed out after",
        "Runway analysis failed",
    )
    for line in lines:
        if line.startswith(important_prefixes):
            return line.replace("|", "/")[:180]
    return lines[-1].replace("|", "/")[:180]


def _decode_timeout_output(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, bytes):
        return value.decode("utf-8", errors="replace")
    return str(value)


def _not_run(cases: list[dict[str, Any]]) -> str:
    return "\n".join(
        [
            "# Evaluation Results",
            "",
            "Status: not run.",
            "",
            "Reason: `OPENAI_API_KEY` was not available in the environment or `runway-agent/.env`.",
            f"Configured cases: {len(cases)}",
            "",
            "Run `./scripts/evaluate.sh` after setting credentials to produce measured scores.",
            "",
        ]
    )


def _print_result_location(path: Path) -> None:
    relative = path.relative_to(ROOT)
    print("")
    print(f"Wrote evaluation results to {path}")
    print(f"From runway-agent, open it with: sed -n '1,120p' {relative}")
    print("")
    print(path.read_text(encoding="utf-8").split("\n\n| Case |", 1)[0])


def _write_results(markdown: str) -> None:
    RESULTS.parent.mkdir(parents=True, exist_ok=True)
    RESULTS.write_text(markdown, encoding="utf-8")


def _load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate baseline and final agent on fixed cases.")
    parser.add_argument("--stop-after", type=int)
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=int(os.environ.get("RUNWAY_AGENT_EVAL_TIMEOUT_SECONDS", "120")),
        help="Maximum seconds allowed for each baseline or agent child run.",
    )
    return parser.parse_args()


if __name__ == "__main__":
    raise SystemExit(main())
