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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

public class LastRecordHelper {
    private static final String PREFS = "preferences";
    private static final String KEY_LAST_SCREEN = "screen_last_path";
    private static final String KEY_LAST_SCREEN_TIME = "screen_last_duration";

    private LastRecordHelper() {
    }

    public static Intent getShareIntent(Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setDataAndType(uri, mimeType);
        Intent chooserIntent = Intent.createChooser(intent, null);
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return chooserIntent;
    }

    public static Intent getOpenIntent(Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    public static void setLastItem(Context context, String path, long duration) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit()
                .putString(KEY_LAST_SCREEN, path)
                .putLong(KEY_LAST_SCREEN_TIME, duration)
                .apply();
    }

    public static Uri getLastItemUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        String uriStr = prefs.getString(KEY_LAST_SCREEN, null);
        return uriStr == null ? null : Uri.parse(uriStr);
    }

}
