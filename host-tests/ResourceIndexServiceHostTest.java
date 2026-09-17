import com.jepongdevxyz.idebuild.core.resources.ResourceIndexService;
import com.jepongdevxyz.idebuild.core.resources.ResourceItem;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class ResourceIndexServiceHostTest {
    private static int passed;

    public static void main(String[] args) throws Exception {
        scansAndroidResourceFoldersAndReferences();
        skipsGeneratedAndHiddenContent();
        rejectsMissingResDirectory();
        System.out.println("RESOURCE INDEX SERVICE HOST TESTS PASSED: " + passed + "/3");
    }

    private static void scansAndroidResourceFoldersAndReferences() throws Exception {
        File root = Files.createTempDirectory("devxyz-res-index").toFile();
        try {
            File res = new File(root, "app/src/main/res");
            write(new File(res, "drawable/ic_logo.xml"), "<shape/>");
            write(new File(res, "layout/activity_main.xml"), "<LinearLayout/>");
            write(new File(res, "mipmap-hdpi/ic_launcher.png"), "png");
            write(new File(res, "raw/sample.json"), "{}");
            List<ResourceItem> items = ResourceIndexService.scan(res, 1000);
            assertContains(items, "drawable", "ic_logo", "@drawable/ic_logo");
            assertContains(items, "layout", "activity_main", "@layout/activity_main");
            assertContains(items, "mipmap", "ic_launcher", "@mipmap/ic_launcher");
            assertContains(items, "raw", "sample", "@raw/sample");
            passed++;
        } finally { deleteTree(root); }
    }

    private static void skipsGeneratedAndHiddenContent() throws Exception {
        File root = Files.createTempDirectory("devxyz-res-hidden").toFile();
        try {
            File res = new File(root, "res");
            write(new File(res, ".hidden/secret.xml"), "<x/>");
            write(new File(res, "drawable/.temp.xml"), "<x/>");
            write(new File(res, "drawable/icon.xml"), "<shape/>");
            List<ResourceItem> items = ResourceIndexService.scan(res, 1000);
            if (items.size() != 1) throw new AssertionError("Expected one visible resource but was " + items.size());
            assertContains(items, "drawable", "icon", "@drawable/icon");
            passed++;
        } finally { deleteTree(root); }
    }

    private static void rejectsMissingResDirectory() throws Exception {
        File root = Files.createTempDirectory("devxyz-res-missing").toFile();
        try {
            boolean failed = false;
            try { ResourceIndexService.scan(new File(root, "missing"), 10); }
            catch (IllegalArgumentException expected) { failed = true; }
            if (!failed) throw new AssertionError("Expected missing res directory to fail");
            passed++;
        } finally { deleteTree(root); }
    }

    private static void assertContains(List<ResourceItem> items, String type, String name, String reference) {
        for (ResourceItem item : items) {
            if (type.equals(item.getType()) && name.equals(item.getName()) && reference.equals(item.getReference())) return;
        }
        throw new AssertionError("Missing resource " + reference + " in " + items);
    }

    private static void write(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new Exception("mkdir failed");
        FileOutputStream out = new FileOutputStream(file);
        try { out.write(value.getBytes(StandardCharsets.UTF_8)); }
        finally { out.close(); }
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
