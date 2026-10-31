"""Derive the next MPEI Lite release name using Moscow local date."""
from __future__ import annotations

import re
import sys
from datetime import datetime
from zoneinfo import ZoneInfo

MOSCOW = ZoneInfo("Europe/Moscow")
_RELEASE = re.compile(r"^MPEI Lite (\d{4}\.\d{2}\.\d{2})\.(\d+)$")


def next_version(previous: str, *, now: datetime | None = None) -> str:
    date = (now or datetime.now(MOSCOW)).astimezone(MOSCOW).strftime("%Y.%m.%d")
    match = _RELEASE.fullmatch(previous.strip())
    # Legacy names used the Android versionCode as the last field.
    # Never interpret such a value as a daily counter.
    count = int(match.group(2)) if match and match.group(1) == date else 0
    if count >= 1_000_000:
        count = 0
    return f"{date}.{count + 1}"


if __name__ == "__main__":
    print(next_version(sys.argv[1] if len(sys.argv) > 1 else ""))
