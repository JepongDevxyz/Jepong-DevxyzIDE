package com.jepongdevxyz.idebuild;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;

/**
 * Advanced real Gradle actions backed by BuildRunner.
 * This control never fabricates success and never runs an artifact build over
 * unsaved editor tabs: it asks MainActivity's real Save All action to persist
 * them first and requires the user to retry after saving completes.
 */
public final class BuildActionsButton extends Button {
    private BuildRunner.BuildHandle activeBuild;
    private String activeLabel;

    public BuildActionsButton(Context context) {
        super(context);
        initialize();
    }

    public BuildActionsButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public BuildActionsButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { showBuildActions(); }
        });
    }

    private void showBuildActions() {
        final File project = resolveProjectRoot();
        if (project == null) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Build Actions")
                    .setMessage("Load a Gradle project before running build actions.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        BuildRunner.BuildHandle active = activeBuild;
        if (active != null && !active.isFinished()) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Build running")
                    .setMessage((activeLabel == null ? "Gradle task" : activeLabel) + " is still running.")
                    .setNegativeButton("Keep Running", null)
                    .setPositiveButton("Cancel Build", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            BuildRunner.BuildHandle running = activeBuild;
                            if (running != null && !running.isFinished()) {
                                appendConsole("BUILD: cancellation requested.");
                                running.cancel();
                            }
                        }
                    })
                    .show();
            return;
        }

        final String[] actions = new String[]{
                "Clean",
                "Assemble Debug",
                "Rebuild Debug",
                "Assemble Release",
                "Offline Debug"
        };
        new AlertDialog.Builder(getContext())
                .setTitle("Build Actions")
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) startTask(project, "clean", false, "Clean", null);
                        else if (which == 1) startArtifactTask(project, "assembleDebug", false, "Assemble Debug");
                        else if (which == 2) startRebuild(project);
                        else if (which == 3) startArtifactTask(project, "assembleRelease", false, "Assemble Release");
                        else startArtifactTask(project, "assembleDebug", true, "Offline Debug");
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void startArtifactTask(File project, String task, boolean offline, String label) {
        if (hasDirtyEditorTabs()) {
            View root = getRootView();
            View save = root == null ? null : root.findViewById(R.id.saveAllButton);
            if (save != null && save.isEnabled()) save.performClick();
            appendConsole("BUILD: Save All requested before " + label + ". Run the build action again after saving completes.");
            new AlertDialog.Builder(getContext())
                    .setTitle("Saving open files")
                    .setMessage("DevxyzIDE requested Save All so Gradle will not build stale editor content. Run " + label + " again after saving finishes.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        startTask(project, task, offline, label, null);
    }

    private void startRebuild(final File project) {
        if (hasDirtyEditorTabs()) {
            startArtifactTask(project, "assembleDebug", false, "Rebuild Debug");
            return;
        }
        startTask(project, "clean", false, "Rebuild Debug · Clean", new Completion() {
            @Override public void onFinished(int exitCode, File ignored) {
                if (exitCode == 0) startTask(project, "assembleDebug", false, "Rebuild Debug · Assemble", null);
                else appendConsole("REBUILD: clean failed; assembleDebug was not started.");
            }
        });
    }

    private void startTask(final File project,
                           final String task,
                           final boolean offline,
                           final String label,
                           final Completion completion) {
        activeLabel = label;
        appendConsole("\n> " + label + (offline ? " [offline]" : "") + " · Gradle task " + task);
        activeBuild = BuildRunner.runBuild(project, getContext().getFilesDir(), task, offline, new BuildRunner.Listener() {
            @Override public void onLine(String line) { appendConsole(line); }

            @Override public void onFinished(final int exitCode, final File apk) {
                post(new Runnable() {
                    @Override public void run() {
                        if (exitCode == 0) {
                            appendConsole(label + ": Gradle exited successfully." + (apk == null ? "" : "\nAPK: " + apk.getAbsolutePath()));
                        } else if (exitCode == 130) {
                            appendConsole(label + ": cancelled.");
                        } else {
                            appendConsole(label + ": failed with exit " + exitCode + ".");
                        }
                        activeBuild = null;
                        activeLabel = null;
                        if (completion != null) completion.onFinished(exitCode, apk);
                    }
                });
            }
        });
    }

    private boolean hasDirtyEditorTabs() {
        View root = getRootView();
        View tabView = root == null ? null : root.findViewById(R.id.tabBar);
        if (!(tabView instanceof LinearLayout)) return false;
        LinearLayout tabs = (LinearLayout) tabView;
        for (int i = 0; i < tabs.getChildCount(); i++) {
            View child = tabs.getChildAt(i);
            if (child instanceof TextView) {
                CharSequence text = ((TextView) child).getText();
                if (text != null && text.toString().indexOf("* ") >= 0) return true;
            }
        }
        return false;
    }

    private File resolveProjectRoot() {
        View rootView = getRootView();
        View pathView = rootView == null ? null : rootView.findViewById(R.id.projectPath);
        if (!(pathView instanceof TextView)) return null;
        CharSequence label = ((TextView) pathView).getText();
        if (label == null) return null;
        String raw = label.toString().trim();
        if (raw.length() == 0 || raw.equals(getResources().getString(R.string.no_project))) return null;

        File current = new File(raw);
        try { current = current.getCanonicalFile(); } catch (Exception ignored) { }
        File buildCandidate = null;
        for (int depth = 0; current != null && depth < 64; depth++) {
            if (new File(current, "settings.gradle").isFile()
                    || new File(current, "settings.gradle.kts").isFile()) return current;
            if (buildCandidate == null
                    && (new File(current, "build.gradle").isFile()
                    || new File(current, "build.gradle.kts").isFile())) buildCandidate = current;
            current = current.getParentFile();
        }
        return buildCandidate;
    }

    private void appendConsole(final String line) {
        post(new Runnable() {
            @Override public void run() {
                View root = getRootView();
                View consoleView = root == null ? null : root.findViewById(R.id.consoleText);
                if (!(consoleView instanceof TextView)) return;
                TextView console = (TextView) consoleView;
                if (console.length() > 200000) console.setText("[console truncated]\n");
                console.append((console.length() == 0 ? "" : "\n") + line);
                View scrollView = root.findViewById(R.id.consoleScroll);
                if (scrollView instanceof ScrollView) {
                    final ScrollView scroll = (ScrollView) scrollView;
                    scroll.post(new Runnable() { @Override public void run() { scroll.fullScroll(View.FOCUS_DOWN); } });
                }
            }
        });
    }

    private interface Completion {
        void onFinished(int exitCode, File apk);
    }
}
