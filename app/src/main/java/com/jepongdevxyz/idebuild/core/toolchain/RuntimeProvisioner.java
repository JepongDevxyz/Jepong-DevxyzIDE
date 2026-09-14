package com.jepongdevxyz.idebuild.core.toolchain;

import java.io.File;
import java.io.IOException;

/** Downloads, verifies and installs one DevxyzIDE runtime pack. */
public final class RuntimeProvisioner {
    private RuntimeProvisioner() {}

    public static ToolchainPackInstaller.InstallResult install(
            RuntimePackDescriptor descriptor,
            File downloadDirectory,
            File appFilesDir) throws IOException {
        if (descriptor == null) throw new IOException("Runtime descriptor is missing");
        if (downloadDirectory == null) throw new IOException("Download directory is missing");
        File zip = new File(downloadDirectory, descriptor.getId() + ".devxyz-toolchain.zip");
        RuntimePackDownloader.download(descriptor, zip);
        try {
            return ToolchainPackInstaller.install(zip, appFilesDir);
        } finally {
            // The installed toolchain is verified again internally. Keep cache pressure low.
            if (zip.exists()) zip.delete();
        }
    }

    public static ToolchainPackInstaller.InstallResult installForTest(
            RuntimePackDescriptor descriptor,
            File downloadDirectory,
            File appFilesDir,
            RuntimePackDownloader.InputStreamFactory source) throws IOException {
        if (descriptor == null) throw new IOException("Runtime descriptor is missing");
        if (downloadDirectory == null) throw new IOException("Download directory is missing");
        File zip = new File(downloadDirectory, descriptor.getId() + ".devxyz-toolchain.zip");
        RuntimePackDownloader.download(descriptor, zip, source);
        try {
            return ToolchainPackInstaller.install(zip, appFilesDir);
        } finally {
            if (zip.exists()) zip.delete();
        }
    }
}
