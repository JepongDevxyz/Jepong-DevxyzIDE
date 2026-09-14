package com.jepongdevxyz.idebuild;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public final class ApkFileProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (getContext() == null) throw new FileNotFoundException("No context");
        String encoded = uri.getLastPathSegment();
        if (encoded == null) throw new FileNotFoundException("Missing APK path");
        String path = Uri.decode(encoded);
        try {
            File file = new File(path).getCanonicalFile();
            File files = getContext().getFilesDir().getCanonicalFile();
            File cache = getContext().getCacheDir().getCanonicalFile();
            File external = getContext().getExternalFilesDir(null);
            boolean allowed = startsWith(file, files) || startsWith(file, cache);
            if (external != null) allowed = allowed || startsWith(file, external.getCanonicalFile());
            if (!allowed || !file.isFile()) throw new FileNotFoundException("APK path is outside DevxyzIDE storage");
            return file;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    private static boolean startsWith(File file, File root) {
        String f = file.getPath();
        String r = root.getPath();
        return f.equals(r) || f.startsWith(r + File.separator);
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            MatrixCursor cursor = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            cursor.addRow(new Object[]{file.getName(), Long.valueOf(file.length())});
            return cursor;
        } catch (FileNotFoundException e) {
            return new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
}
