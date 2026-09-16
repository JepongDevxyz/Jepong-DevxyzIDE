package com.jepongdevxyz.idebuild;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.widget.EditText;

import com.jepongdevxyz.idebuild.core.editor.EditorUndoHistory;

/**
 * Platform-only editor widget with bounded lexical highlighting, undo/redo,
 * a visible line-number gutter, and current-line highlighting.
 * This keeps the AIDE-compatible host free of external editor dependencies.
 */
public final class SyntaxEditText extends EditText {
    private static final long SYNTAX_DEBOUNCE_MS = 120L;
    private static final int UNDO_ENTRIES = 20;
    private static final int MAX_UNDO_SNAPSHOT_CHARS = 256 * 1024;
    private static final int MAX_GUTTER_SCAN_CHARS = 2 * 1024 * 1024;
    private static final float GUTTER_WIDTH_DP = 54f;
    private static final float GUTTER_TEXT_SCALE = 0.78f;

    private final Handler syntaxHandler = new Handler();
    private final Paint gutterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gutterBackgroundPaint = new Paint();
    private final Paint currentLinePaint = new Paint();
    private EditorUndoHistory undoHistory;
    private String sourceHint;
    private boolean attached;
    private boolean applyingHistory;
    private int gutterWidthPx;

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

        float density = getResources().getDisplayMetrics().density;
        gutterWidthPx = Math.max(1, Math.round(GUTTER_WIDTH_DP * density));
        setPadding(getPaddingLeft() + gutterWidthPx, getPaddingTop(), getPaddingRight(), getPaddingBottom());

        gutterPaint.setTextAlign(Paint.Align.RIGHT);
        gutterPaint.setTypeface(android.graphics.Typeface.MONOSPACE);
        updateGutterPaints();

        addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (!applyingHistory && undoHistory != null) undoHistory.record(s == null ? "" : s.toString());
                scheduleSyntaxRefresh();
                invalidate();
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
        if (syntaxHandler == null || syntaxRefresh == null) return;
        syntaxHandler.removeCallbacks(syntaxRefresh);
        if (attached) EditorSyntaxStyler.apply(this, sourceHint);
    }

    @Override protected void onDraw(Canvas canvas) {
        updateGutterPaints();
        drawCurrentLineHighlight(canvas);
        drawGutterBackground(canvas);
        super.onDraw(canvas);
        drawLineNumbers(canvas);
    }

    private void drawCurrentLineHighlight(Canvas canvas) {
        Layout layout = getLayout();
        if (layout == null || length() == 0) return;
        int selection = Math.max(0, Math.min(getSelectionStart(), length()));
        int visualLine = layout.getLineForOffset(selection);
        int top = getExtendedPaddingTop() + layout.getLineTop(visualLine) - getScrollY();
        int bottom = getExtendedPaddingTop() + layout.getLineBottom(visualLine) - getScrollY();
        int editorLeft = Math.max(0, getPaddingLeft() - gutterWidthPx);
        canvas.drawRect(editorLeft, top, getWidth(), bottom, currentLinePaint);
    }

    private void drawGutterBackground(Canvas canvas) {
        int right = Math.min(getWidth(), Math.max(0, getPaddingLeft() - Math.round(4f * getResources().getDisplayMetrics().density)));
        canvas.drawRect(0, 0, right, getHeight(), gutterBackgroundPaint);
    }

    private void drawLineNumbers(Canvas canvas) {
        Layout layout = getLayout();
        CharSequence text = getText();
        if (layout == null || text == null || layout.getLineCount() == 0) return;

        int firstVisualLine = layout.getLineForVertical(Math.max(0, getScrollY()));
        int lastVisualLine = layout.getLineForVertical(Math.max(0, getScrollY() + getHeight()));
        firstVisualLine = Math.max(0, Math.min(firstVisualLine, layout.getLineCount() - 1));
        lastVisualLine = Math.max(firstVisualLine, Math.min(lastVisualLine, layout.getLineCount() - 1));

        int firstOffset = layout.getLineStart(firstVisualLine);
        int logicalLine = logicalLineNumberAtOffset(text, firstOffset);
        float density = getResources().getDisplayMetrics().density;
        float numberX = Math.max(0, getPaddingLeft() - Math.round(10f * density));

        if (logicalLine < 0) {
            float baseline = getExtendedPaddingTop() + layout.getLineBaseline(firstVisualLine) - getScrollY();
            canvas.drawText("…", numberX, baseline, gutterPaint);
            return;
        }

        for (int visualLine = firstVisualLine; visualLine <= lastVisualLine; visualLine++) {
            int offset = layout.getLineStart(visualLine);
            boolean logicalStart = offset == 0 || (offset > 0 && text.charAt(offset - 1) == '\n');
            if (visualLine > firstVisualLine && logicalStart) logicalLine++;
            if (!logicalStart) continue;

            float baseline = getExtendedPaddingTop() + layout.getLineBaseline(visualLine) - getScrollY();
            if (baseline < 0 || baseline > getHeight() + getTextSize()) continue;
            canvas.drawText(Integer.toString(logicalLine), numberX, baseline, gutterPaint);
        }
    }

    private int logicalLineNumberAtOffset(CharSequence text, int offset) {
        if (offset < 0) return 1;
        if (offset > MAX_GUTTER_SCAN_CHARS) return -1;
        int limit = Math.min(offset, text.length());
        int line = 1;
        for (int i = 0; i < limit; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private void updateGutterPaints() {
        int textColor = getCurrentTextColor();
        if (gutterPaint == null || gutterBackgroundPaint == null || currentLinePaint == null) return;
        gutterPaint.setColor(withAlpha(textColor, 120));
        gutterPaint.setTextSize(Math.max(8f, getTextSize() * GUTTER_TEXT_SCALE));
        gutterBackgroundPaint.setColor(withAlpha(textColor, 18));
        currentLinePaint.setColor(withAlpha(textColor, 14));
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
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
        invalidate();
    }

    private String textString() {
        return getText() == null ? "" : getText().toString();
    }

    private void scheduleSyntaxRefresh() {
        // TextView/EditText constructors can invoke overridable callbacks such as
        // onSelectionChanged() before this subclass's field initializers run.
        // During that construction window syntaxHandler/syntaxRefresh are null.
        if (syntaxHandler == null || syntaxRefresh == null) return;
        syntaxHandler.removeCallbacks(syntaxRefresh);
        syntaxHandler.postDelayed(syntaxRefresh, SYNTAX_DEBOUNCE_MS);
    }

    @Override protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        scheduleSyntaxRefresh();
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        scheduleSyntaxRefresh();
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        if (syntaxHandler != null && syntaxRefresh != null) syntaxHandler.removeCallbacks(syntaxRefresh);
        super.onDetachedFromWindow();
    }
}
