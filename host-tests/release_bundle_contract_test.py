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
require("SIGNED_APK=" in workflows, "Android device gate must select the signed APK explicitly")
require("adb install -r \"$SIGNED_APK\"" in workflows, "Android device gate must install the exact signed APK delivered to users")
require("api-level: 29" in workflows, "User-delivery APK must be tested on the configured API 29 emulator")

print("RELEASE BUNDLE CONTRACT TESTS PASSED")
