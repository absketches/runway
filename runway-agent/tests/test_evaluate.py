from __future__ import annotations

import unittest
from pathlib import Path

import evaluation.evaluate as evaluate
from evaluation.evaluate import _clean_error, _render_results, _run, _running_results


class EvaluationRenderTest(unittest.TestCase):
    def test_failed_runs_are_not_reported_as_scored_no_results(self) -> None:
        markdown = _render_results(
            [
                {
                    "case": {"id": "case-1", "expectedClassification": "NEEDS_HUMAN_REVIEW"},
                    "baseline": {
                        "correct": False,
                        "unsupportedClaims": [],
                        "criticalMiss": True,
                        "evidenceCoverage": 0.0,
                        "runFailed": True,
                        "error": "OpenAI API call failed.",
                    },
                    "agent": {
                        "correct": False,
                        "unsupportedClaims": [],
                        "criticalMiss": True,
                        "evidenceCoverage": 0.0,
                        "runFailed": True,
                        "error": "OpenAI API call failed.",
                    },
                }
            ],
            1.23,
        )

        self.assertIn("Baseline correct: not scored", markdown)
        self.assertIn("Agent correct: not scored", markdown)
        self.assertIn("| case-1 | NEEDS_HUMAN_REVIEW | failed | failed |", markdown)
        self.assertNotIn("agent critical miss", markdown)

    def test_clean_error_prefers_openai_failure_line(self) -> None:
        stderr = "\n".join(
            [
                "Some setup line",
                "OpenAI API call failed. Check OPENAI_API_KEY. Details: Connection error.",
                "Trajectory saved at: outputs/agent-trajectory.jsonl",
            ]
        )

        self.assertEqual(
            "OpenAI API call failed. Check OPENAI_API_KEY. Details: Connection error.",
            _clean_error(stderr),
        )

    def test_clean_error_prefers_model_output_failure_line(self) -> None:
        stderr = "\n".join(
            [
                "Model response could not be parsed as review JSON. Details: missing summary",
                "Trajectory saved at: outputs/agent-trajectory.jsonl",
            ]
        )

        self.assertEqual(
            "Model response could not be parsed as review JSON. Details: missing summary",
            _clean_error(stderr),
        )

    def test_run_reports_timeout(self) -> None:
        result = _run(
            [
                "python3",
                "-c",
                "import time; print('started'); time.sleep(5)",
            ],
            Path.cwd(),
            1,
        )

        self.assertEqual(124, result["exitCode"])
        self.assertTrue(result["timedOut"])
        self.assertIn("Timed out after 1s", result["stderr"])

    def test_running_results_show_progress_before_completion(self) -> None:
        markdown = _running_results(
            [{"id": "case-1"}, {"id": "case-2"}],
            [],
            0.0,
        )

        self.assertIn("Status: running.", markdown)
        self.assertIn("Completed cases: 0/2", markdown)
        self.assertIn("updated after each completed case", markdown)

    def test_results_file_lives_under_outputs_evaluation(self) -> None:
        self.assertEqual(Path("outputs/evaluation/results.md"), evaluate.RESULTS.relative_to(evaluate.ROOT))


if __name__ == "__main__":
    unittest.main()
