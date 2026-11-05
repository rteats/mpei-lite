"""Regression tests for the scope of the screen audit inventory."""
import json
from pathlib import Path
import tempfile
import unittest
from check_material3_screen_inventory import ROOT, audit


class ScreenInventoryTest(unittest.TestCase):
    def fixture(self):
        temporary = tempfile.TemporaryDirectory()
        base = Path(temporary.name)
        manifest = json.loads((ROOT / "docs/material3/screen-audit.json").read_text(encoding="utf-8"))
        path = base / "docs/material3/screen-audit.json"
        path.parent.mkdir(parents=True)
        path.write_text(json.dumps(manifest), encoding="utf-8")
        for entry in manifest["screens"]:
            file = base / entry["path"]
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text("", encoding="utf-8")
        return temporary, base

    def test_fully_listed_screens_pass(self):
        tmp, root = self.fixture()
        try:
            self.assertEqual(audit(root), [])
        finally:
            tmp.cleanup()

    def test_new_screen_must_be_included(self):
        tmp, root = self.fixture()
        try:
            (root / "app/src/main/java/com/rteats/mpeineo/ui/NewScreen.kt").write_text("", encoding="utf-8")
            self.assertTrue(any("Untracked UI screen" in issue for issue in audit(root)))
        finally:
            tmp.cleanup()

    def test_reviewed_without_evidence_fails(self):
        tmp, root = self.fixture()
        try:
            path = root / "docs/material3/screen-audit.json"
            manifest = json.loads(path.read_text(encoding="utf-8"))
            manifest["screens"][0]["status"] = "reviewed"
            manifest["screens"][0]["review_evidence"] = []
            path.write_text(json.dumps(manifest), encoding="utf-8")
            self.assertTrue(any("supporting evidence" in issue for issue in audit(root)))
        finally:
            tmp.cleanup()


if __name__ == "__main__":
    unittest.main()
