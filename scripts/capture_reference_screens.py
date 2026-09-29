#!/usr/bin/env python3
"""Capture and exercise the IDE on a real Android emulator."""
from pathlib import Path
import re
import subprocess
import time

PACKAGE = "com.jepongdevxyz.idebuild"
OUT = Path("artifacts/ui-reference")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args: str, timeout: int = 60) -> str:
    return subprocess.check_output(["adb", *args], text=True, stderr=subprocess.STDOUT, timeout=timeout)


def wait(seconds: float = 0.6) -> None:
    time.sleep(seconds)


def display_metrics() -> tuple[int, int, float]:
    size_output = adb("shell", "wm", "size")
    density_output = adb("shell", "wm", "density")
    sizes = re.findall(r"(\d+)x(\d+)", size_output)
    densities = re.findall(r"density:\s*(\d+)", density_output)
    if not sizes or not densities:
        raise RuntimeError(f"Cannot determine emulator display metrics: {size_output!r}; {density_output!r}")
    width_px, height_px = map(int, sizes[-1])
    return round(width_px / (int(densities[-1]) / 160)), round(height_px / (int(densities[-1]) / 160)), int(densities[-1]) / 160


WIDTH_DP, HEIGHT_DP, DENSITY = display_metrics()


def tap_dp(x_dp: float, y_dp: float, label: str) -> None:
    x = round(x_dp * DENSITY)
    y = round(y_dp * DENSITY)
    adb("shell", "input", "tap", str(x), str(y))
    print(f"Tapped {label} at ({x_dp:.0f}dp, {y_dp:.0f}dp)", flush=True)
    wait()


def capture(name: str) -> None:
    wait(0.5)
    destination = OUT / f"{name}.png"
    with destination.open("wb") as screenshot:
        result = subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=screenshot,
                                stderr=subprocess.PIPE, timeout=45)
    if result.returncode or destination.stat().st_size < 10_000:
        raise RuntimeError(f"Could not capture {name}: {result.stderr.decode(errors='replace')}")
    print(f"Captured {name}", flush=True)


def tap_explorer_row(index: int, label: str) -> None:
    # MainActivity uses a fixed 38dp row height; these offsets include the status bar,
    # toolbar, project heading, project name, and ListView top padding.
    tap_dp(70, 191 + index * 38, label)


def tap_bottom_tab(index: int, label: str) -> None:
    # Five equal-width tabs; leave the Android system navigation strip clear.
    tap_dp(WIDTH_DP * (index + 0.5) / 5, HEIGHT_DP - 55, label)


def save_failure_diagnostics() -> None:
    for name, command in (
        ("activity.txt", ("shell", "dumpsys", "activity", "activities")),
        ("window.txt", ("shell", "dumpsys", "window", "windows")),
        ("logcat.txt", ("logcat", "-d", "-t", "500")),
    ):
        try:
            (OUT / name).write_text(adb(*command, timeout=45))
        except Exception as error:  # Keep the original failure while retaining all possible evidence.
            (OUT / name).write_text(f"Could not collect {name}: {error}")
    try:
        capture("failure-screen")
    except Exception as error:
        (OUT / "screenshot-error.txt").write_text(str(error))


def run() -> None:
    adb("wait-for-device", timeout=180)
    deadline = time.monotonic() + 180
    while adb("shell", "getprop", "sys.boot_completed").strip() != "1":
        if time.monotonic() >= deadline:
            raise RuntimeError("Android emulator did not finish booting")
        wait(2)

    # The runner reports boot_completed before FallbackHome and SystemUI have
    # finished first-run setup. Mark this disposable CI AVD provisioned so the
    # Settings placeholder does not stall or cover the app with a system ANR.
    adb("shell", "settings", "put", "global", "device_provisioned", "1")
    adb("shell", "settings", "put", "secure", "user_setup_complete", "1")
    adb("shell", "am", "force-stop", "com.android.settings")
    adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.HOME", timeout=90)
    wait(5)

    adb("shell", "input", "keyevent", "82")
    adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk", timeout=180)
    adb("shell", "pm", "clear", PACKAGE)
    launch = adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity", timeout=90)
    print(launch, flush=True)
    wait(2)
    activity_state = adb("shell", "dumpsys", "activity", "activities")
    assert PACKAGE in activity_state, "DevxyzIDE did not reach the foreground after explicit activity launch"

    capture("launch")

    # This app window draws behind the system navigation bar on the emulator.
    # Plus center = display bottom - 62dp nav - 8dp pane padding - 27dp row center.
    tap_dp(WIDTH_DP - 36, HEIGHT_DP - 97, "new project button")
    capture("new-project-dialog")
    tap_dp(WIDTH_DP / 2, HEIGHT_DP / 2, "project name field")
    adb("shell", "input", "text", "ReferenceApp")
    capture("project-name")
    adb("shell", "input", "keyevent", "4")  # Hide the keyboard while leaving the dialog open.
    tap_dp(WIDTH_DP * 0.80, HEIGHT_DP * 0.59, "create project confirmation")
    wait(1.2)
    created = adb("shell", "run-as", PACKAGE, "ls", "files/workspace/ReferenceApp/settings.gradle.kts")
    assert "settings.gradle.kts" in created, f"New Project did not create the Gradle root: {created}"
    capture("files")

    # The template auto-expands folders through java; expand the three package levels.
    tap_explorer_row(4, "com package")
    tap_explorer_row(5, "example package")
    tap_explorer_row(6, "referenceapp package")
    tap_explorer_row(7, "MainActivity.kt")
    capture("code")

    for index, screen in ((2, "build"), (3, "tools"), (4, "settings")):
        tap_bottom_tab(index, f"{screen} navigation button")
        capture(screen)

    # The starter template is separately assembled and signature-checked in unit tests.
    # Install and launch that genuine generated APK on this emulator as an end-to-end check.
    generated_apk = Path("app/build/template-smoke/generated-app.apk")
    assert generated_apk.is_file(), f"Generated project APK is missing: {generated_apk}"
    adb("install", "-r", str(generated_apk), timeout=180)
    adb("shell", "am", "start", "-W", "-n", "com.example.generatedsmokeapp/.MainActivity", timeout=60)
    assert "package:" in adb("shell", "pm", "path", "com.example.generatedsmokeapp"), "Generated project did not install"
    adb("shell", "am", "force-stop", "com.example.generatedsmokeapp")

    expected = {"launch.png", "new-project-dialog.png", "project-name.png", "files.png", "code.png",
                "build.png", "tools.png", "settings.png"}
    actual = {path.name for path in OUT.glob("*.png")}
    assert actual == expected, f"Screenshot set mismatch: expected {expected}, got {actual}"
    for path in sorted(OUT.glob("*.png")):
        assert path.stat().st_size > 10_000, f"Screenshot looks empty: {path}"
    print(f"Captured all five live DevxyzIDE screens in {OUT}", flush=True)


try:
    run()
except BaseException:
    save_failure_diagnostics()
    raise
