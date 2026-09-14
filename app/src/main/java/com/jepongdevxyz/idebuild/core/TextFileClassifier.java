package com.jepongdevxyz.idebuild.core;

import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

public final class TextFileClassifier {
    private static final Set<String> EXTENSIONS = new HashSet<>(Arrays.asList(
            "java", "kt", "kts", "xml", "gradle", "properties", "pro", "txt", "md",
            "json", "yaml", "yml", "toml", "html", "htm", "css", "js", "ts", "tsx",
            "jsx", "c", "cc", "cpp", "h", "hpp", "cmake", "sh", "bat", "cmd", "py",
            "go", "rs", "sql", "csv", "gitignore", "editorconfig"
    ));

    private TextFileClassifier() {}

    public static boolean isTextFile(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        String simple = lower.substring(lower.lastIndexOf('/') + 1);
        if (simple.equals("gradlew") || simple.equals("settings.gradle") || simple.equals("build.gradle") ||
                simple.equals("gradle.properties") || simple.equals("local.properties") ||
                simple.equals("androidmanifest.xml") || simple.equals("proguard-rules.pro")) return true;
        int dot = simple.lastIndexOf('.');
        if (dot < 0 || dot == simple.length() - 1) return false;
        return EXTENSIONS.contains(simple.substring(dot + 1));
    }
}
