package com.jepongdevxyz.idebuild;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jepongdevxyz.idebuild.core.toolchain.RuntimePackCatalog;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimePackDescriptor;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeProvisioner;
import com.jepongdevxyz.idebuild.core.toolchain.TerminalBootstrapInstaller;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainInventory;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainPackInstaller;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Real toolchain manager: inventories installed app-private components and can
 * install verified packs either from SAF or an explicitly supplied HTTPS
 * catalog. Catalog packs are still checked by declared size and SHA-256 before
 * installation; no component is marked installed until ToolchainInventory sees it.
 */
public final class ToolchainManagerActivity extends Activity {
    private static final int REQUEST_PACK = 7301;
    private static final int REQUEST_RUNTIME = 7302;
    private static final long MAX_MANUAL_PACK_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final int MAX_CATALOG_BYTES = 512 * 1024;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private TextView status;
    private TextView operation;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("DevxyzIDE Toolchain Manager");
        setContentView(buildContent());
        refreshInventory();
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getResources().getColor(R.color.devxyz_bg));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("Toolchain / SDK Manager", 22f, true);
        root.addView(title);
        TextView note = text("Only verified app-private runtime packs are installed. Downloads require HTTPS and a declared SHA-256. Unsupported components stay unavailable instead of being faked.", 13f, false);
        note.setTextColor(getResources().getColor(R.color.devxyz_muted));
        root.addView(note, marginTop(8));

        operation = text("Ready", 13f, false);
        operation.setTextColor(getResources().getColor(R.color.devxyz_muted));
        root.addView(operation, marginTop(14));

        status = text("Scanning…", 13f, false);
        status.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(status, marginTop(10));

        root.addView(button("Refresh inventory", new View.OnClickListener() {
            @Override public void onClick(View v) { refreshInventory(); }
        }), marginTop(16));

        root.addView(button("Install from HTTPS catalog", new View.OnClickListener() {
            @Override public void onClick(View v) { promptCatalogUrl(); }
        }), marginTop(8));

        root.addView(button("Import verified toolchain pack", new View.OnClickListener() {
            @Override public void onClick(View v) { choosePack(); }
        }), marginTop(8));

        root.addView(button("Import terminal runtime bootstrap", new View.OnClickListener() {
            @Override public void onClick(View v) { chooseRuntime(); }
        }), marginTop(8));

        TextView help = text(
                "Supported catalog components: JDK, Gradle, Android SDK platform and Android build tools. " +
                "NDK/CMake are shown by inventory when present but are not advertised as supported installers until their packs are verified.",
                12f, false);
        help.setTextColor(getResources().getColor(R.color.devxyz_muted));
        root.addView(help, marginTop(18));
        return scroll;
    }

    private void refreshInventory() {
        operation.setText("Scanning installed components…");
        io.execute(new Runnable() {
            @Override public void run() {
                final ToolchainInventory inventory = ToolchainInventory.scan(getFilesDir());
                final String primaryAbi = primaryAbi();
                final String text =
                        "Device ABI: " + primaryAbi + "\n" +
                        "App-private root: " + getFilesDir().getAbsolutePath() + "\n\n" +
                        "JDKs: " + inventory.getInstalledJdks() + "\n" +
                        "Android platforms: " + inventory.getAndroidPlatforms() + "\n" +
                        "Build tools: " + inventory.getBuildTools() + "\n" +
                        "AAPT2: " + yesNo(inventory.hasAapt2()) + "\n" +
                        "NDK detected: " + yesNo(inventory.hasNdk()) + "\n" +
                        "CMake detected: " + yesNo(inventory.hasCmake());
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        status.setText(text);
                        operation.setText("Inventory refreshed. Values above are detected from disk.");
                    }
                });
            }
        });
    }

    private void promptCatalogUrl() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("https://example.com/devxyz-runtime-catalog.properties");
        new AlertDialog.Builder(this)
                .setTitle("Runtime catalog URL")
                .setMessage("Use a catalog source you trust. DevxyzIDE validates each pack's HTTPS URL, size and SHA-256 before installation.")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Load", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String url = input.getText() == null ? "" : input.getText().toString().trim();
                        loadCatalog(url);
                    }
                })
                .show();
    }

    private void loadCatalog(final String rawUrl) {
        final URI uri;
        try {
            uri = URI.create(rawUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Catalog URL must use HTTPS");
            }
        } catch (Exception e) {
            operation.setText("CATALOG ERROR: " + safeMessage(e));
            return;
        }

        operation.setText("Downloading catalog from " + uri.getHost() + "…");
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    Properties properties = downloadCatalogProperties(uri);
                    final List<RuntimePackDescriptor> parsed = RuntimePackCatalog.parse(properties);
                    final List<RuntimePackDescriptor> compatible = filterCompatible(parsed, primaryAbi());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            operation.setText("Catalog loaded: " + parsed.size() + " pack(s), " + compatible.size() + " compatible with " + primaryAbi());
                            showCatalogChoices(compatible);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { operation.setText("CATALOG ERROR: " + safeMessage(e)); }
                    });
                }
            }
        });
    }

    private Properties downloadCatalogProperties(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(30_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "DevxyzIDE/0.6");
        int statusCode = connection.getResponseCode();
        if (statusCode < 200 || statusCode >= 300) {
            connection.disconnect();
            throw new IOException("Catalog HTTP " + statusCode);
        }
        int declared = connection.getContentLength();
        if (declared > MAX_CATALOG_BYTES) {
            connection.disconnect();
            throw new IOException("Catalog is too large");
        }
        InputStream input = null;
        try {
            input = new BufferedInputStream(connection.getInputStream());
            byte[] bytes = readLimited(input, MAX_CATALOG_BYTES);
            Properties properties = new Properties();
            properties.load(new ByteArrayInputStream(bytes));
            return properties;
        } finally {
            closeQuietly(input);
            connection.disconnect();
        }
    }

    private List<RuntimePackDescriptor> filterCompatible(List<RuntimePackDescriptor> entries, String primaryAbi) {
        ArrayList<RuntimePackDescriptor> result = new ArrayList<RuntimePackDescriptor>();
        for (RuntimePackDescriptor descriptor : entries) {
            if ("universal".equals(descriptor.getAbi()) || primaryAbi.equals(descriptor.getAbi())) result.add(descriptor);
        }
        return result;
    }

    private void showCatalogChoices(final List<RuntimePackDescriptor> entries) {
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No compatible packs")
                    .setMessage("The catalog contains no universal or " + primaryAbi() + " packs.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            RuntimePackDescriptor item = entries.get(i);
            labels[i] = item.getComponent() + " " + item.getVersion() + " · " + item.getAbi() + " · " + humanBytes(item.getExpectedBytes());
        }
        new AlertDialog.Builder(this)
                .setTitle("Compatible runtime packs")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { confirmCatalogInstall(entries.get(which)); }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void confirmCatalogInstall(final RuntimePackDescriptor descriptor) {
        String message =
                "Component: " + descriptor.getComponent() + "\n" +
                "Version: " + descriptor.getVersion() + "\n" +
                "ABI: " + descriptor.getAbi() + "\n" +
                "Size: " + humanBytes(descriptor.getExpectedBytes()) + "\n\n" +
                "Source: " + descriptor.getUrl() + "\n\n" +
                "SHA-256:\n" + descriptor.getSha256();
        new AlertDialog.Builder(this)
                .setTitle("Install verified runtime pack?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Download & Install", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { installDescriptor(descriptor); }
                })
                .show();
    }

    private void installDescriptor(final RuntimePackDescriptor descriptor) {
        operation.setText("Downloading and verifying " + descriptor.getId() + "…");
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    File downloads = new File(getCacheDir(), "runtime-downloads");
                    if (!downloads.isDirectory() && !downloads.mkdirs()) throw new IOException("Cannot create runtime download cache");
                    final ToolchainPackInstaller.InstallResult result = RuntimeProvisioner.install(descriptor, downloads, getFilesDir());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            operation.setText("INSTALLED: " + result.getInstalledDirectory().getAbsolutePath() + " (" + result.getVerifiedFiles() + " verified files)");
                            refreshInventory();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { operation.setText("INSTALL ERROR: " + safeMessage(e)); }
                    });
                }
            }
        });
    }

    private void choosePack() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, REQUEST_PACK);
    }

    private void chooseRuntime() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, REQUEST_RUNTIME);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_PACK) installManualPack(data.getData());
        else if (requestCode == REQUEST_RUNTIME) installRuntimeBootstrap(data.getData());
    }

    private void installManualPack(final Uri uri) {
        operation.setText("Copying and verifying selected toolchain pack…");
        io.execute(new Runnable() {
            @Override public void run() {
                File temp = new File(getCacheDir(), "manual-toolchain-" + System.nanoTime() + ".zip");
                InputStream input = null;
                OutputStream output = null;
                try {
                    input = getContentResolver().openInputStream(uri);
                    if (input == null) throw new IOException("Cannot open selected pack");
                    output = new BufferedOutputStream(new FileOutputStream(temp));
                    copyLimited(input, output, MAX_MANUAL_PACK_BYTES);
                    closeQuietly(output); output = null;
                    final ToolchainPackInstaller.InstallResult result = ToolchainPackInstaller.install(temp, getFilesDir());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            operation.setText("INSTALLED: " + result.getInstalledDirectory().getAbsolutePath() + " (" + result.getVerifiedFiles() + " verified files)");
                            refreshInventory();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { operation.setText("PACK ERROR: " + safeMessage(e)); }
                    });
                } finally {
                    closeQuietly(output);
                    closeQuietly(input);
                    if (temp.exists()) temp.delete();
                }
            }
        });
    }

    private void installRuntimeBootstrap(final Uri uri) {
        operation.setText("Installing terminal runtime bootstrap…");
        io.execute(new Runnable() {
            @Override public void run() {
                InputStream input = null;
                try {
                    input = getContentResolver().openInputStream(uri);
                    if (input == null) throw new IOException("Cannot open runtime bootstrap");
                    final TerminalBootstrapInstaller.InstallResult result = TerminalBootstrapInstaller.install(input, getFilesDir(), getPackageName());
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            operation.setText("RUNTIME INSTALLED: " + result.getArch() + " · files=" + result.getFiles() + " · symlinks=" + result.getSymlinks());
                            refreshInventory();
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() { operation.setText("RUNTIME ERROR: " + safeMessage(e)); }
                    });
                } finally { closeQuietly(input); }
            }
        });
    }

    private TextView text(String value, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(getResources().getColor(R.color.devxyz_text));
        view.setTextSize(size);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams marginTop(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(dp);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String primaryAbi() {
        if (Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) return Build.SUPPORTED_ABIS[0];
        return Build.CPU_ABI == null ? "unknown" : Build.CPU_ABI;
    }

    private static String yesNo(boolean value) { return value ? "available" : "not detected"; }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = new String[]{"B", "KiB", "MiB", "GiB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) { value /= 1024.0; unit++; }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("Catalog exceeds safety limit");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void copyLimited(InputStream input, OutputStream output, long maxBytes) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0L;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (total > maxBytes - read) throw new IOException("Selected pack exceeds safety limit");
            total += read;
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    private static String safeMessage(Throwable error) {
        String value = error == null ? null : error.getMessage();
        return value == null || value.trim().length() == 0 ? error.getClass().getSimpleName() : value;
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) try { closeable.close(); } catch (IOException ignored) { }
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
