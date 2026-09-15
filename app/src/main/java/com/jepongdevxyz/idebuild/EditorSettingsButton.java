package com.jepongdevxyz.idebuild;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.jepongdevxyz.idebuild.core.settings.EditorSettings;

/**
 * Self-contained editor settings control so preference UI/state does not grow MainActivity.
 * Preferences are app-local and applied directly to the current editor widget.
 */
public final class EditorSettingsButton extends Button {
    private static final String PREFS_NAME = "devxyz_editor_settings";
    private static final String KEY_FONT_SIZE_SP = "font_size_sp";
    private static final String KEY_WORD_WRAP = "word_wrap";

    public EditorSettingsButton(Context context) {
        super(context);
        initialize();
    }

    public EditorSettingsButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public EditorSettingsButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                showSettingsDialog();
            }
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(new Runnable() {
            @Override public void run() {
                applySettings(loadSettings());
            }
        });
    }

    private EditorSettings loadSettings() {
        SharedPreferences preferences = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new EditorSettings(
                preferences.getInt(KEY_FONT_SIZE_SP, EditorSettings.DEFAULT_FONT_SIZE_SP),
                preferences.getBoolean(KEY_WORD_WRAP, EditorSettings.DEFAULT_WORD_WRAP));
    }

    private void saveSettings(EditorSettings settings) {
        getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_FONT_SIZE_SP, settings.getFontSizeSp())
                .putBoolean(KEY_WORD_WRAP, settings.isWordWrap())
                .apply();
    }

    private void applySettings(EditorSettings settings) {
        View root = getRootView();
        if (root == null) return;
        EditText editor = (EditText) root.findViewById(R.id.editor);
        if (editor == null) return;
        editor.setTextSize(settings.getFontSizeSp());
        editor.setHorizontallyScrolling(!settings.isWordWrap());
    }

    private void showSettingsDialog() {
        final EditorSettings current = loadSettings();
        final LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        form.setPadding(padding, dp(8), padding, 0);

        final EditText fontSizeInput = new EditText(getContext());
        fontSizeInput.setHint("Font size (10-24sp)");
        fontSizeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        fontSizeInput.setSingleLine(true);
        fontSizeInput.setText(String.valueOf(current.getFontSizeSp()));
        form.addView(fontSizeInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final CheckBox wordWrapInput = new CheckBox(getContext());
        wordWrapInput.setText("Word wrap");
        wordWrapInput.setChecked(current.isWordWrap());
        form.addView(wordWrapInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(getContext())
                .setTitle("Editor Settings")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        int requestedFontSize = current.getFontSizeSp();
                        try {
                            String raw = fontSizeInput.getText() == null ? "" : fontSizeInput.getText().toString().trim();
                            if (raw.length() > 0) requestedFontSize = Integer.parseInt(raw);
                        } catch (NumberFormatException ignored) { }
                        EditorSettings settings = new EditorSettings(requestedFontSize, wordWrapInput.isChecked());
                        saveSettings(settings);
                        applySettings(settings);
                    }
                })
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
