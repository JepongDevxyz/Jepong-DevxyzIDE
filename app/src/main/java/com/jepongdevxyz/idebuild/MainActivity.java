package com.jepongdevxyz.idebuild;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jepongdevxyz.idebuild.core.ProjectArchiveService;
import com.jepongdevxyz.idebuild.core.ProjectDirectoryListing;
import com.jepongdevxyz.idebuild.core.ProjectDirectoryService;
import com.jepongdevxyz.idebuild.core.ProjectEntry;
import com.jepongdevxyz.idebuild.core.ProjectFileService;
import com.jepongdevxyz.idebuild.core.ProjectPath;
import com.jepongdevxyz.idebuild.core.ProjectRootDetector;
import com.jepongdevxyz.idebuild.core.SafeZip;
import com.jepongdevxyz.idebuild.core.TextFileClassifier;
import com.jepongdevxyz.idebuild.core.WorkspacePathResolver;
import com.jepongdevxyz.idebuild.core.build.BuildDiagnosticsParser;
import com.jepongdevxyz.idebuild.core.build.BuildProblem;
import com.jepongdevxyz.idebuild.core.build.ProjectAnalyzer;
import com.jepongdevxyz.idebuild.core.build.ProjectRequirements;
import com.jepongdevxyz.idebuild.core.build.ProjectTemplateGenerator;
import com.jepongdevxyz.idebuild.core.editor.EditorDocument;
import com.jepongdevxyz.idebuild.core.editor.EditorSession;
import com.jepongdevxyz.idebuild.core.editor.TextSearchService;
import com.jepongdevxyz.idebuild.core.search.ProjectSearchService;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainInventory;
import com.jepongdevxyz.idebuild.core.toolchain.TerminalBootstrapInstaller;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainPackInstaller;
import com.jepongdevxyz.idebuild.core.toolchain.ToolchainProvisioningPlan;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_ZIP = 5001;
    private static final int REQUEST_TOOLCHAIN_PACK = 5002;
    private static final int REQUEST_RUNTIME_BOOTSTRAP = 5003;
    private static final int REQUEST_EXPORT_BACKUP = 5004;
    private static final String PROJECT_BACKEND_ID = "local-project";
    private static final int MAX_DIRECTORY_CHILDREN = 10000;
    private static final String PARENT_ROW = "[UP] ..";
    private static final long MAX_IMPORT_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final int MAX_IMPORT_ENTRIES = 100000;
    private static final long MAX_TEXT_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_TOOLCHAIN_PACK_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final int MAX_BACKUP_FILE_ENTRIES = 250000;
    private static final long MAX_BACKUP_BYTES = 16L * 1024L * 1024L * 1024L;
    private static final int MAX_PROJECT_SEARCH_FILES = 100000;
    private static final int MAX_PROJECT_SEARCH_RESULTS = 500;
    private static final int MAX_BUILD_PROBLEMS = 500;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final EditorSession editorSession = new EditorSession();
    private final List<BuildProblem> buildProblems = new ArrayList<BuildProblem>();
    private Button createProjectButton, importButton, backupButton, toolchainButton, runtimeButton, newFileButton, newFolderButton;
    private Button searchButton, projectSearchButton, saveAllButton, saveButton, buildButton, problemsButton, installButton;
    private TextView projectPath, consoleText;
    private ScrollView consoleScroll;
    private EditText editor;
    private ListView fileList;
    private LinearLayout tabBar;
    private ArrayAdapter<String> fileAdapter;
    private final List<ProjectEntry> directoryEntries = new ArrayList<ProjectEntry>();
    private final List<String> explorerRows = new ArrayList<String>();
    private boolean suppressEditorTextWatcher;
    private SearchCancellationFlag activeProjectSearch;
    private File projectRoot;
    private WorkspacePathResolver workspacePathResolver;
    private ProjectDirectoryService directoryService;
    private ProjectFileService fileService;
    private ProjectPath currentDirectory;
    private ProjectPath currentPath;
    private File lastBuiltApk;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createProjectButton = (Button) findViewById(R.id.createProjectButton);
        importButton = (Button) findViewById(R.id.importButton);
        backupButton = (Button) findViewById(R.id.backupButton);
        toolchainButton = (Button) findViewById(R.id.toolchainButton);
        runtimeButton = (Button) findViewById(R.id.runtimeButton);
        newFileButton = (Button) findViewById(R.id.newFileButton);
        newFolderButton = (Button) findViewById(R.id.newFolderButton);
        searchButton = (Button) findViewById(R.id.searchButton);
        projectSearchButton = (Button) findViewById(R.id.projectSearchButton);
        saveAllButton = (Button) findViewById(R.id.saveAllButton);
        saveButton = (Button) findViewById(R.id.saveButton);
        buildButton = (Button) findViewById(R.id.buildButton);
        problemsButton = (Button) findViewById(R.id.problemsButton);
        installButton = (Button) findViewById(R.id.installButton);
        projectPath = (TextView) findViewById(R.id.projectPath);
        consoleText = (TextView) findViewById(R.id.consoleText);
        consoleScroll = (ScrollView) findViewById(R.id.consoleScroll);
        editor = (EditText) findViewById(R.id.editor);
        fileList = (ListView) findViewById(R.id.fileList);
        tabBar = (LinearLayout) findViewById(R.id.tabBar);

        fileAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, explorerRows) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(getResources().getColor(R.color.devxyz_text));
                view.setTextSize(13f);
                view.setPadding(14, 10, 10, 10);
                return view;
            }
        };
        fileList.setAdapter(fileAdapter);

        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) {
                if (suppressEditorTextWatcher) return;
                EditorDocument active = editorSession.getActive();
                if (active == null) return;
                active.setText(s == null ? "" : s.toString());
                currentPath = active.getPath();
                renderEditorTabs();
                updateEditorButtons();
            }
        });

        createProjectButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { promptCreateProject(); } });
        importButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseZip(); } });
        backupButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseBackupDestination(); } });
        toolchainButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseToolchainPack(); } });
        runtimeButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseRuntimeBootstrap(); } });
        newFileButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { promptCreateEntry(false); } });
        newFolderButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { promptCreateEntry(true); } });
        searchButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showSearchDialog(); } });
        projectSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (activeProjectSearch != null) cancelActiveProjectSearch();
                else showProjectSearchDialog();
            }
        });
        saveAllButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { saveAllOpenDocuments(); } });
        saveButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { saveCurrentFile(); } });
        buildButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { buildProject(); } });
        problemsButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showProblemsDialog(); } });
        installButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (lastBuiltApk != null && lastBuiltApk.isFile()) ApkInstaller.install(MainActivity.this, lastBuiltApk);
            }
        });
        fileList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> parent, View view, int position, long id) { openExplorerRow(position); }
        });
        fileList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                ProjectEntry entry = explorerEntryAt(position);
                if (entry == null) return false;
                showEntryActions(entry);
                return true;
            }
        });
        updateEditorButtons();
        updateProblemsButton();
    }

    private void promptCreateProject() {
        final String[] templates = new String[]{
                "Classic Java · Gradle 4.6 / AGP 3.2.1 / SDK 28",
                "Modern AndroidX Java · JDK 17 / SDK 35",
                "Modern AndroidX Kotlin · JDK 17 / SDK 35",
                "No Activity · AndroidX Java / SDK 35",
                "WebView App · AndroidX Java / SDK 35",
                "Library Module · Android Library / SDK 35"
        };
        new AlertDialog.Builder(this)
                .setTitle("Choose project template")
                .setItems(templates, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        ProjectTemplateGenerator.Template template;
                        if (which == 0) template = ProjectTemplateGenerator.Template.CLASSIC_JAVA;
                        else if (which == 1) template = ProjectTemplateGenerator.Template.MODERN_ANDROIDX_JAVA;
                        else if (which == 2) template = ProjectTemplateGenerator.Template.MODERN_ANDROIDX_KOTLIN;
                        else if (which == 3) template = ProjectTemplateGenerator.Template.NO_ACTIVITY_JAVA;
                        else if (which == 4) template = ProjectTemplateGenerator.Template.WEBVIEW_JAVA;
                        else template = ProjectTemplateGenerator.Template.LIBRARY_JAVA;
                        promptCreateProjectDetails(template);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptCreateProjectDetails(final ProjectTemplateGenerator.Template template) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        form.setPadding(padding, padding / 2, padding, 0);
        final EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true); nameInput.setHint("Project name"); nameInput.setText("MyApp");
        form.addView(nameInput, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final EditText packageInput = new EditText(this);
        packageInput.setSingleLine(true); packageInput.setHint("Application ID"); packageInput.setText("com.example.myapp");
        form.addView(packageInput, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        String title;
        if (template == ProjectTemplateGenerator.Template.CLASSIC_JAVA) title = "New Classic Java project";
        else if (template == ProjectTemplateGenerator.Template.MODERN_ANDROIDX_KOTLIN) title = "New AndroidX Kotlin project";
        else if (template == ProjectTemplateGenerator.Template.NO_ACTIVITY_JAVA) title = "New No Activity project";
        else if (template == ProjectTemplateGenerator.Template.WEBVIEW_JAVA) title = "New WebView App project";
        else if (template == ProjectTemplateGenerator.Template.LIBRARY_JAVA) title = "New Library Module project";
        else title = "New AndroidX Java project";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                        String applicationId = packageInput.getText() == null ? "" : packageInput.getText().toString().trim();
                        createProject(name, applicationId, template);
                    }
                }).show();
    }

    private void createProject(final String name, final String applicationId, final ProjectTemplateGenerator.Template template) {
        createProjectButton.setEnabled(false);
        appendConsole("\nCreating project: " + name);
        io.execute(new Runnable() { @Override public void run() {
            try {
                File projects = projectStorageDirectory();
                final File created = ProjectTemplateGenerator.create(projects, name, applicationId, template);
                appendConsole("PROJECT CREATED: " + created.getAbsolutePath());
                runOnUiThread(new Runnable() { @Override public void run() { loadProject(created); } });
            } catch (Exception e) { appendConsole("CREATE PROJECT ERROR: " + e.getMessage()); }
            finally { runOnUiThread(new Runnable() { @Override public void run() { createProjectButton.setEnabled(true); } }); }
        }});
    }

    private File projectStorageDirectory() throws IOException {
        File storageRoot = getExternalFilesDir(null);
        if (storageRoot == null) storageRoot = getFilesDir();
        File projects = new File(storageRoot, "projects");
        if (!projects.isDirectory() && !projects.mkdirs()) throw new IOException("Cannot create projects directory");
        return projects;
    }

    private void chooseZip() { startPicker("application/zip", REQUEST_IMPORT_ZIP); }
    private void chooseToolchainPack() { startPicker("application/zip", REQUEST_TOOLCHAIN_PACK); }
    private void chooseRuntimeBootstrap() { startPicker("application/zip", REQUEST_RUNTIME_BOOTSTRAP); }

    private void chooseBackupDestination() {
        if (projectRoot == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, sanitizeProjectName(projectRoot.getName()) + "-backup.zip");
        startActivityForResult(intent, REQUEST_EXPORT_BACKUP);
    }

    private void startPicker(String type, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        startActivityForResult(intent, requestCode);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_IMPORT_ZIP) importZip(data.getData());
        else if (requestCode == REQUEST_TOOLCHAIN_PACK) importToolchainPack(data.getData());
        else if (requestCode == REQUEST_RUNTIME_BOOTSTRAP) importRuntimeBootstrap(data.getData());
        else if (requestCode == REQUEST_EXPORT_BACKUP) exportProjectBackup(data.getData());
    }

    private void exportProjectBackup(final Uri uri) {
        final File root = projectRoot;
        final WorkspacePathResolver resolver = workspacePathResolver;
        captureActiveEditorState();
        final List<DocumentSaveSnapshot> saves = snapshotOpenDocuments();
        if (root == null) return;
        backupButton.setEnabled(false);
        appendConsole("\nBacking up project to: " + displayName(uri));
        io.execute(new Runnable() { @Override public void run() {
            OutputStream output = null;
            try {
                writeSnapshots(saves, resolver);
                output = new BufferedOutputStream(getContentResolver().openOutputStream(uri));
                if (output == null) throw new IOException("Cannot open backup destination");
                ProjectArchiveService.ArchiveResult result = ProjectArchiveService.writeSourceArchive(root, output, MAX_BACKUP_FILE_ENTRIES, MAX_BACKUP_BYTES);
                output.flush();
                appendConsole("BACKUP COMPLETE: " + result.getFileEntries() + " files, " + humanBytes(result.getUncompressedBytes()));
                runOnUiThread(new Runnable() { @Override public void run() { markSnapshotsSaved(saves); } });
            } catch (Exception e) { appendConsole("BACKUP ERROR: " + e.getMessage()); }
            finally {
                closeQuietly(output);
                runOnUiThread(new Runnable() { @Override public void run() {
                    backupButton.setEnabled(projectRoot != null); renderEditorTabs(); updateEditorButtons();
                }});
            }
        }});
    }

    private void importRuntimeBootstrap(final Uri uri) {
        runtimeButton.setEnabled(false);
        appendConsole("\nInstalling DevxyzIDE terminal runtime: " + displayName(uri));
        io.execute(new Runnable() { @Override public void run() {
            InputStream input = null;
            try {
                input = getContentResolver().openInputStream(uri);
                if (input == null) throw new IOException("Cannot open runtime bootstrap");
                TerminalBootstrapInstaller.InstallResult result = TerminalBootstrapInstaller.install(input, getFilesDir(), getPackageName());
                ToolchainInventory inventory = ToolchainInventory.scan(getFilesDir());
                appendConsole("RUNTIME INSTALLED: " + result.getPrefix().getAbsolutePath());
                appendConsole("Runtime ABI: " + result.getArch() + ", files=" + result.getFiles() + ", symlinks=" + result.getSymlinks());
                appendConsole("Toolchain inventory: " + inventory.summary());
                if (projectRoot != null) analyzeProject(projectRoot);
            } catch (Exception e) { appendConsole("RUNTIME ERROR: " + e.getMessage()); }
            finally { closeQuietly(input); runOnUiThread(new Runnable() { @Override public void run() { runtimeButton.setEnabled(true); } }); }
        }});
    }

    private void importToolchainPack(final Uri uri) {
        toolchainButton.setEnabled(false);
        appendConsole("\nInstalling DevxyzIDE toolchain pack: " + displayName(uri));
        io.execute(new Runnable() { @Override public void run() {
            File temp = new File(getCacheDir(), "toolchain-" + System.nanoTime() + ".zip");
            InputStream input = null; OutputStream output = null;
            try {
                input = getContentResolver().openInputStream(uri);
                output = new BufferedOutputStream(new FileOutputStream(temp));
                if (input == null) throw new IOException("Cannot open toolchain pack");
                copyWithLimit(input, output, MAX_TOOLCHAIN_PACK_BYTES);
                closeQuietly(output); output = null; closeQuietly(input); input = null;
                ToolchainPackInstaller.InstallResult result = ToolchainPackInstaller.install(temp, getFilesDir());
                ToolchainInventory inventory = ToolchainInventory.scan(getFilesDir());
                appendConsole("TOOLCHAIN INSTALLED: " + result.getInstalledDirectory().getAbsolutePath());
                appendConsole("Verified files: " + result.getVerifiedFiles());
                appendConsole("Toolchain inventory: " + inventory.summary());
                if (projectRoot != null) analyzeProject(projectRoot);
            } catch (Exception e) { appendConsole("TOOLCHAIN ERROR: " + e.getMessage()); }
            finally {
                closeQuietly(output); closeQuietly(input); if (temp.exists()) temp.delete();
                runOnUiThread(new Runnable() { @Override public void run() { toolchainButton.setEnabled(true); } });
            }
        }});
    }

    private void importZip(final Uri uri) {
        importButton.setEnabled(false);
        appendConsole("\nImporting: " + displayName(uri));
        io.execute(new Runnable() { @Override public void run() {
            InputStream input = null;
            try {
                File projects = projectStorageDirectory();
                File target = uniqueDirectory(projects, sanitizeProjectName(displayName(uri)));
                input = getContentResolver().openInputStream(uri);
                if (input == null) throw new IOException("Cannot open ZIP input stream");
                long freeBefore = target.getParentFile().getUsableSpace();
                appendConsole("Import storage available: " + humanBytes(freeBefore));
                SafeZip.extractProject(input, target, MAX_IMPORT_ENTRIES, MAX_IMPORT_BYTES);
                File collapsed = collapseSingleRootFolder(target);
                final File normalized = ProjectRootDetector.findBestGradleRoot(collapsed, 6, 20000);
                appendConsole("Detected project root: " + normalized.getAbsolutePath());
                runOnUiThread(new Runnable() { @Override public void run() { loadProject(normalized); } });
            } catch (Exception e) { appendConsole("IMPORT ERROR: " + e.getMessage()); }
            finally { closeQuietly(input); runOnUiThread(new Runnable() { @Override public void run() { importButton.setEnabled(true); } }); }
        }});
    }

    private void loadProject(File root) {
        if (root == null) return;
        captureActiveEditorState();
        if (editorSession.hasDirtyDocuments()) {
            confirmProjectSwitch(root);
            return;
        }
        loadProjectNow(root);
    }

    private void confirmProjectSwitch(final File root) {
        new AlertDialog.Builder(this)
                .setTitle("Unsaved changes")
                .setMessage("Save open files before switching projects?")
                .setPositiveButton("Save All & Switch", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { saveAllBeforeProjectSwitch(root); }
                })
                .setNeutralButton("Discard & Switch", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { loadProjectNow(root); }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveAllBeforeProjectSwitch(final File root) {
        captureActiveEditorState();
        final WorkspacePathResolver resolver = workspacePathResolver;
        final List<DocumentSaveSnapshot> snapshots = snapshotOpenDocuments();
        if (resolver == null) {
            appendConsole("PROJECT SWITCH ERROR: Workspace resolver is unavailable");
            return;
        }
        editor.setEnabled(false);
        saveAllButton.setEnabled(false);
        saveButton.setEnabled(false);
        io.execute(new Runnable() { @Override public void run() {
            try {
                writeSnapshots(snapshots, resolver);
                runOnUiThread(new Runnable() { @Override public void run() {
                    markSnapshotsSaved(snapshots);
                    appendConsole("SAVE ALL: " + snapshots.size() + " open file(s) saved before project switch.");
                    loadProjectNow(root);
                }});
            } catch (Exception e) {
                appendConsole("PROJECT SWITCH SAVE ERROR: " + e.getMessage());
                runOnUiThread(new Runnable() { @Override public void run() {
                    editor.setEnabled(true);
                    updateEditorButtons();
                }});
            }
        }});
    }

    private void loadProjectNow(File root) {
        final File canonicalRoot; final WorkspacePathResolver resolver;
        try { canonicalRoot = root.getCanonicalFile(); resolver = new WorkspacePathResolver(canonicalRoot, PROJECT_BACKEND_ID); }
        catch (IOException e) { editor.setEnabled(true); appendConsole("PROJECT ERROR: " + e.getMessage()); return; }
        cancelActiveProjectSearch();
        editorSession.closeAll(true);
        buildProblems.clear();
        projectRoot = canonicalRoot;
        workspacePathResolver = resolver;
        directoryService = new ProjectDirectoryService(resolver);
        fileService = new ProjectFileService(resolver, PROJECT_BACKEND_ID);
        currentDirectory = ProjectPath.of(PROJECT_BACKEND_ID, "");
        currentPath = null; lastBuiltApk = null;
        editor.setEnabled(true);
        updateProjectPathLabel();
        backupButton.setEnabled(true); newFileButton.setEnabled(true); newFolderButton.setEnabled(true);
        projectSearchButton.setEnabled(true); buildButton.setEnabled(true); installButton.setEnabled(false);
        updateProblemsButton(); renderActiveEditor(); refreshCurrentDirectory();
        appendConsole("Loaded project: " + canonicalRoot.getName());
        if (!new File(canonicalRoot, "gradlew").isFile()) appendConsole("No project Gradle Wrapper found; DevxyzIDE will try its internal Gradle runtime at build time.");
        analyzeProject(canonicalRoot);
    }

    private void analyzeProject(final File root) {
        io.execute(new Runnable() { @Override public void run() {
            try {
                ProjectRequirements requirements = ProjectAnalyzer.analyze(root);
                appendConsole(BuildRunner.describeProject(requirements));
                ToolchainProvisioningPlan provisioning = ToolchainProvisioningPlan.create(requirements, getFilesDir());
                appendConsole("Toolchain provisioning: " + provisioning.summary());
                for (String warning : requirements.getWarnings()) appendConsole("ANALYZE WARNING: " + warning);
                if (!requirements.getRepositories().isEmpty()) appendConsole("Repository resolution stays with Gradle; custom Maven repositories are preserved.");
            } catch (Exception e) { appendConsole("ANALYZE ERROR: " + e.getMessage()); }
        }});
    }

    private void refreshCurrentDirectory() {
        final ProjectDirectoryService service = directoryService; final ProjectPath directory = currentDirectory;
        if (service == null || directory == null) return;
        io.execute(new Runnable() { @Override public void run() {
            try {
                final ProjectDirectoryListing listing = listDirectory(service, directory);
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (directoryService != service || !sameProjectPath(currentDirectory, directory)) return;
                    directoryEntries.clear(); directoryEntries.addAll(listing.getEntries()); explorerRows.clear();
                    if (directory.parent() != null) explorerRows.add(PARENT_ROW);
                    for (ProjectEntry entry : directoryEntries) explorerRows.add((entry.isDirectory() ? "[DIR] " : "      ") + entry.getName());
                    fileAdapter.notifyDataSetChanged(); updateProjectPathLabel();
                    if (listing.isTruncated()) appendConsole("Explorer shows the first " + MAX_DIRECTORY_CHILDREN + " entries in this folder.");
                }});
            } catch (Exception e) { appendConsole("FILE LIST ERROR: " + e.getMessage()); }
        }});
    }

    private ProjectDirectoryListing listDirectory(ProjectDirectoryService service, ProjectPath directory) throws IOException {
        return service.listChildren(directory, MAX_DIRECTORY_CHILDREN);
    }

    private void openExplorerRow(int position) {
        ProjectPath directory = currentDirectory; if (directory == null) return;
        ProjectPath parent = directory.parent();
        if (parent != null && position == 0) { currentDirectory = parent; refreshCurrentDirectory(); return; }
        ProjectEntry entry = explorerEntryAt(position); if (entry == null) return;
        if (entry.isDirectory()) { currentDirectory = entry.getPath(); refreshCurrentDirectory(); }
        else openProjectFile(entry.getPath());
    }

    private ProjectEntry explorerEntryAt(int position) {
        ProjectPath directory = currentDirectory; if (directory == null) return null;
        int parentOffset = directory.parent() == null ? 0 : 1; int entryIndex = position - parentOffset;
        if (entryIndex < 0 || entryIndex >= directoryEntries.size()) return null;
        return directoryEntries.get(entryIndex);
    }

    private void promptCreateEntry(final boolean directory) {
        if (fileService == null || currentDirectory == null) return;
        final EditText nameInput = new EditText(this); nameInput.setSingleLine(true); nameInput.setHint(directory ? "folder-name" : "File.java");
        new AlertDialog.Builder(this).setTitle(directory ? "New folder" : "New file").setView(nameInput).setNegativeButton("Cancel", null)
                .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String value = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                        if (value.length() == 0) { appendConsole("CREATE ERROR: Name must not be blank"); return; }
                        createProjectEntry(directory, value);
                    }
                }).show();
    }

    private void createProjectEntry(final boolean directory, final String name) {
        final ProjectFileService service = fileService; final ProjectPath parent = currentDirectory;
        if (service == null || parent == null) return;
        io.execute(new Runnable() { @Override public void run() {
            try {
                final ProjectPath created = directory ? service.createDirectory(parent, name) : service.createFile(parent, name);
                appendConsole((directory ? "Created folder: " : "Created file: ") + created.getRelativePath());
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (fileService != service || !sameProjectPath(currentDirectory, parent)) return;
                    refreshCurrentDirectory(); if (!directory) openProjectFile(created);
                }});
            } catch (Exception e) { appendConsole("CREATE ERROR: " + e.getMessage()); }
        }});
    }

    private void showEntryActions(final ProjectEntry entry) {
        final String[] actions = new String[]{"Rename", "Duplicate", "Delete"};
        new AlertDialog.Builder(this).setTitle(entry.getName()).setItems(actions, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) promptRename(entry); else if (which == 1) duplicateEntry(entry); else if (which == 2) confirmDelete(entry);
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void promptRename(final ProjectEntry entry) {
        final EditText nameInput = new EditText(this); nameInput.setSingleLine(true); nameInput.setText(entry.getName()); nameInput.setSelection(nameInput.length());
        new AlertDialog.Builder(this).setTitle("Rename").setView(nameInput).setNegativeButton("Cancel", null)
                .setPositiveButton("Rename", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        String value = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
                        if (value.length() == 0) { appendConsole("RENAME ERROR: Name must not be blank"); return; }
                        renameEntry(entry, value);
                    }
                }).show();
    }

    private void renameEntry(final ProjectEntry entry, final String newName) {
        final ProjectFileService service = fileService; final ProjectPath parent = currentDirectory; final ProjectPath oldPath = entry.getPath();
        final WorkspacePathResolver resolver = workspacePathResolver; captureActiveEditorState(); final List<DocumentSaveSnapshot> affected = snapshotDocumentsUnder(oldPath);
        if (service == null || parent == null) return;
        io.execute(new Runnable() { @Override public void run() {
            try {
                writeSnapshots(affected, resolver); final ProjectPath renamed = service.rename(oldPath, newName);
                appendConsole("Renamed: " + oldPath.getRelativePath() + " -> " + renamed.getRelativePath());
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (fileService != service) return; markSnapshotsSaved(affected); closeDocumentsUnder(oldPath);
                    if (sameProjectPath(currentDirectory, parent)) refreshCurrentDirectory(); renderActiveEditor();
                }});
            } catch (Exception e) { appendConsole("RENAME ERROR: " + e.getMessage()); }
        }});
    }

    private void duplicateEntry(final ProjectEntry entry) {
        final ProjectFileService service = fileService; final ProjectPath parent = currentDirectory; if (service == null || parent == null) return;
        io.execute(new Runnable() { @Override public void run() {
            try { ProjectPath duplicate = service.duplicate(entry.getPath()); appendConsole("Duplicated: " + duplicate.getRelativePath());
                runOnUiThread(new Runnable() { @Override public void run() { if (fileService == service && sameProjectPath(currentDirectory, parent)) refreshCurrentDirectory(); }});
            } catch (Exception e) { appendConsole("DUPLICATE ERROR: " + e.getMessage()); }
        }});
    }

    private void confirmDelete(final ProjectEntry entry) {
        captureActiveEditorState(); boolean hasDirty = hasDirtyDocumentsUnder(entry.getPath());
        String detail = entry.isDirectory() ? "Delete this folder and all files inside it? This cannot be undone." : "Delete this file? This cannot be undone.";
        if (hasDirty) detail += " Unsaved changes in open tabs inside this path will also be discarded.";
        new AlertDialog.Builder(this).setTitle("Delete " + entry.getName() + "?").setMessage(detail).setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) { deleteEntry(entry); }}).show();
    }

    private void deleteEntry(final ProjectEntry entry) {
        final ProjectFileService service = fileService; final ProjectPath parent = currentDirectory; final ProjectPath deletedPath = entry.getPath();
        if (service == null || parent == null) return;
        io.execute(new Runnable() { @Override public void run() {
            try { service.delete(deletedPath); appendConsole("Deleted: " + deletedPath.getRelativePath());
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (fileService != service) return; closeDocumentsUnder(deletedPath);
                    if (sameProjectPath(currentDirectory, parent)) refreshCurrentDirectory(); renderActiveEditor();
                }});
            } catch (Exception e) { appendConsole("DELETE ERROR: " + e.getMessage()); }
        }});
    }

    private void openProjectFile(final ProjectPath path) { openProjectFileAt(path, -1, -1); }

    private void openProjectFileAt(final ProjectPath path, final int line, final int column) {
        final WorkspacePathResolver resolver = workspacePathResolver; if (resolver == null || path == null) return;
        EditorDocument alreadyOpen = editorSession.find(path);
        if (alreadyOpen != null) {
            captureActiveEditorState(); editorSession.switchTo(path); renderActiveEditor();
            if (line > 0) moveEditorToLineColumn(line, column); return;
        }
        if (!TextFileClassifier.isTextFile(path.getRelativePath())) { appendConsole("Binary/non-text file not opened: " + path.getRelativePath()); return; }
        io.execute(new Runnable() { @Override public void run() {
            try {
                final File file = resolver.resolve(path);
                if (file.length() > MAX_TEXT_BYTES) { appendConsole("File is larger than 2 MiB; editor refused it: " + path.getRelativePath()); return; }
                final String text = readUtf8(file, MAX_TEXT_BYTES);
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (workspacePathResolver != resolver) return; captureActiveEditorState(); editorSession.open(path, text); renderActiveEditor();
                    if (line > 0) moveEditorToLineColumn(line, column); appendConsole("Opened: " + path.getRelativePath());
                }});
            } catch (Exception e) { appendConsole("OPEN ERROR: " + e.getMessage()); }
        }});
    }

    private void moveEditorToLineColumn(int targetLine, int targetColumn) {
        String text = editor.getText() == null ? "" : editor.getText().toString();
        int line = 1; int offset = 0;
        while (offset < text.length() && line < Math.max(1, targetLine)) { if (text.charAt(offset++) == '\n') line++; }
        int lineEnd = text.indexOf('\n', offset); if (lineEnd < 0) lineEnd = text.length();
        int columnOffset = Math.max(0, targetColumn - 1); int target = Math.min(lineEnd, offset + columnOffset);
        editor.requestFocus(); editor.setSelection(Math.min(target, editor.length())); captureActiveEditorState();
    }

    private void captureActiveEditorState() {
        EditorDocument active = editorSession.getActive(); if (active == null) return;
        active.setText(editor.getText() == null ? "" : editor.getText().toString());
        int start = editor.getSelectionStart(); int end = editor.getSelectionEnd(); if (start < 0) start = 0; if (end < 0) end = start;
        active.setSelectionStart(start); active.setSelectionEnd(end); active.setCursorOffset(end); active.setScrollY(editor.getScrollY()); currentPath = active.getPath();
    }

    private void renderActiveEditor() {
        final EditorDocument active = editorSession.getActive(); suppressEditorTextWatcher = true;
        try {
            if (active == null) { currentPath = null; editor.setText(""); }
            else {
                currentPath = active.getPath(); editor.setText(active.getText());
                int start = Math.min(active.getSelectionStart(), editor.length()); int end = Math.min(active.getSelectionEnd(), editor.length()); if (start > end) start = end;
                editor.setSelection(start, end); editor.post(new Runnable() { @Override public void run() { editor.scrollTo(0, active.getScrollY()); }});
            }
        } finally { suppressEditorTextWatcher = false; }
        renderEditorTabs(); updateEditorButtons();
    }

    private void renderEditorTabs() {
        tabBar.removeAllViews(); EditorDocument active = editorSession.getActive();
        for (final EditorDocument document : editorSession.getDocuments()) {
            Button tab = new Button(this); tab.setAllCaps(false); String label = (document.isDirty() ? "* " : "") + document.getDisplayName();
            if (document == active) label = "[" + label + "]"; tab.setText(label); tab.setTextSize(11f); tab.setMinHeight(0); tab.setMinimumHeight(0); tab.setPadding(dp(10), dp(4), dp(10), dp(4));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); params.setMargins(dp(2), dp(2), dp(2), dp(2)); tabBar.addView(tab, params);
            tab.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { captureActiveEditorState(); editorSession.switchTo(document.getPath()); renderActiveEditor(); }});
            tab.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { captureActiveEditorState(); requestCloseTab(document); return true; }});
        }
    }

    private void requestCloseTab(final EditorDocument document) {
        if (document == null) return; if (!document.isDirty()) { editorSession.close(document.getPath(), true); renderActiveEditor(); return; }
        new AlertDialog.Builder(this).setTitle("Unsaved changes").setMessage("Save changes to " + document.getDisplayName() + " before closing?")
                .setPositiveButton("Save", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) { saveDocumentAndClose(document); }})
                .setNeutralButton("Discard", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) { editorSession.close(document.getPath(), true); renderActiveEditor(); }})
                .setNegativeButton("Cancel", null).show();
    }

    private void saveDocumentAndClose(final EditorDocument document) {
        final WorkspacePathResolver resolver = workspacePathResolver; final DocumentSaveSnapshot snapshot = new DocumentSaveSnapshot(document, document.getPath(), document.getText());
        io.execute(new Runnable() { @Override public void run() {
            try {
                if (resolver == null) throw new IOException("Workspace resolver is unavailable"); writeUtf8(resolver.resolve(snapshot.path), snapshot.text);
                runOnUiThread(new Runnable() { @Override public void run() { if (document.getText().equals(snapshot.text)) document.markSaved(); editorSession.close(document.getPath(), true); renderActiveEditor(); }});
                appendConsole("Saved and closed: " + snapshot.path.getRelativePath());
            } catch (Exception e) { appendConsole("SAVE ERROR: " + e.getMessage()); }
        }});
    }

    private void showSearchDialog() {
        final EditorDocument active = editorSession.getActive(); if (active == null) return;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); int padding = dp(16); form.setPadding(padding, padding / 2, padding, 0);
        final EditText query = new EditText(this); query.setSingleLine(true); query.setHint("Find text"); form.addView(query, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final EditText replacement = new EditText(this); replacement.setSingleLine(true); replacement.setHint("Replace with (optional)"); form.addView(replacement, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final CheckBox matchCase = new CheckBox(this); matchCase.setText("Match case"); form.addView(matchCase, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle("Search / Replace").setView(form)
                .setPositiveButton("Find Next", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) { String q = textOf(query); if (q.length() == 0) { appendConsole("SEARCH: Enter text to find."); return; } findNextInEditor(q, matchCase.isChecked()); }})
                .setNeutralButton("Replace All", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) { String q = textOf(query); if (q.length() == 0) { appendConsole("SEARCH: Enter text to replace."); return; } replaceAllInEditor(q, textOf(replacement), matchCase.isChecked()); }})
                .setNegativeButton("Cancel", null).show();
    }

    private void findNextInEditor(String query, boolean matchCase) {
        String text = textOf(editor); int from = editor.getSelectionEnd(); if (from < 0) from = 0;
        TextSearchService.Match match = TextSearchService.findNext(text, query, from, matchCase, true);
        if (match == null) { appendConsole("SEARCH: No match for \"" + query + "\"."); return; }
        editor.requestFocus(); editor.setSelection(match.getOffset(), match.getOffset() + match.getLength()); captureActiveEditorState();
        appendConsole("SEARCH: line " + match.getLineNumber() + ", column " + match.getColumnNumber());
    }

    private void replaceAllInEditor(String query, String replacement, boolean matchCase) {
        TextSearchService.ReplaceResult result = TextSearchService.replaceAll(textOf(editor), query, replacement, matchCase, 1000000);
        if (result.getReplacementCount() == 0) { appendConsole("REPLACE: No match for \"" + query + "\"."); return; }
        editor.setText(result.getText()); editor.setSelection(0); captureActiveEditorState(); renderEditorTabs(); appendConsole("REPLACE: " + result.getReplacementCount() + " occurrence(s) replaced.");
    }

    private void showProjectSearchDialog() {
        if (projectRoot == null || workspacePathResolver == null) return;
        final EditText query = new EditText(this); query.setSingleLine(true); query.setHint("Search all project files");
        final CheckBox matchCase = new CheckBox(this); matchCase.setText("Match case");
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); int padding = dp(16); form.setPadding(padding, padding / 2, padding, 0);
        form.addView(query, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        form.addView(matchCase, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this).setTitle("Find in Project").setView(form).setNegativeButton("Cancel", null)
                .setPositiveButton("Search", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) {
                    String q = textOf(query); if (q.length() == 0) { appendConsole("PROJECT SEARCH: Enter text to find."); return; }
                    startProjectSearch(q, matchCase.isChecked());
                }}).show();
    }

    private void startProjectSearch(final String query, final boolean matchCase) {
        final WorkspacePathResolver resolver = workspacePathResolver; if (resolver == null) return;
        final SearchCancellationFlag cancellation = new SearchCancellationFlag(); activeProjectSearch = cancellation;
        projectSearchButton.setEnabled(true); projectSearchButton.setText("Cancel Search"); appendConsole("PROJECT SEARCH: \"" + query + "\"");
        io.execute(new Runnable() { @Override public void run() {
            final List<ProjectSearchService.SearchMatch> results = new ArrayList<ProjectSearchService.SearchMatch>();
            try {
                final int[] streamed = new int[]{0};
                final ProjectSearchService.SearchSummary summary = ProjectSearchService.search(
                        resolver, ProjectPath.of(PROJECT_BACKEND_ID, ""), query, matchCase,
                        MAX_PROJECT_SEARCH_FILES, MAX_PROJECT_SEARCH_RESULTS, MAX_TEXT_BYTES, cancellation,
                        new ProjectSearchService.Listener() { @Override public void onMatch(ProjectSearchService.SearchMatch match) {
                            results.add(match); streamed[0]++; if (streamed[0] == 1 || streamed[0] % 25 == 0) appendConsole("PROJECT SEARCH: " + streamed[0] + " match(es)…");
                        }});
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (activeProjectSearch == cancellation) activeProjectSearch = null; restoreProjectSearchButton();
                    appendConsole("PROJECT SEARCH: " + summary.getMatches() + " match(es) in " + summary.getFilesScanned() + " file(s)" + (summary.isCancelled() ? " (cancelled)" : summary.isTruncated() ? " (limited)" : ""));
                    showProjectSearchResults(results, summary);
                }});
            } catch (Exception e) {
                appendConsole("PROJECT SEARCH ERROR: " + e.getMessage());
                runOnUiThread(new Runnable() { @Override public void run() { if (activeProjectSearch == cancellation) activeProjectSearch = null; restoreProjectSearchButton(); }});
            }
        }});
    }

    private void cancelActiveProjectSearch() {
        SearchCancellationFlag active = activeProjectSearch; if (active != null) { active.cancelled = true; appendConsole("PROJECT SEARCH: cancellation requested."); }
    }

    private void restoreProjectSearchButton() {
        projectSearchButton.setText(R.string.find_project); projectSearchButton.setEnabled(projectRoot != null);
    }

    private void showProjectSearchResults(final List<ProjectSearchService.SearchMatch> results, ProjectSearchService.SearchSummary summary) {
        if (results == null || results.isEmpty()) return;
        String[] rows = new String[results.size()];
        for (int i = 0; i < results.size(); i++) {
            ProjectSearchService.SearchMatch match = results.get(i);
            rows[i] = match.getPath().getRelativePath() + ":" + match.getLineNumber() + ":" + match.getColumnNumber() + "  " + compactSnippet(match.getLineText());
        }
        new AlertDialog.Builder(this).setTitle("Project Search · " + results.size() + (summary.isTruncated() ? "+" : ""))
                .setItems(rows, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) {
                    ProjectSearchService.SearchMatch match = results.get(which); openProjectFileAt(match.getPath(), match.getLineNumber(), match.getColumnNumber());
                }}).setNegativeButton("Close", null).show();
    }

    private void saveCurrentFile() {
        captureActiveEditorState(); final EditorDocument document = editorSession.getActive(); final WorkspacePathResolver resolver = workspacePathResolver;
        if (document == null || resolver == null) return; final DocumentSaveSnapshot snapshot = new DocumentSaveSnapshot(document, document.getPath(), document.getText());
        io.execute(new Runnable() { @Override public void run() {
            try { writeUtf8(resolver.resolve(snapshot.path), snapshot.text);
                runOnUiThread(new Runnable() { @Override public void run() { if (document.getText().equals(snapshot.text)) document.markSaved(); renderEditorTabs(); updateEditorButtons(); }});
                appendConsole("Saved: " + snapshot.path.getRelativePath());
            } catch (Exception e) { appendConsole("SAVE ERROR: " + e.getMessage()); }
        }});
    }

    private void saveAllOpenDocuments() {
        captureActiveEditorState(); final WorkspacePathResolver resolver = workspacePathResolver; final List<DocumentSaveSnapshot> snapshots = snapshotOpenDocuments();
        if (resolver == null || snapshots.isEmpty()) return; saveAllButton.setEnabled(false); saveButton.setEnabled(false);
        io.execute(new Runnable() { @Override public void run() {
            try { writeSnapshots(snapshots, resolver); runOnUiThread(new Runnable() { @Override public void run() { markSnapshotsSaved(snapshots); renderEditorTabs(); updateEditorButtons(); }}); appendConsole("SAVE ALL: " + snapshots.size() + " open file(s) saved."); }
            catch (Exception e) { appendConsole("SAVE ALL ERROR: " + e.getMessage()); }
            finally { runOnUiThread(new Runnable() { @Override public void run() { updateEditorButtons(); }}); }
        }});
    }

    private List<DocumentSaveSnapshot> snapshotOpenDocuments() {
        List<DocumentSaveSnapshot> snapshots = new ArrayList<DocumentSaveSnapshot>();
        for (EditorDocument document : editorSession.getDocuments()) snapshots.add(new DocumentSaveSnapshot(document, document.getPath(), document.getText()));
        return snapshots;
    }

    private List<DocumentSaveSnapshot> snapshotDocumentsUnder(ProjectPath parent) {
        List<DocumentSaveSnapshot> snapshots = new ArrayList<DocumentSaveSnapshot>();
        for (EditorDocument document : editorSession.getDocuments()) if (isSameOrDescendant(parent, document.getPath())) snapshots.add(new DocumentSaveSnapshot(document, document.getPath(), document.getText()));
        return snapshots;
    }

    private void writeSnapshots(List<DocumentSaveSnapshot> snapshots, WorkspacePathResolver resolver) throws IOException {
        if (resolver == null && !snapshots.isEmpty()) throw new IOException("Workspace resolver is unavailable");
        for (DocumentSaveSnapshot snapshot : snapshots) writeUtf8(resolver.resolve(snapshot.path), snapshot.text);
    }

    private void markSnapshotsSaved(List<DocumentSaveSnapshot> snapshots) {
        for (DocumentSaveSnapshot snapshot : snapshots) if (snapshot.document.getText().equals(snapshot.text)) snapshot.document.markSaved();
    }

    private void closeDocumentsUnder(ProjectPath parent) {
        List<ProjectPath> paths = new ArrayList<ProjectPath>(); for (EditorDocument document : editorSession.getDocuments()) if (isSameOrDescendant(parent, document.getPath())) paths.add(document.getPath());
        for (ProjectPath path : paths) editorSession.close(path, true);
    }

    private boolean hasDirtyDocumentsUnder(ProjectPath parent) {
        for (EditorDocument document : editorSession.getDocuments()) if (isSameOrDescendant(parent, document.getPath()) && document.isDirty()) return true;
        return false;
    }

    private void updateEditorButtons() {
        boolean hasActive = editorSession.getActive() != null; searchButton.setEnabled(hasActive); saveButton.setEnabled(hasActive); saveAllButton.setEnabled(editorSession.size() > 0);
    }

    private static boolean isSameOrDescendant(ProjectPath parent, ProjectPath candidate) {
        if (parent == null || candidate == null || !parent.getBackendId().equals(candidate.getBackendId())) return false;
        String parentPath = parent.getRelativePath(); String candidatePath = candidate.getRelativePath();
        if (parentPath.equals(candidatePath)) return true; return parentPath.length() > 0 && candidatePath.startsWith(parentPath + "/");
    }

    private void updateProjectPathLabel() {
        if (projectRoot == null || currentDirectory == null) return; String relative = currentDirectory.getRelativePath();
        projectPath.setText(relative.length() == 0 ? projectRoot.getAbsolutePath() : projectRoot.getAbsolutePath() + File.separator + relative.replace('/', File.separatorChar));
    }

    private static boolean sameProjectPath(ProjectPath left, ProjectPath right) {
        return left != null && right != null && left.getBackendId().equals(right.getBackendId()) && left.getRelativePath().equals(right.getRelativePath());
    }

    private void buildProject() {
        if (projectRoot == null) return; captureActiveEditorState(); final File root = projectRoot; final WorkspacePathResolver resolver = workspacePathResolver; final List<DocumentSaveSnapshot> saves = snapshotOpenDocuments();
        buildProblems.clear(); updateProblemsButton(); buildButton.setEnabled(false); installButton.setEnabled(false); lastBuiltApk = null;
        appendConsole("\n> DevxyzIDE wrapper-aware preflight + assembleDebug");
        io.execute(new Runnable() { @Override public void run() {
            try { writeSnapshots(saves, resolver); }
            catch (IOException e) { appendConsole("SAVE ERROR: " + e.getMessage()); runOnUiThread(new Runnable() { @Override public void run() { buildButton.setEnabled(true); }}); return; }
            runOnUiThread(new Runnable() { @Override public void run() { markSnapshotsSaved(saves); renderEditorTabs(); updateEditorButtons(); }});
            BuildRunner.runDebugBuild(root, getFilesDir(), new BuildRunner.Listener() {
                @Override public void onLine(String line) { appendConsole(line); recordBuildProblem(line, root); }
                @Override public void onFinished(final int exitCode, final File apk) {
                    runOnUiThread(new Runnable() { @Override public void run() {
                        buildButton.setEnabled(true); lastBuiltApk = apk; installButton.setEnabled(apk != null && apk.isFile()); updateProblemsButton();
                        appendConsole(exitCode == 0 ? "BUILD FINISHED: SUCCESS" : "BUILD FINISHED: exit " + exitCode); if (apk != null) appendConsole("APK: " + apk.getAbsolutePath());
                    }});
                }
            });
        }});
    }

    private void recordBuildProblem(String line, File root) {
        try {
            final BuildProblem problem = BuildDiagnosticsParser.parseLine(line, root, PROJECT_BACKEND_ID); if (problem == null) return;
            runOnUiThread(new Runnable() { @Override public void run() {
                if (buildProblems.size() < MAX_BUILD_PROBLEMS) buildProblems.add(problem); updateProblemsButton();
            }});
        } catch (IOException ignored) { }
    }

    private void updateProblemsButton() {
        int count = buildProblems.size(); problemsButton.setEnabled(count > 0); problemsButton.setText(count == 0 ? getString(R.string.problems) : "Problems (" + count + ")");
    }

    private void showProblemsDialog() {
        if (buildProblems.isEmpty()) return; final List<BuildProblem> snapshot = new ArrayList<BuildProblem>(buildProblems); String[] rows = new String[snapshot.size()];
        for (int i = 0; i < snapshot.size(); i++) rows[i] = snapshot.get(i).summary();
        new AlertDialog.Builder(this).setTitle("Build Problems").setItems(rows, new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) {
            BuildProblem problem = snapshot.get(which); openProjectFileAt(problem.getPath(), problem.getLine(), problem.getColumn());
        }}).setNegativeButton("Close", null).show();
    }

    private void appendConsole(final String line) {
        runOnUiThread(new Runnable() { @Override public void run() {
            if (consoleText.length() > 200000) consoleText.setText("[console truncated]\n");
            consoleText.append((consoleText.length() == 0 ? "" : "\n") + line); consoleScroll.post(new Runnable() { @Override public void run() { consoleScroll.fullScroll(View.FOCUS_DOWN); }});
        }});
    }

    private String displayName(Uri uri) {
        Cursor cursor = null;
        try { cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null); if (cursor != null && cursor.moveToFirst()) { int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (index >= 0) return cursor.getString(index); }}
        catch (Exception ignored) { } finally { if (cursor != null) cursor.close(); }
        String last = uri.getLastPathSegment(); return last == null ? "project.zip" : last;
    }

    private static String sanitizeProjectName(String name) {
        String base = name == null ? "project" : name.replaceFirst("(?i)\\.zip$", ""); base = base.replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", ""); return base.length() == 0 ? "project" : base;
    }

    private static File uniqueDirectory(File parent, String base) throws IOException {
        File target = new File(parent, base); int suffix = 2; while (target.exists()) target = new File(parent, base + "-" + suffix++); if (!target.mkdirs()) throw new IOException("Cannot create project directory"); return target;
    }

    private static File collapseSingleRootFolder(File target) { File[] children = target.listFiles(); if (children != null && children.length == 1 && children[0].isDirectory()) return children[0]; return target; }

    private static void writeUtf8(File file, String content) throws IOException {
        OutputStream out = null; try { out = new BufferedOutputStream(new FileOutputStream(file)); out.write(content.getBytes("UTF-8")); out.flush(); } finally { closeQuietly(out); }
    }

    private static void copyWithLimit(InputStream input, OutputStream output, long maxBytes) throws IOException {
        byte[] buffer = new byte[64 * 1024]; long total = 0; int read; while ((read = input.read(buffer)) != -1) { total += read; if (total > maxBytes) throw new IOException("File exceeds import size limit"); output.write(buffer, 0, read); } output.flush();
    }

    private static String readUtf8(File file, long maxBytes) throws IOException {
        if (file.length() > maxBytes) throw new IOException("File exceeds editor size limit"); InputStream in = null; ByteArrayOutputStream out = new ByteArrayOutputStream();
        try { in = new BufferedInputStream(new FileInputStream(file)); byte[] buffer = new byte[16384]; int read; long total = 0; while ((read = in.read(buffer)) != -1) { total += read; if (total > maxBytes) throw new IOException("File exceeds editor size limit"); out.write(buffer, 0, read); } return out.toString("UTF-8"); }
        finally { closeQuietly(in); try { out.close(); } catch (IOException ignored) { } }
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B"; double value = bytes; String[] units = {"B", "KiB", "MiB", "GiB", "TiB"}; int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) { value /= 1024.0; unit++; } return String.format(java.util.Locale.US, "%.1f %s", value, units[unit]);
    }

    private static String compactSnippet(String value) { String clean = value == null ? "" : value.trim().replace('\t', ' '); return clean.length() > 100 ? clean.substring(0, 100) + "…" : clean; }
    private static String textOf(TextView view) { return view == null || view.getText() == null ? "" : view.getText().toString(); }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private static void closeQuietly(java.io.Closeable closeable) { if (closeable != null) try { closeable.close(); } catch (IOException ignored) { } }

    private static final class DocumentSaveSnapshot {
        private final EditorDocument document; private final ProjectPath path; private final String text;
        private DocumentSaveSnapshot(EditorDocument document, ProjectPath path, String text) { this.document = document; this.path = path; this.text = text; }
    }

    private static final class SearchCancellationFlag implements ProjectSearchService.Cancellation {
        private volatile boolean cancelled;
        @Override public boolean isCancelled() { return cancelled; }
    }

    @Override protected void onDestroy() { cancelActiveProjectSearch(); io.shutdownNow(); super.onDestroy(); }
}
