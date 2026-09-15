from pathlib import Path

root = Path(__file__).resolve().parents[1]
script = (root / "runtime-builder" / "build-devxyz-terminal-runtime.sh").read_text(encoding="utf-8")
bootstrap = (root / "tools" / "runtime-builder" / "build-bootstrap.sh").read_text(encoding="utf-8")

assert 'DEVXYZ_APPLICATION_ID="com.jepongdevxyz.idebuild"' in script, "custom application id missing"
assert 'UPSTREAM_COMMIT="610af608b4a3b1127244e90edf8b7be2bf94fafa"' in script, "upstream runtime source must stay pinned"
assert '"$SRC/build.sh"' in script and '"aapt"' in script, "custom-prefix native aapt/aapt2 package must be built"
assert 'BOOTSTRAP_PACKAGES' in script and '"aapt2"' in script, "native aapt2 must be included in bootstrap"
assert '-p "$DEVXYZ_APPLICATION_ID"' in script, "package build must use DevxyzIDE prefix"

# The CI bootstrap must build only the runtime capabilities DevxyzIDE needs.
# Building the entire terminal package set pulls unrelated packages and makes
# reproducibility depend on third-party source archives DevxyzIDE never uses.
assert './build.sh -a aarch64 -e openjdk-21 aapt' in bootstrap, "ARM64 bootstrap must explicitly build only JDK21 + aapt dependency closures"
assert 'PACKAGES="openjdk-21,aapt2"' in bootstrap, "bootstrap root package set must stay minimal"

print("RUNTIME BUILDER CONTRACT PASS")
