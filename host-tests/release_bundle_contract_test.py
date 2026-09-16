from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
workflow = (ROOT / ".github/workflows/android-full-verification.yml").read_text(encoding="utf-8")
capabilities = ROOT / "docs/CAPABILITY_MATRIX.md"
report = ROOT / "docs/VERIFICATION_REPORT.md"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


require(capabilities.is_file(), "Release capability matrix is missing")
require(report.is_file(), "Release verification report is missing")
require("release_bundle:" in workflow, "Full verification workflow must have a verified-source packaging job")
require("needs: [emulator]" in workflow, "Source packaging must be gated by emulator verification")
require("DevxyzIDE-verified-source.zip" in workflow, "Workflow must produce the named verified source ZIP")
require("VERIFICATION_METADATA.txt" in workflow, "Verified source bundle must contain exact CI metadata")
require("unzip -t" in workflow, "Verified source ZIP must receive an integrity check")
require("sha256sum" in workflow, "Verified source ZIP must publish a SHA-256 checksum")
require("actions/upload-artifact@v4" in workflow, "Verified source ZIP must be uploaded as an Actions artifact")
require("app-ci-signed.apk" in workflow, "Workflow must produce a separately signed install APK")
require("SIGNED_APK=" in workflow, "Emulator gate must select the signed APK explicitly")
require("adb install -r \"$SIGNED_APK\"" in workflow, "Emulator gate must install the exact signed APK delivered to users")

print("RELEASE BUNDLE CONTRACT TESTS PASSED")
