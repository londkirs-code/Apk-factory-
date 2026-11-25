#!/usr/bin/env python3
"""Offline tests for ui_driver.py using a fake adb device (no emulator needed)."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ui_driver as d  # noqa: E402

PKG = "com.example.demo"
HOME = f"""<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation="0">
<node class="android.widget.FrameLayout" package="{PKG}" text="" resource-id="" content-desc="" clickable="false" enabled="true" bounds="[0,0][1080,2400]">
<node class="android.widget.TextView" package="{PKG}" text="שלום עולם" resource-id="{PKG}:id/title" content-desc="" clickable="false" enabled="true" bounds="[0,100][1080,200]"/>
<node class="android.widget.Button" package="{PKG}" text="Settings" resource-id="{PKG}:id/settings" content-desc="" clickable="true" enabled="true" bounds="[100,300][500,400]"/>
<node class="android.widget.Button" package="{PKG}" text="Delete all" resource-id="{PKG}:id/del" content-desc="" clickable="true" enabled="true" bounds="[100,500][500,600]"/>
<node class="android.widget.EditText" package="{PKG}" text="" resource-id="{PKG}:id/search" content-desc="Search" clickable="true" enabled="true" bounds="[100,700][900,800]"/>
</node></hierarchy>"""
SETTINGS = HOME.replace("שלום עולם", "Settings screen").replace('text="Settings"', 'text="Back home"')
PNG = b"\x89PNG\r\n\x1a\nfake"


class FakeAdb(d.Adb):
    def __init__(self):
        super().__init__("fake", "adb")
        self.cmds = []
        self.screen = HOME
        self.crash = ""

    def run(self, *args, timeout=60, check=True, binary=False):
        self.cmds.append(args)
        if args[:2] == ("exec-out", "screencap"):
            return PNG
        if args[0] == "logcat":
            return self.crash if "crash" in args else ""
        if args[0] == "shell":
            return self.shell_out(args[1])
        return b"" if binary else ""

    def shell_out(self, cmd):
        if cmd.startswith("uiautomator dump"):
            return "UI hierchary dumped to: /sdcard/apkf-ui.xml"
        if cmd.startswith("cat /sdcard/apkf-ui.xml"):
            return self.screen
        if cmd == "wm size":
            return "Physical size: 1080x2400"
        if cmd.startswith("input tap"):
            x, y = map(int, cmd.split()[2:4])
            if 100 <= x <= 500 and 300 <= y <= 400:
                self.screen = SETTINGS if self.screen == HOME else HOME
        if cmd == "dumpsys window":
            return f"  mCurrentFocus=Window{{abc u0 {PKG}/{PKG}.MainActivity}}"
        return ""


class DriverTests(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.adb = FakeAdb()
        self.r = d.Runner(self.adb, self.tmp, PKG, "demo", "scenario")
        self.r.collect_device_info()

    def test_selectors(self):
        nodes = d.parse_nodes(HOME)
        self.assertTrue(any(d.matches(n, {"text": "שלום"}) for n in nodes))
        self.assertTrue(any(d.matches(n, {"id": "settings"}) for n in nodes))
        self.assertTrue(any(d.matches(n, {"any": "search"}) for n in nodes))
        self.assertFalse(any(d.matches(n, {"text": "Sett", "exact": True}) for n in nodes))

    def test_point_fraction_and_pixels(self):
        self.assertEqual(self.r.point(0.5, 0.25), (540, 600))
        self.assertEqual(self.r.point(10, 20), (10, 20))

    def test_scenario_tap_and_assert(self):
        steps = [
            {"action": "assert_text", "text": "שלום עולם"},
            {"action": "tap", "text": "Settings"},
            {"action": "assert_text", "text": "Settings screen"},
            {"action": "screenshot", "name": "settings"},
            {"action": "assert_text", "text": "nope", "timeout": 0.1, "optional": True},
        ]
        results = [self.r.run_step(i, s) for i, s in enumerate(steps, 1)]
        self.assertEqual([x.status for x in results], ["passed"] * 4 + ["skipped"])
        self.assertIn(("shell", "input tap 300 350"), self.adb.cmds)
        self.assertTrue(results[3].screenshot.endswith("settings.png"))

    def test_failed_step_gets_screenshot(self):
        res = self.r.run_step(1, {"action": "tap", "text": "Missing", "timeout": 0.1})
        self.assertEqual(res.status, "failed")
        self.assertIn("FAILED", res.screenshot)

    def test_explore_avoids_dangerous_buttons(self):
        self.r.explore(6)
        tapped = [s.detail for s in self.r.report.steps if s.action == "explore-tap"]
        self.assertTrue(tapped)
        self.assertFalse(any("Delete" in t for t in tapped))
        self.assertGreaterEqual(len(self.r.report.screens), 2)

    def test_crash_detection_and_reports(self):
        self.adb.crash = f"E/AndroidRuntime( 123): FATAL EXCEPTION: main\nE/AndroidRuntime( 123): Process: {PKG}, PID: 123\n"
        self.r.check_crash()
        self.assertEqual(len(self.r.report.crashes), 1)
        self.r.check_crash()  # same line is not reported twice
        self.assertEqual(len(self.r.report.crashes), 1)
        d.write_reports(self.r.report, self.tmp, "log")
        data = json.loads((self.tmp / "report.json").read_text(encoding="utf-8"))
        self.assertFalse(data["passed"])
        self.assertIn("Crashes", (self.tmp / "report.md").read_text(encoding="utf-8"))

    def test_type_escapes_ascii_and_rejects_hebrew_without_ime(self):
        self.r.type_text("a b&c")
        self.assertIn(("shell", "input text a%sb\\&c"), self.adb.cmds)
        with self.assertRaises(d.StepError):
            self.r.type_text("שלום")

    def test_example_scenarios_are_valid(self):
        known = {"launch", "stop", "force_stop", "clear_data", "wait", "sleep", "wait_for", "wait_gone", "tap", "click",
                 "long_press", "double_tap", "type", "key", "press", "back", "home", "swipe", "scroll_to", "screenshot",
                 "assert_text", "assert_visible", "assert_not_visible", "assert_no_text", "assert_foreground", "dump",
                 "rotate", "open_url", "grant", "shell", "explore", "push"}
        root = Path(__file__).resolve().parents[3]
        files = list((root / "tools/ui-testing/scenarios").glob("*.json")) + list(root.glob("apps/*/ui-tests/*.json")) + list(root.glob("ui-tests/*/*.json"))
        self.assertTrue(files)
        for f in files:
            data = json.loads(f.read_text(encoding="utf-8"))
            for step in data["steps"]:
                self.assertIn(step["action"], known, f"{f}: {step}")


if __name__ == "__main__":
    unittest.main()
