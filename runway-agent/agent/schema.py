from __future__ import annotations

from typing import Any


ALLOWED_DECISIONS = {"SAFE_TO_CONSIDER", "NEEDS_HUMAN_REVIEW", "INSUFFICIENT_EVIDENCE"}
ALLOWED_CONFIDENCE = {"LOW", "MEDIUM", "HIGH"}

REVIEW_SCHEMA: dict[str, Any] = {
    "type": "object",
    "required": ["summary", "findings", "limitations"],
    "additionalProperties": False,
    "properties": {
        "summary": {"type": "string"},
        "findings": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": [
                    "id",
                    "type",
                    "decision",
                    "migrations",
                    "objects",
                    "runwaySignal",
                    "maintainerActions",
                    "explanation",
                    "evidence",
                    "confidence",
                    "risk",
                    "humanReview",
                ],
                "properties": {
                    "id": {"type": "string"},
                    "type": {"type": "string"},
                    "decision": {"type": "string", "enum": sorted(ALLOWED_DECISIONS)},
                    "migrations": {"type": "array", "items": {"type": "string"}},
                    "objects": {"type": "array", "items": {"type": "string"}},
                    "runwaySignal": {"type": "string"},
                    "maintainerActions": {"type": "array", "items": {"type": "string"}},
                    "explanation": {"type": "string"},
                    "evidence": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "additionalProperties": False,
                            "required": ["file", "statementOrExcerpt", "reason"],
                            "properties": {
                                "file": {"type": "string"},
                                "statementOrExcerpt": {"type": "string"},
                                "reason": {"type": "string"},
                            },
                        },
                    },
                    "confidence": {"type": "string", "enum": sorted(ALLOWED_CONFIDENCE)},
                    "risk": {"type": "string"},
                    "humanReview": {"type": "string"},
                },
            },
        },
        "limitations": {"type": "array", "items": {"type": "string"}},
    },
}


def validate_review_shape(report: Any) -> list[str]:
    issues: list[str] = []
    if not isinstance(report, dict):
        return ["Review output must be a JSON object."]

    _require_known_keys(report, {"summary", "findings", "limitations"}, "review", issues)
    _require_string(report, "summary", "review", issues)
    findings = report.get("findings")
    if not isinstance(findings, list):
        issues.append("review.findings must be a list.")
        findings = []
    limitations = report.get("limitations")
    if not isinstance(limitations, list) or not all(isinstance(item, str) for item in limitations):
        issues.append("review.limitations must be a list of strings.")

    for index, finding in enumerate(findings, start=1):
        location = f"finding {index}"
        if not isinstance(finding, dict):
            issues.append(f"{location} must be an object.")
            continue
        _require_known_keys(
            finding,
            {
                "id",
                "type",
                "decision",
                "migrations",
                "objects",
                "runwaySignal",
                "maintainerActions",
                "explanation",
                "evidence",
                "confidence",
                "risk",
                "humanReview",
            },
            location,
            issues,
        )
        for key in ("id", "type", "runwaySignal", "explanation", "risk", "humanReview"):
            _require_string(finding, key, location, issues)
        decision = finding.get("decision")
        if decision not in ALLOWED_DECISIONS:
            issues.append(f"{location}.decision must be one of {sorted(ALLOWED_DECISIONS)}.")
        confidence = finding.get("confidence")
        if confidence not in ALLOWED_CONFIDENCE:
            issues.append(f"{location}.confidence must be one of {sorted(ALLOWED_CONFIDENCE)}.")
        _require_string_list(finding, "migrations", location, issues)
        _require_string_list(finding, "objects", location, issues)
        _require_string_list(finding, "maintainerActions", location, issues)
        evidence = finding.get("evidence")
        if not isinstance(evidence, list):
            issues.append(f"{location}.evidence must be a list.")
            continue
        for evidence_index, item in enumerate(evidence, start=1):
            evidence_location = f"{location}.evidence {evidence_index}"
            if not isinstance(item, dict):
                issues.append(f"{evidence_location} must be an object.")
                continue
            _require_known_keys(item, {"file", "statementOrExcerpt", "reason"}, evidence_location, issues)
            for key in ("file", "statementOrExcerpt", "reason"):
                _require_string(item, key, evidence_location, issues)
    return issues


def _require_known_keys(value: dict[str, Any], allowed: set[str], location: str, issues: list[str]) -> None:
    unknown = sorted(set(value) - allowed)
    if unknown:
        issues.append(f"{location} contains unsupported fields: {unknown}.")


def _require_string(value: dict[str, Any], key: str, location: str, issues: list[str]) -> None:
    if not isinstance(value.get(key), str) or not value.get(key, "").strip():
        issues.append(f"{location}.{key} must be a non-empty string.")


def _require_string_list(value: dict[str, Any], key: str, location: str, issues: list[str]) -> None:
    items = value.get(key)
    if not isinstance(items, list) or not all(isinstance(item, str) and item.strip() for item in items):
        issues.append(f"{location}.{key} must be a list of non-empty strings.")
