package com.jepongdevxyz.idebuild;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jepongdevxyz.idebuild.core.build.ProjectAnalyzer;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;

import java.io.File;
import java.util.List;

/** Read-only project metadata surface derived from the actual Gradle project files. */
public final class ProjectSettingsButton extends Button {
    public ProjectSettingsButton(Context context) {
        super(context);
        initialize();
    }

    public ProjectSettingsButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public ProjectSettingsButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                showProjectSettings();
            }
        });
    }

    private void showProjectSettings() {
        final File root = resolveProjectRoot();
        if (root == null) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Project Settings")
                    .setMessage("No Gradle project is currently loaded.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        setEnabled(false);
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    final ProjectRequirements requirements = ProjectAnalyzer.analyze(root);
                    final String report = buildReport(root, requirements);
                    post(new Runnable() {
                        @Override public void run() {
                            setEnabled(true);
                            showReport(report);
                        }
                    });
                } catch (final Exception error) {
                    post(new Runnable() {
                        @Override public void run() {
                            setEnabled(true);
                            new AlertDialog.Builder(getContext())
                                    .setTitle("Project Settings")
                                    .setMessage("Could not analyze project: " + safeMessage(error))
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    });
                }
            }
        }, "devxyz-project-settings").start();
    }

    private void showReport(String report) {
        ScrollView scroll = new ScrollView(getContext());
        TextView text = new TextView(getContext());
        text.setText(report);
        text.setTextSize(13f);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextIsSelectable(true);
        int padding = dp(16);
        text.setPadding(padding, padding, padding, padding);
        scroll.addView(text, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(getContext())
                .setTitle("Project Settings")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
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
        try { current = current.getCanonicalFile(); }
        catch (Exception ignored) { }
        if (!current.isDirectory()) return null;

        File buildCandidate = null;
        for (int depth = 0; current != null && depth < 64; depth++) {
            if (new File(current, "settings.gradle").isFile()
                    || new File(current, "settings.gradle.kts").isFile()) {
                return current;
            }
            if (buildCandidate == null
                    && (new File(current, "build.gradle").isFile()
                    || new File(current, "build.gradle.kts").isFile())) {
                buildCandidate = current;
            }
            current = current.getParentFile();
        }
        return buildCandidate;
    }

    private static String buildReport(File root, ProjectRequirements requirements) {
        StringBuilder report = new StringBuilder();
        report.append("Detected from project files\n\n");
        report.append("Project root: ").append(root.getAbsolutePath()).append('\n');
        report.append("Gradle: ").append(value(requirements.getGradleVersion())).append('\n');
        report.append("Android Gradle Plugin: ").append(value(requirements.getAgpVersion())).append('\n');
        report.append("Minimum compatible Gradle: ").append(value(requirements.getMinimumGradleVersion())).append('\n');
        report.append("Recommended Java: ").append(requirements.getJavaMajor()).append('\n');
        report.append("compileSdk: ").append(number(requirements.getCompileSdk())).append('\n');
        report.append("minSdk: ").append(number(requirements.getMinSdk())).append('\n');
        report.append("targetSdk: ").append(number(requirements.getTargetSdk())).append('\n');
        report.append("Android project: ").append(requirements.isAndroidProject()).append('\n');
        report.append("AndroidX: ").append(requirements.usesAndroidX()).append('\n');
        report.append("Kotlin: ").append(requirements.usesKotlin()).append('\n');
        report.append("Kotlin DSL: ").append(requirements.usesKotlinDsl()).append('\n');
        report.append("Compose: ").append(requirements.usesCompose()).append('\n');
        report.append("Native build: ").append(requirements.usesNativeBuild()).append('\n');
        report.append("Version catalog: ").append(requirements.usesVersionCatalog()).append('\n');
        report.append("Gradle wrapper complete: ").append(requirements.isWrapperComplete()).append('\n');

        List<String> repositories = requirements.getRepositories();
        if (!repositories.isEmpty()) {
            report.append("\nRepositories:\n");
            for (String repository : repositories) report.append("  - ").append(repository).append('\n');
        }

        List<String> warnings = requirements.getWarnings();
        if (!warnings.isEmpty()) {
            report.append("\nAnalyzer warnings:\n");
            for (String warning : warnings) report.append("  - ").append(warning).append('\n');
        }
        return report.toString();
    }

    private static String value(String value) {
        return value == null || value.trim().length() == 0 ? "Not detected" : value;
    }

    private static String number(int value) {
        return value > 0 ? Integer.toString(value) : "Not detected";
    }

    private static String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.trim().length() == 0
                ? error == null ? "Unknown error" : error.getClass().getSimpleName()
                : message;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
