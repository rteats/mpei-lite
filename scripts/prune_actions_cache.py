#!/usr/bin/env python3
"""Remove obsolete GitHub Actions caches while preserving useful Gradle entries."""
import argparse
import json
import os
import re
import sys
import time
from collections import defaultdict
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

GIB = 1024**3
MAIN = "refs/heads/main"
# setup-gradle's per-job cache keys end with a 40-character commit SHA.
SNAPSHOT = re.compile(r"^.+-gradle\|[^|]+\|(.+)-[0-9a-f]{40}$")


def date(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")) if value else datetime.min.replace(tzinfo=timezone.utc)


def used(cache):
    return date(cache.get("last_accessed_at") or cache.get("created_at"))


def size(cache):
    return max(0, int(cache.get("size_in_bytes") or 0))


def prune_plan(caches, now, budget):
    """Retain 2 main dev snapshots, recently used shared artifacts, and hot caches."""
    remove = {}
    older = lambda days: now - timedelta(days=days)
    groups = defaultdict(list)
    for c in caches:
        match = SNAPSHOT.match(c.get("key", ""))
        if match:
            prefix = c["key"].rsplit("-", 1)[0]
            groups[(c.get("ref"), prefix, match.group(1))].append(c)

    for (ref, prefix, job), entries in groups.items():
        entries.sort(key=lambda c: date(c.get("created_at")), reverse=True)
        keep = 2 if ref == MAIN and job == "build-dev" else 0
        for c in entries[keep:]:
            if date(c.get("created_at")) < older(1):
                remove[c["id"]] = "superseded Gradle job snapshot"

    for c in caches:
        if c["id"] in remove or SNAPSHOT.match(c.get("key", "")):
            continue
        key, ref = c.get("key", ""), c.get("ref")
        if ref != MAIN and used(c) < older(7):
            remove[c["id"]] = "inactive non-main cache"
        elif ref == MAIN and (key.startswith("setup-java-") or key.startswith("gradle-home-")) and used(c) < older(7):
            remove[c["id"]] = "obsolete cache format"
        elif used(c) < older(14):
            remove[c["id"]] = "unused for 14 days"

    remaining = sum(size(c) for c in caches if c["id"] not in remove)
    if remaining > budget:
        cold = []
        for c in caches:
            if c["id"] in remove or used(c) >= older(3):
                continue
            match = SNAPSHOT.match(c.get("key", ""))
            if match and c.get("ref") == MAIN and match.group(1) == "build-dev":
                continue
            cold.append(c)
        cold.sort(key=lambda c: (used(c), -size(c)))
        for c in cold:
            if remaining <= budget:
                break
            remove[c["id"]] = "cold cache above storage budget"
            remaining -= size(c)
    return remove


class API:
    def __init__(self):
        self.root = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
        self.repo = os.environ["GITHUB_REPOSITORY"]
        self.token = os.environ["GH_TOKEN"]

    def call(self, method, endpoint, params=None):
        url = f"{self.root}/repos/{self.repo}/{endpoint}"
        if params:
            url += "?" + urlencode(params)
        req = Request(url, method=method, headers={
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "mpei-neo-cache-pruner",
        })
        for attempt in range(3):
            try:
                with urlopen(req, timeout=20) as resp:
                    data = resp.read()
                    return json.loads(data) if data else None
            except HTTPError as exc:
                if exc.code in (429, 502, 503, 504) and attempt < 2:
                    time.sleep(2 * (attempt + 1))
                    continue
                raise RuntimeError(f"GitHub API {method} {endpoint}: HTTP {exc.code}") from exc
            except URLError as exc:
                if attempt < 2:
                    time.sleep(2 * (attempt + 1))
                    continue
                raise RuntimeError(f"GitHub API {method} {endpoint}: {exc.reason}") from exc

    def caches(self):
        result, page = [], 1
        while True:
            batch = self.call("GET", "actions/caches", {"per_page": 100, "page": page})["actions_caches"]
            result.extend(batch)
            if len(batch) < 100:
                return result
            page += 1


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--apply", action="store_true", help="Delete planned entries; otherwise dry-run")
    p.add_argument("--budget-gib", type=float, default=6.0)
    args = p.parse_args()
    api = API()
    entries = api.caches()
    plan = prune_plan(entries, datetime.now(timezone.utc), int(args.budget_gib * GIB))
    candidates = sorted((c for c in entries if c["id"] in plan), key=used)
    total = sum(size(c) for c in entries)
    saved = sum(size(c) for c in candidates)
    print(f"Cache inventory: {len(entries)} entries, {total / GIB:.2f} GiB")
    print(f"Planned cleanup: {len(candidates)} entries, {saved / GIB:.2f} GiB")
    if not args.apply:
        for c in candidates[:30]:
            print(f"{plan[c['id']]}: {c.get('key')}")
        return 0
    deleted, freed, failed = 0, 0, 0
    for c in candidates:
        try:
            api.call("DELETE", f"actions/caches/{c['id']}")
            deleted += 1
            freed += size(c)
        except RuntimeError as exc:
            failed += 1
            print(f"::warning::{exc}", file=sys.stderr)
        time.sleep(0.12)
    print(f"Deleted {deleted} entries ({freed / GIB:.2f} GiB); failed: {failed}")
    with open(os.environ.get("GITHUB_STEP_SUMMARY", os.devnull), "a", encoding="utf-8") as f:
        f.write(f"### Cache cleanup\nBefore: {total / GIB:.2f} GiB ({len(entries)} entries). "
                f"Deleted: {freed / GIB:.2f} GiB ({deleted} entries). "
                f"After: ~{(total - freed) / GIB:.2f} GiB.\n")
    return int(bool(failed))


if __name__ == "__main__":
    sys.exit(main())
