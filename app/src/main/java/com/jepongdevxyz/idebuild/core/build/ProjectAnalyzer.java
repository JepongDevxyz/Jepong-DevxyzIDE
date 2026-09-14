package com.jepongdevxyz.idebuild.core.build;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProjectAnalyzer {
    private static final long MAX_CONFIG_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_BUILD_FILES = 300;
    private static final int MAX_SOURCE_SCAN = 5000;

    private static final Pattern WRAPPER_VERSION = Pattern.compile("gradle-([0-9]+(?:\\.[0-9]+){1,3}(?:[-A-Za-z0-9.]*)?)-(?:bin|all)\\.zip");
    private static final Pattern AGP_CLASSPATH = Pattern.compile("com\\.android\\.tools\\.build:gradle:([0-9][A-Za-z0-9._+-]*)");
    private static final Pattern AGP_PLUGIN_GROOVY = Pattern.compile("id\\s+['\\\"]com\\.android\\.(?:application|library|test|dynamic-feature)['\\\"]\\s+version\\s+['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern AGP_PLUGIN_KTS = Pattern.compile("id\\s*\\(\\s*['\\\"]com\\.android\\.(?:application|library|test|dynamic-feature)['\\\"]\\s*\\)\\s*version\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern AGP_CLASSPATH_VARIABLE = Pattern.compile("com\\.android\\.tools\\.build:gradle:\\$\\{?([A-Za-z_][A-Za-z0-9_]*)\\}?");
    private static final Pattern COMPILE_SDK = Pattern.compile("(?m)\\bcompileSdk(?:Version)?\\s*(?:=\\s*)?([0-9]{1,3})\\b");
    private static final Pattern MIN_SDK = Pattern.compile("(?m)\\bminSdk(?:Version)?\\s*(?:=\\s*)?([0-9]{1,3})\\b");
    private static final Pattern TARGET_SDK = Pattern.compile("(?m)\\btargetSdk(?:Version)?\\s*(?:=\\s*)?([0-9]{1,3})\\b");
    private static final Pattern URL_DIRECT = Pattern.compile("(?i)\\burl\\s*(?:=\\s*)?(?:uri\\s*\\(\\s*)?['\\\"](https?://[^'\\\"]+)['\\\"]");
    private static final Pattern MAVEN_CALL = Pattern.compile("(?i)\\bmaven\\s*\\(\\s*['\\\"](https?://[^'\\\"]+)['\\\"]\\s*\\)");
    private static final Pattern SET_URL = Pattern.compile("(?i)\\bsetUrl\\s*\\(\\s*['\\\"](https?://[^'\\\"]+)['\\\"]\\s*\\)");
    private static final Pattern TOML_VERSION = Pattern.compile("(?m)^\\s*([A-Za-z0-9_.-]+)\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"]\\s*$");
    private static final Pattern TOML_PLUGIN_ANDROID = Pattern.compile("(?m)^\\s*[A-Za-z0-9_.-]+\\s*=\\s*\\{[^}]*id\\s*=\\s*['\\\"]com\\.android\\.(?:application|library|test|dynamic-feature)['\\\"][^}]*version\\.ref\\s*=\\s*['\\\"]([^'\\\"]+)['\\\"][^}]*}\\s*$");

    private ProjectAnalyzer() {}

    public static ProjectRequirements analyze(File root) throws IOException {
        if (root == null || !root.isDirectory()) throw new IOException("Project root is not a directory");
        File wrapperProps = new File(root, "gradle/wrapper/gradle-wrapper.properties");
        File wrapperJar = new File(root, "gradle/wrapper/gradle-wrapper.jar");
        File wrapperScript = new File(root, "gradlew");
        boolean wrapperComplete = wrapperProps.isFile() && wrapperJar.isFile() && wrapperScript.isFile();
        String gradleVersion = null;
        if (wrapperProps.isFile()) {
            Matcher matcher = WRAPPER_VERSION.matcher(readSmall(wrapperProps));
            if (matcher.find()) gradleVersion = matcher.group(1);
        }

        VersionCatalogInfo catalog = readVersionCatalog(root);
        List<File> configs = collectBuildConfigs(root);
        boolean kotlinDsl = false, androidX = false, androidProject = false, kotlin = false, compose = false, nativeBuild = false;
        String agpVersion = catalog.agpVersion;
        int compileSdk = catalog.compileSdk, minSdk = catalog.minSdk, targetSdk = catalog.targetSdk;
        LinkedHashSet<String> repositories = new LinkedHashSet<>();
        List<String> warnings = new ArrayList<>();

        File gradleProperties = new File(root, "gradle.properties");
        if (gradleProperties.isFile() && readSmall(gradleProperties).contains("android.useAndroidX=true")) androidX = true;

        for (File config : configs) {
            if (config.getName().endsWith(".kts")) kotlinDsl = true;
            String text = readSmall(config);
            if (text.contains("androidx.") || text.contains("android.useAndroidX=true")) androidX = true;
            if (text.contains("com.android.application") || text.contains("com.android.library") || text.contains("com.android.test") || text.contains("com.android.dynamic-feature") || text.contains("libs.plugins.android")) androidProject = true;
            if (text.contains("org.jetbrains.kotlin") || text.contains("kotlin(\"android\")") || text.contains("kotlin('android')")) kotlin = true;
            if (text.contains("androidx.compose") || text.contains("org.jetbrains.kotlin.plugin.compose") || Pattern.compile("(?s)buildFeatures\\s*\\{[^}]*compose\\s*(?:=\\s*)?true").matcher(text).find()) compose = true;
            if (text.contains("externalNativeBuild") || text.contains("ndkVersion") || text.contains("cmake")) nativeBuild = true;
            if (agpVersion == null) {
                agpVersion = firstGroup(text, AGP_CLASSPATH, AGP_PLUGIN_GROOVY, AGP_PLUGIN_KTS);
                if (agpVersion == null) agpVersion = resolveAgpVariable(text);
            }
            compileSdk = maxPositive(compileSdk, firstInt(text, COMPILE_SDK));
            minSdk = chooseMinimumPositive(minSdk, firstInt(text, MIN_SDK));
            targetSdk = maxPositive(targetSdk, firstInt(text, TARGET_SDK));
            scanRepositories(text, repositories);
        }

        SourceIndicators indicators = scanSourceIndicators(root);
        kotlin |= indicators.kotlin;
        nativeBuild |= indicators.nativeBuild;
        if (agpVersion != null || compileSdk > 0) androidProject = true;
        if (gradleVersion == null) warnings.add("Gradle Wrapper version could not be detected.");
        if (!wrapperComplete) warnings.add("Gradle Wrapper is incomplete; DevxyzIDE can use an installed internal Gradle runtime instead.");
        if (androidProject && compileSdk <= 0) warnings.add("Android project detected but compileSdk could not be parsed statically.");
        if (nativeBuild) warnings.add("Native build files detected; a matching Android NDK/CMake toolchain may be required.");

        return new ProjectRequirements(gradleVersion, agpVersion, compileSdk, minSdk, targetSdk, androidProject, androidX, kotlinDsl, wrapperComplete, catalog.present, kotlin, compose, nativeBuild, new ArrayList<>(repositories), warnings);
    }

    private static VersionCatalogInfo readVersionCatalog(File root) throws IOException {
        File catalog = new File(root, "gradle/libs.versions.toml");
        if (!catalog.isFile()) return VersionCatalogInfo.NONE;
        String text = readSmall(catalog);
        LinkedHashMap<String, String> versions = new LinkedHashMap<>();
        boolean inVersions = false;
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.startsWith("[") && t.endsWith("]")) { inVersions = "[versions]".equalsIgnoreCase(t); continue; }
            if (!inVersions) continue;
            Matcher m = TOML_VERSION.matcher(line);
            if (m.find()) versions.put(m.group(1), m.group(2));
        }
        String agp = null;
        Matcher plugin = TOML_PLUGIN_ANDROID.matcher(text);
        if (plugin.find()) agp = versions.get(plugin.group(1));
        if (agp == null) agp = firstVersionLike(versions, "agp", "androidGradlePlugin", "android-gradle-plugin");
        return new VersionCatalogInfo(true, agp,
                intVersionLike(versions, "compileSdk", "compile-sdk", "androidCompileSdk"),
                intVersionLike(versions, "minSdk", "min-sdk", "androidMinSdk"),
                intVersionLike(versions, "targetSdk", "target-sdk", "androidTargetSdk"));
    }

    private static String firstVersionLike(Map<String, String> versions, String... keys) {
        for (String key : keys) { String value = versions.get(key); if (value != null && !value.trim().isEmpty()) return value.trim(); }
        return null;
    }

    private static int intVersionLike(Map<String, String> versions, String... keys) {
        String value = firstVersionLike(versions, keys);
        if (value == null) return 0;
        try { return Integer.parseInt(value.replaceAll("[^0-9].*$", "")); } catch (Exception ignored) { return 0; }
    }

    private static List<File> collectBuildConfigs(File root) {
        ArrayList<File> out = new ArrayList<>();
        Deque<File> queue = new ArrayDeque<>(); queue.add(root);
        while (!queue.isEmpty() && out.size() < MAX_BUILD_FILES) {
            File dir = queue.removeFirst(); File[] children = dir.listFiles(); if (children == null) continue;
            Arrays.sort(children, new Comparator<File>() { @Override public int compare(File a, File b) { return a.getName().compareTo(b.getName()); } });
            for (File child : children) {
                if (child.isDirectory()) { if (!skipDir(child.getName())) queue.addLast(child); }
                else if (isBuildConfig(child.getName())) { out.add(child); if (out.size() >= MAX_BUILD_FILES) break; }
            }
        }
        return out;
    }

    private static SourceIndicators scanSourceIndicators(File root) {
        boolean kotlin = false, nativeBuild = false; int seen = 0;
        Deque<File> queue = new ArrayDeque<>(); queue.add(root);
        while (!queue.isEmpty() && seen < MAX_SOURCE_SCAN && !(kotlin && nativeBuild)) {
            File dir = queue.removeFirst(); File[] children = dir.listFiles(); if (children == null) continue;
            for (File child : children) {
                if (child.isDirectory()) { if (!skipDir(child.getName())) queue.addLast(child); }
                else {
                    seen++; String n = child.getName();
                    if (n.endsWith(".kt") && !n.endsWith(".gradle.kts")) kotlin = true;
                    if (n.equals("CMakeLists.txt") || n.equals("Android.mk") || n.equals("Application.mk") || n.endsWith(".cpp") || n.endsWith(".c") || n.endsWith(".cc")) nativeBuild = true;
                }
            }
        }
        return new SourceIndicators(kotlin, nativeBuild);
    }

    private static boolean isBuildConfig(String name) { return name.equals("build.gradle") || name.equals("build.gradle.kts") || name.equals("settings.gradle") || name.equals("settings.gradle.kts"); }
    private static boolean skipDir(String name) { return name.equals("build") || name.equals(".gradle") || name.equals(".git") || name.equals(".idea") || name.equals(".cxx") || name.equals(".externalNativeBuild"); }

    private static String readSmall(File file) throws IOException {
        if (file.length() > MAX_CONFIG_BYTES) throw new IOException("Build config too large to analyze: " + file.getName());
        byte[] bytes = Files.readAllBytes(file.toPath());
        if (bytes.length > MAX_CONFIG_BYTES) throw new IOException("Build config too large to analyze: " + file.getName());
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String resolveAgpVariable(String text) {
        Matcher use = AGP_CLASSPATH_VARIABLE.matcher(text); if (!use.find()) return null;
        String variable = use.group(1);
        Pattern assignment = Pattern.compile("(?m)(?:\\bext\\.|\\bval\\s+|\\bvar\\s+|\\b)" + Pattern.quote(variable) + "\\s*=\\s*['\\x22]([0-9][A-Za-z0-9._+-]*)['\\x22]");
        Matcher value = assignment.matcher(text); return value.find() ? value.group(1) : null;
    }

    private static String firstGroup(String text, Pattern... patterns) { for (Pattern pattern : patterns) { Matcher matcher = pattern.matcher(text); if (matcher.find()) return matcher.group(1); } return null; }
    private static int firstInt(String text, Pattern pattern) { Matcher matcher = pattern.matcher(text); if (!matcher.find()) return 0; try { return Integer.parseInt(matcher.group(1)); } catch (NumberFormatException ignored) { return 0; } }
    private static int maxPositive(int current, int candidate) { return candidate > current ? candidate : current; }
    private static int chooseMinimumPositive(int current, int candidate) { if (candidate <= 0) return current; if (current <= 0) return candidate; return Math.min(current, candidate); }

    private static void scanRepositories(String text, LinkedHashSet<String> repositories) {
        if (Pattern.compile("\\bgoogle\\s*\\(").matcher(text).find()) repositories.add("google()");
        if (Pattern.compile("\\bmavenCentral\\s*\\(").matcher(text).find()) repositories.add("mavenCentral()");
        if (Pattern.compile("\\bmavenLocal\\s*\\(").matcher(text).find()) repositories.add("mavenLocal()");
        if (Pattern.compile("\\bgradlePluginPortal\\s*\\(").matcher(text).find()) repositories.add("gradlePluginPortal()");
        addSanitizedUrls(text, URL_DIRECT, repositories); addSanitizedUrls(text, MAVEN_CALL, repositories); addSanitizedUrls(text, SET_URL, repositories);
    }

    private static void addSanitizedUrls(String text, Pattern pattern, LinkedHashSet<String> repositories) { Matcher matcher = pattern.matcher(text); while (matcher.find()) repositories.add(sanitizeRepositoryUrl(matcher.group(1))); }

    static String sanitizeRepositoryUrl(String raw) {
        if (raw == null) return "";
        try {
            URI uri = URI.create(raw); StringBuilder safe = new StringBuilder();
            if (uri.getScheme() != null) safe.append(uri.getScheme()).append("://");
            if (uri.getHost() != null) { safe.append(uri.getHost()); if (uri.getPort() >= 0) safe.append(':').append(uri.getPort()); }
            else if (uri.getRawAuthority() != null) safe.append(uri.getRawAuthority().replaceFirst("^.*@", ""));
            if (uri.getRawPath() != null) safe.append(uri.getRawPath());
            String result = safe.toString(); if (!result.isEmpty()) return result;
        } catch (Exception ignored) {}
        return raw.replaceFirst("(?i)(https?://)[^/@]+@", "$1").replaceAll("(?i)(password|passwd|token|key)=[^&\\s]+", "$1=REDACTED");
    }

    private static final class VersionCatalogInfo {
        static final VersionCatalogInfo NONE = new VersionCatalogInfo(false, null, 0, 0, 0);
        final boolean present; final String agpVersion; final int compileSdk; final int minSdk; final int targetSdk;
        VersionCatalogInfo(boolean present, String agpVersion, int compileSdk, int minSdk, int targetSdk) { this.present = present; this.agpVersion = agpVersion; this.compileSdk = compileSdk; this.minSdk = minSdk; this.targetSdk = targetSdk; }
    }
    private static final class SourceIndicators {
        final boolean kotlin; final boolean nativeBuild;
        SourceIndicators(boolean kotlin, boolean nativeBuild) { this.kotlin = kotlin; this.nativeBuild = nativeBuild; }
    }
}
