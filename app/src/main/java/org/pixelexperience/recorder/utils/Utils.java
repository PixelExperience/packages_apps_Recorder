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

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.pixelexperience.recorder.R;

import java.lang.reflect.Method;

public class Utils {
    public static final String ACTION_RECORDING_STATE_CHANGED = "org.pixelexperience.recorder.RECORDING_STATE_CHANGED";
    public static final String PREF_RECORDING_NOTHING = "nothing";
    public static final String PREF_RECORDING_SCREEN = "screen";
    public static final String SCREEN_RECORD_INTENT_DATA = "recorder_intent_data";
    public static final String SCREEN_RECORD_INTENT_RESULT = "recorder_intent_result";
    public static final String RECORDING_DONE_NOTIFICATION_CHANNEL =
            "recording_done_notification_channel";
    public static final int NOTIFICATION_ERROR_ID = 6592;
    private static final String RECORDING_ERROR_NOTIFICATION_CHANNEL =
            "recording_error_notification_channel";

    private Utils() {
    }

    private static String getStatus() {
        return GlobalSettings.sRecordingStatus;
    }

    public static void setStatus(String status, Context context) {
        GlobalSettings.sRecordingStatus = status;
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Utils.ACTION_RECORDING_STATE_CHANGED));
    }

    public static boolean isScreenRecording() {
        return PREF_RECORDING_SCREEN.equals(getStatus());
    }

    public static void collapseStatusBar(Context context, boolean delayed) {
        new Handler().postDelayed(() -> {
            try {
                Object sbservice = context.getSystemService("statusbar");
                Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
                Method collapse2 = statusbarManager.getMethod("collapsePanels");
                collapse2.setAccessible(true);
                collapse2.invoke(sbservice);
            } catch (Exception ignored) {
            }
        }, delayed ? 500 : 0);
    }

    public static void setShowTouches(Context context, boolean show) {
        try {
            Settings.System.putInt(context.getContentResolver(), "show_touches", show ? 1 : 0);
        } catch (Exception e) {
        }
    }

    public static boolean isShowingTouches(Context context) {
        try {
            return Settings.System.getInt(context.getContentResolver(), "show_touches", 0) != 0;
        } catch (Exception e) {
        }
        return false;
    }

    public static void refreshShowTouchesState(Context context) {
        PreferenceUtils preferenceUtils = new PreferenceUtils(context);
        boolean isShowingTouches = isShowingTouches(context);
        boolean isScreenRecording = isScreenRecording();
        boolean shouldShowTouches = preferenceUtils.getShouldShowTouches();
        if (!isScreenRecording && shouldShowTouches && isShowingTouches) {
            setShowTouches(context, false);
        } else if (shouldShowTouches) {
            setShowTouches(context, isScreenRecording);
        }
    }

    public static void createShareNotificationChannel(Context mContext, NotificationManager manager) {
        if (manager.getNotificationChannel(RECORDING_DONE_NOTIFICATION_CHANNEL) != null) {
            return;
        }
        String name = mContext.getString(R.string.screen_notification_message_done);
        NotificationChannel notificationChannel =
                new NotificationChannel(RECORDING_DONE_NOTIFICATION_CHANNEL,
                        name, NotificationManager.IMPORTANCE_HIGH);
        String description = mContext.getString(R.string.ready_channel_desc);
        notificationChannel.setDescription(description);
        manager.createNotificationChannel(notificationChannel);
    }

    public static void notifyError(String message, Context mContext, NotificationManager manager) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
        if (manager.getNotificationChannel(RECORDING_ERROR_NOTIFICATION_CHANNEL) == null) {
            String name = mContext.getString(R.string.recording_error);
            NotificationChannel notificationChannel =
                    new NotificationChannel(RECORDING_ERROR_NOTIFICATION_CHANNEL,
                            name, NotificationManager.IMPORTANCE_DEFAULT);
            String description = mContext.getString(R.string.recording_error_channel_desc);
            notificationChannel.setDescription(description);
            manager.createNotificationChannel(notificationChannel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, Utils.RECORDING_ERROR_NOTIFICATION_CHANNEL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_notification_screen)
                .setContentTitle(mContext.getString(R.string.recording_error))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message))
                .setContentText(message);

        manager.cancel(NOTIFICATION_ERROR_ID);
        manager.notify(NOTIFICATION_ERROR_ID, builder.build());
    }

}
