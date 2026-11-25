#!/usr/bin/env python3
"""APK Factory UI driver: navigate, tap, type and screenshot an Android app over adb.

Works against any adb device: the GitHub Actions emulator (see
.github/workflows/ui-test-apk.yml) or a real phone/emulator on a developer machine.
No third-party Python packages are required.

Two modes:
  scenario  Run a JSON scenario (list of steps) and assert on what is on screen.
  explore   Automatically tap through the app (bounded number of actions),
            screenshot every new screen and detect crashes/ANRs.

Outputs (in --out): screenshots/*.png, ui/*.xml, logcat.txt, report.json,
report.md (renders on GitHub with inline images) and index.html (gallery).
Exit code: 0 = all passed, 1 = a step failed or the app crashed, 2 = setup error.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

DEFAULT_AVOID = (
    r"delete|remove|uninstall|log ?out|sign ?out|reset|wipe|erase|pay|purchase|buy|"
    r"subscribe|send|submit|report abuse|"
    r"מחק|מחיק|הסר|התנתק|יציאה מהחשבון|איפוס|אפס|תשלום|רכישה|קנה|שלח|שליחה|דווח"
)
KEYCODES = {
    "BACK": 4, "HOME": 3, "ENTER": 66, "TAB": 61, "DEL": 67, "MENU": 82,
    "APP_SWITCH": 187, "SEARCH": 84, "ESCAPE": 111, "DPAD_UP": 19, "DPAD_DOWN": 20,
    "DPAD_LEFT": 21, "DPAD_RIGHT": 22, "DPAD_CENTER": 23,
}


class StepError(Exception):
    pass


class SetupError(Exception):
    pass


# --------------------------------------------------------------------------- adb


class Adb:
    def __init__(self, serial: str | None = None, adb_bin: str | None = None):
        self.adb = adb_bin or shutil.which("adb") or "adb"
        self.serial = serial

    def _base(self) -> list[str]:
        return [self.adb] + (["-s", self.serial] if self.serial else [])

    def run(self, *args: str, timeout: float = 60, check: bool = True, binary: bool = False):
        proc = subprocess.run(self._base() + list(args), capture_output=True, timeout=timeout)
        if check and proc.returncode != 0:
            raise StepError(
                f"adb {' '.join(args)} failed ({proc.returncode}): "
                f"{proc.stderr.decode('utf-8', 'replace').strip() or proc.stdout.decode('utf-8', 'replace').strip()}"
            )
        return proc.stdout if binary else proc.stdout.decode("utf-8", "replace")

    def shell(self, cmd: str, timeout: float = 60, check: bool = True) -> str:
        return self.run("shell", cmd, timeout=timeout, check=check)

    def wait_ready(self, timeout: float = 300) -> None:
        self.run("wait-for-device", timeout=timeout)
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self.shell("getprop sys.boot_completed", check=False).strip() == "1":
                return
            time.sleep(2)
        raise SetupError("device did not finish booting")

    def screen_size(self) -> tuple[int, int]:
        out = self.shell("wm size")
        m = re.findall(r"(\d+)x(\d+)", out)
        if not m:
            raise SetupError(f"cannot read screen size: {out!r}")
        w, h = m[-1]  # "Override size" wins when present
        return int(w), int(h)

    def screenshot(self, path: Path) -> None:
        data = self.run("exec-out", "screencap", "-p", binary=True, timeout=30)
        if not data.startswith(b"\x89PNG"):
            raise StepError("screencap did not return a PNG")
        path.write_bytes(data)

    def ui_xml(self) -> str:
        last = ""
        for _ in range(4):
            out = self.shell("uiautomator dump /sdcard/apkf-ui.xml", check=False, timeout=40)
            last = out
            if "dumped" in out.lower():
                xml = self.shell("cat /sdcard/apkf-ui.xml", timeout=20)
                if "<hierarchy" in xml:
                    return xml[xml.index("<?xml") if "<?xml" in xml else xml.index("<hierarchy"):]
            time.sleep(1)  # "could not get idle state" while animating
        raise StepError(f"uiautomator dump failed: {last.strip()}")

    def foreground_package(self) -> str:
        out = self.shell("dumpsys window", check=False)
        for key in ("mCurrentFocus", "mFocusedApp"):
            for line in out.splitlines():
                if key in line:
                    m = re.search(r"\s([a-zA-Z0-9_.]+)/", line)
                    if m:
                        return m.group(1)
        out = self.shell("dumpsys activity activities", check=False)
        m = re.search(r"(?:mResumedActivity|topResumedActivity).*?\s([a-zA-Z0-9_.]+)/", out)
        return m.group(1) if m else ""


# --------------------------------------------------------------------------- UI tree


@dataclass
class Node:
    attrs: dict[str, str]
    bounds: tuple[int, int, int, int]

    @property
    def text(self) -> str:
        return self.attrs.get("text", "")

    @property
    def desc(self) -> str:
        return self.attrs.get("content-desc", "")

    @property
    def rid(self) -> str:
        return self.attrs.get("resource-id", "")

    @property
    def cls(self) -> str:
        return self.attrs.get("class", "")

    @property
    def package(self) -> str:
        return self.attrs.get("package", "")

    def flag(self, name: str) -> bool:
        return self.attrs.get(name) == "true"

    @property
    def center(self) -> tuple[int, int]:
        x1, y1, x2, y2 = self.bounds
        return (x1 + x2) // 2, (y1 + y2) // 2

    @property
    def area(self) -> int:
        x1, y1, x2, y2 = self.bounds
        return max(0, x2 - x1) * max(0, y2 - y1)

    def label(self) -> str:
        short_id = self.rid.split(":id/")[-1] if self.rid else ""
        bits = [b for b in (self.text, self.desc, short_id and f"#{short_id}") if b]
        return (" / ".join(bits) or self.cls.split(".")[-1])[:80]


def parse_nodes(xml: str) -> list[Node]:
    root = ET.fromstring(xml)
    nodes = []
    for el in root.iter("node"):
        m = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", el.get("bounds", ""))
        if not m:
            continue
        nodes.append(Node(dict(el.attrib), tuple(int(v) for v in m.groups())))  # type: ignore[arg-type]
    return nodes


def matches(node: Node, sel: dict[str, Any]) -> bool:
    exact = bool(sel.get("exact", False))

    def cmp(value: str, wanted: str) -> bool:
        if exact:
            return value == wanted
        return wanted.casefold() in value.casefold()

    if "text" in sel and not cmp(node.text, str(sel["text"])):
        return False
    if "desc" in sel and not cmp(node.desc, str(sel["desc"])):
        return False
    if "any" in sel:  # text OR content-desc OR resource-id
        w = str(sel["any"])
        if not (cmp(node.text, w) or cmp(node.desc, w) or cmp(node.rid, w)):
            return False
    if "id" in sel:
        w = str(sel["id"])
        if not (node.rid == w or node.rid.endswith(":id/" + w)):
            return False
    if "class" in sel and not node.cls.endswith(str(sel["class"])):
        return False
    if "regex" in sel and not re.search(str(sel["regex"]), node.text + "\n" + node.desc):
        return False
    if node.area == 0:
        return False
    return True


SELECTOR_KEYS = ("text", "desc", "id", "any", "class", "regex")


def has_selector(step: dict[str, Any]) -> bool:
    return any(k in step for k in SELECTOR_KEYS)


def describe_selector(step: dict[str, Any]) -> str:
    return ", ".join(f"{k}={step[k]!r}" for k in SELECTOR_KEYS if k in step)


# --------------------------------------------------------------------------- runner


@dataclass
class StepResult:
    index: int
    action: str
    detail: str
    status: str  # passed | failed | skipped
    seconds: float
    error: str = ""
    screenshot: str = ""


@dataclass
class Report:
    app: str
    package: str
    mode: str
    device: dict[str, str] = field(default_factory=dict)
    steps: list[StepResult] = field(default_factory=list)
    screens: list[dict[str, str]] = field(default_factory=list)
    crashes: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    started: float = field(default_factory=time.time)
    finished: float = 0.0

    @property
    def passed(self) -> bool:
        return not self.crashes and all(s.status != "failed" for s in self.steps)


class Runner:
    def __init__(self, adb: Adb, out: Path, package: str, app: str, mode: str,
                 shot_every_step: bool = False, avoid: str = DEFAULT_AVOID):
        self.adb = adb
        self.out = out
        self.package = package
        self.shots = out / "screenshots"
        self.ui = out / "ui"
        self.shots.mkdir(parents=True, exist_ok=True)
        self.ui.mkdir(parents=True, exist_ok=True)
        self.report = Report(app=app, package=package, mode=mode)
        self.shot_every_step = shot_every_step
        self.avoid = re.compile(avoid, re.IGNORECASE) if avoid else None
        self.counter = 0
        self.size = (1080, 1920)
        self._seen_crash_lines: set[str] = set()
        self.scenario_dir = Path.cwd()
        self.auto_allow_permissions = True

    # ---------------------------------------------------------------- helpers
    def slug(self, name: str) -> str:
        s = re.sub(r"[^A-Za-z0-9֐-׿_-]+", "-", name).strip("-")
        return s[:60] or "shot"

    def shot(self, name: str) -> str:
        self.counter += 1
        fname = f"{self.counter:03d}-{self.slug(name)}.png"
        self.adb.screenshot(self.shots / fname)
        try:
            (self.ui / fname.replace(".png", ".xml")).write_text(self.adb.ui_xml(), encoding="utf-8")
        except Exception as exc:  # UI dump is best effort for screenshots
            self.report.warnings.append(f"UI dump for {fname} failed: {exc}")
        return f"screenshots/{fname}"

    def nodes(self) -> list[Node]:
        return parse_nodes(self.adb.ui_xml())

    def find(self, sel: dict[str, Any]) -> Node | None:
        found = [n for n in self.nodes() if matches(n, sel)]
        if not found:
            return None
        idx = int(sel.get("index", 0))
        return found[idx] if idx < len(found) else None

    def wait_for(self, sel: dict[str, Any], timeout: float) -> Node:
        deadline = time.time() + timeout
        while True:
            node = self.find(sel)
            if node:
                return node
            if time.time() >= deadline:
                raise StepError(f"element not found within {timeout:g}s: {describe_selector(sel)}")
            time.sleep(0.7)

    def point(self, x: Any, y: Any) -> tuple[int, int]:
        """Pixels, or a fraction of the screen when given as a float in [0, 1] (e.g. 0.5)."""
        w, h = self.size

        def conv(v: Any, total: int) -> int:
            if isinstance(v, float) and 0.0 <= v <= 1.0:
                return int(v * total)
            return int(float(v))

        return conv(x, w), conv(y, h)

    def tap_xy(self, x: int, y: int) -> None:
        self.adb.shell(f"input tap {x} {y}")

    def check_crash(self) -> None:
        if not self.package:
            return
        out = self.adb.run("logcat", "-d", "-b", "crash", "-v", "brief", check=False, timeout=30)
        main = self.adb.run("logcat", "-d", "-v", "brief", "-s", "ActivityManager:E", "AndroidRuntime:E", check=False, timeout=30)
        blob = out + "\n" + main
        pkg = re.escape(self.package)
        patterns = [rf"Process: {pkg}\b", rf"ANR in {pkg}\b", rf"FATAL EXCEPTION.*\n.*{pkg}"]
        for line in blob.splitlines():
            if any(re.search(p, line) for p in patterns) and line not in self._seen_crash_lines:
                self._seen_crash_lines.add(line)
                kind = "ANR" if "ANR in" in line else "Crash"
                context = self._crash_context(blob, line)
                self.report.crashes.append(f"{kind}: {line.strip()}\n{context}")

    @staticmethod
    def _crash_context(blob: str, line: str) -> str:
        lines = blob.splitlines()
        try:
            i = lines.index(line)
        except ValueError:
            return ""
        return "\n".join(lines[max(0, i - 2): i + 25])

    # ---------------------------------------------------------------- setup
    def collect_device_info(self) -> None:
        props = {
            "model": "ro.product.model", "sdk": "ro.build.version.sdk",
            "release": "ro.build.version.release", "abi": "ro.product.cpu.abi",
            "locale": "persist.sys.locale",
        }
        for k, p in props.items():
            self.report.device[k] = self.adb.shell(f"getprop {p}", check=False).strip()
        w, h = self.adb.screen_size()
        self.size = (w, h)
        self.report.device["screen"] = f"{w}x{h}"

    def prepare_device(self, disable_animations: bool = True) -> None:
        self.adb.shell("input keyevent 224", check=False)  # wake
        self.adb.shell("wm dismiss-keyguard", check=False)
        self.adb.shell("input keyevent 82", check=False)  # unlock menu
        if disable_animations:
            for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
                self.adb.shell(f"settings put global {key} 0", check=False)
        self.adb.run("logcat", "-c", check=False)
        self.adb.run("logcat", "-b", "crash", "-c", check=False)

    def launch(self) -> None:
        if not self.package:
            raise StepError("no package known; pass --package or --apk")
        out = self.adb.shell(
            f"monkey -p {self.package} -c android.intent.category.LAUNCHER 1", check=False
        )
        if "No activities found" in out or "monkey aborted" in out:
            raise StepError(f"cannot launch {self.package}: {out.strip()}")
        self.settle(2.5)
        if self.auto_allow_permissions:
            self.allow_permission_dialogs()

    def allow_permission_dialogs(self, rounds: int = 3) -> None:
        """Tap 'Allow' on runtime-permission dialogs that cover the app right after launch."""
        for _ in range(rounds):
            if "permissioncontroller" not in self.adb.foreground_package():
                return
            try:
                nodes = self.nodes()
            except StepError:
                return
            ids = ("permission_allow_button", "permission_allow_foreground_only_button", "permission_allow_one_time_button")
            target = next((n for i in ids for n in nodes if n.rid.endswith(":id/" + i)), None)
            if target is None:
                target = next((n for n in nodes if n.flag("clickable") and n.text.strip().lower() in ("allow", "אישור", "התר", "לאפשר")), None)
            if target is None:
                return
            self.report.warnings.append("auto-allowed a runtime permission dialog after launch")
            self.tap_xy(*target.center)
            self.settle(1.0)

    def settle(self, seconds: float = 1.0) -> None:
        time.sleep(seconds)

    # ---------------------------------------------------------------- scenario
    def run_step(self, i: int, step: dict[str, Any]) -> StepResult:
        action = str(step.get("action", "")).lower()
        detail = step.get("note") or describe_selector(step) or ", ".join(
            f"{k}={v!r}" for k, v in step.items() if k not in ("action", "optional", "screenshot")
        )
        t0 = time.time()
        res = StepResult(i, action, str(detail), "passed", 0.0)
        try:
            self.do(action, step)
            self.check_crash()
        except StepError as exc:
            res.status = "skipped" if step.get("optional") else "failed"
            res.error = str(exc)
        res.seconds = round(time.time() - t0, 2)
        want_shot = step.get("screenshot", self.shot_every_step) or res.status == "failed"
        if action != "screenshot" and want_shot:
            try:
                res.screenshot = self.shot(f"step{i}-{action}" + ("-FAILED" if res.status == "failed" else ""))
            except Exception as exc:
                self.report.warnings.append(f"screenshot after step {i} failed: {exc}")
        if action == "screenshot" and res.status == "passed":
            res.screenshot = self._last_named
        return res

    _last_named = ""

    def do(self, action: str, s: dict[str, Any]) -> None:
        timeout = float(s.get("timeout", 10))
        if action == "launch":
            if s.get("clear_data"):
                self.adb.shell(f"pm clear {self.package}", check=False)
            if s.get("restart", True):
                self.adb.shell(f"am force-stop {self.package}", check=False)
            self.launch()
            if s.get("wait_for"):
                self.wait_for({"any": s["wait_for"]}, timeout)
        elif action in ("stop", "force_stop"):
            self.adb.shell(f"am force-stop {self.package}")
        elif action == "clear_data":
            self.adb.shell(f"pm clear {self.package}")
        elif action in ("wait", "sleep"):
            time.sleep(float(s.get("seconds", 1)))
        elif action == "wait_for":
            self.wait_for(s, timeout)
        elif action == "wait_gone":
            deadline = time.time() + timeout
            while self.find(s):
                if time.time() > deadline:
                    raise StepError(f"still visible after {timeout:g}s: {describe_selector(s)}")
                time.sleep(0.7)
        elif action in ("tap", "click", "long_press", "double_tap"):
            if has_selector(s):
                x, y = self.wait_for(s, timeout).center
            elif "x" in s and "y" in s:
                x, y = self.point(s["x"], s["y"])
            else:
                raise StepError(f"{action} needs a selector (text/id/desc/any) or x,y")
            if action == "long_press":
                ms = int(s.get("ms", 900))
                self.adb.shell(f"input swipe {x} {y} {x} {y} {ms}")
            elif action == "double_tap":
                self.tap_xy(x, y)
                self.tap_xy(x, y)
            else:
                self.tap_xy(x, y)
            self.settle(float(s.get("settle", 1.0)))
        elif action == "type":
            if has_selector(s):
                self.tap_xy(*self.wait_for(s, timeout).center)
                self.settle(0.5)
            if s.get("clear"):
                self.adb.shell("input keyevent 123")  # MOVE_END
                self.adb.shell("input keyevent " + " ".join(["67"] * int(s.get("clear_count", 60))))
            self.type_text(str(s.get("value", s.get("input", ""))))
            if s.get("enter"):
                self.adb.shell("input keyevent 66")
            if s.get("hide_keyboard", False):
                self.adb.shell("input keyevent 111")
            self.settle(0.6)
        elif action in ("key", "press"):
            key = str(s.get("key", "BACK")).upper()
            code = KEYCODES.get(key, key)
            self.adb.shell(f"input keyevent {code}")
            self.settle(float(s.get("settle", 0.8)))
        elif action == "back":
            self.adb.shell("input keyevent 4")
            self.settle(float(s.get("settle", 0.8)))
        elif action == "home":
            self.adb.shell("input keyevent 3")
            self.settle(0.8)
        elif action == "swipe":
            self.swipe(s)
        elif action == "scroll_to":
            sel = {k: s[k] for k in SELECTOR_KEYS + ("exact", "index") if k in s}
            for _ in range(int(s.get("max_swipes", 8))):
                if self.find(sel):
                    return
                self.swipe({"direction": s.get("direction", "up")})
            if not self.find(sel):
                raise StepError(f"not found after scrolling: {describe_selector(sel)}")
        elif action == "screenshot":
            self._last_named = self.shot(str(s.get("name", "screenshot")))
        elif action in ("assert_text", "assert_visible"):
            self.wait_for(s, float(s.get("timeout", 5)))
        elif action in ("assert_not_visible", "assert_no_text"):
            time.sleep(float(s.get("delay", 0.5)))
            if self.find(s):
                raise StepError(f"unexpectedly visible: {describe_selector(s)}")
        elif action == "assert_foreground":
            fg = self.adb.foreground_package()
            if fg != self.package:
                raise StepError(f"foreground app is {fg or 'unknown'}, expected {self.package}")
        elif action == "dump":
            name = self.slug(str(s.get("name", "ui")))
            (self.ui / f"{name}.xml").write_text(self.adb.ui_xml(), encoding="utf-8")
        elif action == "rotate":
            rot = {"portrait": 0, "landscape": 1, "reverse_portrait": 2, "reverse_landscape": 3}
            val = rot.get(str(s.get("to", "portrait")), 0)
            self.adb.shell("settings put system accelerometer_rotation 0")
            self.adb.shell(f"settings put system user_rotation {val}")
            self.settle(1.5)
            w, h = self.adb.screen_size()
            self.size = (min(w, h), max(w, h)) if val % 2 == 0 else (max(w, h), min(w, h))
        elif action == "open_url":
            url = str(s["url"]).replace("'", "")
            self.adb.shell(f"am start -a android.intent.action.VIEW -d '{url}' {self.package}", check=False)
            self.settle(2)
        elif action == "grant":
            self.adb.shell(f"pm grant {self.package} {s['permission']}")
        elif action == "shell":
            out = self.adb.shell(str(s["cmd"]), check=not s.get("ignore_errors"))
            if s.get("expect") and str(s["expect"]) not in out:
                raise StepError(f"shell output does not contain {s['expect']!r}: {out[:300]!r}")
        elif action == "push":
            # Copy a fixture file (path relative to the scenario file) onto the device,
            # then ask MediaStore to index it so system file pickers can show it.
            src = (self.scenario_dir / str(s["src"])).resolve()
            if not src.is_file():
                raise StepError(f"push: fixture not found: {src}")
            dest = str(s.get("dest", "/sdcard/Download/" + src.name))
            self.adb.shell(f"mkdir -p '{dest.rsplit('/', 1)[0]}'", check=False)
            self.adb.run("push", str(src), dest, timeout=120)
            if s.get("media_scan", dest.startswith("/sdcard/")):
                self.adb.shell(f"am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file://{dest}'", check=False)
                self.adb.shell("content call --uri content://media --method scan_volume --arg external_primary", check=False, timeout=90)
                self.settle(1.5)
        elif action == "explore":
            self.explore(int(s.get("max_actions", 25)))
        else:
            raise StepError(f"unknown action: {action!r}")

    def swipe(self, s: dict[str, Any]) -> None:
        w, h = self.size
        if "direction" in s:
            d = str(s["direction"]).lower()
            cx, cy = w // 2, h // 2
            dx, dy = int(w * 0.35), int(h * 0.3)
            # "up" = finger moves up = content scrolls down (reveals items below)
            vec = {"up": (cx, cy + dy, cx, cy - dy), "down": (cx, cy - dy, cx, cy + dy),
                   "left": (cx + dx, cy, cx - dx, cy), "right": (cx - dx, cy, cx + dx, cy)}
            if d not in vec:
                raise StepError(f"bad swipe direction {d!r}")
            x1, y1, x2, y2 = vec[d]
        else:
            x1, y1 = self.point(s["x1"], s["y1"])
            x2, y2 = self.point(s["x2"], s["y2"])
        self.adb.shell(f"input swipe {x1} {y1} {x2} {y2} {int(s.get('ms', 350))}")
        self.settle(float(s.get("settle", 0.8)))

    def type_text(self, text: str) -> None:
        if not text:
            return
        if not text.isascii():
            # `input text` only supports ASCII. Fall back to the ADBKeyBoard IME
            # broadcast if that IME is installed; otherwise fail clearly.
            ime = self.adb.shell("ime list -s", check=False)
            if "com.android.adbkeyboard" not in ime:
                raise StepError(
                    "non-ASCII text (e.g. Hebrew) needs the ADBKeyBoard IME; "
                    "use ASCII text or install ADBKeyBoard (see docs/UI_TESTING.md)"
                )
            self.adb.shell("ime set com.android.adbkeyboard/.AdbIME", check=False)
            import base64
            b64 = base64.b64encode(text.encode("utf-8")).decode()
            self.adb.shell(f"am broadcast -a ADB_INPUT_B64 --es msg {b64}")
            return
        escaped = re.sub(r"([\\\"'`$&|;<>()*~?!#\[\]{}])", r"\\\1", text).replace(" ", "%s")
        self.adb.shell(f"input text {escaped}")

    # ---------------------------------------------------------------- explore
    def screen_signature(self, nodes: list[Node]) -> str:
        parts = []
        for n in nodes:
            if n.package != self.package:
                continue
            txt = n.text if (n.flag("clickable") or n.cls.endswith("Button")) else ""
            parts.append(f"{n.cls}|{n.rid}|{txt[:30]}|{n.desc[:30]}")
        return hashlib.sha1("\n".join(sorted(parts)).encode()).hexdigest()[:12]

    def candidates(self, nodes: list[Node]) -> list[Node]:
        out = []
        h = self.size[1]
        for n in nodes:
            if n.package != self.package or not n.flag("enabled"):
                continue
            if not (n.flag("clickable") or n.flag("long-clickable") or n.flag("checkable")):
                continue
            if n.area < 400 or n.area > self.size[0] * h * 0.8:
                continue
            words = f"{n.text} {n.desc} {n.rid}"
            if self.avoid and self.avoid.search(words):
                continue
            out.append(n)
        # Prefer labelled elements, top-to-bottom.
        out.sort(key=lambda n: (0 if (n.text or n.desc) else 1, n.bounds[1], n.bounds[0]))
        return out

    def explore(self, max_actions: int) -> None:
        tried: dict[str, set[str]] = {}
        seen: dict[str, str] = {}
        relaunches = 0
        stuck = 0
        t0 = time.time()
        for step in range(max_actions):
            fg = self.adb.foreground_package()
            if fg and fg != self.package:
                path = self.shot(f"left-app-to-{fg}")
                self.report.steps.append(StepResult(len(self.report.steps) + 1, "explore", f"left app to {fg}", "passed", 0, screenshot=path))
                self.adb.shell("input keyevent 4")
                self.settle(1)
                if self.adb.foreground_package() != self.package:
                    relaunches += 1
                    if relaunches > 5:
                        self.report.warnings.append("explore: app kept leaving the foreground; stopping")
                        break
                    self.launch()
                self.check_crash()
                continue
            try:
                nodes = self.nodes()
            except StepError as exc:
                self.report.warnings.append(f"explore: {exc}")
                self.settle(1)
                continue
            sig = self.screen_signature(nodes)
            if sig not in seen:
                path = self.shot(f"screen-{len(seen) + 1}")
                seen[sig] = path
                self.report.screens.append({"signature": sig, "screenshot": path, "found_at_action": str(step)})
            todo = [n for n in self.candidates(nodes) if self.node_key(n) not in tried.setdefault(sig, set())]
            if not todo:
                stuck += 1
                if stuck > 6:
                    break
                self.adb.shell("input keyevent 4")
                self.settle(1)
                if self.adb.foreground_package() != self.package:
                    relaunches += 1
                    if relaunches > 5:
                        break
                    self.launch()
                continue
            stuck = 0
            target = todo[0]
            tried[sig].add(self.node_key(target))
            r = StepResult(len(self.report.steps) + 1, "explore-tap", target.label(), "passed", 0)
            t = time.time()
            self.tap_xy(*target.center)
            self.settle(1.2)
            self.check_crash()
            r.seconds = round(time.time() - t, 2)
            if self.report.crashes:
                r.status = "failed"
                r.error = "app crashed after this tap"
                r.screenshot = self.shot("crash-after-" + target.label())
                self.report.steps.append(r)
                break
            self.report.steps.append(r)
        self.report.warnings.append(
            f"explore: {len(seen)} distinct screens, {sum(len(v) for v in tried.values())} elements tapped, "
            f"{relaunches} relaunches, {time.time() - t0:.0f}s"
        )

    @staticmethod
    def node_key(n: Node) -> str:
        return f"{n.rid}|{n.text[:40]}|{n.desc[:40]}|{n.cls}|{n.bounds[0]//20},{n.bounds[1]//20}"


# --------------------------------------------------------------------------- reports


def write_reports(r: Report, out: Path, logcat: str) -> None:
    r.finished = time.time()
    (out / "logcat.txt").write_text(logcat, encoding="utf-8")
    data = {
        "app": r.app, "package": r.package, "mode": r.mode, "passed": r.passed,
        "device": r.device, "duration_seconds": round(r.finished - r.started, 1),
        "crashes": r.crashes, "warnings": r.warnings, "screens": r.screens,
        "steps": [s.__dict__ for s in r.steps],
        "screenshots": sorted(p.name for p in (out / "screenshots").glob("*.png")),
    }
    (out / "report.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")

    icon = {"passed": "✅", "failed": "❌", "skipped": "⚠️"}
    md = [f"# UI test — {r.app} ({r.mode})", "",
          f"**Result:** {'✅ PASSED' if r.passed else '❌ FAILED'}  ",
          f"**Package:** `{r.package}`  ",
          f"**Device:** {r.device.get('model', '?')} · Android {r.device.get('release', '?')} (API {r.device.get('sdk', '?')}) · {r.device.get('screen', '?')}  ",
          f"**Duration:** {data['duration_seconds']}s", ""]
    if r.crashes:
        md += ["## Crashes / ANRs", ""] + [f"```\n{c[:3000]}\n```" for c in r.crashes] + [""]
    if r.steps:
        md += ["## Steps", "", "| # | Action | Detail | Result | Time |", "|---|---|---|---|---|"]
        for s in r.steps:
            det = s.detail.replace("|", "\\|")[:90]
            err = f" — {s.error.replace('|', '/')[:160]}" if s.error else ""
            md.append(f"| {s.index} | {s.action} | {det} | {icon.get(s.status, s.status)}{err} | {s.seconds}s |")
        md.append("")
    if r.warnings:
        md += ["## Notes", ""] + [f"- {w}" for w in r.warnings] + [""]
    md += ["## Screenshots", ""]
    for name in data["screenshots"]:
        md += [f"**{name}**", "", f'<img src="screenshots/{name}" width="270">', ""]
    (out / "report.md").write_text("\n".join(md), encoding="utf-8")

    cards = "\n".join(
        f'<figure><a href="screenshots/{html.escape(n)}"><img src="screenshots/{html.escape(n)}" loading="lazy"></a>'
        f"<figcaption>{html.escape(n)}</figcaption></figure>" for n in data["screenshots"]
    )
    rows = "\n".join(
        f"<tr class='{s.status}'><td>{s.index}</td><td>{html.escape(s.action)}</td><td>{html.escape(s.detail)}</td>"
        f"<td>{s.status}</td><td>{html.escape(s.error)}</td></tr>" for s in r.steps
    )
    crashes = "".join(f"<pre>{html.escape(c)}</pre>" for c in r.crashes)
    (out / "index.html").write_text(f"""<!doctype html><meta charset="utf-8">
<title>UI test — {html.escape(r.app)}</title>
<style>body{{font-family:system-ui,sans-serif;margin:24px;background:#f6f7f9}}
.grid{{display:flex;flex-wrap:wrap;gap:16px}}figure{{margin:0;background:#fff;padding:8px;border-radius:8px;box-shadow:0 1px 3px #0002}}
img{{width:240px;display:block}}figcaption{{font-size:12px;margin-top:4px}}table{{border-collapse:collapse;background:#fff}}
td,th{{border:1px solid #ddd;padding:4px 8px;font-size:13px;text-align:left}}tr.failed{{background:#fde8e8}}tr.skipped{{background:#fff7e0}}
pre{{background:#222;color:#eee;padding:12px;overflow:auto}}</style>
<h1>UI test — {html.escape(r.app)} ({html.escape(r.mode)}): {'PASSED' if r.passed else 'FAILED'}</h1>
<p>Package <code>{html.escape(r.package)}</code> · {html.escape(json.dumps(r.device, ensure_ascii=False))}</p>
{crashes}<table><tr><th>#</th><th>Action</th><th>Detail</th><th>Result</th><th>Error</th></tr>{rows}</table>
<h2>Screenshots</h2><div class="grid">{cards}</div>""", encoding="utf-8")


# --------------------------------------------------------------------------- APK helpers


def find_sdk_tool(name: str) -> str | None:
    if shutil.which(name):
        return shutil.which(name)
    for root in filter(None, [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"), "/usr/local/lib/android/sdk"]):
        hits = sorted(Path(root).glob(f"build-tools/*/{name}")) + sorted(Path(root).glob(f"cmdline-tools/*/bin/{name}"))
        if hits:
            return str(hits[-1])
    return None


def package_from_apk(apk: Path) -> str:
    aapt = find_sdk_tool("aapt2") or find_sdk_tool("aapt")
    if aapt:
        out = subprocess.run([aapt, "dump", "badging", str(apk)], capture_output=True, text=True).stdout
        m = re.search(r"package: name='([^']+)'", out)
        if m:
            return m.group(1)
    analyzer = find_sdk_tool("apkanalyzer")
    if analyzer:
        out = subprocess.run([analyzer, "manifest", "application-id", str(apk)], capture_output=True, text=True).stdout.strip()
        if out:
            return out.splitlines()[-1]
    return ""


def install_apk(adb: Adb, apk: Path, grant: bool) -> str:
    before = set(adb.shell("pm list packages -3", check=False).split())
    args = ["install", "-r", "-t"] + (["-g"] if grant else []) + [str(apk)]
    out = adb.run(*args, timeout=240, check=False)
    if "Success" not in out:
        # Signature mismatch with an older install: uninstall and retry once.
        pkg = package_from_apk(apk)
        if pkg and "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in out:
            adb.run("uninstall", pkg, check=False)
            out = adb.run(*args, timeout=240, check=False)
        if "Success" not in out:
            raise SetupError(f"APK install failed: {out.strip()}")
    pkg = package_from_apk(apk)
    if not pkg:
        after = set(adb.shell("pm list packages -3", check=False).split())
        new = sorted(after - before)
        pkg = new[0].replace("package:", "") if new else ""
    return pkg


# --------------------------------------------------------------------------- main


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", help="adb device serial (default: the only connected device)")
    ap.add_argument("--apk", type=Path, help="install this APK before testing")
    ap.add_argument("--package", default="", help="application id (auto-detected from --apk)")
    ap.add_argument("--app", default="", help="display name for reports")
    ap.add_argument("--scenario", type=Path, help="JSON scenario file")
    ap.add_argument("--explore", type=int, default=0, metavar="N",
                    help="after the scenario (or alone) auto-explore with up to N taps")
    ap.add_argument("--out", type=Path, default=Path("ui-test-results"))
    ap.add_argument("--no-grant", action="store_true", help="do not auto-grant runtime permissions")
    ap.add_argument("--screenshot-every-step", action="store_true")
    ap.add_argument("--avoid", default=DEFAULT_AVOID, help="regex of labels explore must never tap ('' = none)")
    ap.add_argument("--no-launch", action="store_true", help="do not auto-launch before an explore-only run")
    args = ap.parse_args(argv)

    scenario: dict[str, Any] = {}
    if args.scenario:
        scenario = json.loads(args.scenario.read_text(encoding="utf-8"))
        if not isinstance(scenario.get("steps"), list):
            print(f"::error::{args.scenario}: scenario must contain a 'steps' list", file=sys.stderr)
            return 2
    if not args.scenario and not args.explore:
        args.explore = 30

    adb = Adb(args.serial)
    args.out.mkdir(parents=True, exist_ok=True)
    package = args.package or scenario.get("package", "")
    mode = "+".join(m for m, on in (("scenario", bool(args.scenario)), ("explore", bool(args.explore))) if on)
    try:
        adb.wait_ready()
        if args.apk:
            grant = not args.no_grant and scenario.get("grant_permissions", True)
            installed = install_apk(adb, args.apk, grant)
            package = package or installed
        if not package:
            raise SetupError("could not determine the package; pass --package")
    except (SetupError, StepError, subprocess.TimeoutExpired) as exc:
        print(f"::error::UI test setup failed: {exc}", file=sys.stderr)
        (args.out / "setup-error.txt").write_text(str(exc), encoding="utf-8")
        return 2

    runner = Runner(adb, args.out, package, args.app or scenario.get("name", package), mode,
                    shot_every_step=bool(args.screenshot_every_step or scenario.get("screenshot_every_step")),
                    avoid=scenario.get("avoid", args.avoid))
    if args.scenario:
        runner.scenario_dir = args.scenario.resolve().parent
    runner.auto_allow_permissions = bool(scenario.get("auto_allow_permissions", True))
    runner.prepare_device(disable_animations=scenario.get("disable_animations", True))
    runner.collect_device_info()

    try:
        steps = scenario.get("steps", [])
        for i, step in enumerate(steps, 1):
            res = runner.run_step(i, step)
            runner.report.steps.append(res)
            mark = {"passed": "ok", "failed": "FAIL", "skipped": "skip"}[res.status]
            print(f"[{mark}] {i:02d} {res.action} {res.detail} {res.error}".rstrip(), flush=True)
            if runner.report.crashes:
                print("App crashed — stopping scenario.", flush=True)
                break
            if res.status == "failed" and scenario.get("stop_on_failure", True):
                break
        if args.explore and not runner.report.crashes:
            if not steps and not args.no_launch:
                runner.launch()
            print(f"Exploring up to {args.explore} actions…", flush=True)
            runner.explore(args.explore)
        if not runner.report.steps or runner.report.steps[-1].action != "screenshot":
            runner.shot("final")
    except (StepError, subprocess.TimeoutExpired) as exc:
        runner.report.warnings.append(f"aborted: {exc}")
        runner.report.steps.append(StepResult(len(runner.report.steps) + 1, "abort", "", "failed", 0, str(exc)))
    finally:
        runner.check_crash()
        logcat = adb.run("logcat", "-d", "-v", "threadtime", check=False, timeout=60)
        write_reports(runner.report, args.out, logcat)

    r = runner.report
    print(f"\nResult: {'PASSED' if r.passed else 'FAILED'} — {len(r.steps)} steps, "
          f"{len(list((args.out / 'screenshots').glob('*.png')))} screenshots, {len(r.crashes)} crashes")
    return 0 if r.passed else 1


if __name__ == "__main__":
    sys.exit(main())
