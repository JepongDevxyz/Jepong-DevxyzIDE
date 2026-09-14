import com.jepongdevxyz.idebuild.core.toolchain.RuntimePackCatalog;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimePackDescriptor;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

public final class RuntimeCatalogHostTest {
    public static void main(String[] args) throws Exception {
        int passed = 0;
        testValidCatalogAndSelection(); passed++;
        testRejectsHttp(); passed++;
        testRejectsUnknownComponent(); passed++;
        System.out.println("RUNTIME CATALOG TESTS PASSED: " + passed + "/3");
    }

    private static void testValidCatalogAndSelection() throws Exception {
        Properties p = base("https://example.invalid/gradle-8.9.zip", "gradle");
        List<RuntimePackDescriptor> list = RuntimePackCatalog.parse(p);
        if (list.size() != 1) throw new AssertionError("catalog size");
        RuntimePackDescriptor selected = RuntimePackCatalog.findBest(list, "gradle", "arm64-v8a", "8.9");
        if (selected == null || !"8.9".equals(selected.getVersion())) throw new AssertionError("catalog selection");
    }

    private static void testRejectsHttp() throws Exception {
        Properties p = base("http://example.invalid/gradle-8.9.zip", "gradle");
        expectIOException(p, "HTTP catalog entry accepted");
    }

    private static void testRejectsUnknownComponent() throws Exception {
        Properties p = base("https://example.invalid/x.zip", "mystery");
        expectIOException(p, "unknown component accepted");
    }

    private static Properties base(String url, String component) {
        Properties p = new Properties();
        p.setProperty("count", "1");
        p.setProperty("pack.0.id", "gradle-8.9-arm64");
        p.setProperty("pack.0.component", component);
        p.setProperty("pack.0.version", "8.9");
        p.setProperty("pack.0.abi", "arm64-v8a");
        p.setProperty("pack.0.url", url);
        p.setProperty("pack.0.sha256", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        p.setProperty("pack.0.size", "1024");
        return p;
    }

    private static void expectIOException(Properties p, String message) throws Exception {
        try { RuntimePackCatalog.parse(p); }
        catch (IOException expected) { return; }
        throw new AssertionError(message);
    }
}
