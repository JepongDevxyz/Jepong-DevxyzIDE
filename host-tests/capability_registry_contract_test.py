from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def require(condition, message):
    if not condition:
        raise AssertionError(message)


status = read("app/src/main/java/com/jepongdevxyz/idebuild/core/capability/CapabilityStatus.java")
require("AVAILABLE" in status, "CapabilityStatus must include AVAILABLE")
require("NEEDS_INSTALL" in status, "CapabilityStatus must include NEEDS_INSTALL")
require("EXPERIMENTAL" in status, "CapabilityStatus must include EXPERIMENTAL")
require("UNSUPPORTED" in status, "CapabilityStatus must include UNSUPPORTED")
require("UNAVAILABLE" in status, "CapabilityStatus must include UNAVAILABLE")
require("isActionable()" in status, "CapabilityStatus must expose actionable-state policy")

capability = read("app/src/main/java/com/jepongdevxyz/idebuild/core/capability/Capability.java")
require("private final String id;" in capability, "Capability must have a stable id")
require("private final CapabilityStatus status;" in capability, "Capability must carry centralized status")
require("isEnabled()" in capability, "Capability must expose button enabled policy")
require("getUserMessage()" in capability, "Capability must expose a user-facing message")

registry = read("app/src/main/java/com/jepongdevxyz/idebuild/core/capability/CapabilityRegistry.java")
require("apkSigning(" in registry, "CapabilityRegistry must centralize APK signing state")
require("gradleBuild(" in registry, "CapabilityRegistry must centralize Gradle build state")
require("terminal(" in registry, "CapabilityRegistry must centralize terminal state")
require("git(" in registry, "CapabilityRegistry must centralize Git state")
require("RuntimeLayout.findApksigner" in registry, "APK signing capability must use real signer detection")
require("ProjectAnalyzer.analyze" in registry, "Gradle capability must analyze the actual project requirements")
require("BuildPlanner.planBuild" in registry, "Gradle capability must use the same real toolchain preflight as build execution")
require("getBlockers()" in registry, "Gradle capability must expose concrete JDK/SDK/Gradle/build-tools blockers")
require("TerminalCommandPlanner.describeShell" in registry, "Terminal capability must use real shell detection")
require("GitService.isGitAvailable" in registry, "Git capability must use real Git detection")
require("NEEDS_INSTALL" in registry, "Registry must report install-needed states")
require("UNAVAILABLE" in registry, "Registry must report unavailable states")

signing_activity = read("app/src/main/java/com/jepongdevxyz/idebuild/ApkSigningActivity.java")
require("CapabilityRegistry.apkSigning" in signing_activity, "APK signing UI must derive from CapabilityRegistry")
require("CapabilityStatus.AVAILABLE" in signing_activity, "APK signing UI must check centralized status")

build_actions = read("app/src/main/java/com/jepongdevxyz/idebuild/BuildActionsButton.java")
require("CapabilityRegistry.gradleBuild" in build_actions, "Build actions UI must derive from CapabilityRegistry")
require("getUserMessage()" in build_actions, "Build actions UI must show registry reason when disabled")

terminal_button = read("app/src/main/java/com/jepongdevxyz/idebuild/TerminalButton.java")
require("CapabilityRegistry.terminal" in terminal_button, "Terminal UI must derive from CapabilityRegistry")
require("CapabilityStatus.UNAVAILABLE" in terminal_button, "Terminal UI must honor unavailable shell state")

git_button = read("app/src/main/java/com/jepongdevxyz/idebuild/GitButton.java")
require("CapabilityRegistry.git" in git_button, "Git UI must derive from CapabilityRegistry")
require("CapabilityStatus.NEEDS_INSTALL" in git_button, "Git UI must honor missing Git state")

run_sh = read("host-tests/run.sh")
require("capability_registry_contract_test.py" in run_sh, "Host test runner must include capability registry contract")

print("CAPABILITY REGISTRY CONTRACT TESTS PASSED")
