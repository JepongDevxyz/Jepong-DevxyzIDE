package com.jepongdevxyz.idebuild;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jepongdevxyz.idebuild.core.git.GitResult;
import com.jepongdevxyz.idebuild.core.git.GitService;

import java.io.File;
import java.util.Collections;

/** Real Git workspace controls. All output is produced by the installed git executable. */
public final class GitButton extends Button {
    private static final int MAX_OUTPUT_CHARS = 200000;

    public GitButton(Context context) {
        super(context);
        initialize();
    }

    public GitButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public GitButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { showGitDialog(); }
        });
    }

    private void showGitDialog() {
        final File repository = resolveProjectRoot();
        if (repository == null) {
            showCloneDialog();
            return;
        }

        final LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(6), dp(12), 0);

        final TextView context = new TextView(getContext());
        context.setTextSize(11f);
        context.setText("Repository: " + repository.getAbsolutePath());
        root.addView(context);

        final ScrollView scroll = new ScrollView(getContext());
        final TextView output = new TextView(getContext());
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(12f);
        output.setTextIsSelectable(true);
        output.setPadding(dp(4), dp(8), dp(4), dp(8));
        scroll.addView(output, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(260)));

        final LinearLayout row1 = actionRow();
        final Button refresh = actionButton("Status", row1);
        final Button diff = actionButton("Diff", row1);
        final Button stage = actionButton("Stage All", row1);
        final Button unstage = actionButton("Unstage All", row1);
        root.addView(row1);

        final LinearLayout row2 = actionRow();
        final Button commit = actionButton("Commit", row2);
        final Button branches = actionButton("Branches", row2);
        final Button pull = actionButton("Pull", row2);
        final Button push = actionButton("Push", row2);
        root.addView(row2);

        final AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("Git")
                .setView(root)
                .setNegativeButton("Close", null)
                .create();
        dialog.show();

        final Button[] actions = new Button[]{refresh, diff, stage, unstage, commit, branches, pull, push};
        setEnabled(actions, false);
        append(output, scroll, "Checking Git availability...");

        runAsync(new BackgroundGitOperation() {
            @Override public GitResult run() {
                boolean available = GitService.isGitAvailable(repository);
                if (!available) return new GitResult(127, false, 0L, "", "Git executable is unavailable on this runtime.\n");
                if (!new File(repository, ".git").isDirectory()) return GitService.init(repository);
                return GitService.status(repository);
            }
        }, new ResultHandler() {
            @Override public void onResult(GitResult result) {
                renderResult(output, scroll, result);
                if (result.isSuccess()) setEnabled(actions, true);
            }
        });

        refresh.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                perform("git status --porcelain", output, scroll, new BackgroundGitOperation() {
                    @Override public GitResult run() { return GitService.status(repository); }
                });
            }
        });

        diff.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { showDiffChoice(repository, output, scroll); }
        });

        stage.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                perform("git add -- .", output, scroll, new BackgroundGitOperation() {
                    @Override public GitResult run() { return GitService.stage(repository, "."); }
                });
            }
        });

        unstage.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) {
                perform("git reset HEAD -- .", output, scroll, new BackgroundGitOperation() {
                    @Override public GitResult run() { return GitService.unstage(repository, "."); }
                });
            }
        });

        commit.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { showCommitDialog(repository, output, scroll); }
        });

        branches.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { showBranchDialog(repository, output, scroll); }
        });

        pull.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { performRemote(repository, false, output, scroll); }
        });

        push.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View view) { performRemote(repository, true, output, scroll); }
        });
    }

    private void showCloneDialog() {
        final LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), 0, dp(12), 0);
        final EditText remote = field("Repository URL");
        final EditText folder = field("Destination folder");
        form.addView(remote);
        form.addView(folder);

        new AlertDialog.Builder(getContext())
                .setTitle("Clone Repository")
                .setMessage("No project is loaded. Clone into DevxyzIDE project storage.")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clone", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        final String remoteValue = text(remote);
                        final String folderValue = text(folder);
                        if (remoteValue.length() == 0 || folderValue.length() == 0) {
                            showMessage("Clone Repository", "Repository URL and destination folder are required.");
                            return;
                        }
                        cloneRepository(remoteValue, folderValue);
                    }
                })
                .show();
    }

    private void cloneRepository(final String remote, final String folder) {
        final File projects;
        final File destination;
        try {
            File storage = getContext().getExternalFilesDir(null);
            if (storage == null) storage = getContext().getFilesDir();
            projects = new File(storage, "projects").getCanonicalFile();
            if (!projects.isDirectory() && !projects.mkdirs()) throw new IllegalStateException("Cannot create project storage");
            if (folder.indexOf('/') >= 0 || folder.indexOf('\\') >= 0 || folder.contains("..")) {
                throw new IllegalArgumentException("Destination must be a single safe folder name");
            }
            destination = new File(projects, folder).getCanonicalFile();
            String projectPrefix = projects.getCanonicalPath() + File.separator;
            if (!destination.getCanonicalPath().startsWith(projectPrefix)) {
                throw new IllegalArgumentException("Destination escaped project storage");
            }
            if (destination.exists()) throw new IllegalArgumentException("Destination already exists");
        } catch (Exception error) {
            showMessage("Clone Repository", "CLONE ERROR: " + safeMessage(error));
            return;
        }

        final AlertDialog progress = new AlertDialog.Builder(getContext())
                .setTitle("Clone Repository")
                .setMessage("Cloning into " + destination.getName() + "...")
                .setNegativeButton("Close", null)
                .create();
        progress.show();

        runAsync(new BackgroundGitOperation() {
            @Override public GitResult run() {
                return GitService.cloneRepository(
                        projects,
                        remote,
                        destination.getName(),
                        Collections.<String, String>emptyMap(),
                        Collections.<String>emptyList());
            }
        }, new ResultHandler() {
            @Override public void onResult(GitResult result) {
                StringBuilder message = new StringBuilder();
                if (result.getStdout().trim().length() > 0) message.append(result.getStdout().trim()).append('\n');
                if (result.getStderr().trim().length() > 0) message.append(result.getStderr().trim()).append('\n');
                if (result.isSuccess()) {
                    message.append("Clone complete: ").append(destination.getAbsolutePath());
                } else {
                    message.append(result.isCancelled() ? "Clone cancelled." : "Clone failed with exit " + result.getExitCode() + ".");
                }
                progress.setMessage(message.toString().trim());
            }
        });
    }

    private void showDiffChoice(final File repository, final TextView output, final ScrollView scroll) {
        new AlertDialog.Builder(getContext())
                .setTitle("Diff")
                .setItems(new String[]{"Working tree", "Staged"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, final int which) {
                        perform(which == 0 ? "git diff" : "git diff --cached", output, scroll, new BackgroundGitOperation() {
                            @Override public GitResult run() { return GitService.diff(repository, which == 1); }
                        });
                    }
                })
                .show();
    }

    private void showCommitDialog(final File repository, final TextView output, final ScrollView scroll) {
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(12), 0, dp(12), 0);
        final EditText message = field("Commit message");
        final EditText name = field("Author name");
        final EditText email = field("Author email");
        email.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        form.addView(message); form.addView(name); form.addView(email);

        new AlertDialog.Builder(getContext())
                .setTitle("Commit staged changes")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Commit", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        final String commitMessage = text(message);
                        final String authorName = text(name);
                        final String authorEmail = text(email);
                        if (commitMessage.length() == 0 || authorName.length() == 0 || authorEmail.length() == 0) {
                            append(output, scroll, "COMMIT ERROR: message, author name, and author email are required.");
                            return;
                        }
                        perform("git commit", output, scroll, new BackgroundGitOperation() {
                            @Override public GitResult run() {
                                return GitService.commit(repository, commitMessage, authorName, authorEmail);
                            }
                        });
                    }
                })
                .show();
    }

    private void showBranchDialog(final File repository, final TextView output, final ScrollView scroll) {
        final EditText branch = field("Branch name");
        new AlertDialog.Builder(getContext())
                .setTitle("Branches")
                .setView(branch)
                .setNeutralButton("List", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        perform("git branch --list", output, scroll, new BackgroundGitOperation() {
                            @Override public GitResult run() { return GitService.branches(repository); }
                        });
                    }
                })
                .setNegativeButton("Checkout", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        final String value = text(branch);
                        if (value.length() == 0) return;
                        perform("git checkout " + value, output, scroll, new BackgroundGitOperation() {
                            @Override public GitResult run() { return GitService.checkout(repository, value); }
                        });
                    }
                })
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        final String value = text(branch);
                        if (value.length() == 0) return;
                        perform("git branch " + value, output, scroll, new BackgroundGitOperation() {
                            @Override public GitResult run() { return GitService.createBranch(repository, value); }
                        });
                    }
                })
                .show();
    }

    private void performRemote(final File repository,
                               final boolean push,
                               final TextView output,
                               final ScrollView scroll) {
        perform(push ? "git push origin <current>" : "git pull --ff-only origin <current>", output, scroll,
                new BackgroundGitOperation() {
                    @Override public GitResult run() {
                        GitResult branch = GitService.currentBranch(repository);
                        if (!branch.isSuccess()) return branch;
                        String name = branch.getStdout().trim();
                        if (name.length() == 0) return new GitResult(2, false, 0L, "", "Detached HEAD: no current branch.\n");
                        return push
                                ? GitService.push(repository, "origin", name, Collections.<String, String>emptyMap(), Collections.<String>emptyList())
                                : GitService.pull(repository, "origin", name, Collections.<String, String>emptyMap(), Collections.<String>emptyList());
                    }
                });
    }

    private void perform(String label,
                         final TextView output,
                         final ScrollView scroll,
                         BackgroundGitOperation operation) {
        append(output, scroll, "\n> " + label);
        runAsync(operation, new ResultHandler() {
            @Override public void onResult(GitResult result) { renderResult(output, scroll, result); }
        });
    }

    private void runAsync(final BackgroundGitOperation operation, final ResultHandler handler) {
        new Thread(new Runnable() {
            @Override public void run() {
                final GitResult result;
                try { result = operation.run(); }
                catch (Exception error) {
                    final String message = safeMessage(error);
                    post(new Runnable() {
                        @Override public void run() { handler.onResult(new GitResult(1, false, 0L, "", "GIT ERROR: " + message + "\n")); }
                    });
                    return;
                }
                post(new Runnable() {
                    @Override public void run() { handler.onResult(result); }
                });
            }
        }, "DevxyzIDE-Git").start();
    }

    private static void renderResult(TextView output, ScrollView scroll, GitResult result) {
        if (result.getStdout().length() > 0) append(output, scroll, result.getStdout().trim());
        if (result.getStderr().length() > 0) append(output, scroll, result.getStderr().trim());
        append(output, scroll, result.isCancelled() ? "[cancelled]" : "[git exit " + result.getExitCode() + "]");
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
        File buildCandidate = null;
        for (int depth = 0; current != null && depth < 64; depth++) {
            if (new File(current, "settings.gradle").isFile() || new File(current, "settings.gradle.kts").isFile()) return current;
            if (buildCandidate == null && (new File(current, "build.gradle").isFile() || new File(current, "build.gradle.kts").isFile())) buildCandidate = current;
            current = current.getParentFile();
        }
        return buildCandidate;
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(getContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private Button actionButton(String label, LinearLayout row) {
        Button button = new Button(getContext());
        button.setText(label);
        row.addView(button, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return button;
    }

    private EditText field(String hint) {
        EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return input;
    }

    private static String text(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private static void setEnabled(Button[] buttons, boolean enabled) {
        for (Button button : buttons) button.setEnabled(enabled);
    }

    private static void append(final TextView output, final ScrollView scroll, final String value) {
        output.post(new Runnable() {
            @Override public void run() {
                if (output.length() > MAX_OUTPUT_CHARS) output.setText("[git output truncated]\n");
                if (output.length() > 0) output.append("\n");
                output.append(value == null ? "" : value);
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

    private interface BackgroundGitOperation { GitResult run(); }
    private interface ResultHandler { void onResult(GitResult result); }
}
