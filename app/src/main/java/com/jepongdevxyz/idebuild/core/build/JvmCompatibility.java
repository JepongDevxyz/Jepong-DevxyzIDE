package com.jepongdevxyz.idebuild.core.build;

import com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout;
import java.io.File;

public final class JvmCompatibility {
    private static final int[] CANDIDATES = {11, 17, 21, 25, 26};

    private JvmCompatibility() {}

    public static int recommendedJavaMajor(String agpVersion, String gradleVersion) {
        int agpMajor = major(agpVersion);
        if (agpMajor >= 8) return 17;
        if (agpMajor == 7) return 11;
        int gradleMajor = major(gradleVersion);
        if (gradleMajor >= 9) return 17;
        if (gradleMajor >= 7) return 17;
        return 11;
    }

    public static int minimumJavaMajorForAgp(String agpVersion) {
        int agpMajor = major(agpVersion);
        if (agpMajor >= 8) return 17;
        if (agpMajor == 7) return 11;
        return 8;
    }

    public static int chooseInstalledJavaMajor(File appFilesDir, String agpVersion, String gradleVersion) {
        if (appFilesDir == null) return 0;
        int preferred = recommendedJavaMajor(agpVersion, gradleVersion);
        int minimum = minimumJavaMajorForAgp(agpVersion);
        if (installed(appFilesDir, preferred) && preferred >= minimum && canRunGradle(preferred, gradleVersion)) return preferred;
        for (int candidate : CANDIDATES) {
            if (candidate < minimum || candidate == preferred) continue;
            if (installed(appFilesDir, candidate) && canRunGradle(candidate, gradleVersion)) return candidate;
        }
        return 0;
    }

    private static boolean installed(File appFilesDir, int javaMajor) {
        return RuntimeLayout.findJavaHome(appFilesDir, javaMajor) != null;
    }

    public static boolean canRunGradle(int javaMajor, String gradleVersion) {
        Version g = Version.parse(gradleVersion);
        if (g == null) return javaMajor == 11 || javaMajor == 17 || javaMajor == 21;

        if (javaMajor <= 8) return g.atMost(8, 14);
        if (javaMajor >= 9 && javaMajor <= 13) return g.atMost(8, 14);
        if (javaMajor == 14) return g.atLeast(6, 3) && g.atMost(8, 14);
        if (javaMajor == 15) return g.atLeast(6, 7) && g.atMost(8, 14);
        if (javaMajor == 16) return g.atLeast(7, 0) && g.atMost(8, 14);
        if (javaMajor == 17) return g.atLeast(7, 3);
        if (javaMajor == 18) return g.atLeast(7, 5);
        if (javaMajor == 19) return g.atLeast(7, 6);
        if (javaMajor == 20) return g.atLeast(8, 3);
        if (javaMajor == 21) return g.atLeast(8, 5);
        if (javaMajor == 22) return g.atLeast(8, 8);
        if (javaMajor == 23) return g.atLeast(8, 10);
        if (javaMajor == 24) return g.atLeast(8, 14);
        if (javaMajor == 25) return g.atLeast(9, 1);
        if (javaMajor == 26) return g.atLeast(9, 4);
        return false;
    }

    static int major(String version) {
        Version parsed = Version.parse(version);
        return parsed == null ? 0 : parsed.major;
    }

    static final class Version {
        final int major;
        final int minor;

        Version(int major, int minor) { this.major = major; this.minor = minor; }

        static Version parse(String text) {
            if (text == null || text.trim().isEmpty()) return null;
            String[] parts = text.trim().split("[.-]");
            try {
                int major = Integer.parseInt(parts[0].replaceAll("[^0-9].*$", ""));
                int minor = parts.length > 1 ? Integer.parseInt(parts[1].replaceAll("[^0-9].*$", "")) : 0;
                return new Version(major, minor);
            } catch (Exception ignored) { return null; }
        }

        boolean atLeast(int wantMajor, int wantMinor) { return major > wantMajor || (major == wantMajor && minor >= wantMinor); }
        boolean atMost(int wantMajor, int wantMinor) { return major < wantMajor || (major == wantMajor && minor <= wantMinor); }
    }
}
