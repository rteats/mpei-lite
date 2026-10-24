"""Safety checks for deleting GitHub Actions history; no GitHub API calls."""
import unittest
from datetime import datetime, timedelta, timezone
from maintain_actions import stale_run_candidates

NOW = datetime(2026, 10, 24, tzinfo=timezone.utc)


def run(identifier, name, days, conclusion="success", status="completed"):
    return {
        "id": identifier, "name": name, "conclusion": conclusion, "status": status,
        "created_at": (NOW - timedelta(days=days)).isoformat(),
    }


class ActionsRetentionTest(unittest.TestCase):
    def test_retired_runs_deleted_but_unrecognized_and_running_preserved(self):
        runs = [
            run(1, "Prune merged branches", 1),
            run(2, "Experimental future workflow", 400),
            run(3, "PR APK", 40, status="in_progress"),
            run(4, "Stable release", 400),
        ]
        self.assertEqual([1], [r["id"] for r in stale_run_candidates(runs, NOW)])

    def test_successful_pushed_legacy_release_is_preserved(self):
        pushed = run(5, "Android CI and APK release", 100)
        pushed["event"] = "push"
        cancelled = run(6, "Android CI and APK release", 100, conclusion="cancelled")
        self.assertEqual([6], [r["id"] for r in stale_run_candidates([pushed, cancelled], NOW)])

    def test_active_workflow_keeps_newest_ten_and_recent_history(self):
        old = [run(i, "PR verification", 30 + i) for i in range(12)]
        recent = [run(100 + i, "PR verification", 1) for i in range(10)]
        removed = stale_run_candidates(old + recent, NOW)
        self.assertEqual(set(range(12)), {r["id"] for r in removed})

    def test_recent_failed_and_cancelled_runs_are_not_removed(self):
        old = [run(i, "Dev APK", 20 + i) for i in range(12)]
        recent = [run(100 + i, "Dev APK", 1, conclusion="failure") for i in range(10)]
        self.assertEqual(set(range(12)),
                         {r["id"] for r in stale_run_candidates(old + recent, NOW)})


if __name__ == "__main__":
    unittest.main()
