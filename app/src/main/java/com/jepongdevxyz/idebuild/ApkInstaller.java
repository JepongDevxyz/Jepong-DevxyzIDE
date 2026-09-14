package com.jepongdevxyz.idebuild;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.io.File;

public final class ApkInstaller {
    private ApkInstaller() {}

    public static void install(Context context, File apk) {
        Uri uri = new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".apkprovider")
                .appendPath(Uri.encode(apk.getAbsolutePath()))
                .build();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
