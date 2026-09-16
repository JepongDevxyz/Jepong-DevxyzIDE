package com.jepongdevxyz.idebuild;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

/** Launches the isolated APK signing workflow for the currently loaded project. */
public final class ApkSigningButton extends Button {
    public ApkSigningButton(Context context) {
        super(context);
        initialize();
    }

    public ApkSigningButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public ApkSigningButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                launchSigning();
            }
        });
    }

    private void launchSigning() {
        Context context = getContext();
        if (!(context instanceof Activity)) return;
        File projectRoot = resolveProjectRoot();
        if (projectRoot == null || !projectRoot.isDirectory()) {
            setError("Load a project before signing an APK.");
            return;
        }
        Intent intent = new Intent(context, ApkSigningActivity.class);
        intent.putExtra(ApkSigningActivity.EXTRA_PROJECT_ROOT, projectRoot.getAbsolutePath());
        context.startActivity(intent);
    }

    private File resolveProjectRoot() {
        View root = getRootView();
        View pathView = root == null ? null : root.findViewById(R.id.projectPath);
        if (!(pathView instanceof TextView)) return null;
        CharSequence label = ((TextView) pathView).getText();
        if (label == null) return null;
        String raw = label.toString().trim();
        if (raw.length() == 0 || raw.equals(getResources().getString(R.string.no_project))) return null;

        File current = new File(raw);
        try { current = current.getCanonicalFile(); }
        catch (Exception ignored) { }
        if (!current.exists()) return null;
        if (current.isFile()) current = current.getParentFile();

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

    private void setError(String message) {
        setContentDescription(message);
        android.widget.Toast.makeText(getContext(), message, android.widget.Toast.LENGTH_LONG).show();
    }
}
