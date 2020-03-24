package org.pixelexperience.recorder.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

public class PermissionUtils {
    public static boolean hasAudioPermission(Context context) {
        int result = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED;
    }
}
