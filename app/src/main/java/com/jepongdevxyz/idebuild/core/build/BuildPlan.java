package com.jepongdevxyz.idebuild.core.build;

import java.io.File;
import java.util.*;

public final class BuildPlan {
    private final int javaMajor;
    private final File javaHome;
    private final File androidSdk;
    private final File aapt2;
    private final List<String> arguments;
    private final Map<String, String> environment;
    private final List<String> blockers;
    private final List<String> warnings;

    BuildPlan(int javaMajor, File javaHome, File androidSdk, File aapt2,
              List<String> arguments, Map<String, String> environment,
              List<String> blockers, List<String> warnings) {
        this.javaMajor = javaMajor;
        this.javaHome = javaHome;
        this.androidSdk = androidSdk;
        this.aapt2 = aapt2;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.environment = Collections.unmodifiableMap(new LinkedHashMap<>(environment));
        this.blockers = Collections.unmodifiableList(new ArrayList<>(blockers));
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public int getJavaMajor() { return javaMajor; }
    public File getJavaHome() { return javaHome; }
    public File getAndroidSdk() { return androidSdk; }
    public File getAapt2() { return aapt2; }
    public List<String> getArguments() { return arguments; }
    public Map<String, String> getEnvironment() { return environment; }
    public List<String> getBlockers() { return blockers; }
    public List<String> getWarnings() { return warnings; }
    public boolean canBuild() { return blockers.isEmpty(); }
}
