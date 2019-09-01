/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pixelexperience.recorder.utils;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.core.content.FileProvider;

import org.pixelexperience.recorder.DeleteActivity;
import org.pixelexperience.recorder.R;
import org.pixelexperience.recorder.screen.ScreencastService;
import org.pixelexperience.recorder.sounds.SoundRecorderService;

import java.io.File;

public class LastRecordHelper {
    private static final String PREFS = "preferences";
    private static final String KEY_LAST_SOUND = "sound_last_path";
    private static final String KEY_LAST_SCREEN = "screen_last_path";
    private static final String KEY_LAST_SOUND_TIME = "sound_last_duration";
    private static final String KEY_LAST_SCREEN_TIME = "screen_last_duration";
    private static final String FILE_PROVIDER = "org.pixelexperience.recorder.fileprovider";

    private LastRecordHelper() {
    }

    public static AlertDialog deleteFile(Context context, final String path, boolean isSound) {
        return new AlertDialog.Builder(context, R.style.CustomDialogTheme)
                .setTitle(R.string.delete_title)
                .setMessage(context.getString(R.string.delete_message, path))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    File record = new File(path);
                    if (record.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        record.delete();
                    }
                    NotificationManager nm = context.getSystemService(NotificationManager.class);
                    if (nm == null) {
                        return;
                    }

                    if (isSound) {
                        nm.cancel(SoundRecorderService.NOTIFICATION_ID);
                    } else {
                        nm.cancel(ScreencastService.NOTIFICATION_ID);
                    }
                    setLastItem(context, null, 0, isSound);
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    public static Intent getShareIntent(Context context, String filePath, String mimeType) {
        File file = new File(filePath);
        Uri uri = FileProvider.getUriForFile(context, FILE_PROVIDER, file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, file.getName());
        Intent chooserIntent = Intent.createChooser(intent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        return chooserIntent;
    }

    public static Intent getOpenIntent(Context context, String filePath, String mimeType) {
        Uri uri = FileProvider.getUriForFile(context, FILE_PROVIDER, new File(filePath));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    public static Intent getDeleteIntent(Context context, boolean isSound) {
        Intent intent = new Intent(context, DeleteActivity.class);
        intent.putExtra(DeleteActivity.EXTRA_LAST_SOUND, isSound);
        return intent;
    }

    public static void setLastItem(Context context, String path, long duration,
                                   boolean isSound) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit()
                .putString(isSound ? KEY_LAST_SOUND : KEY_LAST_SCREEN, path)
                .putLong(isSound ? KEY_LAST_SOUND_TIME : KEY_LAST_SCREEN_TIME, duration)
                .apply();
    }

    public static String getLastItemPath(Context context, boolean isSound) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getString(isSound ? KEY_LAST_SOUND : KEY_LAST_SCREEN, null);
    }

}
