from __future__ import annotations

import copy
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from agent.schema import ALLOWED_CONFIDENCE, ALLOWED_DECISIONS, validate_review_shape
from agent.tools import ToolContext, extract_objects, read_migration


UNSAFE_ACTION_PATTERNS = [
    r"\b(delete|drop|remove|rewrite|apply|execute)\s+(this\s+)?migration\b",
    r"\b(delete|drop|remove|rewrite|apply|execute)\s+migrations\b",
    r"\bautomatically\s+(delete|drop|remove|rewrite|apply|execute)\b",
]


@dataclass
class VerificationResult:
    report: dict[str, Any]
    accepted: list[dict[str, Any]]
    changed: list[dict[str, Any]]
    rejected: list[dict[str, Any]]
    inspected_files: set[str]


def verify_report(
    report: dict[str, Any],
    context: ToolContext,
    inspected_files: set[str],
    impact_summary: dict[str, Any],
) -> VerificationResult:
    verified = copy.deepcopy(report)
    findings = verified.get("findings")
    if not isinstance(findings, list):
        verified["findings"] = []
        findings = []

    accepted: list[dict[str, Any]] = []
    changed: list[dict[str, Any]] = []
    rejected: list[dict[str, Any]] = []
    impact_text = _impact_text(impact_summary)
    shape_issues = validate_review_shape(verified)

    final_findings: list[dict[str, Any]] = []
    for index, finding in enumerate(findings, start=1):
        if not isinstance(finding, dict):
            rejected.append({"id": f"finding-{index}", "reason": "Finding is not an object."})
            continue
        checked = copy.deepcopy(finding)
        issues = _finding_issues(checked, context, inspected_files, impact_summary, impact_text)
        issues.extend(_schema_issues_for_finding(shape_issues, index))
        if issues:
            checked["decision"] = "INSUFFICIENT_EVIDENCE"
            checked["confidence"] = "LOW"
            checked.setdefault("humanReview", "A developer must inspect the referenced migrations manually.")
            checked["verificationNotes"] = issues
            changed.append({"id": checked.get("id", f"finding-{index}"), "issues": issues})
        else:
            accepted.append({"id": checked.get("id", f"finding-{index}")})
        final_findings.append(checked)

    verified["findings"] = final_findings
    verified["verification"] = {
        "acceptedFindings": accepted,
        "changedFindings": changed,
        "rejectedFindings": rejected,
        "schemaIssues": shape_issues,
    }
    return VerificationResult(verified, accepted, changed, rejected, inspected_files)


def _finding_issues(
    finding: dict[str, Any],
    context: ToolContext,
    inspected_files: set[str],
    impact_summary: dict[str, Any],
    impact_text: str,
) -> list[str]:
    issues: list[str] = []
    decision = finding.get("decision")
    confidence = finding.get("confidence")
    if decision not in ALLOWED_DECISIONS:
        issues.append(f"Unsupported decision: {decision}")
    if confidence not in ALLOWED_CONFIDENCE:
        issues.append(f"Unsupported confidence: {confidence}")

    evidence = finding.get("evidence")
    if not isinstance(evidence, list) or not evidence:
        issues.append("Finding has no evidence items.")
        evidence = []

    migrations = finding.get("migrations")
    if not isinstance(migrations, list) or not migrations:
        issues.append("Finding references no migrations.")
        migrations = []

    source_by_file: dict[str, str] = {}
    for migration in migrations:
        if not isinstance(migration, str):
            issues.append("Migration reference is not a string.")
            continue
        try:
            path = context.require_migration_path(migration)
            source_by_file[path.name] = read_migration(path.name, context)
        except Exception as exc:
            issues.append(str(exc))
        if Path(migration).name not in inspected_files:
            issues.append(f"Migration was not inspected by the agent: {migration}")

    for item in evidence:
        if not isinstance(item, dict):
            issues.append("Evidence item is not an object.")
            continue
        try:
            evidence_path = context.require_migration_path(str(item.get("file", "")))
            file_name = evidence_path.name
        except Exception as exc:
            issues.append(str(exc))
            continue
        if file_name not in source_by_file:
            issues.append(f"Evidence references a file outside the finding migrations: {file_name}")
            continue
        if file_name not in inspected_files:
            issues.append(f"Evidence file was not inspected by the agent: {file_name}")
        excerpt = str(item.get("statementOrExcerpt", "")).strip()
        if not excerpt:
            issues.append(f"Evidence item has an empty excerpt: {file_name}")
        elif not _excerpt_supported(excerpt, source_by_file[file_name]):
            issues.append(f"Evidence excerpt was not found in cited file: {file_name}")

    objects = finding.get("objects", [])
    if not isinstance(objects, list):
        issues.append("Objects field is not a list.")
        objects = []
    available_objects = set()
    for sql in source_by_file.values():
        available_objects.update(extract_objects(sql))
    for obj in objects:
        normalized = _normalize(str(obj))
        if normalized and normalized not in available_objects and normalized not in impact_text:
            issues.append(f"Referenced object is unsupported by inspected SQL or impact data: {obj}")

    combined_text = " ".join(
        str(finding.get(key, ""))
        for key in ("runwaySignal", "explanation", "risk", "humanReview", "summary")
    ).lower()
    actions = finding.get("maintainerActions", [])
    if isinstance(actions, list):
        combined_text += " " + " ".join(str(action) for action in actions).lower()
    for pattern in UNSAFE_ACTION_PATTERNS:
        if re.search(pattern, combined_text):
            issues.append("Finding appears to recommend unsafe migration action.")
            break

    has_incomplete = _has_incomplete_analysis(finding, impact_summary)
    if has_incomplete and confidence == "HIGH" and decision == "SAFE_TO_CONSIDER":
        issues.append("Incomplete Runway analysis cannot produce an unqualified high-confidence recommendation.")

    return issues


def _excerpt_supported(excerpt: str, source: str) -> bool:
    compact_excerpt = _compact_sql(excerpt)
    compact_source = _compact_sql(source)
    return compact_excerpt in compact_source


def _compact_sql(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip().strip(";")).lower()


def _normalize(value: str) -> str:
    return value.strip('"`').split(".")[-1].lower()


def _impact_text(impact_summary: dict[str, Any]) -> str:
    return str(impact_summary).lower()


def _has_incomplete_analysis(finding: dict[str, Any], impact_summary: dict[str, Any]) -> bool:
    migration_names = {Path(str(name)).name for name in finding.get("migrations", []) if isinstance(name, str)}
    object_names = {_normalize(str(name)) for name in finding.get("objects", []) if isinstance(name, str)}
    for row in impact_summary.get("files", []):
        row_file = Path(str(row.get("file", ""))).name
        row_objects = {_normalize(str(name)) for name in row.get("objects", [])}
        related = row_file in migration_names or bool(object_names & row_objects)
        if not related:
            continue
        status = str(row.get("status", "")).lower()
        warnings = " ".join(str(warning).lower() for warning in row.get("warnings", []))
        if "incomplete" in status or "incomplete" in warnings:
            return True
    return False


def _schema_issues_for_finding(shape_issues: list[str], index: int) -> list[str]:
    prefix = f"finding {index}"
    return [issue for issue in shape_issues if issue.startswith(prefix)]
