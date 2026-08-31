from __future__ import annotations

from typing import Any


def render_markdown(report: dict[str, Any]) -> str:
    lines: list[str] = ["# Runway Migration Review", ""]
    lines.append(report.get("summary") or "No summary provided.")
    lines.append("")

    findings = report.get("findings") or []
    if not findings:
        lines.extend(["## Findings", "", "No findings survived verification.", ""])
    else:
        lines.extend(["## Findings", ""])
        for finding in findings:
            lines.append(f"### {finding.get('id', 'finding')} - {finding.get('type', 'UNKNOWN')}")
            lines.append("")
            lines.append(f"- Decision: `{finding.get('decision', 'INSUFFICIENT_EVIDENCE')}`")
            lines.append(f"- Confidence: `{finding.get('confidence', 'LOW')}`")
            lines.append(f"- Migrations: {', '.join(f'`{m}`' for m in finding.get('migrations', []))}")
            objects = finding.get("objects", [])
            if objects:
                lines.append(f"- Objects: {', '.join(f'`{obj}`' for obj in objects)}")
            lines.append(f"- Runway signal: {finding.get('runwaySignal', '')}")
            lines.append(f"- Explanation: {finding.get('explanation', '')}")
            lines.append(f"- Risk: {finding.get('risk', '')}")
            lines.append(f"- Human review: {finding.get('humanReview', '')}")
            lines.append("")
            actions = finding.get("maintainerActions") or []
            if actions:
                lines.append("Maintainer action plan:")
                for action in actions:
                    lines.append(f"- {action}")
                lines.append("")
            lines.append("Evidence:")
            for evidence in finding.get("evidence", []):
                file_name = evidence.get("file", "")
                excerpt = evidence.get("statementOrExcerpt", "").replace("\n", " ")
                reason = evidence.get("reason", "")
                lines.append(f"- `{file_name}`: `{excerpt}` - {reason}")
            notes = finding.get("verificationNotes") or []
            if notes:
                lines.append("")
                lines.append("Verification notes:")
                for note in notes:
                    lines.append(f"- {note}")
            lines.append("")

    limitations = report.get("limitations") or []
    lines.extend(["## Limitations", ""])
    if limitations:
        for limitation in limitations:
            lines.append(f"- {limitation}")
    else:
        lines.append("- None reported.")
    lines.append("")

    verification = report.get("verification") or {}
    lines.extend(["## Verification", ""])
    lines.append(f"- Accepted findings: {len(verification.get('acceptedFindings', []))}")
    lines.append(f"- Changed findings: {len(verification.get('changedFindings', []))}")
    lines.append(f"- Rejected findings: {len(verification.get('rejectedFindings', []))}")
    schema_issues = verification.get("schemaIssues") or []
    if schema_issues:
        lines.append(f"- Schema issues: {len(schema_issues)}")
        for issue in schema_issues:
            lines.append(f"- Schema issue: {issue}")
    for changed in verification.get("changedFindings", []):
        issues = "; ".join(changed.get("issues", []))
        lines.append(f"- `{changed.get('id')}` changed: {issues}")
    for rejected in verification.get("rejectedFindings", []):
        lines.append(f"- `{rejected.get('id')}` rejected: {rejected.get('reason')}")
    lines.append("")
    lines.append("> This report is advisory. It does not authorize automatic migration edits or execution.")
    lines.append("")
    return "\n".join(lines)
