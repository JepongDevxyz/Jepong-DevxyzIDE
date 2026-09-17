package com.jepongdevxyz.idebuild;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

public final class WorkspaceNavigationButton extends Button {
    public WorkspaceNavigationButton(Context c) { super(c); init(); }
    public WorkspaceNavigationButton(Context c, AttributeSet a) { super(c, a); init(); }
    public WorkspaceNavigationButton(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { navigate(); }
        });
    }

    private void navigate() {
        View root = getRootView();
        if (root == null) return;
        int id = getId();
        if (id == R.id.nav_files) {
            show(root, R.id.workspace_files);
        } else if (id == R.id.nav_search) {
            show(root, R.id.workspace_files);
            runAction(root, R.id.searchButton);
        } else if (id == R.id.nav_git) {
            show(root, R.id.workspace_files);
            runAction(root, R.id.gitButton);
        } else if (id == R.id.nav_build_tools) {
            show(root, R.id.workspace_build_tools);
            runAction(root, R.id.buildActionsButton);
        } else if (id == R.id.nav_more) {
            show(root, R.id.workspace_more);
            runAction(root, R.id.settingsButton);
        }
    }

    private static void show(View root, int target) {
        visible(root, R.id.workspace_files, target == R.id.workspace_files);
        visible(root, R.id.workspace_build_tools, target == R.id.workspace_build_tools);
        visible(root, R.id.workspace_more, target == R.id.workspace_more);
    }

    private static void visible(View root, int id, boolean yes) {
        View v = root.findViewById(id);
        if (v != null) v.setVisibility(yes ? View.VISIBLE : View.GONE);
    }

    private static void runAction(View root, int id) {
        View v = root.findViewById(id);
        if (v != null && v.isEnabled()) v.performClick();
    }
}
