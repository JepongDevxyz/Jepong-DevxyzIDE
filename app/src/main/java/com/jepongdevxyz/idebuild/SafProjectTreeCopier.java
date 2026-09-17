package com.jepongdevxyz.idebuild;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import com.jepongdevxyz.idebuild.core.ProjectImportService;
import com.jepongdevxyz.idebuild.core.ProjectRootTracker;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Streams an Android Storage Access Framework document tree into a local
 * staging directory. The copier never resolves content:// URIs to filesystem
 * paths and never builds a complete in-memory project tree.
 */
public final class SafProjectTreeCopier {
    private static final int BUFFER_SIZE = 64 * 1024;

    public interface ProgressListener {
        void onProgress(long filesProcessed, long bytesCopied, String currentRelativePath) throws IOException;
    }

    public static final ProgressListener NO_PROGRESS = new ProgressListener() {
        @Override public void onProgress(long filesProcessed, long bytesCopied, String currentRelativePath) { }
    };

    public static final class CopyResult {
        private final long filesProcessed;
        private final long bytesCopied;
        private final String projectRootRelativePath;

        CopyResult(long filesProcessed, long bytesCopied, String projectRootRelativePath) {
            this.filesProcessed = filesProcessed;
            this.bytesCopied = bytesCopied;
            this.projectRootRelativePath = projectRootRelativePath == null ? "" : projectRootRelativePath;
        }

        public long getFilesProcessed() { return filesProcessed; }
        public long getBytesCopied() { return bytesCopied; }
        public String getProjectRootRelativePath() { return projectRootRelativePath; }
    }

    private SafProjectTreeCopier() { }

    public static CopyResult copyTree(ContentResolver resolver,
                                      Uri treeUri,
                                      File stagingDirectory,
                                      ProjectImportService.CancellationSignal cancellation,
                                      ProgressListener progress,
                                      ProjectImportService.StorageProbe storageProbe) throws IOException {
        if (resolver == null) throw new IllegalArgumentException("resolver must not be null");
        if (treeUri == null) throw new IllegalArgumentException("treeUri must not be null");
        if (stagingDirectory == null || !stagingDirectory.isDirectory()) {
            throw new IllegalArgumentException("stagingDirectory must be an existing directory");
        }

        ProjectImportService.CancellationSignal signal = cancellation == null
                ? ProjectImportService.NEVER_CANCELLED : cancellation;
        ProgressListener listener = progress == null ? NO_PROGRESS : progress;
        ProjectImportService.StorageProbe probe = storageProbe == null
                ? ProjectImportService.NO_STORAGE_PROBE : storageProbe;

        String rootDocumentId;
        try {
            rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (RuntimeException e) {
            throw new IOException("Unsupported or invalid document-tree provider", e);
        }
        if (rootDocumentId == null || rootDocumentId.length() == 0) {
            throw new IOException("Document provider did not expose a root document");
        }

        File canonicalStaging = stagingDirectory.getCanonicalFile();
        String canonicalRoot = canonicalStaging.getCanonicalPath();
        String canonicalPrefix = canonicalRoot + File.separator;
        ProjectRootTracker rootTracker = new ProjectRootTracker();
        Deque<DirectoryWork> pending = new ArrayDeque<DirectoryWork>();
        pending.push(new DirectoryWork(rootDocumentId, ""));

        byte[] buffer = new byte[BUFFER_SIZE];
        long filesProcessed = 0L;
        long bytesCopied = 0L;
        probe.check(canonicalStaging, 0L);

        while (!pending.isEmpty()) {
            checkCancelled(signal);
            DirectoryWork work = pending.pop();
            Uri directoryUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, work.documentId);
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(directoryUri, work.documentId);
            Cursor cursor = null;
            try {
                cursor = resolver.query(childrenUri,
                        new String[] {
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                                DocumentsContract.Document.COLUMN_MIME_TYPE,
                                DocumentsContract.Document.COLUMN_SIZE
                        }, null, null, null);
                if (cursor == null) throw new IOException("Document provider returned no directory cursor");

                int idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
                if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) {
                    throw new IOException("Document provider is missing required metadata");
                }

                while (cursor.moveToNext()) {
                    checkCancelled(signal);
                    String documentId = cursor.getString(idColumn);
                    String displayName = cursor.getString(nameColumn);
                    String mimeType = cursor.getString(mimeColumn);
                    validateChildName(displayName);

                    String relative = work.relativePath.length() == 0
                            ? displayName
                            : work.relativePath + "/" + displayName;
                    if (shouldSkip(relative, displayName)) continue;

                    File target = new File(canonicalStaging, relative.replace('/', File.separatorChar)).getCanonicalFile();
                    requireContained(canonicalRoot, canonicalPrefix, target);

                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)) {
                        if (target.exists() && !target.isDirectory()) {
                            throw new IOException("Import path conflicts with a file: " + relative);
                        }
                        if (!target.exists() && !target.mkdirs()) {
                            throw new IOException("Cannot create import directory: " + relative);
                        }
                        pending.push(new DirectoryWork(documentId, relative));
                        listener.onProgress(filesProcessed, bytesCopied, relative + "/");
                    } else {
                        if (target.exists()) throw new IOException("Duplicate/conflicting import path: " + relative);
                        File parent = target.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            throw new IOException("Cannot create import directory: " + relative);
                        }
                        Uri documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                        InputStream raw = null;
                        InputStream input = null;
                        OutputStream output = null;
                        try {
                            raw = resolver.openInputStream(documentUri);
                            if (raw == null) throw new IOException("Cannot open source document: " + relative);
                            input = new BufferedInputStream(raw);
                            output = new BufferedOutputStream(new FileOutputStream(target));
                            int read;
                            while ((read = input.read(buffer)) != -1) {
                                checkCancelled(signal);
                                if (read > 0 && bytesCopied > Long.MAX_VALUE - read) {
                                    throw new IOException("Imported byte counter overflow");
                                }
                                probe.check(canonicalStaging, bytesCopied);
                                output.write(buffer, 0, read);
                                bytesCopied += read;
                                listener.onProgress(filesProcessed, bytesCopied, relative);
                            }
                            output.flush();
                        } catch (SecurityException e) {
                            throw new IOException("Permission denied while reading: " + relative, e);
                        } finally {
                            closeQuietly(output);
                            closeQuietly(input);
                            if (input == null) closeQuietly(raw);
                        }
                        filesProcessed++;
                        rootTracker.onEntry(relative);
                        listener.onProgress(filesProcessed, bytesCopied, relative);
                    }
                }
            } catch (SecurityException e) {
                throw new IOException("Permission denied while reading project folder", e);
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        checkCancelled(signal);
        String rootRelative = rootTracker.getBestRootRelativePath();
        return new CopyResult(filesProcessed, bytesCopied, rootRelative == null ? "" : rootRelative);
    }

    private static void checkCancelled(ProjectImportService.CancellationSignal signal) throws ProjectImportService.ImportCanceledException {
        if (signal != null && signal.isCancelled()) throw new ProjectImportService.ImportCanceledException();
    }

    private static void validateChildName(String name) throws IOException {
        if (name == null || name.length() == 0) throw new IOException("Invalid empty document name");
        if (name.equals(".") || name.equals("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\u0000') >= 0) {
            throw new IOException("Unsafe document name: " + name);
        }
    }

    private static void requireContained(String canonicalRoot, String canonicalPrefix, File target) throws IOException {
        String path = target.getCanonicalPath();
        if (!path.equals(canonicalRoot) && !path.startsWith(canonicalPrefix)) {
            throw new IOException("Imported project path escaped the staging root");
        }
    }

    private static boolean shouldSkip(String relative, String name) {
        if (name.equals("build") || name.equals(".gradle") || name.equals(".git") ||
                name.equals(".idea") || name.equals(".cxx") ||
                name.equals(".externalNativeBuild") || name.equals(".androidide")) {
            return true;
        }
        return relative.equals("local.properties") || relative.endsWith("/local.properties");
    }

    private static void closeQuietly(java.io.Closeable closeable) {
        if (closeable != null) {
            try { closeable.close(); } catch (IOException ignored) { }
        }
    }

    private static final class DirectoryWork {
        final String documentId;
        final String relativePath;

        DirectoryWork(String documentId, String relativePath) {
            this.documentId = documentId;
            this.relativePath = relativePath;
        }
    }
}
