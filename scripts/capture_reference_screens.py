#!/usr/bin/env python3
"""Capture the real Files, Code, Build, Tools, and Settings screens on an emulator."""
from pathlib import Path
import re
import subprocess
import time

PACKAGE = "com.jepongdevxyz.idebuild"
OUT = Path("artifacts/ui-reference")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args: str) -> str:
    return subprocess.check_output(["adb", *args], text=True, stderr=subprocess.STDOUT, timeout=90)


def wait(seconds: float = 0.6) -> None:
    time.sleep(seconds)


def screen_size() -> tuple[int, int]:
    output = adb("shell", "wm", "size")
    match = re.search(r"(\d+)x(\d+)", output)
    if not match:
        raise RuntimeError(f"Could not read emulator screen size: {output}")
    return int(match.group(1)), int(match.group(2))


WIDTH, HEIGHT = screen_size()


def tap(x: float, y: float, label: str) -> None:
    """Tap normalized coordinates without launching memory-heavy uiautomator."""
    adb("shell", "input", "tap", str(round(WIDTH * x)), str(round(HEIGHT * y)))
    print(f"Tapped {label}", flush=True)
    wait(0.45)


def capture(name: str) -> None:
    wait(0.6)
    remote = f"/sdcard/{name}.png"
    adb("shell", "screencap", "-p", remote)
    subprocess.run(["adb", "pull", remote, str(OUT / f"{name}.png")], check=True, timeout=90)
    print(f"Captured {name}", flush=True)


adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
adb("shell", "pm", "clear", PACKAGE)
adb("shell", "monkey", "-p", PACKAGE, "1")
wait(2)

# New Project sits at the right of the Files pane's bottom action row.
tap(0.89, 0.88, "new project button")
tap(0.50, 0.48, "project name field")
adb("shell", "input", "text", "ReferenceApp")
adb("shell", "input", "keyevent", "4")  # Hide the keyboard before choosing Create.
tap(0.83, 0.59, "create project confirmation")
wait(1.2)
capture("files")

# The starter tree expands through java; expand package directories to reach its source file.
for row, label in ((4, "com package"), (5, "example package"), (6, "referenceapp package")):
    tap(0.38, 0.28 + row * 0.055, label)
tap(0.42, 0.28 + 7 * 0.055, "MainActivity.kt")
capture("code")

# The bottom navigation is laid out as five equal width items.
for screen, x in (("build", 0.50), ("tools", 0.70), ("settings", 0.90)):
    tap(x, 0.93, f"{screen} navigation button")
    capture(screen)

# Unit tests assemble and signature-check a project from New Project's writer.
# Install and launch that APK on this emulator to verify it is genuinely installable.
generated_apk = Path("app/build/template-smoke/generated-app.apk")
assert generated_apk.is_file(), f"Generated project APK is missing: {generated_apk}"
adb("install", "-r", str(generated_apk))
adb("shell", "monkey", "-p", "com.example.generatedsmokeapp", "1")
wait(1)
assert "package:" in adb("shell", "pm", "path", "com.example.generatedsmokeapp"), "Generated project did not install"
adb("shell", "am", "force-stop", "com.example.generatedsmokeapp")

expected = {"files.png", "code.png", "build.png", "tools.png", "settings.png"}
actual = {path.name for path in OUT.glob("*.png")}
assert actual == expected, f"Screenshot set mismatch: expected {expected}, got {actual}"
for path in sorted(OUT.glob("*.png")):
    assert path.stat().st_size > 10_000, f"Screenshot looks empty: {path}"
print(f"Captured all five live DevxyzIDE screens in {OUT}", flush=True)
