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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioSystem;
import android.os.Handler;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.pixelexperience.recorder.R;
import org.pixelexperience.recorder.screen.OverlayService;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;

public class Utils {
    public static final String ACTION_RECORDING_STATE_CHANGED = "org.pixelexperience.recorder.RECORDING_STATE_CHANGED";
    public static final String ACTION_RECORDING_TIME_TICK = "org.pixelexperience.recorder.RECORDING_TIME_TICK";
    public static final String ACTION_HIDE_ACTIVITY = "org.pixelexperience.recorder.HIDE_ACTIVITY";
    public static final String PREF_RECORDING_NOTHING = "nothing";
    public static final String PREF_RECORDING_SCREEN = "screen";
    public static final String PREF_RECORDING_SOUND = "sound";
    public static final int SCREEN_RECORD_REQUEST_CODE = 1058;
    public static final String SCREEN_RECORD_INTENT_DATA = "recorder_intent_data";
    public static final String SCREEN_RECORD_INTENT_RESULT = "recorder_intent_result";
    public static final String RECORDING_DONE_NOTIFICATION_CHANNEL =
            "recording_done_notification_channel";
    public static final String RECORDING_ERROR_NOTIFICATION_CHANNEL =
            "recording_error_notification_channel";
    public static final int NOTIFICATION_ERROR_ID = 6592;

    private Utils() {
    }

    public static boolean isWifiDisplaySessionRunning() {
        return (AudioSystem.getDevicesForStream(AudioSystem.STREAM_MUSIC) & AudioSystem.DEVICE_OUT_PROXY) != 0;
    }

    public static boolean isRoutedToSubmix() {
        return (AudioSystem.getDevicesForStream(AudioSystem.STREAM_MUSIC) & AudioSystem.DEVICE_OUT_REMOTE_SUBMIX) != 0;
    }

    public static boolean isBluetoothHeadsetConnected() {
        BluetoothAdapter ba = BluetoothAdapter.getDefaultAdapter();
        if (ba == null || !ba.isEnabled()) {
            return false;
        }
        int a2dp = ba.getProfileConnectionState(BluetoothProfile.A2DP);
        int headset = ba.getProfileConnectionState(BluetoothProfile.HEADSET);
        return a2dp == BluetoothProfile.STATE_CONNECTED || headset == BluetoothProfile.STATE_CONNECTED;
    }

    public static boolean isRoutedOnlyToSpeakerOrHeadset() {
        if (isBluetoothHeadsetConnected()) {
            return false;
        }
        int devices = filterDevices(AudioSystem.getDevicesForStream(AudioSystem.STREAM_MUSIC));
        return (devices == AudioSystem.DEVICE_OUT_SPEAKER || devices == AudioSystem.DEVICE_OUT_SPEAKER_SAFE ||
                devices == AudioSystem.DEVICE_OUT_WIRED_HEADPHONE || devices == AudioSystem.DEVICE_OUT_WIRED_HEADSET ||
                devices == AudioSystem.DEVICE_OUT_USB_HEADSET);
    }

    public static boolean isDeviceInCall(Context context){
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK;
    }

    public static boolean isInternalAudioRecordingAllowed(Context context, boolean checkSubmix, boolean ui) {
        if (isWifiDisplaySessionRunning()) {
            Utils.notifyError(context.getString(R.string.screen_audio_recording_disabled_wfd), true, context, ui);
            return false;
        }
        if (checkSubmix && isRoutedToSubmix()) {
            Utils.notifyError(context.getString(R.string.screen_audio_recording_disabled_others_apps), true, context, ui);
            return false;
        }
        if (isDeviceInCall(context)) {
            Utils.notifyError(context.getString(R.string.screen_audio_recording_not_allowed_in_call), true, context, ui);
            return false;
        }
        if (!isRoutedOnlyToSpeakerOrHeadset()) {
            Utils.notifyError(context.getString(R.string.screen_audio_recording_not_allowed), true, context, ui);
            return false;
        }
        return true;
    }

    public static int filterDevices(int devices) {
        devices &= ~AudioSystem.DEVICE_OUT_REMOTE_SUBMIX;
        return devices;
    }

    private static String getStatus() {
        return GlobalSettings.sRecordingStatus;
    }

    public static void setStatus(UiStatus status, Context context) {
        if (status.equals(UiStatus.SOUND)) {
            setStatus(PREF_RECORDING_SOUND, context);
        } else if (status.equals(UiStatus.SCREEN)) {
            setStatus(PREF_RECORDING_SCREEN, context);
        } else {
            setStatus(PREF_RECORDING_NOTHING, context);
        }
    }

    public static void setStatus(String status, Context context) {
        GlobalSettings.sRecordingStatus = status;
        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Utils.ACTION_RECORDING_STATE_CHANGED));
    }

    public static boolean isRecording() {
        return !PREF_RECORDING_NOTHING.equals(getStatus());
    }

    public static boolean isSoundRecording() {
        return PREF_RECORDING_SOUND.equals(getStatus());
    }

    public static boolean isScreenRecording() {
        return PREF_RECORDING_SCREEN.equals(getStatus());
    }

    @SuppressWarnings("SameParameterValue")
    public static int convertDp2Px(Context context, int dp) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return Math.round(dp * metrics.density + 0.5f);
    }

    public static int darkenedColor(int color) {
        int alpha = Color.alpha(color);
        int red = getDarkenedColorValue(Color.red(color));
        int green = getDarkenedColorValue(Color.green(color));
        int blue = getDarkenedColorValue(Color.blue(color));
        return Color.argb(alpha, red, green, blue);
    }

    private static int getDarkenedColorValue(int value) {
        float dark = 0.8f; // -20% lightness
        return Math.min(Math.round(value * dark), 255);
    }

    public static void stopOverlayService(Context context) {
        // Stop overlay service if running
        if (OverlayService.isRunning) {
            context.stopService(new Intent(context, OverlayService.class));
            OverlayService.isRunning = false;
        }
    }

    /**
     * Unconditionally close a <code>Closeable</code>.
     * <p>
     * Equivalent to {@link Closeable#close()}, except any exceptions will be ignored.
     * This is typically used in finally blocks.
     * <p>
     * Example code:
     * <pre>
     *   Closeable closeable = null;
     *   try {
     *       closeable = new FileReader("foo.txt");
     *       // process closeable
     *       closeable.close();
     *   } catch (Exception e) {
     *       // error handling
     *   } finally {
     *       IOUtils.closeQuietly(closeable);
     *   }
     * </pre>
     *
     * @param closeable the object to close, may be null or already closed
     * @since 2.0
     */
    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException ioe) {
            // ignore
        }
    }


    public enum UiStatus {
        NOTHING,
        SOUND,
        SCREEN
    }

    public static void collapseStatusBar(Context context, boolean delayed) {
        new Handler().postDelayed(() -> {
            try{
                Object sbservice = context.getSystemService( "statusbar" );
                Class<?> statusbarManager = Class.forName( "android.app.StatusBarManager" );
                Method collapse2 = statusbarManager.getMethod("collapsePanels");
                collapse2.setAccessible(true);
                collapse2.invoke(sbservice);
            }catch (Exception ignored){
            }
        }, delayed ? 500 : 0);
    }

    public static void setShowTouches(Context context, boolean show){
        try{
            Settings.System.putInt(context.getContentResolver(), "show_touches", show ? 1 : 0);
        }catch (Exception e){
        }
    }
    public static boolean isShowingTouches(Context context){
        try{
            return Settings.System.getInt(context.getContentResolver(), "show_touches", 0) != 0;
        }catch (Exception e){
        }
        return false;
    }

    public static void refreshShowTouchesState(Context context){
        PreferenceUtils preferenceUtils = new PreferenceUtils(context);
        boolean isShowingTouches = isShowingTouches(context);
        boolean isScreenRecording = isScreenRecording();
        boolean shouldShowTouches = preferenceUtils.getShouldShowTouches();
        if (!isScreenRecording && shouldShowTouches && isShowingTouches){
            setShowTouches(context, false);
        }else if(shouldShowTouches){
            setShowTouches(context, isScreenRecording);
        }
    }

    public static void createShareNotificationChannel(Context mContext, NotificationManager manager){
        if (manager.getNotificationChannel(RECORDING_DONE_NOTIFICATION_CHANNEL) != null){
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

    public static void notifyError(String message, boolean isScreenIcon, Context mContext, boolean fromUi) {
        NotificationManager manager = mContext.getSystemService(NotificationManager.class);
        notifyError(message, isScreenIcon, mContext, manager, fromUi);
    }

    public static void notifyError(String message, boolean isScreenIcon, Context mContext, NotificationManager manager, boolean fromUi) {
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        if (fromUi){
            return;
        }
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
                .setSmallIcon(isScreenIcon ? R.drawable.ic_notification_screen : R.drawable.ic_notification_sound)
                .setContentTitle(mContext.getString(R.string.recording_error))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message))
                .setContentText(message);

        manager.cancel(NOTIFICATION_ERROR_ID);
        manager.notify(NOTIFICATION_ERROR_ID, builder.build());
    }

    public static void preventTwoClick(final View view){
        view.setEnabled(false);
        view.postDelayed(() -> view.setEnabled(true), 500);
    }

}
