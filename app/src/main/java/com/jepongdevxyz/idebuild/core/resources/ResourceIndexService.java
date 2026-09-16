package com.jepongdevxyz.idebuild.core.resources;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Bounded scanner for real files in an Android project's res directory. */
public final class ResourceIndexService {
    private ResourceIndexService() { }

    public static List<ResourceItem> scan(File resDirectory, int maxItems) throws IOException {
        if (resDirectory == null || !resDirectory.isDirectory()) {
            throw new IllegalArgumentException("resDirectory must be an existing directory");
        }
        if (maxItems < 1) throw new IllegalArgumentException("maxItems must be positive");
        File canonicalRoot = resDirectory.getCanonicalFile();
        ArrayList<ResourceItem> items = new ArrayList<ResourceItem>();
        File[] typeDirs = canonicalRoot.listFiles();
        if (typeDirs == null) throw new IOException("Could not list resource directory");
        Arrays.sort(typeDirs, FILE_NAME_ORDER);
        for (File typeDir : typeDirs) {
            if (!typeDir.isDirectory() || isHidden(typeDir.getName())) continue;
            String folder = typeDir.getName();
            int dash = folder.indexOf('-');
            String type = dash < 0 ? folder : folder.substring(0, dash);
            String qualifier = dash < 0 ? "" : folder.substring(dash + 1);
            if (!isResourceType(type)) continue;
            File canonicalType = typeDir.getCanonicalFile();
            requireContained(canonicalRoot, canonicalType);
            File[] children = canonicalType.listFiles();
            if (children == null) continue;
            Arrays.sort(children, FILE_NAME_ORDER);
            for (File child : children) {
                if (items.size() >= maxItems) return Collections.unmodifiableList(items);
                if (!child.isFile() || isHidden(child.getName())) continue;
                File canonicalFile = child.getCanonicalFile();
                requireContained(canonicalRoot, canonicalFile);
                if (!child.getAbsolutePath().equals(canonicalFile.getPath())) continue;
                String name = stripExtension(child.getName());
                if (name.length() == 0) continue;
                items.add(new ResourceItem(type, qualifier, name, "@" + type + "/" + name, canonicalFile));
            }
        }
        return Collections.unmodifiableList(items);
    }

    private static final Comparator<File> FILE_NAME_ORDER = new Comparator<File>() {
        @Override public int compare(File left, File right) {
            return left.getName().compareToIgnoreCase(right.getName());
        }
    };

    private static boolean isResourceType(String type) {
        return "anim".equals(type)
                || "animator".equals(type)
                || "color".equals(type)
                || "drawable".equals(type)
                || "font".equals(type)
                || "interpolator".equals(type)
                || "layout".equals(type)
                || "menu".equals(type)
                || "mipmap".equals(type)
                || "navigation".equals(type)
                || "raw".equals(type)
                || "transition".equals(type)
                || "values".equals(type)
                || "xml".equals(type);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static boolean isHidden(String name) {
        return name == null || name.length() == 0 || name.charAt(0) == '.';
    }

    private static void requireContained(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String filePath = file.getCanonicalPath();
        String prefix = rootPath.endsWith(File.separator) ? rootPath : rootPath + File.separator;
        if (!filePath.startsWith(prefix)) throw new IOException("Resource entry escapes res root");
    }
}
