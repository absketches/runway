from __future__ import annotations

import unittest

from agent.render import render_markdown


class RenderTest(unittest.TestCase):
    def test_verification_schema_issues_are_visible(self) -> None:
        markdown = render_markdown(
            {
                "summary": "Review",
                "findings": [],
                "limitations": [],
                "verification": {
                    "acceptedFindings": [],
                    "changedFindings": [],
                    "rejectedFindings": [],
                    "schemaIssues": ["finding 1.humanReview must be a non-empty string."],
                },
            }
        )

        self.assertIn("Schema issues: 1", markdown)
        self.assertIn("finding 1.humanReview", markdown)


if __name__ == "__main__":
    unittest.main()
