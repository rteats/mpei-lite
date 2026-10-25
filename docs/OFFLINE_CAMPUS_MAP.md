# MPEI Lite: Lefortovo map and nearby places

The campus map uses a Monet-colored, offline-capable OpenStreetMap vector
renderer. It has no mapping SDK, tile service, API keys, or device-location
permission. It covers the rectangle from:

- North-west: **55.767230, 37.696083**
- South-east: **55.746344, 37.720361**

The **first map visit with a network connection** fetches this fixed area once
from the public Overpass API. The response includes buildings, streets,
walkways, green areas, and mapped places. It is converted to a compact,
persistent `filesDir/lefortovo_osm_v1.json` cache and then drawn offline.
The cache is refreshed after 30 days when the map is opened online. A failed
request retains the previous complete cached extract. On a clean, offline
installation, the smaller campus outline bundled in `assets/campus_osm.json`
is used until the first successful download; this fallback does **not** cover
the full Lefortovo rectangle.

The request is for the same fixed rectangle for every user. The app does not
send device location or student information. The public Overpass server may
be unavailable, rate-limit requests, or return incomplete/old map data. Data
coverage depends on OpenStreetMap volunteers. The app validates the response
before replacing its local cache, and does not retry continuously.

## Places and markers

The offline database retains POIs mapped as nodes, ways, or relations with
OpenStreetMap tags including `amenity`, `shop`, `tourism`, `office`,
`craft`, `healthcare`, `historic`, `public_transport`, selected
`railway`, `leisure`, and `man_made` features. Named buildings and
buildings marked as dormitories are also included. Cafés, restaurants,
hostels, hotels, shops, healthcare services, education, transport, offices,
parks, and other mapped objects receive category markers. Records with no
official name use a type label where possible.

Each marker uses roles from `MaterialTheme.colorScheme` (Monet). The map
uses a compact screen-space de-overlap: close markers are separated by
zooming in, not deleted from the cached dataset. Tap a visible mark to see
its name, category, and mapped street address when available. Empty tap
space clears the current selection. Category labels do not imply current
opening hours or availability.

MPEI building letters still come from named OSM footprints, with the
existing building keyboard and cross-tab classroom focus. At the overview
zoom the full requested rectangle fits on screen. Pinch or use the vertical
Material 3 floating toolbar to zoom in, and use the reset control to fit the
whole area again. Campus selection zooms in to the requested footprint.

The base geometry and colors are rasterized once per palette and prepared
scene. Markers and selected building outlines stay separate so pan, zoom,
and selection do not trigger another full base-map rasterization.

## Source and licenses

OpenStreetMap data © OpenStreetMap contributors, licensed under
[ODbL 1.0](https://www.openstreetmap.org/copyright). The map has an on-screen
OpenStreetMap attribution link.

`CampusAreaData.kt` defines the new fixed-area Overpass query and conversion.
The legacy `scripts/build_campus_geometry.py` produces the smaller bundled
fallback asset only; it does not supply the enlarged online extract.

Original building address labeling was informed by
[MpeiX](https://github.com/tonykolomeytsev/mpeiapp)
(Copyright © 2021 Anton Kolomeytsev, MIT License).
Full notice for adapted address data:

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
