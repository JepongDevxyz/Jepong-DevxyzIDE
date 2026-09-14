from pathlib import Path

root = Path(__file__).resolve().parents[1]
script = (root / "runtime-builder" / "build-devxyz-terminal-runtime.sh").read_text(encoding="utf-8")

assert 'DEVXYZ_APPLICATION_ID="com.jepongdevxyz.idebuild"' in script, "custom application id missing"
assert 'UPSTREAM_COMMIT="610af608b4a3b1127244e90edf8b7be2bf94fafa"' in script, "upstream runtime source must stay pinned"
assert '"$SRC/build.sh"' in script and '"aapt"' in script, "custom-prefix native aapt/aapt2 package must be built"
assert 'BOOTSTRAP_PACKAGES' in script and '"aapt2"' in script, "native aapt2 must be included in bootstrap"
assert '-p "$DEVXYZ_APPLICATION_ID"' in script, "package build must use DevxyzIDE prefix"

print("RUNTIME BUILDER CONTRACT PASS")
