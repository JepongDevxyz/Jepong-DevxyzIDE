package com.jepongdevxyz.idebuild;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.jepongdevxyz.idebuild.core.capability.Capability;
import com.jepongdevxyz.idebuild.core.capability.CapabilityRegistry;
import com.jepongdevxyz.idebuild.core.capability.CapabilityStatus;
import com.jepongdevxyz.idebuild.core.signing.ApkSignerService;
import com.jepongdevxyz.idebuild.core.signing.ApkSigningResult;
import com.jepongdevxyz.idebuild.core.toolchain.RuntimeLayout;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Real APK signing workflow. Keystores are selected through SAF and copied only
 * to a short-lived app-private file. Passwords are kept in memory for the active
 * signing request and are never written to preferences or console output.
 */
public final class ApkSigningActivity extends Activity {
    public static final String EXTRA_PROJECT_ROOT = "project_root";
    private static final int REQUEST_KEYSTORE = 6101;
    private static final long MAX_KEYSTORE_BYTES = 64L * 1024L * 1024L;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private File projectRoot;
    private File inputApk;
    private File signer;
    private File signedApk;
    private Uri keyStoreUri;
    private TextView apkLabel;
    private TextView signerLabel;
    private TextView keyStoreLabel;
    private TextView status;
    private EditText aliasInput;
    private EditText storePasswordInput;
    private EditText keyPasswordInput;
    private Button selectKeyStoreButton;
    private Button signButton;
    private Button installButton;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Sign APK");
        resolveInputs();
        setContentView(buildContent());
        refreshCapabilityState();
    }

    private void resolveInputs() {
        String rawRoot = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_PROJECT_ROOT);
        if (rawRoot == null) return;
        try {
            File candidate = new File(rawRoot).getCanonicalFile();
            if (candidate.isDirectory()) projectRoot = candidate;
        } catch (IOException ignored) { }
        if (projectRoot != null) inputApk = ApkLocator.findDebugApk(projectRoot);
        signer = RuntimeLayout.findApksigner(getFilesDir());
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));

        apkLabel = label();
        signerLabel = label();
        keyStoreLabel = label();
        status = label();
        status.setTextIsSelectable(true);

        root.addView(apkLabel);
        root.addView(signerLabel);
        root.addView(keyStoreLabel);

        selectKeyStoreButton = new Button(this);
        selectKeyStoreButton.setText("Select JKS / keystore");
        selectKeyStoreButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { chooseKeyStore(); }
        });
        root.addView(selectKeyStoreButton);

        aliasInput = new EditText(this);
        aliasInput.setSingleLine(true);
        aliasInput.setHint("Key alias");
        root.addView(aliasInput);

        storePasswordInput = secretInput("Keystore password");
        root.addView(storePasswordInput);

        keyPasswordInput = secretInput("Key password (blank = keystore password)");
        root.addView(keyPasswordInput);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        Button close = new Button(this);
        close.setText("Close");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { finish(); }
        });

        signButton = new Button(this);
        signButton.setText("Sign & Verify");
        signButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { startSigning(); }
        });

        installButton = new Button(this);
        installButton.setText("Install Signed APK");
        installButton.setEnabled(false);
        installButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (signedApk != null && signedApk.isFile()) ApkInstaller.install(ApkSigningActivity.this, signedApk);
            }
        });

        actions.addView(close);
        actions.addView(signButton);
        actions.addView(installButton);
        root.addView(actions);

        ScrollView statusScroll = new ScrollView(this);
        statusScroll.addView(status, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(statusScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return root;
    }

    private TextView label() {
        TextView view = new TextView(this);
        view.setTextSize(12f);
        view.setPadding(0, dp(4), 0, dp(4));
        return view;
    }

    private EditText secretInput(String hint) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return input;
    }

    private void refreshCapabilityState() {
        Capability capability = CapabilityRegistry.apkSigning(getFilesDir(), inputApk, keyStoreUri != null);
        apkLabel.setText(inputApk == null
                ? "APK: no debug APK found. Build the project first."
                : "APK: " + inputApk.getAbsolutePath());
        signerLabel.setText(signer == null
                ? "Signer: APK signer component not installed."
                : "Signer: " + signer.getAbsolutePath());
        keyStoreLabel.setText(keyStoreUri == null ? "Keystore: not selected" : "Keystore: selected via Android document picker");
        signButton.setEnabled(capability.getStatus() == CapabilityStatus.AVAILABLE);
        if (status != null && capability.getStatus() != CapabilityStatus.AVAILABLE && status.length() == 0) {
            status.setText(capability.getStatus().name() + ": " + capability.getUserMessage());
        }
    }

    private void chooseKeyStore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_KEYSTORE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_KEYSTORE || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        keyStoreUri = data.getData();
        refreshCapabilityState();
        setStatus("Keystore selected. Enter alias and passwords, then tap Sign & Verify.");
    }

    private void startSigning() {
        if (keyStoreUri == null || inputApk == null || signer == null) {
            refreshCapabilityState();
            return;
        }
        final String alias = text(aliasInput).trim();
        final String storePassword = text(storePasswordInput);
        final String keyPassword = text(keyPasswordInput);
        if (alias.length() == 0 || storePassword.length() == 0) {
            setStatus("Alias and keystore password are required.");
            return;
        }

        // Remove secrets from visible widgets as soon as the in-memory request owns them.
        storePasswordInput.setText("");
        keyPasswordInput.setText("");
        setBusy(true);
        setStatus("Signing and verifying APK…");

        final Uri uri = keyStoreUri;
        final File apk = inputApk;
        final File apksigner = signer;
        io.execute(new Runnable() {
            @Override public void run() {
                File tempKeyStore = null;
                try {
                    tempKeyStore = materializeKeyStore(uri);
                    File outputDir = new File(projectRoot, "devxyzide-signed");
                    String inputName = apk.getName();
                    String base = inputName.toLowerCase(java.util.Locale.US).endsWith(".apk")
                            ? inputName.substring(0, inputName.length() - 4)
                            : inputName;
                    File output = new File(outputDir, base + "-signed.apk");
                    ApkSigningResult result = ApkSignerService.signAndVerify(
                            apksigner,
                            apk,
                            output,
                            tempKeyStore,
                            alias,
                            storePassword,
                            keyPassword);
                    final ApkSigningResult finalResult = result;
                    final File finalOutput = output;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (finalResult.isSuccess()) {
                                signedApk = finalOutput;
                                installButton.setEnabled(true);
                                setStatus("SIGNED + VERIFIED\n" + finalOutput.getAbsolutePath() + "\n\n" + safeVerificationSummary(finalResult));
                            } else {
                                signedApk = null;
                                installButton.setEnabled(false);
                                setStatus("SIGNING FAILED\n" + safeVerificationSummary(finalResult));
                            }
                            setBusy(false);
                        }
                    });
                } catch (final Exception error) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            signedApk = null;
                            installButton.setEnabled(false);
                            setStatus("SIGNING ERROR: " + safeMessage(error));
                            setBusy(false);
                        }
                    });
                } finally {
                    if (tempKeyStore != null && tempKeyStore.exists()) tempKeyStore.delete();
                }
            }
        });
    }

    private File materializeKeyStore(Uri uri) throws IOException {
        File tempDir = new File(getFilesDir(), "signing-temp");
        if (!tempDir.isDirectory() && !tempDir.mkdirs()) throw new IOException("Could not create private signing-temp directory");
        File target = new File(tempDir, "keystore-" + System.nanoTime() + ".jks");
        InputStream raw = getContentResolver().openInputStream(uri);
        if (raw == null) throw new IOException("Could not open selected keystore");
        InputStream input = new BufferedInputStream(raw);
        OutputStream output = null;
        long total = 0L;
        try {
            output = new BufferedOutputStream(new FileOutputStream(target));
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_KEYSTORE_BYTES) throw new IOException("Selected keystore exceeds 64 MiB safety limit");
                output.write(buffer, 0, read);
            }
            output.flush();
            if (total == 0L) throw new IOException("Selected keystore is empty");
            return target;
        } catch (IOException error) {
            if (target.exists()) target.delete();
            throw error;
        } finally {
            try { input.close(); } catch (IOException ignored) { }
            if (output != null) try { output.close(); } catch (IOException ignored) { }
        }
    }

    private void setBusy(boolean busy) {
        selectKeyStoreButton.setEnabled(!busy);
        aliasInput.setEnabled(!busy);
        storePasswordInput.setEnabled(!busy);
        keyPasswordInput.setEnabled(!busy);
        signButton.setEnabled(!busy && inputApk != null && signer != null && keyStoreUri != null);
    }

    private void setStatus(String message) {
        status.setText(message == null ? "" : message);
    }

    private static String safeVerificationSummary(ApkSigningResult result) {
        if (result == null) return "No signing result.";
        String verify = result.getVerifyStdout();
        if (verify == null || verify.trim().length() == 0) verify = result.getVerifyStderr();
        if (verify == null || verify.trim().length() == 0) {
            return "sign exit=" + result.getSignExitCode() + ", verify exit=" + result.getVerifyExitCode();
        }
        if (verify.length() > 8000) verify = verify.substring(0, 8000) + "\n[verification output truncated]";
        return verify;
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().length() == 0 ? error.getClass().getSimpleName() : message;
    }

    private static String text(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        storePasswordInput.setText("");
        keyPasswordInput.setText("");
        super.onDestroy();
    }
}
