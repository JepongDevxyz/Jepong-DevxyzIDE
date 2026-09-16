package com.jepongdevxyz.idebuild;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jepongdevxyz.idebuild.core.log.BoundedLogBuffer;
import com.jepongdevxyz.idebuild.core.process.ProcessEngine;
import com.jepongdevxyz.idebuild.core.process.ProcessRequest;
import com.jepongdevxyz.idebuild.core.process.ProcessResult;
import com.jepongdevxyz.idebuild.core.terminal.TerminalCommandPlanner;

import java.io.File;

/**
 * Minimal real terminal surface backed by ProcessEngine.
 *
 * Commands are executed only after explicit user input. Output comes directly
 * from the launched shell process; this view never fabricates command output.
 */
public final class TerminalButton extends Button {
    private static final int MAX_OUTPUT_CHARS = 200000;
    private static final int MAX_OUTPUT_LINES = 5000;

    private File workingDirectory;
    private ProcessEngine.RunningProcess runningProcess;

    public TerminalButton(Context context) {
        super(context);
        initialize();
    }

    public TerminalButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public TerminalButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                showTerminalDialog();
            }
        });
    }

    /** Explicit override for callers that already own a trusted project root. */
    public void setWorkingDirectory(File projectRoot) {
        workingDirectory = projectRoot;
    }

    /**
     * Resolves the loaded project without coupling MainActivity to this view.
     * The project-path label always represents a path inside the active project;
     * walking upward finds the nearest Gradle root. If no project is loaded, the
     * command planner falls back to the app runtime home.
     */
    private File resolveWorkingDirectory() {
        File explicit = workingDirectory;
        if (explicit != null && explicit.isDirectory()) return explicit;

        View rootView = getRootView();
        View pathView = rootView == null ? null : rootView.findViewById(R.id.projectPath);
        if (!(pathView instanceof TextView)) return null;

        CharSequence label = ((TextView) pathView).getText();
        if (label == null) return null;
        String raw = label.toString().trim();
        if (raw.length() == 0 || raw.equals(getResources().getString(R.string.no_project))) return null;

        File current = new File(raw);
        try { current = current.getCanonicalFile(); }
        catch (Exception ignored) { }
        if (!current.isDirectory()) return null;

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

    private void showTerminalDialog() {
        final LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(14);
        root.setPadding(padding, dp(6), padding, 0);

        final TextView shellLabel = new TextView(getContext());
        shellLabel.setTextSize(11f);
        shellLabel.setText(describeTerminalContext());
        root.addView(shellLabel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final ScrollView scroll = new ScrollView(getContext());
        final TextView output = new TextView(getContext());
        final BoundedLogBuffer log = new BoundedLogBuffer(MAX_OUTPUT_CHARS, MAX_OUTPUT_LINES);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(12f);
        output.setTextIsSelectable(true);
        output.setPadding(dp(4), dp(8), dp(4), dp(8));
        scroll.addView(output, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(280)));

        final EditText commandInput = new EditText(getContext());
        commandInput.setSingleLine(true);
        commandInput.setHint("Enter shell command");
        commandInput.setTypeface(Typeface.MONOSPACE);
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        root.addView(commandInput, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final LinearLayout controls = new LinearLayout(getContext());
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.END);

        final Button run = new Button(getContext());
        run.setText("Run");
        final Button stop = new Button(getContext());
        stop.setText("Stop");
        stop.setEnabled(false);
        final Button clear = new Button(getContext());
        clear.setText("Clear");
        controls.addView(clear);
        controls.addView(stop);
        controls.addView(run);
        root.addView(controls, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("Terminal")
                .setView(root)
                .setNegativeButton("Close", null)
                .create();

        run.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                String command = commandInput.getText() == null ? "" : commandInput.getText().toString();
                if (command.trim().length() == 0) return;
                if (runningProcess != null && !runningProcess.isFinished()) {
                    appendOutput(log, output, scroll, "[a command is already running]");
                    return;
                }
                executeCommand(command, log, output, scroll, run, stop, commandInput);
            }
        });

        stop.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                ProcessEngine.RunningProcess active = runningProcess;
                if (active != null && !active.isFinished()) active.cancel();
            }
        });

        clear.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                log.clear();
                output.setText("");
            }
        });

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override public void onDismiss(DialogInterface ignored) {
                ProcessEngine.RunningProcess active = runningProcess;
                if (active != null && !active.isFinished()) active.cancel();
            }
        });
        dialog.show();
    }

    private void executeCommand(final String command,
                                final BoundedLogBuffer log,
                                final TextView output,
                                final ScrollView scroll,
                                final Button run,
                                final Button stop,
                                final EditText commandInput) {
        final ProcessRequest request;
        try {
            File cwd = resolveWorkingDirectory();
            request = TerminalCommandPlanner.plan(getContext().getFilesDir(), cwd, command);
        } catch (Exception error) {
            appendOutput(log, output, scroll, "TERMINAL ERROR: " + safeMessage(error));
            return;
        }

        appendOutput(log, output, scroll, "$ " + command);
        run.setEnabled(false);
        stop.setEnabled(true);
        commandInput.setEnabled(false);

        runningProcess = ProcessEngine.start(request, new ProcessEngine.Listener() {
            @Override public void onStdout(String line) {
                appendOutput(log, output, scroll, line);
            }

            @Override public void onStderr(String line) {
                appendOutput(log, output, scroll, line);
            }

            @Override public void onFinished(final ProcessResult result) {
                output.post(new Runnable() {
                    @Override public void run() {
                        String status = result.isCancelled()
                                ? "[cancelled]"
                                : "[exit " + result.getExitCode() + ", " + result.getDurationMillis() + " ms]";
                        appendOutput(log, output, scroll, status);
                        runningProcess = null;
                        run.setEnabled(true);
                        stop.setEnabled(false);
                        commandInput.setEnabled(true);
                        commandInput.requestFocus();
                        commandInput.selectAll();
                    }
                });
            }
        });
    }

    private String describeTerminalContext() {
        String shell;
        try { shell = TerminalCommandPlanner.describeShell(getContext().getFilesDir()); }
        catch (Exception error) { shell = "shell unavailable: " + safeMessage(error); }
        File cwd = resolveWorkingDirectory();
        String directory = cwd == null ? "app runtime home" : cwd.getAbsolutePath();
        return "Shell: " + shell + "\nWorking directory: " + directory;
    }

    private static void appendOutput(final BoundedLogBuffer log,
                                     final TextView output,
                                     final ScrollView scroll,
                                     final String line) {
        output.post(new Runnable() {
            @Override public void run() {
                log.appendLine(line == null ? "" : line);
                output.setText(log.snapshot());
                scroll.post(new Runnable() {
                    @Override public void run() { scroll.fullScroll(View.FOCUS_DOWN); }
                });
            }
        });
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().length() == 0 ? error.getClass().getSimpleName() : message;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
