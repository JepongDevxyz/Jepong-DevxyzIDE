package com.jepongdevxyz.idebuild.core.build;

import com.jepongdevxyz.idebuild.core.ProjectPath;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses common Java, AAPT2 and Kotlin build diagnostics into navigable problems. */
public final class BuildDiagnosticsParser {
    private static final Pattern JAVA_OR_AAPT = Pattern.compile(
            "^(.+):(\\d+)(?::(\\d+)(?:-\\d+)?)?:\\s*(error|warning):\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern KOTLIN = Pattern.compile(
            "^([ew]):\\s+(.+):\\s*\\((\\d+),\\s*(\\d+)\\):\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);

    private BuildDiagnosticsParser() { }

    public static BuildProblem parseLine(String line, File projectRoot, String backendId) throws IOException {
        if (line == null || line.length() == 0) return null;
        if (projectRoot == null || !projectRoot.isDirectory()) return null;
        if (backendId == null || backendId.trim().length() == 0) return null;

        Matcher kotlin = KOTLIN.matcher(line);
        if (kotlin.matches()) {
            ProjectPath path = toProjectPath(kotlin.group(2), projectRoot, backendId);
            if (path == null) return null;
            BuildProblem.Severity severity = "w".equalsIgnoreCase(kotlin.group(1))
                    ? BuildProblem.Severity.WARNING
                    : BuildProblem.Severity.ERROR;
            return new BuildProblem(
                    severity,
                    path,
                    parsePositive(kotlin.group(3), 1),
                    parsePositive(kotlin.group(4), 1),
                    kotlin.group(5).trim(),
                    line);
        }

        Matcher general = JAVA_OR_AAPT.matcher(line);
        if (general.matches()) {
            ProjectPath path = toProjectPath(general.group(1), projectRoot, backendId);
            if (path == null) return null;
            BuildProblem.Severity severity = "warning".equalsIgnoreCase(general.group(4))
                    ? BuildProblem.Severity.WARNING
                    : BuildProblem.Severity.ERROR;
            return new BuildProblem(
                    severity,
                    path,
                    parsePositive(general.group(2), 1),
                    parsePositive(general.group(3), 1),
                    general.group(5).trim(),
                    line);
        }

        return null;
    }

    private static ProjectPath toProjectPath(String pathText, File projectRoot, String backendId) throws IOException {
        if (pathText == null || pathText.trim().length() == 0) return null;
        File canonicalRoot = projectRoot.getCanonicalFile();
        File candidate = new File(pathText.trim());
        if (!candidate.isAbsolute()) candidate = new File(canonicalRoot, pathText.trim());
        File canonicalFile = candidate.getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        String filePath = canonicalFile.getPath();
        String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        if (!filePath.startsWith(prefix)) return null;
        String relative = filePath.substring(prefix.length()).replace(File.separatorChar, '/');
        if (relative.length() == 0) return null;
        return ProjectPath.of(backendId, relative);
    }

    private static int parsePositive(String value, int fallback) {
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return parsed < 1 ? fallback : parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
