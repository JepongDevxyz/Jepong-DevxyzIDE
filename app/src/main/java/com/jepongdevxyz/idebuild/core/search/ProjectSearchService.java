package com.jepongdevxyz.idebuild.core.search;

import com.jepongdevxyz.idebuild.core.ProjectDirectoryListing;
import com.jepongdevxyz.idebuild.core.ProjectDirectoryService;
import com.jepongdevxyz.idebuild.core.ProjectEntry;
import com.jepongdevxyz.idebuild.core.ProjectPath;
import com.jepongdevxyz.idebuild.core.TextFileClassifier;
import com.jepongdevxyz.idebuild.core.WorkspacePathResolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/** Streaming, literal, cancel-aware project search that never leaves the workspace root. */
public final class ProjectSearchService {
    private static final int MAX_CHILDREN_PER_DIRECTORY = 100000;

    public interface Listener {
        void onMatch(SearchMatch match);
    }

    public interface Cancellation {
        boolean isCancelled();
    }

    public static final Cancellation NEVER_CANCELLED = new Cancellation() {
        @Override public boolean isCancelled() { return false; }
    };

    private ProjectSearchService() { }

    public static SearchSummary search(WorkspacePathResolver resolver,
                                       ProjectPath startDirectory,
                                       String query,
                                       boolean matchCase,
                                       int maxFiles,
                                       int maxResults,
                                       long maxFileBytes,
                                       Cancellation cancellation,
                                       Listener listener) throws IOException {
        if (resolver == null) throw new IllegalArgumentException("resolver must not be null");
        if (startDirectory == null) throw new IllegalArgumentException("startDirectory must not be null");
        if (query == null || query.length() == 0) throw new IllegalArgumentException("query must not be empty");
        if (maxFiles < 1) throw new IllegalArgumentException("maxFiles must be positive");
        if (maxResults < 1) throw new IllegalArgumentException("maxResults must be positive");
        if (maxFileBytes < 1) throw new IllegalArgumentException("maxFileBytes must be positive");
        if (cancellation == null) cancellation = NEVER_CANCELLED;
        if (listener == null) throw new IllegalArgumentException("listener must not be null");

        MutableSummary state = new MutableSummary(maxFiles, maxResults, maxFileBytes);
        ProjectDirectoryService directoryService = new ProjectDirectoryService(resolver);
        searchDirectory(resolver, directoryService, startDirectory, query, matchCase, cancellation, listener, state);
        return new SearchSummary(state.filesScanned, state.matches, state.cancelled, state.truncated);
    }

    private static boolean searchDirectory(WorkspacePathResolver resolver,
                                           ProjectDirectoryService directoryService,
                                           ProjectPath directory,
                                           String query,
                                           boolean matchCase,
                                           Cancellation cancellation,
                                           Listener listener,
                                           MutableSummary state) throws IOException {
        if (cancellation.isCancelled()) {
            state.cancelled = true;
            return false;
        }
        ProjectDirectoryListing listing = directoryService.listChildren(directory, MAX_CHILDREN_PER_DIRECTORY);
        if (listing.isTruncated()) state.truncated = true;

        for (ProjectEntry entry : listing.getEntries()) {
            if (cancellation.isCancelled()) {
                state.cancelled = true;
                return false;
            }
            if (entry.isDirectory()) {
                if (shouldSkipDirectory(entry.getName())) continue;
                if (!searchDirectory(resolver, directoryService, entry.getPath(), query, matchCase, cancellation, listener, state)) return false;
                continue;
            }

            if (state.filesScanned >= state.maxFiles) {
                state.truncated = true;
                return false;
            }
            if (!TextFileClassifier.isTextFile(entry.getPath().getRelativePath())) continue;
            File file = resolver.resolve(entry.getPath());
            if (!file.isFile() || file.length() > state.maxFileBytes) continue;
            state.filesScanned++;
            if (!searchFile(file, entry.getPath(), query, matchCase, cancellation, listener, state)) return false;
        }
        return true;
    }

    private static boolean searchFile(File file,
                                      ProjectPath path,
                                      String query,
                                      boolean matchCase,
                                      Cancellation cancellation,
                                      Listener listener,
                                      MutableSummary state) throws IOException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"), 32 * 1024);
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (cancellation.isCancelled()) {
                    state.cancelled = true;
                    return false;
                }
                int from = 0;
                while (from <= line.length() - query.length()) {
                    int columnOffset = indexOf(line, query, from, matchCase);
                    if (columnOffset < 0) break;
                    if (state.matches >= state.maxResults) {
                        state.truncated = true;
                        return false;
                    }
                    listener.onMatch(new SearchMatch(path, lineNumber, columnOffset + 1, line));
                    state.matches++;
                    if (cancellation.isCancelled()) {
                        state.cancelled = true;
                        return false;
                    }
                    if (state.matches >= state.maxResults) {
                        state.truncated = true;
                        return false;
                    }
                    from = columnOffset + Math.max(1, query.length());
                }
            }
            return true;
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
        }
    }

    private static int indexOf(String source, String query, int from, boolean matchCase) {
        int max = source.length() - query.length();
        for (int i = Math.max(0, from); i <= max; i++) {
            if (source.regionMatches(!matchCase, i, query, 0, query.length())) return i;
        }
        return -1;
    }

    private static boolean shouldSkipDirectory(String name) {
        return "build".equals(name)
                || ".gradle".equals(name)
                || ".git".equals(name)
                || ".idea".equals(name)
                || ".cxx".equals(name)
                || ".kotlin".equals(name)
                || "out".equals(name);
    }

    public static final class SearchMatch {
        private final ProjectPath path;
        private final int lineNumber;
        private final int columnNumber;
        private final String lineText;

        private SearchMatch(ProjectPath path, int lineNumber, int columnNumber, String lineText) {
            this.path = path;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.lineText = lineText;
        }

        public ProjectPath getPath() { return path; }
        public int getLineNumber() { return lineNumber; }
        public int getColumnNumber() { return columnNumber; }
        public String getLineText() { return lineText; }
    }

    public static final class SearchSummary {
        private final int filesScanned;
        private final int matches;
        private final boolean cancelled;
        private final boolean truncated;

        private SearchSummary(int filesScanned, int matches, boolean cancelled, boolean truncated) {
            this.filesScanned = filesScanned;
            this.matches = matches;
            this.cancelled = cancelled;
            this.truncated = truncated;
        }

        public int getFilesScanned() { return filesScanned; }
        public int getMatches() { return matches; }
        public boolean isCancelled() { return cancelled; }
        public boolean isTruncated() { return truncated; }
    }

    private static final class MutableSummary {
        private final int maxFiles;
        private final int maxResults;
        private final long maxFileBytes;
        private int filesScanned;
        private int matches;
        private boolean cancelled;
        private boolean truncated;

        private MutableSummary(int maxFiles, int maxResults, long maxFileBytes) {
            this.maxFiles = maxFiles;
            this.maxResults = maxResults;
            this.maxFileBytes = maxFileBytes;
        }
    }
}
