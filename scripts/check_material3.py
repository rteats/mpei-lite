#!/usr/bin/env python3
"""Incremental Material 3 architecture guards. Not a full design specification validator."""
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
REGISTRY = Path("docs/material3/rules.json")
PRODUCTION = Path("app/src/main/java")
AREAS = {"components", "navigation", "color", "typography", "shape",
         "motion", "accessibility", "adaptive", "interaction", "layout"}
KINDS = {"mandatory", "recommendation", "product"}
METHODS = {"static", "instrumentation", "visual", "review"}
IMPORT = re.compile(r"^\s*import\s+androidx\.compose\.material3\.([A-Za-z]\w*)\b", re.M)
MATERIAL2 = re.compile(r"^\s*import\s+(?:androidx\.compose\.material\.(?!icons(?:\.|\b))|com\.google\.android\.material\.)", re.M)


def audit(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    policy_file = root / REGISTRY
    try:
        policy = json.loads(policy_file.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        return [f"{REGISTRY}: cannot read policy: {exc}"]

    if policy.get("schema_version") != 1:
        errors.append("Expected rules.json schema_version=1")
    if set(policy.get("areas", [])) != AREAS:
        errors.append("Registry must list every Material design review area")
    owners = policy.get("component_import_owners")
    if not isinstance(owners, dict) or not owners:
        errors.append("Missing component_import_owners")
        owners = {}
    for name, paths in owners.items():
        if not re.fullmatch(r"[A-Z]\w*", name) or not isinstance(paths, list) or not paths:
            errors.append(f"Invalid component owner specification: {name}")
            continue
        for path in paths:
            if not isinstance(path, str) or not path.startswith(PRODUCTION.as_posix() + "/"):
                errors.append(f"Invalid production owner path for {name}: {path}")
            elif not (root / path).is_file():
                errors.append(f"Owner missing for {name}: {path}")

    seen: set[str] = set()
    coverage: set[str] = set()
    for rule in policy.get("rules", []):
        if not isinstance(rule, dict):
            errors.append("Invalid rule record")
            continue
        rid = rule.get("id")
        if not isinstance(rid, str) or not re.fullmatch(r"(M3|MPEI)-[A-Z]+-\d{3}", rid):
            errors.append(f"Invalid rule ID: {rid}")
        elif rid in seen:
            errors.append(f"Duplicate rule ID: {rid}")
        else:
            seen.add(rid)
        area = rule.get("area")
        if area not in AREAS:
            errors.append(f"{rid}: unrecognized area {area}")
        else:
            coverage.add(area)
        if rule.get("kind") not in KINDS or rule.get("enforcement") not in METHODS:
            errors.append(f"{rid}: invalid kind/enforcement")
        if not isinstance(rule.get("statement"), str) or not rule["statement"].strip():
            errors.append(f"{rid}: empty statement")
        src = rule.get("source", "")
        if not isinstance(src, str) or not (
            src.startswith("https://m3.material.io/") or
            src.startswith("https://developer.android.com/")
        ):
            errors.append(f"{rid}: source must be an official Google design/Android URL")
        if rule.get("status") not in {"implementation-review-required", "verified", "known-gap"}:
            errors.append(f"{rid}: invalid verification status")
    if coverage != AREAS:
        errors.append(f"Rules do not cover areas: {sorted(AREAS - coverage)}")

    for file in sorted((root / PRODUCTION).rglob("*.kt")):
        name = file.relative_to(root).as_posix()
        source = file.read_text(encoding="utf-8")
        if MATERIAL2.search(source):
            errors.append(f"{name}: Material 2 UI import prohibited (use Compose Material 3)")
        for component in IMPORT.findall(source):
            if component in owners and name not in owners[component]:
                errors.append(f"{name}: {component} belongs to {', '.join(owners[component])}")
    return errors


if __name__ == "__main__":
    violations = audit()
    if violations:
        print("Material 3 policy violations:")
        for violation in violations:
            print("  - " + violation)
        sys.exit(1)
    print("Material 3 registry and sensitive component import boundaries verified.")
