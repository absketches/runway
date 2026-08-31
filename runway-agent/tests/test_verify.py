from __future__ import annotations

import unittest
from pathlib import Path

from agent.tools import ToolContext
from agent.verify import verify_report


class VerificationTest(unittest.TestCase):
    def test_supported_finding_is_accepted(self) -> None:
        ctx = ToolContext.create()
        report = {
            "summary": "Review",
            "findings": [
                {
                    "id": "finding-1",
                    "type": "REPLACEMENT_CANDIDATE",
                    "decision": "NEEDS_HUMAN_REVIEW",
                    "migrations": ["V90__create_user_names_legacy.sql", "V102__create_user_names.sql"],
                    "objects": ["user_names"],
                    "runwaySignal": "Runway reports that both migrations write user_names.",
                    "maintainerActions": ["Check applied environments.", "Inspect downstream dependencies."],
                    "explanation": "A later migration drops and recreates user_names.",
                    "evidence": [
                        {
                            "file": "V102__create_user_names.sql",
                            "statementOrExcerpt": "drop view if exists user_names",
                            "reason": "The later migration explicitly drops the existing view.",
                        }
                    ],
                    "confidence": "MEDIUM",
                    "risk": "Production usage is unknown from SQL history.",
                    "humanReview": "Check applied environments and application dependencies.",
                }
            ],
            "limitations": [],
        }
        result = verify_report(report, ctx, {"V90__create_user_names_legacy.sql", "V102__create_user_names.sql"}, {})
        self.assertEqual(1, len(result.accepted))
        self.assertEqual("NEEDS_HUMAN_REVIEW", result.report["findings"][0]["decision"])

    def test_unsupported_finding_is_downgraded(self) -> None:
        ctx = ToolContext.create()
        report = {
            "summary": "Review",
            "findings": [
                {
                    "id": "finding-1",
                    "type": "REPLACEMENT_CANDIDATE",
                    "decision": "SAFE_TO_CONSIDER",
                    "migrations": ["V102__create_user_names.sql"],
                    "objects": ["missing_object"],
                    "runwaySignal": "Runway data is inconclusive for this object.",
                    "maintainerActions": ["Automatically delete this migration."],
                    "explanation": "Automatically delete this migration.",
                    "evidence": [],
                    "confidence": "HIGH",
                    "risk": "None.",
                    "humanReview": "None.",
                }
            ],
            "limitations": [],
        }
        result = verify_report(
            report,
            ctx,
            {"V102__create_user_names.sql"},
            {"overview": {"statusCounts": {"Incomplete analysis": 1}}},
        )
        finding = result.report["findings"][0]
        self.assertEqual("INSUFFICIENT_EVIDENCE", finding["decision"])
        self.assertEqual("LOW", finding["confidence"])
        self.assertTrue(result.changed)

    def test_cautious_automatic_language_is_not_rejected(self) -> None:
        ctx = ToolContext.create()
        report = {
            "summary": "Review",
            "findings": [
                {
                    "id": "finding-1",
                    "type": "REPLACEMENT_CANDIDATE",
                    "decision": "NEEDS_HUMAN_REVIEW",
                    "migrations": ["V90__create_user_names_legacy.sql", "V102__create_user_names.sql"],
                    "objects": ["user_names"],
                    "runwaySignal": "Runway reports that both migrations write user_names.",
                    "maintainerActions": ["Check applied environments.", "Inspect downstream dependencies."],
                    "explanation": "A later migration drops and recreates user_names.",
                    "evidence": [
                        {
                            "file": "V102__create_user_names.sql",
                            "statementOrExcerpt": "drop view if exists user_names",
                            "reason": "The later migration explicitly drops the existing view.",
                        }
                    ],
                    "confidence": "MEDIUM",
                    "risk": "Do not automatically change migration history from this finding alone.",
                    "humanReview": "Check applied environments and application dependencies.",
                }
            ],
            "limitations": [],
        }
        result = verify_report(report, ctx, {"V90__create_user_names_legacy.sql", "V102__create_user_names.sql"}, {})
        self.assertEqual(1, len(result.accepted))

    def test_incomplete_analysis_only_affects_related_high_confidence_findings(self) -> None:
        ctx = ToolContext.create()
        report = {
            "summary": "Review",
            "findings": [
                {
                    "id": "finding-1",
                    "type": "REPLACEMENT_CANDIDATE",
                    "decision": "SAFE_TO_CONSIDER",
                    "migrations": ["V90__create_user_names_legacy.sql", "V102__create_user_names.sql"],
                    "objects": ["user_names"],
                    "runwaySignal": "Runway reports that both migrations write user_names.",
                    "maintainerActions": ["Check applied environments.", "Inspect downstream dependencies."],
                    "explanation": "A later migration drops and recreates user_names.",
                    "evidence": [
                        {
                            "file": "V102__create_user_names.sql",
                            "statementOrExcerpt": "drop view if exists user_names",
                            "reason": "The later migration explicitly drops the existing view.",
                        }
                    ],
                    "confidence": "HIGH",
                    "risk": "Production usage is unknown from SQL history.",
                    "humanReview": "Check applied environments and application dependencies.",
                }
            ],
            "limitations": [],
        }
        impact = {
            "files": [
                {"file": "V106__create_incomplete_analysis_view.sql", "status": "incomplete analysis", "objects": ["views.user_name_filter"]},
                {"file": "V90__create_user_names_legacy.sql", "status": "merge candidate", "objects": ["views.user_names"]},
            ]
        }
        result = verify_report(report, ctx, {"V90__create_user_names_legacy.sql", "V102__create_user_names.sql"}, impact)
        self.assertEqual(1, len(result.accepted))

    def test_path_escape_is_rejected(self) -> None:
        ctx = ToolContext.create()
        escaped = str(Path("..") / ".." / "README.md")
        report = {
            "summary": "Review",
            "findings": [
                {
                    "id": "finding-1",
                    "type": "OTHER",
                    "decision": "NEEDS_HUMAN_REVIEW",
                    "migrations": [escaped],
                    "objects": [],
                    "runwaySignal": "Runway signal is not available for this escaped path.",
                    "maintainerActions": ["Inspect the configured migration directory."],
                    "explanation": "Unsupported.",
                    "evidence": [{"file": escaped, "statementOrExcerpt": "Runway", "reason": "bad path"}],
                    "confidence": "MEDIUM",
                    "risk": "Unknown.",
                    "humanReview": "Inspect manually.",
                }
            ],
            "limitations": [],
        }
        result = verify_report(report, ctx, {escaped}, {})
        self.assertEqual("INSUFFICIENT_EVIDENCE", result.report["findings"][0]["decision"])

    def test_schema_issue_downgrades_finding_when_verifier_is_called_directly(self) -> None:
        ctx = ToolContext.create()
        report = {
            "summary": "Review",
            "findings": [
                {
                    "id": "finding-1",
                    "type": "REPLACEMENT_CANDIDATE",
                    "decision": "NEEDS_HUMAN_REVIEW",
                    "migrations": ["V102__create_user_names.sql"],
                    "objects": ["user_names"],
                    "runwaySignal": "Runway reports that V102 writes user_names.",
                    "maintainerActions": ["Check applied environments.", "Inspect downstream dependencies."],
                    "explanation": "A later migration drops and recreates user_names.",
                    "evidence": [
                        {
                            "file": "V102__create_user_names.sql",
                            "statementOrExcerpt": "drop view if exists user_names",
                            "reason": "The later migration explicitly drops the existing view.",
                        }
                    ],
                    "confidence": "MEDIUM",
                    "risk": "Production usage is unknown from SQL history.",
                }
            ],
            "limitations": [],
        }
        result = verify_report(report, ctx, {"V102__create_user_names.sql"}, {})
        finding = result.report["findings"][0]
        self.assertEqual("INSUFFICIENT_EVIDENCE", finding["decision"])
        self.assertTrue(any("humanReview" in note for note in finding["verificationNotes"]))


if __name__ == "__main__":
    unittest.main()
