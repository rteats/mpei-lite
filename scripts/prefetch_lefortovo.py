#!/usr/bin/env python3
"""Prefetch validated Overpass JSON into compressed assets for an offline map."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import re
import urllib.parse
import urllib.request

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/rteats/mpeineo/ui/CampusAreaData.kt"
ENDPOINTS = (
    "https://overpass-api.de/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
)
MAX_BYTES = 32_000_000


def build_query(source):
    matched = re.search(r'private val query = """(.*?)""".trimIndent\(\)', source, re.S)
    if matched is None:
        raise ValueError("Could not find Overpass query")
    query = matched.group(1)
    for key in ("SOUTH", "WEST", "NORTH", "EAST"):
        value = re.search(r"internal const val " + key + r" = ([0-9.]+)", source)
        if value is None:
            raise ValueError("Missing bounding box coordinate " + key)
        query = query.replace("$" + key, value.group(1))
    # Overpass regular expressions can include literal end anchors.
    # Reject unresolved Kotlin variables rather than every dollar sign.
    if re.search(r"\$[A-Z][A-Z0-9_]*", query):
        raise ValueError("Unexpanded query interpolation")
    return query.strip()


def validate(payload):
    elements = payload.get("elements")
    if not isinstance(elements, list):
        raise ValueError("Missing Overpass elements")
    buildings = sum(bool(e.get("geometry")) and e.get("tags", {}).get("building") not in (None, "no")
                    for e in elements if e.get("type") == "way")
    roads = sum(bool(e.get("geometry")) and bool(e.get("tags", {}).get("highway"))
                for e in elements if e.get("type") == "way")
    poi_keys = ("amenity", "shop", "tourism", "office", "craft", "healthcare",
                "historic", "public_transport", "railway", "leisure", "man_made")
    pois = sum(any(e.get("tags", {}).get(k) for k in poi_keys) for e in elements)
    if buildings < 100 or roads < 30 or pois < 30:
        raise ValueError(f"Incomplete map buildings={buildings} roads={roads} pois={pois}")
    return buildings, roads, pois


def fetch(query):
    encoded = urllib.parse.urlencode({"data": query}).encode("utf-8")
    failures = []
    for endpoint in ENDPOINTS:
        try:
            request = urllib.request.Request(
                endpoint, data=encoded, method="POST",
                headers={"User-Agent": "MPEILite-CI/1.0 (offline map)"})
            with urllib.request.urlopen(request, timeout=48) as response:
                payload = response.read(MAX_BYTES + 1)
            if len(payload) > MAX_BYTES:
                raise ValueError("Response larger than 32 MB")
            count = validate(json.loads(payload))
            print("Map accepted", endpoint, count)
            return payload
        except Exception as error:
            failures.append(f"{endpoint}: {error}")
    raise RuntimeError("Overpass unavailable or invalid: " + " | ".join(failures))


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    query = build_query(SOURCE.read_text(encoding="utf-8"))
    raw = fetch(query)
    output = gzip.compress(raw, compresslevel=9, mtime=0)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temp = args.output.with_suffix(args.output.suffix + ".tmp")
    temp.write_bytes(output)
    temp.replace(args.output)
    print("Bundled", args.output, "bytes", len(output), "sha256", hashlib.sha256(output).hexdigest())


if __name__ == "__main__":
    main()
