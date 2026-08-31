from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import time
from pathlib import Path

from openai import OpenAI


REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MIGRATIONS = REPO_ROOT / "runway-integration-tests" / "src" / "test" / "runway" / "postgresql"


def main() -> int:
    args = _parse_args()
    _load_dotenv(Path(args.env_file))
    if not os.environ.get("OPENAI_API_KEY"):
        print("OPENAI_API_KEY is required to run the baseline. Copy .env.example to .env and set it.", file=sys.stderr)
        return 2

    migrations = Path(args.migrations or DEFAULT_MIGRATIONS).resolve()
    if not migrations.is_dir():
        print(f"Invalid migration directory: {migrations}", file=sys.stderr)
        return 2
    if args.files:
        case_dir = Path(args.output_dir) / "_selected_migrations"
        case_dir.mkdir(parents=True, exist_ok=True)
        for existing in case_dir.glob("*.sql"):
            existing.unlink()
        for name in args.files:
            source = (migrations / name).resolve()
            if not source.is_file() or migrations not in source.parents:
                print(f"Invalid migration file for case: {name}", file=sys.stderr)
                return 2
            shutil.copy2(source, case_dir / source.name)
        migrations = case_dir.resolve()
    files = sorted(path for path in migrations.glob("V*.sql") if path.is_file())
    sources = {path.name: path.read_text(encoding="utf-8") for path in files}

    prompt = Path(args.prompt).read_text(encoding="utf-8")
    client = OpenAI()
    started = time.monotonic()
    request = {
        "model": args.model or os.environ.get("RUNWAY_AGENT_MODEL", "gpt-5"),
        "input": prompt + "\n\nSQL migrations:\n" + json.dumps(sources, indent=2),
        "temperature": float(os.environ.get("RUNWAY_AGENT_TEMPERATURE", "0")),
    }
    try:
        response = client.responses.create(**request)
    except Exception as exc:
        if "temperature" in str(exc).lower():
            request.pop("temperature", None)
            try:
                response = client.responses.create(**request)
            except Exception as retry_exc:
                print(_openai_error_message(retry_exc), file=sys.stderr)
                return 1
        else:
            print(_openai_error_message(exc), file=sys.stderr)
            return 1
    text = getattr(response, "output_text", None) or str(response)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    raw = output_dir / "baseline-review.md"
    raw.write_text(text, encoding="utf-8")
    summary = {
        "rawOutput": str(raw),
        "runtimeSeconds": round(time.monotonic() - started, 3),
        "model": args.model or os.environ.get("RUNWAY_AGENT_MODEL", "gpt-5"),
    }
    (output_dir / "baseline-run-summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))
    return 0


def _load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


def _openai_error_message(exc: Exception) -> str:
    detail = str(exc).strip() or exc.__class__.__name__
    return (
        "OpenAI API call failed. Check OPENAI_API_KEY, network/DNS access, model name and account quota. "
        f"Details: {detail}"
    )


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the direct-prompt baseline.")
    parser.add_argument("--migrations", default=os.environ.get("RUNWAY_AGENT_MIGRATIONS"))
    parser.add_argument("--model", default=os.environ.get("RUNWAY_AGENT_MODEL", "gpt-5"))
    parser.add_argument("--output-dir", default=str(Path(__file__).resolve().parents[1] / "outputs"))
    parser.add_argument("--env-file", default=str(Path(__file__).resolve().parents[1] / ".env"))
    parser.add_argument("--prompt", default=str(Path(__file__).with_name("prompt.md")))
    parser.add_argument("--files", nargs="*", help="Optional subset of migration filenames for evaluation.")
    return parser.parse_args()


if __name__ == "__main__":
    raise SystemExit(main())
