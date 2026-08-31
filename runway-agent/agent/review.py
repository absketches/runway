from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import time
from pathlib import Path
from typing import Any

from openai import OpenAI

from agent.render import render_markdown
from agent.schema import REVIEW_SCHEMA, validate_review_shape
from agent.tools import ToolContext, inspect_sql_files, list_migrations, run_runway_analysis, write_json
from agent.verify import verify_report


class ModelOutputError(RuntimeError):
    pass


def main() -> int:
    args = _parse_args()
    _load_dotenv(Path(args.env_file))
    if not os.environ.get("OPENAI_API_KEY"):
        print("OPENAI_API_KEY is required to run the agent. Copy .env.example to .env and set it.", file=sys.stderr)
        return 2

    context = ToolContext.create(args.migrations, args.output_dir)
    output_dir = context.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    if args.files:
        case_dir = output_dir / "_selected_migrations"
        case_dir.mkdir(parents=True, exist_ok=True)
        for existing in case_dir.glob("*.sql"):
            existing.unlink()
        for name in args.files:
            source = context.require_migration_path(name)
            shutil.copy2(source, case_dir / source.name)
        context = ToolContext.create(str(case_dir), str(output_dir))
    trajectory_path = Path(args.trajectory or output_dir / "agent-trajectory.jsonl")
    trajectory_path.parent.mkdir(parents=True, exist_ok=True)
    trajectory_path.write_text("", encoding="utf-8")

    started = time.monotonic()
    _event(trajectory_path, "instruction", {"systemPrompt": Path(args.system_prompt).read_text(encoding="utf-8")})

    migrations = list_migrations(context=context)
    _event(trajectory_path, "tool_request", {"name": "list_migrations", "arguments": {"directory": str(context.migration_dir)}})
    _event(trajectory_path, "tool_response", {"name": "list_migrations", "result": migrations})

    analysis = run_runway_analysis(str(context.migration_dir), args.dialect, context)
    _event(
        trajectory_path,
        "tool_response",
        {"name": "run_runway_analysis", "result": {"report": analysis["report"], "overview": analysis["summary"]["overview"]}},
    )

    selected = _select_files(migrations, analysis["summary"])
    sql_sources = inspect_sql_files(selected, context)
    _event(trajectory_path, "selected_files", {"files": selected})
    for name, sql in sql_sources.items():
        _event(trajectory_path, "tool_response", {"name": "read_migration", "file": name, "chars": len(sql)})

    try:
        draft = _call_model(
            system_prompt=Path(args.system_prompt).read_text(encoding="utf-8"),
            migrations=migrations,
            impact=analysis["summary"],
            sql_sources=sql_sources,
            model=args.model or os.environ.get("RUNWAY_AGENT_MODEL", "gpt-5"),
            temperature=float(os.environ.get("RUNWAY_AGENT_TEMPERATURE", "0")),
            max_output_tokens=args.max_output_tokens,
            concise=args.concise,
        )
    except ModelOutputError as exc:
        message = f"Model response could not be parsed as review JSON. Details: {exc}"
        _event(trajectory_path, "model_output_error", {"message": message})
        print(message, file=sys.stderr)
        print(f"Trajectory saved at: {trajectory_path}", file=sys.stderr)
        return 1
    except Exception as exc:
        message = _openai_error_message(exc)
        _event(trajectory_path, "openai_error", {"message": message})
        print(message, file=sys.stderr)
        print(f"Trajectory saved at: {trajectory_path}", file=sys.stderr)
        return 1
    _event(trajectory_path, "draft_finding", {"draft": draft})

    usage = draft.pop("_usage", None) if isinstance(draft, dict) else None
    verification = verify_report(draft, context, set(sql_sources.keys()), analysis["summary"])
    _event(
        trajectory_path,
        "verification_feedback",
        {
            "accepted": verification.accepted,
            "changed": verification.changed,
            "rejected": verification.rejected,
        },
    )

    report_json = output_dir / "agent-review.json"
    report_md = output_dir / "agent-review.md"
    write_json(report_json, verification.report)
    report_md.write_text(render_markdown(verification.report), encoding="utf-8")

    result = {
        "json": str(report_json),
        "markdown": str(report_md),
        "trajectory": str(trajectory_path),
        "runwayAnalysis": analysis["report"],
        "runtimeSeconds": round(time.monotonic() - started, 3),
        "usage": usage,
    }
    write_json(output_dir / "agent-run-summary.json", result)
    print(json.dumps(result, indent=2))
    return 0


def _call_model(
    system_prompt: str,
    migrations: list[str],
    impact: dict[str, Any],
    sql_sources: dict[str, str],
    model: str,
    temperature: float,
    max_output_tokens: int | None,
    concise: bool,
) -> dict[str, Any]:
    client = OpenAI()
    user_payload = {
        "task": "Review these SQL migrations using the Runway impact summary and inspected SQL sources.",
        "requiredSchema": REVIEW_SCHEMA,
        "migrationFiles": migrations,
        "runwayImpactSummary": impact,
        "inspectedSqlSources": sql_sources,
    }
    instructions = [
        "Return only valid JSON.",
        "Use exact migration filenames.",
        "Every finding must include evidence from inspectedSqlSources.",
    ]
    if concise:
        instructions.append("Demo mode: return at most one finding and keep every field concise.")
    prompt = " ".join(instructions) + "\n\n" + json.dumps(user_payload, indent=2)
    request = {
        "model": model,
        "input": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": prompt},
        ],
        "temperature": temperature,
    }
    if max_output_tokens is not None:
        request["max_output_tokens"] = max_output_tokens
    try:
        response = client.responses.create(**request)
    except Exception as exc:
        if "temperature" not in str(exc).lower():
            raise
        request.pop("temperature", None)
        response = client.responses.create(**request)
    text = _response_text(response)
    parsed = _parse_json(text)
    shape_issues = validate_review_shape(parsed)
    if shape_issues:
        raise ModelOutputError("; ".join(shape_issues))
    usage = getattr(response, "usage", None)
    if usage is not None:
        parsed["_usage"] = usage.model_dump() if hasattr(usage, "model_dump") else str(usage)
    return parsed


def _response_text(response: Any) -> str:
    output_text = getattr(response, "output_text", None)
    if output_text:
        return output_text
    chunks: list[str] = []
    for item in getattr(response, "output", []) or []:
        for content in getattr(item, "content", []) or []:
            text = getattr(content, "text", None)
            if text:
                chunks.append(text)
    if chunks:
        return "\n".join(chunks)
    if hasattr(response, "model_dump"):
        dumped = response.model_dump()
        chunks = _collect_text_values(dumped.get("output", []))
        if chunks:
            return "\n".join(chunks)
    return str(response)


def _collect_text_values(value: Any) -> list[str]:
    if isinstance(value, dict):
        if value.get("type") in {"output_text", "text"} and isinstance(value.get("text"), str):
            return [value["text"]]
        chunks: list[str] = []
        for nested in value.values():
            chunks.extend(_collect_text_values(nested))
        return chunks
    if isinstance(value, list):
        chunks = []
        for item in value:
            chunks.extend(_collect_text_values(item))
        return chunks
    return []


def _openai_error_message(exc: Exception) -> str:
    detail = str(exc).strip() or exc.__class__.__name__
    return (
        "OpenAI API call failed. Check OPENAI_API_KEY, network/DNS access, model name and account quota. "
        f"Details: {detail}"
    )


def _parse_json(text: str) -> dict[str, Any]:
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = stripped.strip("`")
        if stripped.lower().startswith("json"):
            stripped = stripped[4:].strip()
    candidates: list[dict[str, Any]] = []
    decoder = json.JSONDecoder()
    for index, char in enumerate(stripped):
        if char != "{":
            continue
        try:
            parsed, _ = decoder.raw_decode(stripped[index:])
        except json.JSONDecodeError:
            continue
        if isinstance(parsed, dict):
            candidates.append(parsed)
    if not candidates:
        raise ModelOutputError("Response did not contain a JSON object.")
    for candidate in reversed(candidates):
        if {"summary", "findings", "limitations"}.issubset(candidate):
            return candidate
    return candidates[-1]


def _select_files(migrations: list[str], impact: dict[str, Any]) -> list[str]:
    interesting: set[str] = set()
    interesting_statuses = {
        "merge candidate",
        "partial merge candidate",
        "incomplete analysis",
        "data changes",
    }
    for row in impact.get("files", []):
        status = str(row.get("status", "")).lower()
        if status in interesting_statuses:
            interesting.add(row.get("file", ""))
        for target in row.get("mergeWith", []):
            if target:
                interesting.add(target.split(":", 1)[0].strip())
    if not interesting:
        interesting.update(migrations)
    selected = [name for name in migrations if name in interesting]
    return selected or migrations


def _event(path: Path, event_type: str, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    event = {"type": event_type, "payload": payload}
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(event, sort_keys=True) + "\n")


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
    parser = argparse.ArgumentParser(description="Run the Runway migration review agent.")
    parser.add_argument("--migrations", default=os.environ.get("RUNWAY_AGENT_MIGRATIONS"))
    parser.add_argument("--dialect", default=os.environ.get("RUNWAY_AGENT_DIALECT", "postgresql"))
    parser.add_argument("--model", default=os.environ.get("RUNWAY_AGENT_MODEL", "gpt-5"))
    parser.add_argument("--output-dir", default=str(Path(__file__).resolve().parents[1] / "outputs"))
    parser.add_argument("--trajectory")
    parser.add_argument("--max-output-tokens", type=int)
    parser.add_argument("--concise", action="store_true")
    parser.add_argument("--env-file", default=str(Path(__file__).resolve().parents[1] / ".env"))
    parser.add_argument("--system-prompt", default=str(Path(__file__).with_name("system-prompt.md")))
    parser.add_argument("--files", nargs="*", help="Optional subset of migration filenames for evaluation.")
    return parser.parse_args()


if __name__ == "__main__":
    raise SystemExit(main())
