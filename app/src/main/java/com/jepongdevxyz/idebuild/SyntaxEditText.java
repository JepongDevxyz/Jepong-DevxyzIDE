package com.jepongdevxyz.idebuild;

import android.content.Context;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;

/**
 * Platform-only editor widget that debounces bounded lexical highlighting.
 * This keeps the AIDE-compatible host free of external editor dependencies.
 */
public final class SyntaxEditText extends EditText {
    private static final long SYNTAX_DEBOUNCE_MS = 120L;

    private final Handler syntaxHandler = new Handler();
    private String sourceHint;
    private boolean attached;

    private final Runnable syntaxRefresh = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            EditorSyntaxStyler.apply(SyntaxEditText.this, sourceHint);
        }
    };

    public SyntaxEditText(Context context) {
        super(context);
        init();
    }

    public SyntaxEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SyntaxEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { scheduleSyntaxRefresh(); }
        });
    }

    public void setSourceHint(String sourceHint) {
        this.sourceHint = sourceHint;
        scheduleSyntaxRefresh();
    }

    public void refreshSyntaxNow() {
        syntaxHandler.removeCallbacks(syntaxRefresh);
        if (attached) EditorSyntaxStyler.apply(this, sourceHint);
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
