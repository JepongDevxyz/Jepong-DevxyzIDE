package com.jepongdevxyz.idebuild;

import android.app.Activity;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.jepongdevxyz.idebuild.core.settings.AppAppearanceSettings;
import com.jepongdevxyz.idebuild.core.settings.EditorSettings;

/**
 * Self-contained editor/app settings control so preference UI/state does not grow MainActivity.
 * Preferences are app-local and applied directly to the current editor widget.
 */
public final class EditorSettingsButton extends Button {
    private static final String PREFS_NAME = "devxyz_editor_settings";
    private static final String KEY_FONT_SIZE_SP = "font_size_sp";
    private static final String KEY_WORD_WRAP = "word_wrap";

    public EditorSettingsButton(Context context) { super(context); initialize(); }
    public EditorSettingsButton(Context context, AttributeSet attrs) { super(context, attrs); initialize(); }
    public EditorSettingsButton(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); initialize(); }

    private void initialize() {
        setAllCaps(false);
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { showSettingsDialog(); }
        });
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        post(new Runnable() {
            @Override public void run() { applyEditorSettings(loadEditorSettings()); }
        });
    }

    private EditorSettings loadEditorSettings() {
        SharedPreferences preferences = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new EditorSettings(
                preferences.getInt(KEY_FONT_SIZE_SP, EditorSettings.DEFAULT_FONT_SIZE_SP),
                preferences.getBoolean(KEY_WORD_WRAP, EditorSettings.DEFAULT_WORD_WRAP));
    }

    private void saveEditorSettings(EditorSettings settings) {
        getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_FONT_SIZE_SP, settings.getFontSizeSp())
                .putBoolean(KEY_WORD_WRAP, settings.isWordWrap())
                .apply();
    }

    private void applyEditorSettings(EditorSettings settings) {
        View root = getRootView();
        if (root == null) return;
        EditText editor = (EditText) root.findViewById(R.id.editor);
        if (editor == null) return;
        editor.setTextSize(settings.getFontSizeSp());
        editor.setHorizontallyScrolling(!settings.isWordWrap());
    }

    private void showSettingsDialog() {
        final EditorSettings currentEditor = loadEditorSettings();
        final AppAppearanceSettings currentAppearance = AppearanceContext.load(getContext());
        final LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(18);
        form.setPadding(padding, dp(8), padding, 0);

        final EditText fontSizeInput = new EditText(getContext());
        fontSizeInput.setHint("Font size (10-24sp)");
        fontSizeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        fontSizeInput.setSingleLine(true);
        fontSizeInput.setText(String.valueOf(currentEditor.getFontSizeSp()));
        form.addView(fontSizeInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final CheckBox wordWrapInput = new CheckBox(getContext());
        wordWrapInput.setText("Word wrap");
        wordWrapInput.setChecked(currentEditor.isWordWrap());
        form.addView(wordWrapInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final RadioGroup appearanceGroup = new RadioGroup(getContext());
        appearanceGroup.setOrientation(RadioGroup.HORIZONTAL);
        final RadioButton dark = radio("Dark", 7101);
        final RadioButton light = radio("Light", 7102);
        final RadioButton system = radio("System", 7103);
        appearanceGroup.addView(dark);
        appearanceGroup.addView(light);
        appearanceGroup.addView(system);
        if (currentAppearance.isLight()) light.setChecked(true);
        else if (currentAppearance.followsSystem()) system.setChecked(true);
        else dark.setChecked(true);
        form.addView(appearanceGroup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(getContext())
                .setTitle("DevxyzIDE Settings")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        int requestedFontSize = currentEditor.getFontSizeSp();
                        try {
                            String raw = fontSizeInput.getText() == null ? "" : fontSizeInput.getText().toString().trim();
                            if (raw.length() > 0) requestedFontSize = Integer.parseInt(raw);
                        } catch (NumberFormatException ignored) { }
                        EditorSettings editorSettings = new EditorSettings(requestedFontSize, wordWrapInput.isChecked());
                        saveEditorSettings(editorSettings);
                        applyEditorSettings(editorSettings);

                        String requestedMode = AppAppearanceSettings.MODE_DARK;
                        if (light.isChecked()) requestedMode = AppAppearanceSettings.MODE_LIGHT;
                        else if (system.isChecked()) requestedMode = AppAppearanceSettings.MODE_SYSTEM;
                        AppAppearanceSettings appearance = AppAppearanceSettings.of(requestedMode);
                        boolean appearanceChanged = !appearance.getMode().equals(currentAppearance.getMode());
                        AppearanceContext.save(getContext(), appearance);
                        if (appearanceChanged) applyAppearanceAndRecreate();
                    }
                })
                .show();
    }

    private RadioButton radio(String label, int id) {
        RadioButton button = new RadioButton(getContext());
        button.setId(id);
        button.setText(label);
        return button;
    }

    private void applyAppearanceAndRecreate() {
        Context context = getContext();
        if (context.getApplicationContext() instanceof DevxyzApplication) {
            ((DevxyzApplication) context.getApplicationContext()).applyAppearance();
        }
        if (context instanceof Activity) ((Activity) context).recreate();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
