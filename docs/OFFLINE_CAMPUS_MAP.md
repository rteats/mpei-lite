# MPEI Neo offline OpenStreetMap campus

The Maps destination now renders **real OSM building footprints, streets,
footpaths and green spaces** from a locally bundled 33 KB GeoJSON-like
asset. No SDK, WebView, map server, tiles, API keys, permissions or runtime
network requests.

## Data generation

OpenStreetMap data © OpenStreetMap contributors, licensed under
[ODbL 1.0](https://www.openstreetmap.org/copyright).

Source: OpenStreetMap API 0.6, geographic bbox
\`37.70125,55.75285,37.71085,55.7587\`, extracted via
\`scripts/build_campus_geometry.py\`.
The generator preserves true way vertices (simplified to 0.3–1 m tolerance
depending on the feature class) and stores them as decimetres relative to
55.7559 N, 37.70605 E. The map shows an on-screen © OpenStreetMap
attribution linked to the copyright/license page.

The manual GitHub Actions workflow \`Refresh campus geometry\` can regenerate
the dataset when OSM changes. Update \`app/src/main/assets/campus_osm.json\`
with the regenerated output, review changed features, and rebuild.

## Selection

Campus buildings are identified from names on OSM polygons (Корпус А,
Корпус Б etc.), **not** from MpeiX's approximate marker coordinates. Taps
hit actual building outlines, with a small nearby-label tolerance. Each
building opens a compact address card. The previous «Корпуса» button and
bottom sheet were replaced by an always-available **letter keyboard** at the
top of the map. It uses the locally bundled building names, adapts to screen
width, highlights the selected key, and zooms straight to its footprint.

Single taps are resolved at finger-up instead of waiting for a second
double-tap; double-tap zoom was removed to eliminate that delay. Use pinch
or the existing +/- controls to zoom. Prepared selected-building paths are
reused on redraw instead of filtering features every frame. The map's
left-edge swipe-back zone starts below the keyboard and remains available.

Diagnostics: `MAP_UI` records only building letter, selection source,
polygon/label hit and callback processing time; `PERF` continues to record
asset/bitmap preparation time. Neither records private schedule text or
student data. Buildings without a recognized MPEI name are drawn in a
neutral surface color.

Pan and pinch-zoom work entirely in Compose, using Material 3 dynamic color
roles. No schematic roads/rectangles remain.

OSM data can still be inaccurate, incomplete or outdated. Building outlines
are not navigation-grade surveying data.

## Addresses

Address labeling was informed by MpeiX
([repository](https://github.com/tonykolomeytsev/mpeiapp),
Copyright © 2021 Anton Kolomeytsev, MIT License).
Full MIT license notice for adapted address data:

Copyright (c) 2021 Anton Kolomeytsev

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
