package com.jepongdevxyz.idebuild;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jepongdevxyz.idebuild.core.ProjectFiles;
import com.jepongdevxyz.idebuild.core.ProjectRootDetector;
import com.jepongdevxyz.idebuild.core.SafeZip;
import com.jepongdevxyz.idebuild.core.TextFileClassifier;
import com.jepongdevxyz.idebuild.core.build.ProjectAnalyzer;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainInventory;
import com.jepongdevxyz.idebuild.core.toolchain.TerminalBootstrapInstaller;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainPackInstaller;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainProvisioningPlan;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_ZIP = 5001;
    private static final int REQUEST_TOOLCHAIN_PACK = 5002;
    private static final int REQUEST_RUNTIME_BOOTSTRAP = 5003;
    private static final long MAX_IMPORT_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final int MAX_IMPORT_ENTRIES = 100000;
    private static final long MAX_TEXT_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_TOOLCHAIN_PACK_BYTES = 2L * 1024L * 1024L * 1024L;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Button importButton, toolchainButton, runtimeButton, saveButton, buildButton, installButton;
    private TextView projectPath, consoleText;
    private ScrollView consoleScroll;
    private EditText editor;
    private ListView fileList;
    private ArrayAdapter<String> fileAdapter;
    private final List<String> relativeFiles = new ArrayList<String>();
    private File projectRoot;
    private File currentFile;
    private File lastBuiltApk;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        importButton = (Button) findViewById(R.id.importButton);
        toolchainButton = (Button) findViewById(R.id.toolchainButton);
        runtimeButton = (Button) findViewById(R.id.runtimeButton);
        saveButton = (Button) findViewById(R.id.saveButton);
        buildButton = (Button) findViewById(R.id.buildButton);
        installButton = (Button) findViewById(R.id.installButton);
        projectPath = (TextView) findViewById(R.id.projectPath);
        consoleText = (TextView) findViewById(R.id.consoleText);
        consoleScroll = (ScrollView) findViewById(R.id.consoleScroll);
        editor = (EditText) findViewById(R.id.editor);
        fileList = (ListView) findViewById(R.id.fileList);

        fileAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, relativeFiles) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(getResources().getColor(R.color.devxyz_text));
                view.setTextSize(13f);
                view.setPadding(14, 10, 10, 10);
                return view;
            }
        };
        fileList.setAdapter(fileAdapter);

        importButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseZip(); } });
        toolchainButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseToolchainPack(); } });
        runtimeButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseRuntimeBootstrap(); } });
        saveButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { saveCurrentFile(); } });
        buildButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { buildProject(); } });
        installButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (lastBuiltApk != null && lastBuiltApk.isFile()) ApkInstaller.install(MainActivity.this, lastBuiltApk);
            }
        });
        fileList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openRelativeFile(relativeFiles.get(position));
            }
        });
    }

    private void chooseZip() { startPicker("application/zip", REQUEST_IMPORT_ZIP); }
    private void chooseToolchainPack() { startPicker("application/zip", REQUEST_TOOLCHAIN_PACK); }
    private void chooseRuntimeBootstrap() { startPicker("application/zip", REQUEST_RUNTIME_BOOTSTRAP); }

    private void startPicker(String type, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        startActivityForResult(intent, requestCode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_IMPORT_ZIP) importZip(data.getData());
        else if (requestCode == REQUEST_TOOLCHAIN_PACK) importToolchainPack(data.getData());
        else if (requestCode == REQUEST_RUNTIME_BOOTSTRAP) importRuntimeBootstrap(data.getData());
    }

    private void importRuntimeBootstrap(final Uri uri) {
        runtimeButton.setEnabled(false);
        appendConsole("\nInstalling DevxyzIDE terminal runtime: " + displayName(uri));
        io.execute(new Runnable() { @Override public void run() {
            InputStream input = null;
            try {
                input = getContentResolver().openInputStream(uri);
                if (input == null) throw new IOException("Cannot open runtime bootstrap");
                TerminalBootstrapInstaller.InstallResult result = TerminalBootstrapInstaller.install(input, getFilesDir(), getPackageName());
                ToolchainInventory inventory = ToolchainInventory.scan(getFilesDir());
                appendConsole("RUNTIME INSTALLED: " + result.getPrefix().getAbsolutePath());
                appendConsole("Runtime ABI: " + result.getArch() + ", files=" + result.getFiles() + ", symlinks=" + result.getSymlinks());
                appendConsole("Toolchain inventory: " + inventory.summary());
                if (projectRoot != null) analyzeProject(projectRoot);
            } catch (Exception e) { appendConsole("RUNTIME ERROR: " + e.getMessage()); }
            finally {
                closeQuietly(input);
                runOnUiThread(new Runnable() { @Override public void run() { runtimeButton.setEnabled(true); } });
            }
        }});
    }

    private void importToolchainPack(final Uri uri) {
        toolchainButton.setEnabled(false);
        appendConsole("\nInstalling DevxyzIDE toolchain pack: " + displayName(uri));
        io.execute(new Runnable() { @Override public void run() {
            File temp = new File(getCacheDir(), "toolchain-" + System.nanoTime() + ".zip");
            InputStream input = null;
            OutputStream output = null;
            try {
                input = getContentResolver().openInputStream(uri);
                output = new BufferedOutputStream(new FileOutputStream(temp));
                if (input == null) throw new IOException("Cannot open toolchain pack");
                copyWithLimit(input, output, MAX_TOOLCHAIN_PACK_BYTES);
                closeQuietly(output); output = null;
                closeQuietly(input); input = null;
                ToolchainPackInstaller.InstallResult result = ToolchainPackInstaller.install(temp, getFilesDir());
                ToolchainInventory inventory = ToolchainInventory.scan(getFilesDir());
                appendConsole("TOOLCHAIN INSTALLED: " + result.getInstalledDirectory().getAbsolutePath());
                appendConsole("Verified files: " + result.getVerifiedFiles());
                appendConsole("Toolchain inventory: " + inventory.summary());
                if (projectRoot != null) analyzeProject(projectRoot);
            } catch (Exception e) { appendConsole("TOOLCHAIN ERROR: " + e.getMessage()); }
            finally {
                closeQuietly(output); closeQuietly(input);
                if (temp.exists()) temp.delete();
                runOnUiThread(new Runnable() { @Override public void run() { toolchainButton.setEnabled(true); } });
            }
        }});
    }

    private void importZip(final Uri uri) {
        importButton.setEnabled(false);
        appendConsole("\nImporting: " + displayName(uri));
        io.execute(new Runnable() { @Override public void run() {
            InputStream input = null;
            try {
                File storageRoot = getExternalFilesDir(null);
                if (storageRoot == null) storageRoot = getFilesDir();
                File projects = new File(storageRoot, "projects");
                if (!projects.exists() && !projects.mkdirs()) throw new IOException("Cannot create projects directory");
                File target = uniqueDirectory(projects, sanitizeProjectName(displayName(uri)));
                input = getContentResolver().openInputStream(uri);
                if (input == null) throw new IOException("Cannot open ZIP input stream");
                long freeBefore = target.getParentFile().getUsableSpace();
                appendConsole("Import storage available: " + humanBytes(freeBefore));
                SafeZip.extractProject(input, target, MAX_IMPORT_ENTRIES, MAX_IMPORT_BYTES);
                File collapsed = collapseSingleRootFolder(target);
                final File normalized = ProjectRootDetector.findBestGradleRoot(collapsed, 6, 20000);
                appendConsole("Detected project root: " + normalized.getAbsolutePath());
                runOnUiThread(new Runnable() { @Override public void run() { loadProject(normalized); } });
            } catch (Exception e) { appendConsole("IMPORT ERROR: " + e.getMessage()); }
            finally {
                closeQuietly(input);
                runOnUiThread(new Runnable() { @Override public void run() { importButton.setEnabled(true); } });
            }
        }});
    }

    private void loadProject(File root) {
        projectRoot = root; currentFile = null; lastBuiltApk = null;
        projectPath.setText(root.getAbsolutePath());
        saveButton.setEnabled(false);
        buildButton.setEnabled(true);
        installButton.setEnabled(false);
        editor.setText("");
        refreshProjectFiles();
        appendConsole("Loaded project: " + root.getName());
        if (!new File(root, "gradlew").isFile()) appendConsole("No project Gradle Wrapper found; DevxyzIDE will try its internal Gradle runtime at build time.");
        analyzeProject(root);
    }

    private void analyzeProject(final File root) {
        io.execute(new Runnable() { @Override public void run() {
            try {
                ProjectRequirements requirements = ProjectAnalyzer.analyze(root);
                appendConsole(BuildRunner.describeProject(requirements));
                ToolchainProvisioningPlan provisioning = ToolchainProvisioningPlan.create(requirements, getFilesDir());
                appendConsole("Toolchain provisioning: " + provisioning.summary());
                for (String warning : requirements.getWarnings()) appendConsole("ANALYZE WARNING: " + warning);
                if (!requirements.getRepositories().isEmpty()) appendConsole("Repository resolution stays with Gradle; custom Maven repositories are preserved.");
            } catch (Exception e) { appendConsole("ANALYZE ERROR: " + e.getMessage()); }
        }});
    }

    private void refreshProjectFiles() {
        if (projectRoot == null) return;
        final File root = projectRoot;
        io.execute(new Runnable() { @Override public void run() {
            try {
                final List<String> files = ProjectFiles.listRelativeFiles(root, 20000);
                runOnUiThread(new Runnable() { @Override public void run() {
                    relativeFiles.clear(); relativeFiles.addAll(files); fileAdapter.notifyDataSetChanged();
                }});
            } catch (Exception e) { appendConsole("FILE LIST ERROR: " + e.getMessage()); }
        }});
    }

    private void openRelativeFile(final String relative) {
        if (projectRoot == null) return;
        final File file = new File(projectRoot, relative);
        if (!TextFileClassifier.isTextFile(relative)) { appendConsole("Binary/non-text file not opened: " + relative); return; }
        if (file.length() > MAX_TEXT_BYTES) { appendConsole("File is larger than 2 MiB; editor refused it: " + relative); return; }
        io.execute(new Runnable() { @Override public void run() {
            try {
                final String text = readUtf8(file, MAX_TEXT_BYTES);
                runOnUiThread(new Runnable() { @Override public void run() {
                    currentFile = file; editor.setText(text); editor.setSelection(0); saveButton.setEnabled(true); appendConsole("Opened: " + relative);
                }});
            } catch (Exception e) { appendConsole("OPEN ERROR: " + e.getMessage()); }
        }});
    }

    private void saveCurrentFile() {
        final File file = currentFile;
        if (file == null) return;
        final String content = editor.getText() == null ? "" : editor.getText().toString();
        io.execute(new Runnable() { @Override public void run() {
            try { writeUtf8(file, content); appendConsole("Saved: " + relativePath(file)); }
            catch (Exception e) { appendConsole("SAVE ERROR: " + e.getMessage()); }
        }});
    }

    private void buildProject() {
        if (projectRoot == null) return;
        final File root = projectRoot;
        final File fileToSave = currentFile;
        final String contentToSave = editor.getText() == null ? "" : editor.getText().toString();
        buildButton.setEnabled(false); installButton.setEnabled(false); lastBuiltApk = null;
        appendConsole("\n> DevxyzIDE wrapper-aware preflight + assembleDebug");
        io.execute(new Runnable() { @Override public void run() {
            if (fileToSave != null) {
                try { writeUtf8(fileToSave, contentToSave); appendConsole("Saved before build: " + relativePath(fileToSave)); }
                catch (IOException e) {
                    appendConsole("SAVE ERROR: " + e.getMessage());
                    runOnUiThread(new Runnable() { @Override public void run() { buildButton.setEnabled(true); } });
                    return;
                }
            }
            BuildRunner.runDebugBuild(root, getFilesDir(), new BuildRunner.Listener() {
                @Override public void onLine(String line) { appendConsole(line); }
                @Override public void onFinished(final int exitCode, final File apk) {
                    runOnUiThread(new Runnable() { @Override public void run() {
                        buildButton.setEnabled(true); lastBuiltApk = apk; installButton.setEnabled(apk != null && apk.isFile());
                        appendConsole(exitCode == 0 ? "BUILD FINISHED: SUCCESS" : "BUILD FINISHED: exit " + exitCode);
                        if (apk != null) appendConsole("APK: " + apk.getAbsolutePath());
                    }});
                }
            });
        }});
    }

    private void appendConsole(final String line) {
        runOnUiThread(new Runnable() { @Override public void run() {
            if (consoleText.length() > 200000) consoleText.setText("[console truncated]\n");
            consoleText.append((consoleText.length() == 0 ? "" : "\n") + line);
            consoleScroll.post(new Runnable() { @Override public void run() { consoleScroll.fullScroll(View.FOCUS_DOWN); } });
        }});
    }

    private String relativePath(File file) {
        if (projectRoot == null) return file.getName();
        try { return ProjectFiles.relativePath(projectRoot, file); } catch (IOException e) { return file.getName(); }
    }

    private String displayName(Uri uri) {
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (Exception ignored) { }
        finally { if (cursor != null) cursor.close(); }
        String last = uri.getLastPathSegment();
        return last == null ? "project.zip" : last;
    }

    private static String sanitizeProjectName(String name) {
        String base = name == null ? "project" : name.replaceFirst("(?i)\\.zip$", "");
        base = base.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return base.length() == 0 ? "project" : base;
    }

    private static File uniqueDirectory(File parent, String base) throws IOException {
        File target = new File(parent, base); int suffix = 2;
        while (target.exists()) target = new File(parent, base + "-" + suffix++);
        if (!target.mkdirs()) throw new IOException("Cannot create project directory");
        return target;
    }

    private static File collapseSingleRootFolder(File target) {
        File[] children = target.listFiles();
        if (children != null && children.length == 1 && children[0].isDirectory()) return children[0];
        return target;
    }

    private static void writeUtf8(File file, String content) throws IOException {
        OutputStream out = null;
        try { out = new BufferedOutputStream(new FileOutputStream(file)); out.write(content.getBytes("UTF-8")); out.flush(); }
        finally { closeQuietly(out); }
    }

    private static void copyWithLimit(InputStream input, OutputStream output, long maxBytes) throws IOException {
        byte[] buffer = new byte[64 * 1024]; long total = 0; int read;
        while ((read = input.read(buffer)) != -1) { total += read; if (total > maxBytes) throw new IOException("File exceeds import size limit"); output.write(buffer, 0, read); }
        output.flush();
    }

    private static String readUtf8(File file, long maxBytes) throws IOException {
        if (file.length() > maxBytes) throw new IOException("File exceeds editor size limit");
        InputStream in = null; ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            in = new BufferedInputStream(new FileInputStream(file)); byte[] buffer = new byte[16384]; int read; long total = 0;
            while ((read = in.read(buffer)) != -1) { total += read; if (total > maxBytes) throw new IOException("File exceeds editor size limit"); out.write(buffer, 0, read); }
            return out.toString("UTF-8");
        } finally { closeQuietly(in); try { out.close(); } catch (IOException ignored) { } }
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) { value /= 1024.0; unit++; }
        return String.format(java.util.Locale.US, "%.1f %s", value, units[unit]);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) try { closeable.close(); } catch (IOException ignored) { }
    }

    @Override protected void onDestroy() { io.shutdownNow(); super.onDestroy(); }
}
