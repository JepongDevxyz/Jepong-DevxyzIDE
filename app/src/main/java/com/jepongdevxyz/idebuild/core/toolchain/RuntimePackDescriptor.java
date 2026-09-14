package com.jepongdevxyz.idebuild.core.toolchain;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Properties;

/** Metadata for a downloadable DevxyzIDE runtime/toolchain pack. */
public final class RuntimePackDescriptor {
    private static final long MAX_PACK_BYTES = 2L * 1024L * 1024L * 1024L;

    private final String id;
    private final String component;
    private final String version;
    private final String abi;
    private final String url;
    private final String sha256;
    private final long expectedBytes;

    private RuntimePackDescriptor(String id, String component, String version, String abi,
                                  String url, String sha256, long expectedBytes) {
        this.id = id;
        this.component = component;
        this.version = version;
        this.abi = abi;
        this.url = url;
        this.sha256 = sha256;
        this.expectedBytes = expectedBytes;
    }

    public static RuntimePackDescriptor from(Properties p) throws IOException {
        if (p == null) throw new IOException("Runtime descriptor is missing");
        if (!"1".equals(required(p, "format"))) throw new IOException("Unsupported runtime descriptor format");
        String id = safeToken(required(p, "id"), "id");
        String component = safeToken(required(p, "component"), "component");
        String version = safeVersion(required(p, "version"));
        String abi = required(p, "abi");
        if (!abi.equals("arm64-v8a") && !abi.equals("armeabi-v7a") && !abi.equals("universal")) {
            throw new IOException("Unsupported runtime ABI: " + abi);
        }
        String url = required(p, "url");
        URI uri;
        try { uri = URI.create(url); } catch (Exception e) { throw new IOException("Invalid runtime URL", e); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            throw new IOException("Runtime pack URL must use HTTPS");
        }
        String sha = required(p, "sha256").toLowerCase(Locale.ROOT);
        if (!sha.matches("[0-9a-f]{64}")) throw new IOException("Invalid runtime pack SHA-256");
        long size;
        try { size = Long.parseLong(required(p, "size")); }
        catch (NumberFormatException e) { throw new IOException("Invalid runtime pack size", e); }
        if (size <= 0 || size > MAX_PACK_BYTES) throw new IOException("Runtime pack size is outside allowed range");
        return new RuntimePackDescriptor(id, component, version, abi, url, sha, size);
    }

    /** Host-test factory. Production descriptors must use {@link #from(Properties)}. */
    public static RuntimePackDescriptor forTest(String id, String component, String version,
                                                String abi, String sha256, long expectedBytes) throws IOException {
        Properties p = new Properties();
        p.setProperty("format", "1");
        p.setProperty("id", id);
        p.setProperty("component", component);
        p.setProperty("version", version);
        p.setProperty("abi", abi);
        p.setProperty("url", "https://example.invalid/" + id + ".zip");
        p.setProperty("sha256", sha256);
        p.setProperty("size", Long.toString(expectedBytes));
        return from(p);
    }

    private static String required(Properties p, String key) throws IOException {
        String value = p.getProperty(key);
        if (value == null || value.trim().isEmpty()) throw new IOException("Missing runtime descriptor field: " + key);
        return value.trim();
    }

    private static String safeToken(String value, String field) throws IOException {
        if (!value.matches("[A-Za-z0-9._-]+")) throw new IOException("Invalid runtime " + field);
        return value;
    }

    private static String safeVersion(String value) throws IOException {
        if (!value.matches("[A-Za-z0-9._+\\-]+")) throw new IOException("Invalid runtime version");
        return value;
    }

    public String getId() { return id; }
    public String getComponent() { return component; }
    public String getVersion() { return version; }
    public String getAbi() { return abi; }
    public String getUrl() { return url; }
    public String getSha256() { return sha256; }
    public long getExpectedBytes() { return expectedBytes; }
}
