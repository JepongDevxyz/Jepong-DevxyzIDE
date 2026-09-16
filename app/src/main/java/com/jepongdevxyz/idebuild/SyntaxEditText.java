package com.jepongdevxyz.idebuild;

import android.content.Context;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;

import com.jepongdevxyz.idebuild.core.editor.EditorUndoHistory;

/**
 * Platform-only editor widget with bounded lexical highlighting and undo/redo.
 * This keeps the AIDE-compatible host free of external editor dependencies.
 */
public final class SyntaxEditText extends EditText {
    private static final long SYNTAX_DEBOUNCE_MS = 120L;
    private static final int UNDO_ENTRIES = 20;
    private static final int MAX_UNDO_SNAPSHOT_CHARS = 256 * 1024;

    private final Handler syntaxHandler = new Handler();
    private EditorUndoHistory undoHistory;
    private String sourceHint;
    private boolean attached;
    private boolean applyingHistory;

    private final Runnable syntaxRefresh = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            EditorSyntaxStyler.apply(SyntaxEditText.this, sourceHint);
        }
    };

    public SyntaxEditText(Context context) { super(context); init(); }
    public SyntaxEditText(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public SyntaxEditText(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        undoHistory = new EditorUndoHistory(UNDO_ENTRIES, MAX_UNDO_SNAPSHOT_CHARS);
        undoHistory.reset(textString());
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (!applyingHistory && undoHistory != null) undoHistory.record(s == null ? "" : s.toString());
                scheduleSyntaxRefresh();
            }
        });
    }

    public void setSourceHint(String sourceHint) {
        this.sourceHint = sourceHint;
        scheduleSyntaxRefresh();
    }

    public String getSourceHint() { return sourceHint; }

    public boolean canUndoEdit() { return undoHistory != null && undoHistory.canUndo(); }
    public boolean canRedoEdit() { return undoHistory != null && undoHistory.canRedo(); }

    public boolean undoEdit() {
        if (!canUndoEdit()) return false;
        applyHistoryText(undoHistory.undo(textString()));
        return true;
    }

    public boolean redoEdit() {
        if (!canRedoEdit()) return false;
        applyHistoryText(undoHistory.redo(textString()));
        return true;
    }

    public boolean goToLine(int requestedLine) {
        if (requestedLine < 1) return false;
        String text = textString();
        int line = 1;
        int offset = 0;
        while (offset < text.length() && line < requestedLine) {
            if (text.charAt(offset++) == '\n') line++;
        }
        if (line != requestedLine) return false;
        requestFocus();
        setSelection(Math.min(offset, length()));
        return true;
    }

    public void resetUndoHistory() {
        if (undoHistory != null) undoHistory.reset(textString());
    }

    public void refreshSyntaxNow() {
        syntaxHandler.removeCallbacks(syntaxRefresh);
        if (attached) EditorSyntaxStyler.apply(this, sourceHint);
    }

    private void applyHistoryText(String value) {
        int oldSelection = Math.max(0, getSelectionStart());
        applyingHistory = true;
        try {
            Editable editable = getText();
            if (editable != null) editable.replace(0, editable.length(), value == null ? "" : value);
            setSelection(Math.min(oldSelection, length()));
        } finally {
            applyingHistory = false;
        }
        refreshSyntaxNow();
    }

    private String textString() {
        return getText() == null ? "" : getText().toString();
    }

    private void scheduleSyntaxRefresh() {
        syntaxHandler.removeCallbacks(syntaxRefresh);
        syntaxHandler.postDelayed(syntaxRefresh, SYNTAX_DEBOUNCE_MS);
    }

    @Override protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        scheduleSyntaxRefresh();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        scheduleSyntaxRefresh();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        syntaxHandler.removeCallbacks(syntaxRefresh);
        super.onDetachedFromWindow();
    }
}
