# Offline MPEI campus map

The fifth tab has **no network calls** and **does not use MapKit**, Yandex,
WebView, maps API keys, native mapping libraries, or any location permission.

The campus is drawn with Compose Canvas, using MaterialTheme.colorScheme
for every fill, road and building label. Drag and pinch to zoom, tap a building
to see its official street address, or use the "Корпуса" list. Reset zoom
using the floating reset icon. The map is a **schematic**, not a navigational
map: building rectangles, grass and pedestrian paths are deliberately
simplified illustrations, not actual surveyed polygons. It must not be used
for turn-by-turn directions or precise routing.

## Source and attribution

Individual marker coordinates (A, Б, В, Г, Д, Е, Ж, З, И, К, Л, М, Н, Р,
С, Т, Ф, Х, Э) were adapted from the MpeiX open-source
\`statics/map/markers.json\` dataset:
https://github.com/tonykolomeytsev/mpeiapp
(Copyright © 2021 Anton Kolomeytsev; MIT License).
Street addresses were checked against MPEI's directory:
https://mpei.ru/AboutUniverse/Contact/Pages/Sheme.aspx

The campus geometry is local and drawn in Compose. **No OSM building
polygons have been imported yet**. Replace the simplified footprint
geometry when source-verified open building polygons are available,
rather than presenting approximate footprints as architectural plans.

### MpeiX MIT license notice

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
