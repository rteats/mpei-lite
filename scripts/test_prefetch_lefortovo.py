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

    def test_live_release_prefetch_preflight(self):
        # Temporary validation: removed after observing the network build prerequisite.
        from prefetch_lefortovo import SOURCE, build_query, fetch
        raw = fetch(build_query(SOURCE.read_text(encoding="utf-8")))
        self.assertGreater(len(raw), 10_000)

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
