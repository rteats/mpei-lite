#!/usr/bin/env python3
"""Reject Android test runs that Gradle marked successful without running tests."""
import argparse
from pathlib import Path
import re
import sys

REPORT = Path("app/build/reports/androidTests/connected/debug/index.html")
TEST_COUNT = re.compile(r'<div class="infoBox" id="tests">\s*<div class="counter">(\d+)</div>')


def check(report: Path, since: float | None = None) -> int:
    if not report.is_file() or (since is not None and report.stat().st_mtime < since):
        raise ValueError(f"Fresh Android test report missing: {report}")
    match = TEST_COUNT.search(report.read_text(encoding="utf-8"))
    if not match or int(match.group(1)) == 0:
        raise ValueError(f"No instrumented tests ran; inspect {report}")
    return int(match.group(1))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--since", type=float)
    args = parser.parse_args()
    try:
        print(f"Instrumented tests run: {check(REPORT, args.since)}")
    except ValueError as error:
        print(error, file=sys.stderr)
        sys.exit(1)
