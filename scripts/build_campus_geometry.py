#!/usr/bin/env python3
"""Extract real campus footprints/roads from the OpenStreetMap public API.

The resulting app/src/main/assets/campus_osm.json is bundled at build time:
the Android app never uses OSM servers or map tiles.
OSM contributors (ODbL 1.0): https://www.openstreetmap.org/copyright
"""
import json
import math
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

# MPEI Lefortovo main campus, bounding box inclusive of A-T and sports buildings.
BBOX = (37.70125, 55.75285, 37.71085, 55.7587)
CENTER = (55.7559, 37.70605)
OUT = Path("app/src/main/assets/campus_osm.json")
USER_AGENT = "MPEINeoCampusOfflineBuilder/1.0 (OpenStreetMap ODbL)"
URLS = (
    "https://api.openstreetmap.org/api/0.6/map",
    "https://www.openstreetmap.org/api/0.6/map",
)
ROAD_TYPES = {
    "primary", "secondary", "tertiary", "residential", "unclassified",
    "living_street", "service", "pedestrian", "footway", "path",
    "steps", "cycleway", "track", "platform",
}
LAND_TYPES = {"grass", "meadow", "forest", "recreation_ground", "village_green"}
LEISURE_TYPES = {"park", "garden", "pitch", "playground"}
NATURAL_TYPES = {"wood", "grassland", "scrub"}


def to_xy(lat, lon):
    x = (lon - CENTER[1]) * 62600
    y = (CENTER[0] - lat) * 111200
    return (round(x * 10), round(y * 10))


def segment_distance(point, start, end):
    px, py = point
    ax, ay = start
    bx, by = end
    dx, dy = bx - ax, by - ay
    if dx == dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / (dx*dx + dy*dy)))
    return math.hypot(px - (ax + t*dx), py - (ay + t*dy))


def simplify(points, tolerance):
    if len(points) <= 2:
        return points
    index = max(range(1, len(points)-1),
                key=lambda i: segment_distance(points[i], points[0], points[-1]),
                default=0)
    if not index or segment_distance(points[index], points[0], points[-1]) <= tolerance:
        return [points[0], points[-1]]
    return simplify(points[:index + 1], tolerance)[:-1] + simplify(points[index:], tolerance)


def fetch_osm():
    bbox = ",".join(str(v) for v in BBOX)
    last_error = None
    for host in URLS:
        try:
            url = host + "?bbox=" + bbox
            print("Fetching " + url, flush=True)
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=100) as response:
                data = response.read(15_000_001)
            if len(data) > 15_000_000:
                raise ValueError("Unexpectedly large OSM response")
            root = ET.fromstring(data)
            if root.tag != "osm":
                raise ValueError("Unexpected OSM XML")
            return root
        except Exception as exc:
            last_error = exc
            print("OSM host unavailable: " + str(exc), flush=True)
            time.sleep(2)
    raise RuntimeError("Unable to download OSM data: " + str(last_error))


def main():
    root = fetch_osm()
    nodes = {
        element.attrib["id"]: (
            float(element.attrib["lat"]), float(element.attrib["lon"]))
        for element in root.iter("node")
    }
    buildings, roads, land = [], [], []
    for way in root.iter("way"):
        tags = {t.attrib["k"]: t.attrib.get("v", "") for t in way.iter("tag")}
        coords = [nodes[nd.attrib["ref"]] for nd in way.iter("nd")
                  if nd.attrib["ref"] in nodes]
        if len(coords) < 2:
            continue
        p = [to_xy(*point) for point in coords]
        p = [pt for i, pt in enumerate(p) if i == 0 or pt != p[i - 1]]
        if len(p) < 2:
            continue
        building = "building" in tags and tags["building"] != "no"
        is_land = tags.get("landuse") in LAND_TYPES or (
            tags.get("leisure") in LEISURE_TYPES) or (
            tags.get("natural") in NATURAL_TYPES)
        road = tags.get("highway") in ROAD_TYPES
        closed = p[0] == p[-1]
        if building and closed and len(p) >= 4:
            pts = simplify(p[:-1] + [p[0]], 3)
            if len(pts) < 4:
                pts = p
            buildings.append({
                "id": int(way.attrib["id"]),
                "name": tags.get("name", ""),
                "ref": tags.get("ref", ""),
                "p": pts[:-1] if pts[0] == pts[-1] else pts,
            })
        elif is_land and closed and len(p) >= 4:
            pts = simplify(p, 10)
            land.append({
                "kind": tags.get("landuse") or tags.get("leisure") or tags.get("natural"),
                "p": pts[:-1] if pts[0] == pts[-1] else pts,
            })
        elif road:
            for segment in [simplify(p, 10)]:
                roads.append({
                    "kind": tags["highway"],
                    "name": tags.get("name", ""),
                    "p": segment,
                })
    print(f"OSM nodes={len(nodes)} buildings={len(buildings)} roads={len(roads)} land={len(land)}", flush=True)
    for b in buildings:
        if b["name"] and ("корпус" in b["name"].lower() or "мэи" in b["name"].lower()):
            print(f"OSM named building {b['id']} {b['name']}", flush=True)
    if len(buildings) < 45 or len(roads) < 25:
        raise RuntimeError("OSM extraction looks incomplete; refusing empty/undersized data")
    data = {
        "attribution": "© OpenStreetMap contributors · ODbL 1.0",
        "origin": [CENTER[0], CENTER[1]],
        "bbox": list(BBOX),
        "buildings": buildings,
        "roads": roads,
        "land": land,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"Wrote {OUT} size={OUT.stat().st_size} bytes", flush=True)


if __name__ == "__main__":
    main()
