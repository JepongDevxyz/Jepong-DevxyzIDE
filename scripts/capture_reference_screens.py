#!/usr/bin/env python3
"""Capture the real Files, Code, Build, Tools, and Settings screens on an emulator."""
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "com.jepongdevxyz.idebuild"
OUT = Path("artifacts/ui-reference")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args: str) -> str:
    return subprocess.check_output(["adb", *args], text=True, stderr=subprocess.STDOUT)


def wait(seconds: float = 0.6) -> None:
    time.sleep(seconds)


def elements():
    adb("shell", "uiautomator", "dump", "/data/local/tmp/devxyzide-window.xml")
    xml = adb("shell", "cat", "/data/local/tmp/devxyzide-window.xml")
    return ET.fromstring(xml).iter("node")


def tap(predicate, label: str, timeout: float = 12) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        for node in elements():
            if predicate(node.attrib):
                bounds = node.attrib["bounds"]
                left, top = bounds.split("][")[0].strip("[]").split(",")
                right, bottom = bounds.split("][")[1].strip("[]").split(",")
                x = (int(left) + int(right)) // 2
                y = (int(top) + int(bottom)) // 2
                adb("shell", "input", "tap", str(x), str(y))
                wait()
                return
        wait(0.4)
    raise RuntimeError(f"Could not find tappable UI element: {label}")


def by_id(resource_id: str):
    suffix = f"{PACKAGE}:id/{resource_id}"
    return lambda attrs: attrs.get("resource-id", "").endswith(suffix) and attrs.get("clickable") == "true"


def by_text(text: str):
    return lambda attrs: text in attrs.get("text", "") and attrs.get("clickable") == "true"


def capture(name: str) -> None:
    wait(0.6)
    adb("shell", "screencap", "-p", f"/sdcard/{name}.png")
    subprocess.run(["adb", "pull", f"/sdcard/{name}.png", str(OUT / f"{name}.png")], check=True)


def assert_header(expected: str) -> None:
    nodes = list(elements())
    title = next((node.attrib.get("text") for node in nodes if node.attrib.get("resource-id", "").endswith(":id/brandTitle")), None)
    assert title == expected, f"Expected screen title {expected!r}, got {title!r}"


adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
adb("shell", "pm", "clear", PACKAGE)
adb("shell", "monkey", "-p", PACKAGE, "1")
wait(2)
tap(by_id("newProjectBtn"), "new project button")
tap(lambda a: a.get("class", "") == "android.widget.EditText", "project name field")
adb("shell", "input", "text", "ReferenceApp")
tap(by_text("Create"), "create project confirmation")
wait(1.2)
assert_header("DevxyzIDE")
capture("files")

# Open the real generated Kotlin source so the editor screen shows a project file.
tap(lambda a: "referenceapp" in a.get("text", "").lower(), "starter package directory")
tap(lambda a: "MainActivity.kt" in a.get("text", ""), "MainActivity.kt project row")
assert_header("MainActivity.kt")
capture("code")
for screen in ("build", "tools", "settings"):
    tap(by_id(f"{screen}Btn"), f"{screen} navigation button")
    assert_header({"build": "Build", "tools": "Tools", "settings": "Settings"}[screen])
    capture(screen)

expected = {"files.png", "code.png", "build.png", "tools.png", "settings.png"}
actual = {path.name for path in OUT.glob("*.png")}
assert actual == expected, f"Screenshot set mismatch: expected {expected}, got {actual}"
for path in sorted(OUT.glob("*.png")):
    assert path.stat().st_size > 10_000, f"Screenshot looks empty: {path}"
print(f"Captured all five live DevxyzIDE screens in {OUT}")
