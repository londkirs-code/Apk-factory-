#!/usr/bin/env python3
"""Regression tests for synchronized versionCode updates across decoded APK splits."""
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
HELPER = ROOT / "tools/reverse-engineering/set-apk-version.py"

class SetApkVersionTests(unittest.TestCase):
    def make_project(self, root, version="41"):
        root.mkdir()
        (root / "apktool.yml").write_text(
            "versionInfo:\n  versionCode: " + version + "\n  versionName: '1.0'\n"
            "sdkInfo:\n  minSdkVersion: '23'\n", encoding="utf-8")
        (root / "AndroidManifest.xml").write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
            'package="org.example.app" android:versionCode="' + version + '">'
            '<application /></manifest>', encoding="utf-8")
        return root

    def test_updates_yaml_and_manifest_to_same_code(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = self.make_project(Path(tmp) / "base")
            subprocess.run(["python3", str(HELPER), str(project), "42"], check=True)
            self.assertIn("versionCode: 42", (project / "apktool.yml").read_text())
            self.assertIn('android:versionCode="42"', (project / "AndroidManifest.xml").read_text())

    def test_rejects_missing_version_info(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = Path(tmp) / "bad"
            project.mkdir()
            (project / "apktool.yml").write_text("sdkInfo: {}\n")
            (project / "AndroidManifest.xml").write_text('<manifest package="x"/>')
            result = subprocess.run(["python3", str(HELPER), str(project), "42"], capture_output=True)
            self.assertNotEqual(result.returncode, 0)

    def test_rejects_android_maximum_and_above(self):
        with tempfile.TemporaryDirectory() as tmp:
            project = self.make_project(Path(tmp) / "bad")
            result = subprocess.run(["python3", str(HELPER), str(project), "2100000000"], capture_output=True)
            self.assertNotEqual(result.returncode, 0)

if __name__ == "__main__":
    unittest.main(verbosity=2)
