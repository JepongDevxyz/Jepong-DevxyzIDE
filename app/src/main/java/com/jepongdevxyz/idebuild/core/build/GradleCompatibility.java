package com.jepongdevxyz.idebuild.core.build;

/** Minimal Android Gradle Plugin -> Gradle compatibility mapping used for internal runtime selection. */
public final class GradleCompatibility {
    private GradleCompatibility() {}

    public static String minimumGradleForAgp(String agpVersion) {
        Version v = Version.parse(agpVersion);
        if (v == null) return null;

        if (v.major >= 9) {
            if (v.major > 9 || v.minor >= 4) return "9.6.0";
            if (v.minor == 3) return "9.5.0";
            if (v.minor == 2) return "9.4.1";
            if (v.minor == 1) return "9.3.1";
            return "9.1.0";
        }
        if (v.major == 8) {
            if (v.minor >= 13) return "8.13";
            if (v.minor >= 11) return "8.13";
            if (v.minor == 10) return "8.11.1";
            if (v.minor == 9) return "8.11.1";
            if (v.minor == 8) return "8.10.2";
            if (v.minor == 7) return "8.9";
            if (v.minor == 6) return "8.7";
            if (v.minor == 5) return "8.7";
            if (v.minor == 4) return "8.6";
            if (v.minor == 3) return "8.4";
            if (v.minor == 2) return "8.2";
            return "8.0";
        }
        if (v.major == 7) {
            if (v.minor >= 4) return "7.5";
            if (v.minor == 3) return "7.4";
            if (v.minor == 2) return "7.3.3";
            if (v.minor == 1) return "7.2";
            return "7.0";
        }
        if (v.major == 4) {
            if (v.minor >= 2) return "6.7.1";
            if (v.minor == 1) return "6.5";
            return "6.1.1";
        }
        if (v.major == 3) {
            if (v.minor >= 6) return "5.6.4";
            if (v.minor == 5) return "5.4.1";
            if (v.minor == 4) return "5.1.1";
            if (v.minor == 3) return "4.10.1";
            if (v.minor == 2) return "4.6";
        }
        return null;
    }

    public static boolean isAtLeast(String actual, String required) {
        Version a = Version.parse(actual);
        Version r = Version.parse(required);
        if (a == null || r == null) return false;
        return a.compareTo(r) >= 0;
    }

    private static final class Version implements Comparable<Version> {
        final int major;
        final int minor;
        final int patch;

        Version(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        static Version parse(String text) {
            if (text == null || text.trim().length() == 0) return null;
            String[] parts = text.trim().split("[.-]");
            try {
                int major = parsePart(parts, 0);
                int minor = parsePart(parts, 1);
                int patch = parsePart(parts, 2);
                return new Version(major, minor, patch);
            } catch (Exception ignored) {
                return null;
            }
        }

        private static int parsePart(String[] parts, int index) {
            if (index >= parts.length) return 0;
            String value = parts[index].replaceFirst("[^0-9].*$", "");
            return value.length() == 0 ? 0 : Integer.parseInt(value);
        }

        public int compareTo(Version other) {
            if (major != other.major) return major < other.major ? -1 : 1;
            if (minor != other.minor) return minor < other.minor ? -1 : 1;
            if (patch != other.patch) return patch < other.patch ? -1 : 1;
            return 0;
        }
    }
}
