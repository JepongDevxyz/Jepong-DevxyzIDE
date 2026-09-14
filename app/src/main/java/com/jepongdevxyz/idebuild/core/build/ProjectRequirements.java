package com.jepongdevxyz.idebuild.core.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ProjectRequirements {
    private final String gradleVersion;
    private final String agpVersion;
    private final int compileSdk;
    private final int minSdk;
    private final int targetSdk;
    private final boolean androidProject;
    private final boolean androidX;
    private final boolean kotlinDsl;
    private final boolean wrapperComplete;
    private final boolean versionCatalog;
    private final boolean kotlin;
    private final boolean compose;
    private final boolean nativeBuild;
    private final List<String> repositories;
    private final List<String> warnings;

    ProjectRequirements(String gradleVersion, String agpVersion, int compileSdk, int minSdk, int targetSdk,
            boolean androidProject, boolean androidX, boolean kotlinDsl, boolean wrapperComplete,
            boolean versionCatalog, boolean kotlin, boolean compose, boolean nativeBuild,
            List<String> repositories, List<String> warnings) {
        this.gradleVersion = gradleVersion;
        this.agpVersion = agpVersion;
        this.compileSdk = compileSdk;
        this.minSdk = minSdk;
        this.targetSdk = targetSdk;
        this.androidProject = androidProject;
        this.androidX = androidX;
        this.kotlinDsl = kotlinDsl;
        this.wrapperComplete = wrapperComplete;
        this.versionCatalog = versionCatalog;
        this.kotlin = kotlin;
        this.compose = compose;
        this.nativeBuild = nativeBuild;
        this.repositories = Collections.unmodifiableList(new ArrayList<>(repositories));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public String getGradleVersion() { return gradleVersion; }
    public String getAgpVersion() { return agpVersion; }
    public int getCompileSdk() { return compileSdk; }
    public int getMinSdk() { return minSdk; }
    public int getTargetSdk() { return targetSdk; }
    public boolean isAndroidProject() { return androidProject; }
    public boolean usesAndroidX() { return androidX; }
    public boolean usesKotlinDsl() { return kotlinDsl; }
    public boolean isWrapperComplete() { return wrapperComplete; }
    public boolean usesVersionCatalog() { return versionCatalog; }
    public boolean usesKotlin() { return kotlin; }
    public boolean usesCompose() { return compose; }
    public boolean usesNativeBuild() { return nativeBuild; }
    public List<String> getRepositories() { return repositories; }
    public List<String> getWarnings() { return warnings; }

    public String summary() {
        return "Gradle=" + value(gradleVersion) + ", AGP=" + value(agpVersion) +
                ", compileSdk=" + number(compileSdk) + ", minSdk=" + number(minSdk) +
                ", targetSdk=" + number(targetSdk) + ", AndroidX=" + androidX +
                ", Kotlin=" + kotlin + ", Compose=" + compose + ", Native=" + nativeBuild +
                ", KotlinDSL=" + kotlinDsl + ", VersionCatalog=" + versionCatalog;
    }

    private static String value(String text) { return text == null || text.isEmpty() ? "unknown" : text; }
    private static String number(int value) { return value > 0 ? Integer.toString(value) : "unknown"; }
}
