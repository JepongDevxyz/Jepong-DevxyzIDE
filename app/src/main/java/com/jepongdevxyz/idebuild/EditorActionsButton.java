package com.jepongdevxyz.idebuild;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/** Mobile editor actions that operate on the real SyntaxEditText instance. */
public final class EditorActionsButton extends Button {
    public EditorActionsButton(Context context) { super(context); init(); }
    public EditorActionsButton(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public EditorActionsButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setAllCaps(false);
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) { showActions(); }
        });
    }

    private SyntaxEditText editor() {
        View root = getRootView();
        View view = root == null ? null : root.findViewById(R.id.editor);
        return view instanceof SyntaxEditText ? (SyntaxEditText) view : null;
    }

    private void showActions() {
        final SyntaxEditText editor = editor();
        if (editor == null) {
            Toast.makeText(getContext(), "Editor is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        String undo = editor.canUndoEdit() ? "Undo" : "Undo (unavailable)";
        String redo = editor.canRedoEdit() ? "Redo" : "Redo (unavailable)";
        String[] actions = new String[]{undo, redo, "Go to Line"};
        new AlertDialog.Builder(getContext())
                .setTitle("Editor Actions")
                .setItems(actions, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            if (!editor.undoEdit()) Toast.makeText(getContext(), "Nothing to undo", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            if (!editor.redoEdit()) Toast.makeText(getContext(), "Nothing to redo", Toast.LENGTH_SHORT).show();
                        } else {
                            promptGoToLine(editor);
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptGoToLine(final SyntaxEditText editor) {
        final EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setHint("Line number");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(getContext())
                .setTitle("Go to Line")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Go", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        int line = -1;
                        try {
                            String value = input.getText() == null ? "" : input.getText().toString().trim();
                            if (value.length() > 0) line = Integer.parseInt(value);
                        } catch (NumberFormatException ignored) { }
                        if (!editor.goToLine(line)) {
                            Toast.makeText(getContext(), "Line is outside this document", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }
}
