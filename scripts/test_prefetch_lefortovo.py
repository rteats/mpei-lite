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

    def test_offline_osm_export_converts_way_coordinates(self):
        import io
        import json
        from unittest.mock import patch
        from prefetch_lefortovo import fetch_osm_map

        nodes = [
            {"type": "node", "id": 1, "lat": 55.755, "lon": 37.706},
            {"type": "node", "id": 2, "lat": 55.7551, "lon": 37.706},
            {"type": "node", "id": 3, "lat": 55.7551, "lon": 37.7061},
        ]
        buildings = [
            {"type": "way", "id": 1000 + i, "nodes": [1, 2, 3, 1],
             "tags": {"building": "yes"}} for i in range(100)
        ]
        roads = [
            {"type": "way", "id": 2000 + i, "nodes": [1, 2],
             "tags": {"highway": "residential"}} for i in range(30)
        ]
        pois = [
            {"type": "node", "id": 3000 + i, "lat": 55.755,
             "lon": 37.706, "tags": {"amenity": "cafe"}}
            for i in range(30)
        ]
        mock_payload = json.dumps({"elements": nodes + buildings + roads + pois}).encode()
        with patch("prefetch_lefortovo.urllib.request.urlopen",
                   return_value=io.BytesIO(mock_payload)):
            parsed = json.loads(fetch_osm_map())
        self.assertGreaterEqual(len(parsed["elements"]), 160)
        building = next(e for e in parsed["elements"] if e["id"] == 1000)
        self.assertEqual(len(building["geometry"]), 4)
        self.assertEqual(building["geometry"][0]["lat"], 55.755)

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
