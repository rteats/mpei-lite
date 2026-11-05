import tempfile
from pathlib import Path
import unittest

from check_android_test_report import check


class AndroidTestReportTest(unittest.TestCase):
    def test_zero_tests_is_failure(self):
        with tempfile.TemporaryDirectory() as temp:
            report = Path(temp) / "index.html"
            report.write_text('<div class="infoBox" id="tests"><div class="counter">0</div>')
            with self.assertRaisesRegex(ValueError, "No instrumented tests"):
                check(report)

    def test_nonzero_tests_is_success(self):
        with tempfile.TemporaryDirectory() as temp:
            report = Path(temp) / "index.html"
            report.write_text('<div class="infoBox" id="tests"><div class="counter">3</div>')
            self.assertEqual(3, check(report))
