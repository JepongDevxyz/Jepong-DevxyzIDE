package com.jepongdevxyz.idebuild.core.toolchain;

import com.jepongdevxyz.idebuild.core.build.JvmCompatibility;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Describes missing app-private runtime components before a Gradle build starts. */
public final class ToolchainProvisioningPlan {
    private final List<String> requiredComponents;
    private final List<String> recommendedComponents;

    private ToolchainProvisioningPlan(List<String> required, List<String> recommended) {
        this.requiredComponents = Collections.unmodifiableList(new ArrayList<>(required));
        this.recommendedComponents = Collections.unmodifiableList(new ArrayList<>(recommended));
    }

    public static ToolchainProvisioningPlan create(ProjectRequirements req, File appFilesDir) {
        if (req == null) throw new IllegalArgumentException("req == null");
        if (appFilesDir == null) throw new IllegalArgumentException("appFilesDir == null");

        List<String> required = new ArrayList<>();
        List<String> recommended = new ArrayList<>();
        ToolchainInventory inventory = ToolchainInventory.scan(appFilesDir);

        int selected = JvmCompatibility.chooseInstalledJavaMajor(appFilesDir, req.getAgpVersion(), req.getGradleVersion());
        if (selected <= 0) {
            int preferred = JvmCompatibility.recommendedJavaMajor(req.getAgpVersion(), req.getGradleVersion());
            required.add("jdk" + preferred + "-compatible");
        }

        if (req.isAndroidProject()) {
            if (req.getCompileSdk() > 0 && !inventory.hasAndroidPlatform(req.getCompileSdk())) {
                required.add("android-platform-" + req.getCompileSdk());
            } else if (req.getCompileSdk() <= 0 && inventory.getAndroidPlatforms().isEmpty()) {
                required.add("android-sdk-platform");
            }
            if (!inventory.hasAapt2()) required.add("android-aapt2");

            if (req.usesNativeBuild()) {
                if (!inventory.hasNdk()) required.add("android-ndk");
                if (!inventory.hasCmake()) recommended.add("cmake");
            }
        }

        return new ToolchainProvisioningPlan(required, recommended);
    }

    public List<String> getRequiredComponents() { return requiredComponents; }
    public List<String> getRecommendedComponents() { return recommendedComponents; }
    public boolean isReady() { return requiredComponents.isEmpty(); }

    public String summary() {
        return "required=" + requiredComponents + ", recommended=" + recommendedComponents;
    }
}
