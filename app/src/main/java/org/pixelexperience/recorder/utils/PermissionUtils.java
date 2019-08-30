package org.pixelexperience.recorder.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

public class PermissionUtils {
    public static boolean hasStoragePermission(Context context) {
        int result = context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasAudioPermission(Context context) {
        int result = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasPhoneReaderPermission(Context context) {
        int result = context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasDrawOverOtherAppsPermission(Context context) {
        return Settings.canDrawOverlays(context);
    }

    public static boolean hasAllAudioRecorderPermissions(Context context) {
        return hasStoragePermission(context) && hasAudioPermission(context) && hasPhoneReaderPermission(context);
    }

    public static boolean hasAllScreenRecorderPermissions(Context context) {
        return hasStoragePermission(context);
    }
}
