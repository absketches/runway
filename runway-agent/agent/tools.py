from __future__ import annotations

import html
import json
import os
import re
import subprocess
import time
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MIGRATIONS = REPO_ROOT / "runway-integration-tests" / "src" / "test" / "runway" / "postgresql"
OUTPUT_ROOT = Path(__file__).resolve().parents[1] / "outputs"


class ToolError(RuntimeError):
    pass


@dataclass(frozen=True)
class ToolContext:
    repo_root: Path
    migration_dir: Path
    output_dir: Path

    @classmethod
    def create(cls, migration_dir: str | None = None, output_dir: str | None = None) -> "ToolContext":
        root = REPO_ROOT.resolve()
        configured = Path(migration_dir or os.environ.get("RUNWAY_AGENT_MIGRATIONS", str(DEFAULT_MIGRATIONS)))
        if not configured.is_absolute():
            configured = (Path.cwd() / configured)
        migration_path = configured.resolve()
        if not migration_path.is_dir():
            raise ToolError(f"Migration directory does not exist: {migration_path}")
        out = Path(output_dir or OUTPUT_ROOT)
        if not out.is_absolute():
            out = (Path.cwd() / out)
        out = out.resolve()
        if not _inside(out, root):
            raise ToolError(f"Output directory must stay inside repository: {out}")
        out.mkdir(parents=True, exist_ok=True)
        return cls(root, migration_path, out)

    def require_migration_path(self, path: str) -> Path:
        candidate = Path(path)
        if not candidate.is_absolute():
            candidate = self.migration_dir / candidate
        resolved = candidate.resolve()
        if not _inside(resolved, self.migration_dir):
            raise ToolError(f"Path escapes migration directory: {path}")
        if resolved.suffix.lower() != ".sql":
            raise ToolError(f"Only SQL migration files can be read: {path}")
        if not resolved.is_file():
            raise ToolError(f"Migration file does not exist: {path}")
        return resolved

    def require_output_path(self, path: str) -> Path:
        candidate = Path(path)
        if not candidate.is_absolute():
            candidate = self.output_dir / candidate
        resolved = candidate.resolve()
        if not _inside(resolved, self.output_dir):
            raise ToolError(f"Path escapes output directory: {path}")
        if not resolved.is_file():
            raise ToolError(f"Output file does not exist: {path}")
        return resolved


def _inside(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def list_migrations(directory: str | None = None, context: ToolContext | None = None) -> list[str]:
    ctx = context or ToolContext.create(directory)
    return sorted(path.name for path in ctx.migration_dir.glob("V*.sql") if path.is_file())


def read_migration(path: str, context: ToolContext | None = None) -> str:
    ctx = context or ToolContext.create()
    return ctx.require_migration_path(path).read_text(encoding="utf-8")


def run_runway_analysis(
    directory: str | None = None,
    dialect: str = "postgresql",
    context: ToolContext | None = None,
) -> dict[str, Any]:
    ctx = context or ToolContext.create(directory)
    timestamp = time.strftime("%Y%m%d-%H%M%S")
    report = ctx.output_dir / f"runway-impact-{timestamp}.html"
    jar = _ensure_codegen_jar(ctx.repo_root)
    cmd = [
        "java",
        "-cp",
        str(jar),
        "io.github.absketches.runway.codegen.RunwayCodegen",
        "--input",
        str(ctx.migration_dir),
        "--dialect",
        dialect,
        "--impact-output",
        str(report),
        "--analysis-only",
    ]
    started = time.monotonic()
    completed = subprocess.run(cmd, cwd=ctx.repo_root, text=True, capture_output=True, check=False)
    runtime = time.monotonic() - started
    if completed.returncode != 0:
        raise ToolError(
            "Runway analysis failed with exit code "
            f"{completed.returncode}: {completed.stderr.strip() or completed.stdout.strip()}"
        )
    return {
        "report": str(report.relative_to(ctx.repo_root)),
        "runtimeSeconds": round(runtime, 3),
        "summary": read_impact_report(str(report), ctx),
    }


def read_impact_report(path: str, context: ToolContext | None = None) -> dict[str, Any]:
    ctx = context or ToolContext.create()
    report = ctx.require_output_path(path)
    parser = _RunwayReportParser()
    parser.feed(report.read_text(encoding="utf-8"))
    return {
        "files": parser.rows,
        "overview": _summarize_rows(parser.rows),
    }


def inspect_sql_files(files: list[str], context: ToolContext) -> dict[str, str]:
    return {name: read_migration(name, context) for name in files}


def extract_objects(sql: str) -> set[str]:
    identifiers: set[str] = set()
    patterns = [
        r"\bcreate\s+(?:or\s+replace\s+)?(?:view|table|function|procedure|trigger)\s+(?:if\s+not\s+exists\s+)?([\"`\w.]+)",
        r"\bdrop\s+(?:view|table|function|procedure|trigger)\s+(?:if\s+exists\s+)?([\"`\w.]+)",
        r"\bupdate\s+([\"`\w.]+)",
        r"\bfrom\s+([\"`\w.]+)",
        r"\bjoin\s+([\"`\w.]+)",
    ]
    for pattern in patterns:
        for match in re.finditer(pattern, sql, flags=re.IGNORECASE):
            identifiers.add(_clean_identifier(match.group(1)))
    return identifiers


def _clean_identifier(value: str) -> str:
    return value.strip('"`').split(".")[-1].lower()


def _ensure_codegen_jar(repo_root: Path) -> Path:
    target = repo_root / "runway-codegen" / "target"
    jars = sorted(target.glob("runway-codegen-*.jar"))
    jars = [jar for jar in jars if "sources" not in jar.name and "javadoc" not in jar.name]
    if jars:
        return jars[-1]
    cmd = ["mvn", "-q", "-pl", "runway-codegen", "-am", "package", "-DskipTests"]
    completed = subprocess.run(cmd, cwd=repo_root, text=True, capture_output=True, check=False)
    if completed.returncode != 0:
        raise ToolError(
            "Could not build runway-codegen jar. Run `mvn -q -pl runway-codegen -am package -DskipTests` first. "
            f"Maven output: {completed.stderr.strip() or completed.stdout.strip()}"
        )
    jars = sorted(target.glob("runway-codegen-*.jar"))
    jars = [jar for jar in jars if "sources" not in jar.name and "javadoc" not in jar.name]
    if not jars:
        raise ToolError("Maven completed but no runway-codegen jar was found.")
    return jars[-1]


class _RunwayReportParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.rows: list[dict[str, Any]] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag != "tr":
            return
        data = {key: value or "" for key, value in attrs if key.startswith("data-copy-")}
        if not data:
            return
        row = {
            "file": html.unescape(data.get("data-copy-file", "")),
            "version": html.unescape(data.get("data-copy-version", "")),
            "status": html.unescape(data.get("data-copy-status", "")),
            "tables": _split(data.get("data-copy-tables", "")),
            "objects": _split(data.get("data-copy-objects", "")),
            "schemaWrites": _split(data.get("data-copy-schema-writes", "")),
            "runtimeWrites": _split(data.get("data-copy-runtime-writes", "")),
            "runtimeReads": _split(data.get("data-copy-runtime-reads", "")),
            "mergeWith": _split(data.get("data-copy-merge-with", "")),
            "warnings": _split(data.get("data-copy-warnings", "")),
            "details": html.unescape(data.get("data-copy-details", "")),
        }
        self.rows.append(row)


def _split(value: str) -> list[str]:
    decoded = html.unescape(value).strip()
    if not decoded or decoded == "-":
        return []
    return [item.strip() for item in re.split(r",\s*", decoded) if item.strip()]


def _summarize_rows(rows: list[dict[str, Any]]) -> dict[str, Any]:
    counts: dict[str, int] = {}
    for row in rows:
        status = row.get("status") or "Unknown"
        counts[status] = counts.get(status, 0) + 1
    return {"fileCount": len(rows), "statusCounts": counts}


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
