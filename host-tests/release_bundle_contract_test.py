from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
workflow_dir = ROOT / ".github/workflows"
workflows = "\n".join(p.read_text(encoding="utf-8") for p in sorted(workflow_dir.glob("*.yml")))
capabilities = ROOT / "docs/CAPABILITY_MATRIX.md"
report = ROOT / "docs/VERIFICATION_REPORT.md"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


require(capabilities.is_file(), "Release capability matrix is missing")
require(report.is_file(), "Release verification report is missing")
require("release_bundle:" in workflows, "Full verification workflow must have a verified-source packaging job")
require("needs: [emulator]" in workflows, "Source packaging must be gated by emulator verification")
require("DevxyzIDE-verified-source.zip" in workflows, "Workflow must produce the named verified source ZIP")
require("VERIFICATION_METADATA.txt" in workflows, "Verified source bundle must contain exact CI metadata")
require("unzip -t" in workflows, "Verified source ZIP must receive an integrity check")
require("sha256sum" in workflows, "Verified source ZIP must publish a SHA-256 checksum")
require("actions/upload-artifact@v4" in workflows, "Verified source ZIP must be uploaded as an Actions artifact")
require("DevxyzIDE-v0.6.1-installable.apk" in workflows, "Workflow must produce the exact signed user-delivery APK")
require("apksigner\" sign" in workflows and "--out app/build/outputs/apk/debug/DevxyzIDE-v0.6.1-installable.apk" in workflows,
        "Signing gate must write the exact user-delivery APK explicitly")
require("adb install ./DevxyzIDE-v0.6.1-installable.apk" in workflows, "Android device gate must install the exact flattened signed APK delivered to users")
require("script: ./verify-device.sh" in workflows, "Android device gate must execute runtime assertions atomically in one shell process")
require("api-level: 28" in workflows, "Signed APK must pass the repository's proven emulator runtime gate")
require("targetSdkVersion:'29'" in workflows, "Release gate must preserve targetSdk 29 metadata verification")
require("DevxyzIDE-v0.6.1-signed-candidate" in workflows, "Signed candidate must be preserved before the runtime gate")
require("device-apk-signing.txt" in workflows, "Release gate must preserve APK signing verification evidence")
require("install_apk()" in workflows, "Full emulator gate must use the bounded APK install helper")
require("timeout 120 adb install -r" in workflows, "Emulator APK installs must have a hard timeout instead of hanging the entire job")
require("adb kill-server" in workflows and "adb start-server" in workflows, "Timed-out emulator installs must recover the ADB server before retrying")

print("RELEASE BUNDLE CONTRACT TESTS PASSED")