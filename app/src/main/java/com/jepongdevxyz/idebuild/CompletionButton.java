package com.jepongdevxyz.idebuild;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.jepongdevxyz.idebuild.core.editor.BasicCompletionService;
import com.jepongdevxyz.idebuild.core.editor.SyntaxLanguage;
import com.jepongdevxyz.idebuild.core.editor.SyntaxLanguageService;

import java.util.List;

/** Lightweight lexical completion trigger for the platform-only editor. */
public final class CompletionButton extends Button {
    private static final int MAX_RESULTS = 30;

    public CompletionButton(Context context) { super(context); init(); }
    public CompletionButton(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public CompletionButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setAllCaps(false);
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { showCompletions(); }
        });
    }

    private void showCompletions() {
        View root = getRootView();
        View editorView = root == null ? null : root.findViewById(R.id.editor);
        if (!(editorView instanceof SyntaxEditText)) {
            Toast.makeText(getContext(), "Editor is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        final SyntaxEditText editor = (SyntaxEditText) editorView;
        Editable editable = editor.getText();
        String text = editable == null ? "" : editable.toString();
        int cursor = Math.max(0, editor.getSelectionStart());
        SyntaxLanguage language = SyntaxLanguageService.detect(editor.getSourceHint());
        if (language == SyntaxLanguage.PLAIN_TEXT) language = SyntaxLanguageService.detectFromContent(text);
        final String prefix = BasicCompletionService.currentPrefix(text, cursor);
        final List<String> suggestions = BasicCompletionService.complete(text, cursor, language, MAX_RESULTS);
        if (prefix.length() == 0) {
            Toast.makeText(getContext(), "Type part of a word first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (suggestions.isEmpty()) {
            Toast.makeText(getContext(), "No lexical completions for “" + prefix + "”", Toast.LENGTH_SHORT).show();
            return;
        }

        CharSequence[] rows = new CharSequence[suggestions.size()];
        for (int i = 0; i < suggestions.size(); i++) rows[i] = suggestions.get(i);
        new AlertDialog.Builder(getContext())
                .setTitle("Complete “" + prefix + "”")
                .setItems(rows, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        apply(editor, prefix, suggestions.get(which));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void apply(SyntaxEditText editor, String prefix, String suggestion) {
        Editable editable = editor.getText();
        if (editable == null) return;
        int cursor = Math.max(0, editor.getSelectionStart());
        int start = Math.max(0, cursor - prefix.length());
        if (start > cursor || cursor > editable.length()) return;
        editable.replace(start, cursor, suggestion);
        editor.setSelection(start + suggestion.length());
        editor.refreshSyntaxNow();
    }
}
