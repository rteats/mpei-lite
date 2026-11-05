#!/usr/bin/env python3
"""Prefetch validated Overpass JSON into compressed assets for an offline map."""
import argparse
import gzip
import hashlib
import json
import io
import zipfile
from pathlib import Path
import re
import urllib.error
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



POI_KEYS = (
    "amenity", "shop", "tourism", "office", "craft", "healthcare",
    "historic", "public_transport", "railway", "leisure", "man_made",
)
MAP_KEYS = POI_KEYS + ("building", "highway", "landuse", "natural")


def fetch_osm_map():
    """Use the read-only OSM API for a bounded extract when Overpass is overloaded.

    OSM /map.json returns ways referencing node IDs; convert those nodes to
    Overpass-style way.geometry so Android's existing parser needs no changes.
    The requested area is small. Subdivide only if the API's 50k-node limit
    is hit; never retry an HTTP 429/509 rate limit.
    """
    src = SOURCE.read_text(encoding="utf-8")
    values = []
    for key in ("WEST", "SOUTH", "EAST", "NORTH"):
        match = re.search(r"internal const val " + key + r" = ([0-9.]+)", src)
        if match is None:
            raise ValueError("Missing OSM bbox coordinate " + key)
        values.append(float(match.group(1)))
    west, south, east, north = values
    elements = {}

    def tile(left, bottom, right, top, depth=0):
        coords = ",".join(str(x) for x in (left, bottom, right, top))
        url = "https://api.openstreetmap.org/api/0.6/map.json?" + urllib.parse.urlencode({"bbox": coords})
        request = urllib.request.Request(url, headers={
            "User-Agent": "MPEILite-Build/1.0 (https://github.com/rteats/mpei-lite)",
            "Accept": "application/json",
        })
        try:
            with urllib.request.urlopen(request, timeout=50) as response:
                raw = response.read(MAX_BYTES + 1)
            if len(raw) > MAX_BYTES:
                raise ValueError("OSM tile response exceeds 32 MB")
            data = json.loads(raw)
            if not isinstance(data.get("elements"), list):
                raise ValueError("OSM tile lacks elements")
        except Exception as error:
            oversized = (isinstance(error, urllib.error.HTTPError) and error.code == 400) or (
                isinstance(error, ValueError) and "exceeds 32 MB" in str(error)
            )
            if not oversized or depth >= 3:
                raise
            mid_lon = (left + right) / 2
            mid_lat = (bottom + top) / 2
            for sub in ((left, bottom, mid_lon, mid_lat),
                        (mid_lon, bottom, right, mid_lat),
                        (left, mid_lat, mid_lon, top),
                        (mid_lon, mid_lat, right, top)):
                tile(*sub, depth=depth + 1)
            return
        for item in data["elements"]:
            kind = item.get("type")
            identifier = item.get("id")
            if kind in ("node", "way", "relation") and identifier is not None:
                elements[(kind, identifier)] = item
        print("OSM API tile", coords, "elements", len(data["elements"]))

    tile(west, south, east, north)
    nodes = {ident: e for (kind, ident), e in elements.items() if kind == "node"}
    records = []
    for (kind, ident), element in elements.items():
        tags = element.get("tags") or {}
        if not any(k in tags for k in MAP_KEYS):
            continue
        if kind == "node":
            lat, lon = element.get("lat"), element.get("lon")
            if lat is not None and lon is not None and south <= lat <= north and west <= lon <= east:
                records.append({
                    "type": "node", "id": ident, "lat": lat, "lon": lon, "tags": tags,
                })
        elif kind == "way":
            geometry = []
            for ref in element.get("nodes", []):
                node = nodes.get(ref)
                if node is not None and "lat" in node and "lon" in node:
                    geometry.append({"lat": node["lat"], "lon": node["lon"]})
            if len(geometry) >= 2:
                records.append({
                    "type": "way", "id": ident, "tags": tags, "geometry": geometry,
                })
        elif kind == "relation" and any(k in tags for k in POI_KEYS):
            locations = []
            for member in element.get("members", []):
                if member.get("type") == "node":
                    node = nodes.get(member.get("ref"))
                    if node and "lat" in node and "lon" in node:
                        locations.append(node)
                elif member.get("type") == "way":
                    way = elements.get(("way", member.get("ref"))) or {}
                    for ref in way.get("nodes", [])[:40]:
                        node = nodes.get(ref)
                        if node and "lat" in node and "lon" in node:
                            locations.append(node)
            if locations:
                lat = sum(n["lat"] for n in locations) / len(locations)
                lon = sum(n["lon"] for n in locations) / len(locations)
                if south <= lat <= north and west <= lon <= east:
                    records.append({
                        "type": "relation", "id": ident, "tags": tags,
                        "center": {"lat": lat, "lon": lon},
                    })
    result = {"elements": records}
    counts = validate(result)
    print("OSM map validated: buildings=%d roads=%d pois=%d" % counts)
    return json.dumps(result, separators=(",", ":")).encode("utf-8")



def fetch_previous_release():
    """Reuse a validated asset from our last APK instead of requerying public OSM servers.

    The first release containing an extract will populate the next release's
    local source. A missing or old release safely falls back to bounded export.
    """
    url = "https://github.com/rteats/mpei-lite/releases/download/latest/MPEI-Lite.apk"
    request = urllib.request.Request(url, headers={
        "User-Agent": "MPEILite-Build/1.0 (https://github.com/rteats/mpei-lite)",
    })
    try:
        with urllib.request.urlopen(request, timeout=50) as response:
            data = response.read(100_000_001)
        if len(data) > 100_000_000:
            raise ValueError("Previous APK exceeds 100 MB")
        with zipfile.ZipFile(io.BytesIO(data)) as apk:
            compressed = apk.read("assets/lefortovo_overpass.osmdata")
        raw = gzip.decompress(compressed)
        if len(raw) > MAX_BYTES:
            raise ValueError("Previous extracted OSM payload exceeds 32 MB")
        print("Using verified OSM snapshot from last Dev APK:", validate(json.loads(raw)))
        return raw
    except Exception as error:
        print("Previous release has no reusable map:", type(error).__name__, error)
        return None

def fetch(query):
    previous = fetch_previous_release()
    if previous is not None:
        return previous
    try:
        return fetch_osm_map()
    except Exception as error:
        print("OSM API export unavailable, trying Overpass mirrors:", error)
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
