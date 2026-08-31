from __future__ import annotations

import unittest

from agent.review import ModelOutputError, _parse_json, _response_text, _select_files
from agent.schema import validate_review_shape


class ReviewTest(unittest.TestCase):
    def test_select_files_ignores_active_rows_without_merge_targets(self) -> None:
        migrations = ["V1__active.sql", "V2__candidate.sql", "V3__replacement.sql"]
        impact = {
            "files": [
                {"file": "V1__active.sql", "status": "active", "mergeWith": []},
                {
                    "file": "V2__candidate.sql",
                    "status": "merge candidate",
                    "mergeWith": ["V3__replacement.sql: views.example"],
                },
                {"file": "V3__replacement.sql", "status": "active", "mergeWith": []},
            ]
        }
        self.assertEqual(["V2__candidate.sql", "V3__replacement.sql"], _select_files(migrations, impact))

    def test_select_files_falls_back_to_all_migrations(self) -> None:
        migrations = ["V1__active.sql", "V2__active.sql"]
        impact = {"files": [{"file": name, "status": "active", "mergeWith": []} for name in migrations]}
        self.assertEqual(migrations, _select_files(migrations, impact))

    def test_parse_json_rejects_non_json_text(self) -> None:
        with self.assertRaises(ModelOutputError):
            _parse_json("not json")

    def test_parse_json_selects_review_object_from_multiple_chunks(self) -> None:
        parsed = _parse_json('{}\n{"summary":"Review","findings":[],"limitations":[]}')
        self.assertEqual("Review", parsed["summary"])

    def test_schema_validation_reports_missing_required_fields(self) -> None:
        issues = validate_review_shape({"summary": "x", "findings": [{}], "limitations": []})
        self.assertTrue(any("finding 1.id" in issue for issue in issues))

    def test_schema_validation_rejects_extra_fields(self) -> None:
        issues = validate_review_shape(
            {
                "summary": "Review",
                "findings": [],
                "limitations": [],
                "extra": "not part of the model contract",
            }
        )
        self.assertTrue(any("unsupported fields" in issue for issue in issues))

    def test_response_text_reads_output_content_when_output_text_is_empty(self) -> None:
        class Content:
            text = '{"summary":"Review","findings":[],"limitations":[]}'

        class Item:
            content = [Content()]

        class Response:
            output_text = ""
            output = [Item()]

        self.assertEqual('{"summary":"Review","findings":[],"limitations":[]}', _response_text(Response()))

    def test_response_text_reads_model_dump_content(self) -> None:
        class Response:
            output_text = ""
            output = []

            def model_dump(self) -> dict[str, object]:
                return {
                    "output": [
                        {
                            "type": "message",
                            "content": [
                                {
                                    "type": "output_text",
                                    "text": '{"summary":"Review","findings":[],"limitations":[]}',
                                }
                            ],
                        }
                    ]
                }

        self.assertEqual('{"summary":"Review","findings":[],"limitations":[]}', _response_text(Response()))


if __name__ == "__main__":
    unittest.main()
