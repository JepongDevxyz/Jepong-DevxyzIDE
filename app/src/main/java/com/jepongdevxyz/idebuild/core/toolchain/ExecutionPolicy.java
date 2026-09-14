package com.jepongdevxyz.idebuild.core.toolchain;

/**
 * Host-app execution policy for DevxyzIDE.
 *
 * DevxyzIDE executes Gradle/JDK/toolchain binaries from its private writable
 * directory. Android's target-29+ W^X behavior blocks that execution model,
 * therefore the IDE host intentionally targets API 28 while still compiling
 * against a current SDK. This does not constrain targetSdk of user projects.
 */
public final class ExecutionPolicy {
    public static final int REQUIRED_HOST_TARGET_SDK = 28;

    private ExecutionPolicy() {}

    public static boolean canExecuteAppPrivateToolchains(int hostTargetSdk) {
        return hostTargetSdk > 0 && hostTargetSdk <= REQUIRED_HOST_TARGET_SDK;
    }
}
