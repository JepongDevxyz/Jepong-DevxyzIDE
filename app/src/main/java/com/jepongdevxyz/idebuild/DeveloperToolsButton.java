package com.jepongdevxyz.idebuild;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

/** Opens the developer extras hub with the currently loaded project context. */
public final class DeveloperToolsButton extends Button {
    public DeveloperToolsButton(Context context) { super(context); initialize(); }
    public DeveloperToolsButton(Context context, AttributeSet attrs) { super(context, attrs); initialize(); }
    public DeveloperToolsButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); initialize(); }

    private void initialize() {
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { launchTools(); }
        });
    }

    private void launchTools() {
        Context context = getContext();
        if (!(context instanceof Activity)) return;
        Intent intent = new Intent(context, DeveloperToolsActivity.class);
        File root = resolveProjectRoot();
        if (root != null) intent.putExtra(DeveloperToolsActivity.EXTRA_PROJECT_ROOT, root.getAbsolutePath());
        context.startActivity(intent);
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
        if (current.isFile()) current = current.getParentFile();
        File buildCandidate = null;
        for (int depth = 0; current != null && depth < 64; depth++) {
            if (new File(current, "settings.gradle").isFile() || new File(current, "settings.gradle.kts").isFile()) return current;
            if (buildCandidate == null && (new File(current, "build.gradle").isFile() || new File(current, "build.gradle.kts").isFile())) buildCandidate = current;
            current = current.getParentFile();
        }
        return buildCandidate;
    }
}
