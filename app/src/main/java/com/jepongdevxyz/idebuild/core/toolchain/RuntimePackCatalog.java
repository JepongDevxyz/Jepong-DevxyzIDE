package com.jepongdevxyz.idebuild.core.toolchain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/** Parses a small properties-backed catalog of verified runtime-pack descriptors. */
public final class RuntimePackCatalog {
    private RuntimePackCatalog() {}

    public static List<RuntimePackDescriptor> parse(Properties properties) throws IOException {
        if (properties == null) throw new IOException("Runtime catalog is missing");
        String countText = properties.getProperty("count");
        if (countText == null) throw new IOException("Runtime catalog count missing");
        int count;
        try { count = Integer.parseInt(countText.trim()); }
        catch (NumberFormatException e) { throw new IOException("Invalid runtime catalog count", e); }
        if (count < 0 || count > 64) throw new IOException("Runtime catalog count outside allowed range");

        ArrayList<RuntimePackDescriptor> result = new ArrayList<RuntimePackDescriptor>();
        for (int i = 0; i < count; i++) {
            String prefix = "pack." + i + ".";
            Properties descriptor = new Properties();
            descriptor.setProperty("format", value(properties, prefix + "format", "1"));
            descriptor.setProperty("id", required(properties, prefix + "id"));
            descriptor.setProperty("component", requiredComponent(properties, prefix + "component"));
            descriptor.setProperty("version", required(properties, prefix + "version"));
            descriptor.setProperty("abi", required(properties, prefix + "abi"));
            descriptor.setProperty("url", required(properties, prefix + "url"));
            descriptor.setProperty("sha256", required(properties, prefix + "sha256"));
            descriptor.setProperty("size", required(properties, prefix + "size"));
            result.add(RuntimePackDescriptor.from(descriptor));
        }
        return Collections.unmodifiableList(result);
    }

    public static RuntimePackDescriptor findBest(List<RuntimePackDescriptor> entries,
                                                 String component, String abi, String minimumVersion) {
        RuntimePackDescriptor best = null;
        if (entries == null) return null;
        for (RuntimePackDescriptor entry : entries) {
            if (!component.equals(entry.getComponent())) continue;
            if (!(abi.equals(entry.getAbi()) || "universal".equals(entry.getAbi()))) continue;
            if (minimumVersion != null && !com.jepongdevxyz.idebuild.core.build.GradleCompatibility.isAtLeast(entry.getVersion(), minimumVersion)) continue;
            if (best == null || compareVersions(entry.getVersion(), best.getVersion()) > 0) best = entry;
        }
        return best;
    }

    private static String requiredComponent(Properties properties, String key) throws IOException {
        String value = required(properties, key);
        if (!value.equals("jdk") && !value.equals("gradle") &&
                !value.equals("android-sdk-platform") && !value.equals("android-build-tools")) {
            throw new IOException("Unsupported runtime component: " + value);
        }
        return value;
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.trim().length() == 0) throw new IOException("Missing runtime catalog field: " + key);
        return value.trim();
    }

    private static String value(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }

    private static int compareVersions(String a, String b) {
        String[] aa = a.split("[.-]");
        String[] bb = b.split("[.-]");
        int count = Math.max(aa.length, bb.length);
        for (int i = 0; i < count; i++) {
            int av = i < aa.length ? intPrefix(aa[i]) : 0;
            int bv = i < bb.length ? intPrefix(bb[i]) : 0;
            if (av != bv) return av < bv ? -1 : 1;
        }
        return a.compareTo(b);
    }

    private static int intPrefix(String value) {
        try { return Integer.parseInt(value.replaceFirst("[^0-9].*$", "")); }
        catch (Exception ignored) { return 0; }
    }
}
