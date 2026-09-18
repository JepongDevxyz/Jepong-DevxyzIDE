package com.jepongdevxyz.idebuild;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.jepongdevxyz.idebuild.core.ProjectImportService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/** Coordinates real ZIP/folder project imports and their responsive progress UI. */
public final class ProjectImportController {
    private static final long UI_PROGRESS_INTERVAL_MS = 150L;

    public interface Listener {
        File getProjectStorageDirectory() throws IOException;
        void onImportMessage(String message);
        void onProjectImported(File projectRoot);
    }

    private final Activity activity;
    private final ExecutorService io;
    private final Listener listener;
    private final Object stateLock = new Object();

    private volatile ImportCancellation cancellation;
    private volatile InputStream activeInput;
    private volatile boolean busy;
    private AlertDialog progressDialog;
    private TextView filesText;
    private TextView copiedText;
    private TextView currentText;
    private long lastUiProgressAt;

    public ProjectImportController(Activity activity, ExecutorService io, Listener listener) {
        if (activity == null) throw new IllegalArgumentException("activity must not be null");
        if (io == null) throw new IllegalArgumentException("io must not be null");
        if (listener == null) throw new IllegalArgumentException("listener must not be null");
        this.activity = activity;
        this.io = io;
        this.listener = listener;
    }

    public boolean isBusy() { return busy; }

    public void importZip(final Uri uri) {
        if (uri == null || !beginImport("Importing Project")) return;
        final String displayName = displayName(uri, "project.zip");
        listener.onImportMessage("Importing ZIP: " + displayName);
        io.execute(new Runnable() {
            @Override public void run() {
                InputStream raw = null;
                InputStream input = null;
                try {
                    final ImportCancellation token = cancellation;
                    final File projects = listener.getProjectStorageDirectory();
                    ContentResolver resolver = activity.getContentResolver();
                    raw = resolver.openInputStream(uri);
                    if (raw == null) throw new IOException("Cannot open ZIP input stream");
                    input = new BufferedInputStream(raw);
                    activeInput = input;

                    ProjectImportService.ImportResult result = ProjectImportService.importProject(
                            input,
                            projects,
                            displayName,
                            token,
                            new ProjectImportService.ProgressListener() {
                                @Override public void onProgress(int entries, long expandedBytes) {
                                    postProgress(entries, expandedBytes, "Streaming ZIP contents…");
                                }
                            },
                            new ProjectImportService.StorageProbe() {
                                @Override public void check(File destinationRoot, long expandedBytes) throws IOException {
                                    checkStorage(destinationRoot, expandedBytes);
                                }
                            });
                    finishSuccess(result.getProjectRoot());
                } catch (Exception e) {
                    finishFailure(e);
                } finally {
                    activeInput = null;
                    closeQuietly(input);
                    if (input == null) closeQuietly(raw);
                }
            }
        });
    }

    public void importFolder(final Uri treeUri) {
        if (treeUri == null || !beginImport("Importing Project Folder")) return;
        final String displayName = treeDisplayName(treeUri);
        listener.onImportMessage("Importing folder: " + displayName);
        io.execute(new Runnable() {
            @Override public void run() {
                try {
                    final ImportCancellation token = cancellation;
                    final File projects = listener.getProjectStorageDirectory();
                    final ContentResolver resolver = activity.getContentResolver();
                    ProjectImportService.ImportResult result = ProjectImportService.importPreparedProject(
                            projects,
                            displayName,
                            token,
                            new ProjectImportService.StagingWriter() {
                                @Override public String write(final File stagingDirectory,
                                                              ProjectImportService.CancellationSignal signal) throws IOException {
                                    SafProjectTreeCopier.CopyResult copied = SafProjectTreeCopier.copyTree(
                                            resolver,
                                            treeUri,
                                            stagingDirectory,
                                            signal,
                                            new SafProjectTreeCopier.ProgressListener() {
                                                @Override public void onProgress(long filesProcessed,
                                                                                 long bytesCopied,
                                                                                 String currentRelativePath) {
                                                    postProgress(filesProcessed, bytesCopied, currentRelativePath);
                                                }
                                            },
                                            new ProjectImportService.StorageProbe() {
                                                @Override public void check(File destinationRoot, long expandedBytes) throws IOException {
                                                    checkStorage(destinationRoot, expandedBytes);
                                                }
                                            });
                                    return copied.getProjectRootRelativePath();
                                }
                            });
                    finishSuccess(result.getProjectRoot());
                } catch (Exception e) {
                    finishFailure(e);
                }
            }
        });
    }

    public void cancel() {
        ImportCancellation token = cancellation;
        if (token != null) token.cancelled = true;
        InputStream stream = activeInput;
        if (stream != null) closeQuietly(stream);
        postToLiveActivity(new Runnable() {
            @Override public void run() {
                if (currentText != null) currentText.setText("Canceling safely…");
                Button button = progressDialog == null ? null : progressDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                if (button != null) button.setEnabled(false);
            }
        });
    }

    private boolean isActivityAlive() {
        if (activity.isFinishing()) return false;
        return android.os.Build.VERSION.SDK_INT < 17 || !activity.isDestroyed();
    }

    private void postToLiveActivity(Runnable action) {
        if (action == null || !isActivityAlive()) return;
        activity.runOnUiThread(new LiveActivityRunnable(action));
    }

    private final class LiveActivityRunnable implements Runnable {
        private final Runnable action;
        private LiveActivityRunnable(Runnable action) { this.action = action; }
        @Override public void run() {
            if (isActivityAlive()) action.run();
        }
    }

    private boolean beginImport(final String title) {
        synchronized (stateLock) {
            if (busy) return false;
            busy = true;
            cancellation = new ImportCancellation();
            lastUiProgressAt = 0L;
        }
        postToLiveActivity(new Runnable() {
            @Override public void run() { showProgressDialog(title); }
        });
        return true;
    }

    private void showProgressDialog(String title) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, dp(8), padding, dp(4));

        ProgressBar progress = new ProgressBar(activity);
        progress.setIndeterminate(true);
        content.addView(progress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        filesText = new TextView(activity);
        filesText.setText("Files: starting…");
        content.addView(filesText);

        copiedText = new TextView(activity);
        copiedText.setText("Copied: 0 B");
        content.addView(copiedText);

        currentText = new TextView(activity);
        currentText.setText("Current: preparing source…");
        currentText.setMaxLines(2);
        content.addView(currentText);

        progressDialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(content)
                .setNegativeButton("Cancel", null)
                .create();
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface dialog) {
                Button cancelButton = progressDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { cancel(); }
                });
            }
        });
        progressDialog.show();
    }

    private void postProgress(final long files, final long bytes, final String current) {
        long now = SystemClock.uptimeMillis();
        if (now - lastUiProgressAt < UI_PROGRESS_INTERVAL_MS) return;
        lastUiProgressAt = now;
        postToLiveActivity(new Runnable() {
            @Override public void run() {
                if (!busy) return;
                if (filesText != null) filesText.setText("Files: " + files + " processed");
                if (copiedText != null) copiedText.setText("Copied: " + humanBytes(bytes));
                if (currentText != null) currentText.setText("Current: " + (current == null ? "…" : current));
            }
        });
    }

    private void checkStorage(File destinationRoot, long copiedBytes) throws IOException {
        if (cancellation != null && cancellation.isCancelled()) {
            throw new ProjectImportService.ImportCanceledException();
        }
        File probe = destinationRoot;
        while (probe != null && !probe.exists()) probe = probe.getParentFile();
        if (probe == null) throw new IOException("Import destination is unavailable");
        long usable = probe.getUsableSpace();
        if (usable <= 0L) {
            throw new IOException("Not enough free storage");
        }
    }

    private void finishSuccess(final File projectRoot) {
        postToLiveActivity(new Runnable() {
            @Override public void run() {
                clearBusyState();
                listener.onImportMessage("IMPORT COMPLETE: " + projectRoot.getAbsolutePath());
                listener.onProjectImported(projectRoot);
            }
        });
    }

    private void finishFailure(final Exception failure) {
        postToLiveActivity(new Runnable() {
            @Override public void run() {
                boolean canceled = failure instanceof ProjectImportService.ImportCanceledException ||
                        (cancellation != null && cancellation.isCancelled());
                clearBusyState();
                if (canceled) {
                    listener.onImportMessage("IMPORT CANCELED");
                    showResultDialog("Import canceled", "The partial import was not activated as a project.");
                    return;
                }
                String detail = friendlyError(failure);
                listener.onImportMessage("IMPORT ERROR: " + detail);
                showResultDialog("Import failed", detail);
            }
        });
    }

    private void clearBusyState() {
        synchronized (stateLock) {
            busy = false;
            cancellation = null;
            activeInput = null;
        }
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
        filesText = null;
        copiedText = null;
        currentText = null;
    }

    private void showResultDialog(String title, String message) {
        if (activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private String friendlyError(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.trim().length() == 0) message = failure.getClass().getSimpleName();
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("enospc") || lower.contains("no space left") || lower.contains("not enough free storage")) {
            return "Not enough free storage. The partial import was cleaned up.";
        }
        if (lower.contains("permission") || failure instanceof SecurityException) {
            return "Permission denied while reading the selected source.";
        }
        if (lower.contains("unsafe zip") || lower.contains("escaped the staging") || lower.contains("absolute zip")) {
            return "The archive contains an unsafe path and was rejected.";
        }
        if (lower.contains("zip") && (lower.contains("invalid") || lower.contains("corrupt") || lower.contains("error"))) {
            return "The ZIP archive is invalid or corrupted.";
        }
        return message;
    }

    private String displayName(Uri uri, String fallback) {
        Cursor cursor = null;
        try {
            cursor = activity.getContentResolver().query(uri,
                    new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String value = cursor.getString(column);
                    if (value != null && value.length() > 0) return value;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        return fallback;
    }

    private String treeDisplayName(Uri treeUri) {
        Cursor cursor = null;
        try {
            String id = DocumentsContract.getTreeDocumentId(treeUri);
            Uri root = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
            cursor = activity.getContentResolver().query(root,
                    new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                if (column >= 0) {
                    String value = cursor.getString(column);
                    if (value != null && value.length() > 0) return value;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String last = treeUri.getLastPathSegment();
        return last == null || last.length() == 0 ? "project" : last.replace(':', '-');
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double value = bytes;
        String[] units = { "B", "KiB", "MiB", "GiB", "TiB" };
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit]);
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (IOException ignored) { }
        }
    }

    private static final class ImportCancellation implements ProjectImportService.CancellationSignal {
        volatile boolean cancelled;
        @Override public boolean isCancelled() { return cancelled; }
    }
}
