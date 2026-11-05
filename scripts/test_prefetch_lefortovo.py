import unittest
from prefetch_lefortovo import build_query, validate


class OfflineMapPrefetchTests(unittest.TestCase):
    def test_expand_bbox_in_query(self):
        source = (
            "internal const val NORTH = 55.767230\n"
            "internal const val SOUTH = 55.746344\n"
            "internal const val WEST = 37.696083\n"
            "internal const val EAST = 37.720361\n"
            'private val query = """way["building"]($SOUTH,$WEST,$NORTH,$EAST);""".trimIndent()'
        )
        self.assertIn("55.746344,37.696083,55.767230,37.720361",
                      build_query(source))
        # A regex end-anchor is part of Overpass QL, not Kotlin interpolation.
        regex_source = source.replace(
            'way["building"]($SOUTH,$WEST,$NORTH,$EAST);',
            'way["building"~"^(yes|residential)$"]($SOUTH,$WEST,$NORTH,$EAST);',
        )
        self.assertIn('"^(yes|residential)$"', build_query(regex_source))

    def test_live_osm_api_preflight(self):
        # Temporary test of OSM's bounded, read-only map export endpoint.
        import json
        import urllib.request
        url = ("https://api.openstreetmap.org/api/0.6/map.json"
               "?bbox=37.704,55.755,37.705,55.756")
        req = urllib.request.Request(url, headers={
            "User-Agent": "MPEILite-Build/1.0 (https://github.com/rteats/mpei-lite)",
        })
        with urllib.request.urlopen(req, timeout=35) as response:
            sample = json.load(response)
        self.assertGreater(len(sample.get("elements", [])), 0)

    def test_empty_and_incomplete_extracts_rejected(self):
        with self.assertRaisesRegex(ValueError, "Missing"):
            validate({})
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            validate({"elements": [{
                "type": "way",
                "tags": {"building": "yes"},
                "geometry": [{"lat": 0, "lon": 0}],
            }]})


if __name__ == "__main__":
    unittest.main()
