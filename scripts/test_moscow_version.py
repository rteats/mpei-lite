"""Moscow date-boundary and daily release-number tests."""
import unittest
from datetime import datetime, timezone
from moscow_version import next_version


class MoscowVersionTest(unittest.TestCase):
    def test_first_release(self):
        now = datetime(2026, 10, 30, 21, 15, tzinfo=timezone.utc)
        self.assertEqual("2026.10.31.1", next_version("", now=now))

    def test_increment_same_moscow_day(self):
        now = datetime(2026, 10, 31, 19, 59, tzinfo=timezone.utc)
        self.assertEqual(
            "2026.10.31.4", next_version("MPEI Lite 2026.10.31.3", now=now)
        )

    def test_reset_at_moscow_midnight(self):
        now = datetime(2026, 10, 31, 21, 1, tzinfo=timezone.utc)
        self.assertEqual(
            "2026.11.01.1", next_version("MPEI Lite 2026.10.31.7", now=now)
        )

    def test_legacy_counter_not_carried_forward(self):
        now = datetime(2026, 10, 31, 8, 0, tzinfo=timezone.utc)
        self.assertEqual(
            "2026.10.31.1",
            next_version("MPEI Lite 2026.10.31.1000004", now=now),
        )


if __name__ == "__main__":
    unittest.main()
