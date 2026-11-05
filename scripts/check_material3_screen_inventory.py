#!/usr/bin/env python3
"""Fail when new screen entrypoints are not tracked by the Material review inventory."""
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
UI = Path("app/src/main/java/com/rteats/mpeineo/ui")
INVENTORY = Path("docs/material3/screen-audit.json")
SHELL = (UI / "MpeiNeoApp.kt").as_posix()
ALLOWED_STATES = {"not-reviewed", "pending-fix", "reviewed"}


def audit(root: Path = ROOT) -> list[str]:
    issues = []
    try:
        policy = json.loads((root / INVENTORY).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        return [f"{INVENTORY}: {exc}"]
    if policy.get("schema_version") != 1:
        issues.append("Invalid screen audit schema")
    entries = policy.get("screens", [])
    if not isinstance(entries, list):
        return issues + ["screens must be an array"]
    actual = {
        p.relative_to(root).as_posix() for p in (root / UI).rglob("*Screen.kt")
    }
    actual.add(SHELL)
    registered: set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict):
            issues.append("Invalid screen audit entry")
            continue
        path = entry.get("path")
        if not isinstance(path, str) or path in registered:
            issues.append(f"Duplicate or invalid screen entry: {path}")
            continue
        registered.add(path)
        if path not in actual:
            issues.append(f"Screen no longer exists or is out of inventory scope: {path}")
        if entry.get("status") not in ALLOWED_STATES:
            issues.append(f"{path}: unknown review status")
        prs = entry.get("open_prs")
        evidence = entry.get("review_evidence")
        if not isinstance(prs, list) or not all(isinstance(n, int) and n > 0 for n in prs):
            issues.append(f"{path}: open_prs must contain positive PR numbers")
        if not isinstance(evidence, list) or not all(isinstance(e, str) for e in evidence):
            issues.append(f"{path}: review_evidence must be a list of links")
        if entry.get("status") == "pending-fix" and not prs:
            issues.append(f"{path}: pending-fix must reference at least one PR")
        if entry.get("status") == "reviewed" and not evidence:
            issues.append(f"{path}: reviewed must contain supporting evidence")
    for missing in sorted(actual - registered):
        issues.append(f"Untracked UI screen (review entry required): {missing}")
    return issues


if __name__ == "__main__":
    violations = audit()
    if violations:
        print("Material 3 screen coverage failures:")
        for violation in violations:
            print("  - " + violation)
        sys.exit(1)
    print("Material 3 screen inventory covers all named UI screens and the shell.")
