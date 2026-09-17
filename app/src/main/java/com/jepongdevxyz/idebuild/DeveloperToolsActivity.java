package com.jepongdevxyz.idebuild;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jepongdevxyz.idebuild.core.database.SqlQueryGuard;
import com.jepongdevxyz.idebuild.core.resources.ResourceIndexService;
import com.jepongdevxyz.idebuild.core.resources.ResourceItem;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Functional developer extras hub. Each tool operates on real project/filesystem
 * data and labels preview limitations instead of simulating Android Studio.
 */
public final class DeveloperToolsActivity extends Activity {
    public static final String EXTRA_PROJECT_ROOT = "project_root";
    private static final int REQUEST_DATABASE = 7201;
    private static final long MAX_DATABASE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_PREVIEW_TEXT_BYTES = 1024L * 1024L;
    private static final int MAX_RESOURCE_ITEMS = 5000;
    private static final int MAX_DISCOVERY_FILES = 12000;
    private static final int MAX_QUERY_ROWS = 200;
    private static final int MAX_QUERY_CHARS = 100000;

    private File projectRoot;
    private File databaseSnapshot;
    private TextView contextLabel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Developer Tools");
        resolveProjectRoot();
        setContentView(buildContent());
    }

    private void resolveProjectRoot() {
        String raw = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PROJECT_ROOT);
        if (raw == null) return;
        try {
            File candidate = new File(raw).getCanonicalFile();
            if (candidate.isDirectory()) projectRoot = candidate;
        } catch (IOException ignored) { }
    }

    private View buildContent() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(16));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("DevxyzIDE Developer Tools");
        title.setTextSize(20f);
        title.setPadding(0, 0, 0, dp(6));
        root.addView(title);

        contextLabel = new TextView(this);
        contextLabel.setText(projectRoot == null ? "No project loaded. Color Picker and Database Viewer remain available." : "Project: " + projectRoot.getAbsolutePath());
        contextLabel.setTextSize(12f);
        contextLabel.setTextIsSelectable(true);
        contextLabel.setPadding(0, 0, 0, dp(10));
        root.addView(contextLabel);

        root.addView(toolButton("Resource Manager", new View.OnClickListener() {
            @Override public void onClick(View view) { showResourceManager(); }
        }));
        root.addView(toolButton("Color Picker", new View.OnClickListener() {
            @Override public void onClick(View view) { showColorPicker(); }
        }));
        root.addView(toolButton("SQLite Database Viewer", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseDatabase(); }
        }));
        root.addView(toolButton("HTML / Web Preview", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseHtmlPreview(); }
        }));
        root.addView(toolButton("Basic XML Layout Preview", new View.OnClickListener() {
            @Override public void onClick(View view) { chooseLayoutPreview(); }
        }));

        TextView note = new TextView(this);
        note.setText("Preview policy: local project content only. Basic XML layout preview is a source-structure preview, not Android Studio LayoutLib rendering. Unsupported custom views/Compose are not simulated.");
        note.setTextSize(12f);
        note.setPadding(0, dp(12), 0, 0);
        root.addView(note);
        return scroll;
    }

    private Button toolButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return button;
    }

    // ---------------------------------------------------------------------------------------------
    // Resource Manager

    private void showResourceManager() {
        if (!requireProject("Resource Manager")) return;
        try {
            File res = findResDirectory(projectRoot);
            if (res == null) {
                showMessage("Resource Manager", "No src/main/res directory was found in the loaded project.");
                return;
            }
            final List<ResourceItem> resources = ResourceIndexService.scan(res, MAX_RESOURCE_ITEMS);
            if (resources.isEmpty()) {
                showMessage("Resource Manager", "No resource files were found under " + res.getAbsolutePath());
                return;
            }
            CharSequence[] rows = new CharSequence[resources.size()];
            for (int i = 0; i < resources.size(); i++) rows[i] = resources.get(i).toString();
            new AlertDialog.Builder(this)
                    .setTitle("Resources · " + resources.size())
                    .setItems(rows, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            showResource(resources.get(which));
                        }
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } catch (Exception error) {
            showMessage("Resource Manager", "RESOURCE ERROR: " + safeMessage(error));
        }
    }

    private void showResource(final ResourceItem item) {
        final File file = item.getFile();
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(14), dp(8), dp(14), 0);

        TextView reference = new TextView(this);
        reference.setText(item.getReference() + "\n" + file.getAbsolutePath());
        reference.setTextIsSelectable(true);
        body.addView(reference);

        if (isBitmap(file)) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                ImageView preview = new ImageView(this);
                preview.setAdjustViewBounds(true);
                preview.setImageBitmap(bitmap);
                body.addView(preview, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(260)));
            }
        } else if (file.length() <= MAX_PREVIEW_TEXT_BYTES) {
            try {
                TextView source = codeText(readUtf8(file, MAX_PREVIEW_TEXT_BYTES));
                ScrollView sourceScroll = new ScrollView(this);
                sourceScroll.addView(source);
                body.addView(sourceScroll, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(260)));
            } catch (IOException ignored) { }
        }

        new AlertDialog.Builder(this)
                .setTitle(item.getType() + "/" + item.getName())
                .setView(body)
                .setPositiveButton("Copy Reference", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { copyText("Resource reference", item.getReference()); }
                })
                .setNeutralButton("Copy Path", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { copyText("Resource path", file.getAbsolutePath()); }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    // ---------------------------------------------------------------------------------------------
    // Color Picker

    private void showColorPicker() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(6), dp(14), 0);

        final TextView preview = new TextView(this);
        preview.setText("Preview");
        preview.setGravity(Gravity.CENTER);
        preview.setTextColor(Color.WHITE);
        preview.setBackgroundColor(Color.rgb(33, 150, 243));
        root.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(90)));

        final EditText value = new EditText(this);
        value.setSingleLine(true);
        value.setHint("#AARRGGBB or #RRGGBB");
        value.setText("#FF2196F3");
        root.addView(value);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button apply = new Button(this); apply.setText("Apply Preview");
        Button copyHex = new Button(this); copyHex.setText("Copy HEX");
        Button copyXml = new Button(this); copyXml.setText("Copy XML");
        buttons.addView(apply); buttons.addView(copyHex); buttons.addView(copyXml);
        root.addView(buttons);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Color Picker · Android #AARRGGBB")
                .setView(root)
                .setNegativeButton("Close", null)
                .create();

        apply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    int color = Color.parseColor(normalizeColor(value.getText() == null ? "" : value.getText().toString()));
                    preview.setBackgroundColor(color);
                    preview.setText("ARGB " + normalizeColor(value.getText().toString()));
                } catch (Exception error) {
                    Toast.makeText(DeveloperToolsActivity.this, "Invalid Android color", Toast.LENGTH_SHORT).show();
                }
            }
        });
        copyHex.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try { copyText("HEX color", normalizeColor(value.getText() == null ? "" : value.getText().toString())); }
                catch (Exception error) { Toast.makeText(DeveloperToolsActivity.this, "Invalid Android color", Toast.LENGTH_SHORT).show(); }
            }
        });
        copyXml.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    String color = normalizeColor(value.getText() == null ? "" : value.getText().toString());
                    copyText("Color XML", "<color name=\"color_name\">" + color + "</color>");
                } catch (Exception error) { Toast.makeText(DeveloperToolsActivity.this, "Invalid Android color", Toast.LENGTH_SHORT).show(); }
            }
        });
        dialog.show();
    }

    private static String normalizeColor(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.US);
        if (!value.startsWith("#")) value = "#" + value;
        if (!(value.matches("#[0-9A-F]{6}") || value.matches("#[0-9A-F]{8}"))) {
            throw new IllegalArgumentException("Invalid color");
        }
        return value;
    }

    // ---------------------------------------------------------------------------------------------
    // SQLite Database Viewer

    private void chooseDatabase() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_DATABASE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_DATABASE || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        try {
            replaceDatabaseSnapshot(data.getData());
            showDatabaseViewer(databaseSnapshot);
        } catch (Exception error) {
            showMessage("Database Viewer", "DATABASE ERROR: " + safeMessage(error));
        }
    }

    private void replaceDatabaseSnapshot(Uri uri) throws IOException {
        deleteDatabaseSnapshot();
        File dir = new File(getCacheDir(), "database-viewer");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Could not create database snapshot directory");
        File target = new File(dir, "snapshot-" + System.nanoTime() + ".db");
        InputStream raw = getContentResolver().openInputStream(uri);
        if (raw == null) throw new IOException("Could not open selected database");
        InputStream input = new BufferedInputStream(raw);
        OutputStream output = null;
        long total = 0L;
        try {
            output = new BufferedOutputStream(new FileOutputStream(target));
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_DATABASE_BYTES) throw new IOException("Database exceeds 512 MiB snapshot safety limit");
                output.write(buffer, 0, read);
            }
            output.flush();
            if (total == 0L) throw new IOException("Selected database is empty");
            databaseSnapshot = target;
        } catch (IOException error) {
            if (target.exists()) target.delete();
            throw error;
        } finally {
            try { input.close(); } catch (IOException ignored) { }
            if (output != null) try { output.close(); } catch (IOException ignored) { }
        }
    }

    private void showDatabaseViewer(final File databaseFile) {
        final ArrayList<String> tables = new ArrayList<String>();
        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            database = SQLiteDatabase.openDatabase(databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name", null);
            while (cursor.moveToNext()) tables.add(cursor.getString(0));
        } finally {
            if (cursor != null) cursor.close();
            if (database != null) database.close();
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(6), dp(14), 0);
        TextView summary = new TextView(this);
        summary.setText("Read-only snapshot\nTables: " + tables.toString());
        summary.setTextIsSelectable(true);
        root.addView(summary);

        final EditText query = new EditText(this);
        query.setGravity(Gravity.TOP | Gravity.LEFT);
        query.setMinLines(3);
        query.setText(tables.isEmpty() ? "SELECT name, type FROM sqlite_master LIMIT 100" : "SELECT * FROM \"" + tables.get(0).replace("\"", "\"\"") + "\" LIMIT 100");
        root.addView(query);

        final TextView output = codeText("");
        ScrollView outputScroll = new ScrollView(this);
        outputScroll.addView(output);
        root.addView(outputScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(260)));

        Button run = new Button(this);
        run.setText("Run Read-Only Query");
        root.addView(run);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("SQLite Database Viewer")
                .setView(root)
                .setNegativeButton("Close", null)
                .create();

        run.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String sql = query.getText() == null ? "" : query.getText().toString();
                if (!SqlQueryGuard.isReadOnly(sql)) {
                    output.setText("Blocked: only SELECT / PRAGMA / WITH / EXPLAIN queries are allowed in the read-only viewer.");
                    return;
                }
                try { output.setText(executeReadOnlyQuery(databaseFile, sql)); }
                catch (Exception error) { output.setText("QUERY ERROR: " + safeMessage(error)); }
            }
        });
        dialog.show();
    }

    private static String executeReadOnlyQuery(File databaseFile, String sql) {
        SQLiteDatabase database = null;
        Cursor cursor = null;
        StringBuilder out = new StringBuilder();
        try {
            database = SQLiteDatabase.openDatabase(databaseFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            cursor = database.rawQuery(sql, null);
            String[] columns = cursor.getColumnNames();
            appendRow(out, columns);
            int rows = 0;
            while (cursor.moveToNext() && rows < MAX_QUERY_ROWS && out.length() < MAX_QUERY_CHARS) {
                String[] values = new String[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    if (cursor.isNull(i)) values[i] = "NULL";
                    else if (cursor.getType(i) == Cursor.FIELD_TYPE_BLOB) values[i] = "<BLOB " + cursor.getBlob(i).length + " bytes>";
                    else values[i] = cursor.getString(i);
                }
                appendRow(out, values);
                rows++;
            }
            if (!cursor.isAfterLast()) out.append("… output limited to ").append(MAX_QUERY_ROWS).append(" rows\n");
            if (out.length() > MAX_QUERY_CHARS) out.setLength(MAX_QUERY_CHARS);
            return out.toString();
        } finally {
            if (cursor != null) cursor.close();
            if (database != null) database.close();
        }
    }

    private static void appendRow(StringBuilder out, String[] values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(" | ");
            String value = values[i] == null ? "" : values[i].replace('\n', ' ').replace('\r', ' ');
            if (value.length() > 200) value = value.substring(0, 200) + "…";
            out.append(value);
        }
        out.append('\n');
    }

    // ---------------------------------------------------------------------------------------------
    // HTML preview

    private void chooseHtmlPreview() {
        if (!requireProject("HTML Preview")) return;
        final List<File> files = discoverFiles(projectRoot, ".html", 200);
        if (files.isEmpty()) {
            showMessage("HTML Preview", "No .html files were found in the loaded project.");
            return;
        }
        CharSequence[] names = relativeNames(files);
        new AlertDialog.Builder(this)
                .setTitle("Choose HTML file")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { showHtmlPreview(files.get(which)); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHtmlPreview(final File html) {
        final WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isSafeLocalPreviewUrl(url);
            }
        });
        webView.loadUrl(Uri.fromFile(html).toString());
        new AlertDialog.Builder(this)
                .setTitle("Local HTML Preview · " + html.getName())
                .setView(webView)
                .setPositiveButton("Reload", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { webView.reload(); }
                })
                .setNegativeButton("Close", null)
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) { webView.stopLoading(); webView.destroy(); }
                })
                .show();
    }

    private boolean isSafeLocalPreviewUrl(String url) {
        if (url == null || projectRoot == null) return false;
        try {
            Uri uri = Uri.parse(url);
            if (!"file".equalsIgnoreCase(uri.getScheme())) return false;
            File file = new File(uri.getPath()).getCanonicalFile();
            return isContained(projectRoot, file);
        } catch (Exception ignored) { return false; }
    }

    // ---------------------------------------------------------------------------------------------
    // Layout preview

    private void chooseLayoutPreview() {
        if (!requireProject("Layout Preview")) return;
        File res = findResDirectory(projectRoot);
        if (res == null) {
            showMessage("Layout Preview", "No Android res directory was found.");
            return;
        }
        final List<File> files = discoverLayoutXml(res, 300);
        if (files.isEmpty()) {
            showMessage("Layout Preview", "No layout XML files were found.");
            return;
        }
        CharSequence[] names = relativeNames(files);
        new AlertDialog.Builder(this)
                .setTitle("Choose layout XML")
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { showLayoutSourcePreview(files.get(which)); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLayoutSourcePreview(File file) {
        try {
            String source = readUtf8(file, MAX_PREVIEW_TEXT_BYTES);
            String summary = "Basic XML layout preview\n"
                    + "This is a bounded source-structure preview, not Android Studio LayoutLib. "
                    + "Runtime-only custom views, data binding, Compose, and theme rendering are not simulated.\n\n"
                    + summarizeXmlTags(source)
                    + "\n--- XML source ---\n" + source;
            TextView text = codeText(summary);
            ScrollView scroll = new ScrollView(this);
            scroll.addView(text);
            new AlertDialog.Builder(this)
                    .setTitle(file.getName())
                    .setView(scroll)
                    .setPositiveButton("Copy Path", new CopyClickListener(file.getAbsolutePath()))
                    .setNegativeButton("Close", null)
                    .show();
        } catch (Exception error) {
            showMessage("Layout Preview", "PREVIEW ERROR: " + safeMessage(error));
        }
    }

    private static String summarizeXmlTags(String source) {
        StringBuilder out = new StringBuilder("Detected elements:\n");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<\\s*([A-Za-z_][A-Za-z0-9_.$:-]*)\\b").matcher(source);
        int count = 0;
        while (matcher.find() && count < 100) {
            String tag = matcher.group(1);
            if (!"manifest".equals(tag) && !tag.startsWith("?")) out.append(" • ").append(tag).append('\n');
            count++;
        }
        if (count == 0) out.append(" (no XML elements detected)\n");
        if (matcher.find()) out.append(" … element summary truncated\n");
        return out.toString();
    }

    // ---------------------------------------------------------------------------------------------
    // Shared helpers

    private boolean requireProject(String tool) {
        if (projectRoot != null && projectRoot.isDirectory()) return true;
        showMessage(tool, "Load an Android project first.");
        return false;
    }

    private static File findResDirectory(File root) {
        if (root == null) return null;
        File common = new File(root, "app/src/main/res");
        if (common.isDirectory()) return common;
        ArrayDeque<FileDepth> queue = new ArrayDeque<FileDepth>();
        queue.add(new FileDepth(root, 0));
        int seen = 0;
        while (!queue.isEmpty() && seen < 2000) {
            FileDepth current = queue.removeFirst();
            if (current.depth > 5) continue;
            File dir = current.file;
            if ("res".equals(dir.getName()) && dir.getParentFile() != null
                    && "main".equals(dir.getParentFile().getName())) return dir;
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                if (!child.isDirectory() || skipDirectory(child.getName())) continue;
                seen++;
                queue.addLast(new FileDepth(child, current.depth + 1));
            }
        }
        return null;
    }

    private static List<File> discoverFiles(File root, String suffix, int maxResults) {
        if (root == null || !root.isDirectory()) return Collections.emptyList();
        ArrayList<File> results = new ArrayList<File>();
        ArrayDeque<File> queue = new ArrayDeque<File>();
        queue.add(root);
        int seen = 0;
        while (!queue.isEmpty() && seen < MAX_DISCOVERY_FILES && results.size() < maxResults) {
            File dir = queue.removeFirst();
            File[] children = dir.listFiles();
            if (children == null) continue;
            for (File child : children) {
                seen++;
                if (child.isDirectory()) {
                    if (!skipDirectory(child.getName())) queue.addLast(child);
                } else if (child.getName().toLowerCase(java.util.Locale.US).endsWith(suffix)) {
                    try {
                        File canonical = child.getCanonicalFile();
                        if (isContained(root, canonical)) results.add(canonical);
                    } catch (IOException ignored) { }
                }
                if (seen >= MAX_DISCOVERY_FILES || results.size() >= maxResults) break;
            }
        }
        Collections.sort(results, FILE_ORDER);
        return results;
    }

    private static List<File> discoverLayoutXml(File res, int maxResults) {
        ArrayList<File> results = new ArrayList<File>();
        File[] dirs = res.listFiles();
        if (dirs == null) return results;
        for (File dir : dirs) {
            if (!dir.isDirectory() || !(dir.getName().equals("layout") || dir.getName().startsWith("layout-"))) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".xml")) results.add(file);
                if (results.size() >= maxResults) break;
            }
            if (results.size() >= maxResults) break;
        }
        Collections.sort(results, FILE_ORDER);
        return results;
    }

    private CharSequence[] relativeNames(List<File> files) {
        CharSequence[] names = new CharSequence[files.size()];
        for (int i = 0; i < files.size(); i++) names[i] = relativeName(projectRoot, files.get(i));
        return names;
    }

    private static String relativeName(File root, File file) {
        if (root == null) return file.getName();
        try {
            String rp = root.getCanonicalPath();
            String fp = file.getCanonicalPath();
            String prefix = rp.endsWith(File.separator) ? rp : rp + File.separator;
            return fp.startsWith(prefix) ? fp.substring(prefix.length()) : file.getName();
        } catch (IOException ignored) { return file.getName(); }
    }

    private static boolean isContained(File root, File file) throws IOException {
        String rp = root.getCanonicalPath();
        String fp = file.getCanonicalPath();
        String prefix = rp.endsWith(File.separator) ? rp : rp + File.separator;
        return fp.equals(rp) || fp.startsWith(prefix);
    }

    private static boolean skipDirectory(String name) {
        return name == null || name.length() == 0 || name.charAt(0) == '.'
                || "build".equals(name) || ".gradle".equals(name) || ".git".equals(name)
                || ".idea".equals(name) || ".cxx".equals(name) || "node_modules".equals(name);
    }

    private static final Comparator<File> FILE_ORDER = new Comparator<File>() {
        @Override public int compare(File left, File right) {
            return left.getAbsolutePath().compareToIgnoreCase(right.getAbsolutePath());
        }
    };

    private static boolean isBitmap(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.US);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
    }

    private TextView codeText(String value) {
        TextView text = new TextView(this);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setTextSize(11f);
        text.setTextIsSelectable(true);
        text.setPadding(dp(8), dp(8), dp(8), dp(8));
        text.setText(value == null ? "" : value);
        return text;
    }

    private void copyText(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
    }

    private void showMessage(String title, String message) {
        TextView text = new TextView(this);
        text.setText(message);
        text.setTextIsSelectable(true);
        text.setPadding(dp(16), dp(8), dp(16), 0);
        new AlertDialog.Builder(this).setTitle(title).setView(text).setPositiveButton("OK", null).show();
    }

    private static String readUtf8(File file, long maxBytes) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Preview file is missing");
        if (file.length() > maxBytes) throw new IOException("Preview file is too large");
        InputStream input = new BufferedInputStream(new FileInputStream(file));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[16 * 1024];
            int read;
            long total = 0L;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IOException("Preview file is too large");
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        } finally {
            try { input.close(); } catch (IOException ignored) { }
            try { output.close(); } catch (IOException ignored) { }
        }
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().length() == 0 ? error.getClass().getSimpleName() : message;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class CopyClickListener implements DialogInterface.OnClickListener {
        private final String value;
        CopyClickListener(String value) { this.value = value; }
        @Override public void onClick(DialogInterface dialog, int which) { copyText("Path", value); }
    }

    private static final class FileDepth {
        final File file;
        final int depth;
        FileDepth(File file, int depth) { this.file = file; this.depth = depth; }
    }

    @Override protected void onDestroy() {
        deleteDatabaseSnapshot();
        super.onDestroy();
    }

    private void deleteDatabaseSnapshot() {
        File snapshot = databaseSnapshot;
        databaseSnapshot = null;
        if (snapshot != null && snapshot.exists()) snapshot.delete();
    }
}
