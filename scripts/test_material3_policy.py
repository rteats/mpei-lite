"""Tests of policy enforcement including intentional violations."""
import json
from pathlib import Path
import tempfile
import unittest
from check_material3 import ROOT, audit


class MaterialPolicyTest(unittest.TestCase):
    def fixture(self, source: str) -> tuple[tempfile.TemporaryDirectory, Path]:
        temp = tempfile.TemporaryDirectory()
        root = Path(temp.name)
        src = root / "app/src/main/java/com/rteats/mpeineo/ui"
        src.mkdir(parents=True)
        (src / "MpeiNeoApp.kt").write_text(source, encoding="utf-8")
        (src / "MailScreen.kt").write_text("", encoding="utf-8")
        (src / "PrimaryIconServiceTabs.kt").write_text("", encoding="utf-8")
        reg = root / "docs/material3/rules.json"
        reg.parent.mkdir(parents=True)
        reg.write_text((ROOT / "docs/material3/rules.json").read_text(encoding="utf-8"),
                       encoding="utf-8")
        return temp, root

    def test_valid_registered_owner_passes(self):
        temp, root = self.fixture("import androidx.compose.material3.ShortNavigationBar\n")
        try:
            self.assertEqual([], audit(root))
        finally:
            temp.cleanup()

    def test_material2_import_is_forbidden(self):
        temp, root = self.fixture("import androidx.compose.material.Button\n")
        try:
            self.assertTrue(any("Material 2" in issue for issue in audit(root)))
        finally:
            temp.cleanup()

    def test_sensitive_component_not_allowed_in_new_screen(self):
        temp, root = self.fixture("")
        try:
            new = root / "app/src/main/java/com/rteats/mpeineo/ui/Another.kt"
            new.write_text("import androidx.compose.material3.ShortNavigationBar\n",
                           encoding="utf-8")
            self.assertTrue(any("Another.kt" in issue for issue in audit(root)))
        finally:
            temp.cleanup()

    def test_registry_missing_guidelines_is_rejected(self):
        temp, root = self.fixture("")
        try:
            path = root / "docs/material3/rules.json"
            policy = json.loads(path.read_text(encoding="utf-8"))
            policy["rules"] = []
            path.write_text(json.dumps(policy), encoding="utf-8")
            self.assertTrue(any("cover areas" in issue for issue in audit(root)))
        finally:
            temp.cleanup()


if __name__ == "__main__":
    unittest.main()
