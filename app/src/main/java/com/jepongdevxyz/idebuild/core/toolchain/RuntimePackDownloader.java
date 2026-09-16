package com.jepongdevxyz.idebuild.core.toolchain;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;

/** HTTPS + SHA-256 verified, atomic runtime pack downloader. */
public final class RuntimePackDownloader {
    private static final int BUFFER_SIZE = 64 * 1024;

    public interface InputStreamFactory {
        InputStream open() throws IOException;
    }

    public interface CancellationSignal {
        boolean isCancelled();
    }

    public interface ProgressListener {
        void onProgress(long downloadedBytes, long expectedBytes);
    }

    public static final class DownloadCanceledException extends IOException {
        public DownloadCanceledException() { super("Runtime download canceled"); }
    }

    public static final CancellationSignal NEVER_CANCELLED = new CancellationSignal() {
        @Override public boolean isCancelled() { return false; }
    };

    public static final ProgressListener NO_PROGRESS = new ProgressListener() {
        @Override public void onProgress(long downloadedBytes, long expectedBytes) { }
    };

    private RuntimePackDownloader() {}

    public static File download(RuntimePackDescriptor descriptor, File destination) throws IOException {
        return download(descriptor, destination, NEVER_CANCELLED, NO_PROGRESS);
    }

    public static File download(final RuntimePackDescriptor descriptor,
                                File destination,
                                CancellationSignal cancellation,
                                ProgressListener progress) throws IOException {
        if (descriptor == null) throw new IOException("Runtime descriptor is missing");
        URI uri;
        try { uri = URI.create(descriptor.getUrl()); } catch (Exception e) { throw new IOException("Invalid runtime URL", e); }
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("Runtime download requires HTTPS");
        final URI runtimeUri = uri;
        final RuntimePackDescriptor runtimeDescriptor = descriptor;
        return download(descriptor, destination, new InputStreamFactory() {
            @Override public InputStream open() throws IOException {
                HttpURLConnection connection = (HttpURLConnection) runtimeUri.toURL().openConnection();
                connection.setConnectTimeout(20_000);
                connection.setReadTimeout(60_000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "DevxyzIDE/0.6");
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    connection.disconnect();
                    throw new IOException("Runtime download HTTP " + status);
                }
                long length = connection.getContentLength();
                if (length > 0 && length != runtimeDescriptor.getExpectedBytes()) {
                    connection.disconnect();
                    throw new IOException("Runtime download size mismatch");
                }
                return new DisconnectingInputStream(connection);
            }
        }, cancellation, progress);
    }

    public static File download(RuntimePackDescriptor descriptor,
                                File destination,
                                InputStreamFactory source) throws IOException {
        return download(descriptor, destination, source, NEVER_CANCELLED, NO_PROGRESS);
    }

    public static File download(RuntimePackDescriptor descriptor,
                                File destination,
                                InputStreamFactory source,
                                CancellationSignal cancellation,
                                ProgressListener progress) throws IOException {
        if (descriptor == null) throw new IOException("Runtime descriptor is missing");
        if (destination == null) throw new IOException("Runtime destination is missing");
        if (source == null) throw new IOException("Runtime source is missing");
        final CancellationSignal signal = cancellation == null ? NEVER_CANCELLED : cancellation;
        final ProgressListener listener = progress == null ? NO_PROGRESS : progress;
        checkCancelled(signal);

        File canonicalDestination = destination.getCanonicalFile();
        File parent = canonicalDestination.getParentFile();
        if (parent == null) throw new IOException("Runtime destination has no parent");
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create runtime download directory");

        File temp = new File(parent, "." + canonicalDestination.getName() + ".part-" + System.nanoTime());
        boolean committed = false;
        try {
            MessageDigest digest;
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (Exception e) { throw new IOException("SHA-256 is unavailable", e); }
            long total = 0;
            listener.onProgress(0L, descriptor.getExpectedBytes());
            try (InputStream in = new BufferedInputStream(source.open());
                 BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    checkCancelled(signal);
                    if (total > descriptor.getExpectedBytes() - read) {
                        throw new IOException("Runtime download exceeds declared size");
                    }
                    total += read;
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                    listener.onProgress(total, descriptor.getExpectedBytes());
                    checkCancelled(signal);
                }
                out.flush();
            }
            checkCancelled(signal);
            if (total != descriptor.getExpectedBytes()) throw new IOException("Runtime download size mismatch");
            String actual = hex(digest.digest());
            if (!actual.equals(descriptor.getSha256())) throw new IOException("Runtime download SHA-256 mismatch");
            checkCancelled(signal);

            replaceVerifiedFile(temp, canonicalDestination);
            committed = true;
            return canonicalDestination;
        } finally {
            if (!committed && temp.exists()) temp.delete();
        }
    }

    private static void checkCancelled(CancellationSignal cancellation) throws DownloadCanceledException {
        if (cancellation != null && cancellation.isCancelled()) throw new DownloadCanceledException();
    }

    private static void replaceVerifiedFile(File source, File destination) throws IOException {
        if (!destination.exists()) {
            moveCompat(source, destination);
            return;
        }

        File parent = destination.getParentFile();
        File backup = new File(parent, "." + destination.getName() + ".backup-" + System.nanoTime());
        if (!destination.renameTo(backup)) {
            throw new IOException("Cannot stage existing runtime download for replacement");
        }
        boolean installed = false;
        try {
            moveCompat(source, destination);
            installed = true;
        } finally {
            if (installed) {
                if (backup.exists() && !backup.delete()) {
                    // The verified new runtime is already committed. A stale backup is
                    // non-fatal and can be removed by cache maintenance later.
                }
            } else {
                if (destination.exists()) destination.delete();
                if (backup.exists() && !backup.renameTo(destination)) {
                    throw new IOException("Runtime replacement failed and previous download could not be restored");
                }
            }
        }
    }

    private static void moveCompat(File source, File destination) throws IOException {
        if (source.renameTo(destination)) return;

        BufferedInputStream in = null;
        BufferedOutputStream out = null;
        boolean copied = false;
        try {
            in = new BufferedInputStream(new FileInputStream(source));
            out = new BufferedOutputStream(new FileOutputStream(destination));
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            out.flush();
            copied = true;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
            if (out != null) try { out.close(); } catch (IOException ignored) {}
            if (!copied && destination.exists()) destination.delete();
        }
        if (!source.delete()) {
            destination.delete();
            throw new IOException("Cannot remove temporary runtime download");
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private static final class DisconnectingInputStream extends InputStream {
        private final HttpURLConnection connection;
        private final InputStream delegate;

        DisconnectingInputStream(HttpURLConnection connection) throws IOException {
            this.connection = connection;
            this.delegate = connection.getInputStream();
        }
        @Override public int read() throws IOException { return delegate.read(); }
        @Override public int read(byte[] b, int off, int len) throws IOException { return delegate.read(b, off, len); }
        @Override public void close() throws IOException {
            try { delegate.close(); } finally { connection.disconnect(); }
        }
    }
}
