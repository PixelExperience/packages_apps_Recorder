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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioSystem;
import android.util.DisplayMetrics;
import android.widget.Toast;

import org.pixelexperience.recorder.R;

import java.io.Closeable;
import java.io.IOException;

public class Utils {
    public static final String PREFS = "preferences";
    public static final String KEY_RECORDING = "recording";
    public static final String PREF_RECORDING_NOTHING = "nothing";
    public static final String PREF_RECORDING_SCREEN = "screen";
    private static final String PREF_RECORDING_SOUND = "sound";
    public static final String PREF_AUDIO_RECORDING_TYPE = "audio_recording_type";
    public static final int PREF_AUDIO_RECORDING_TYPE_DISABLED = 0;
    public static final int PREF_AUDIO_RECORDING_TYPE_INTERNAL = 1;
    public static final int PREF_AUDIO_RECORDING_TYPE_MICROPHONE = 2;
    public static final int PREF_AUDIO_RECORDING_TYPE_DEFAULT = PREF_AUDIO_RECORDING_TYPE_DISABLED;

    private Utils() {
    }

    public static boolean isWifiDisplaySessionRunning(){
        return (AudioSystem.getDevicesForStream(AudioSystem.STREAM_MUSIC) & AudioSystem.DEVICE_OUT_PROXY) != 0;
    }

    public static boolean isRoutedToSubmix(){
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

    public static boolean isRoutedOnlyToSpeakerOrHeadset(){
        if (isBluetoothHeadsetConnected()){
            return false;
        }
        int devices = AudioSystem.getDevicesForStream(AudioSystem.STREAM_MUSIC);
        devices &= ~AudioSystem.DEVICE_OUT_REMOTE_SUBMIX; // Remove submix
        return (devices == AudioSystem.DEVICE_OUT_SPEAKER || devices == AudioSystem.DEVICE_OUT_SPEAKER_SAFE ||
                devices == AudioSystem.DEVICE_OUT_WIRED_HEADPHONE || devices == AudioSystem.DEVICE_OUT_WIRED_HEADSET ||
                devices == AudioSystem.DEVICE_OUT_USB_HEADSET);
    }

    public static boolean isInternalAudioRecordingAllowed(Context context, boolean checkSubmix){
        if (isWifiDisplaySessionRunning()){
            Toast.makeText(context, R.string.screen_audio_recording_disabled_wfd,Toast.LENGTH_LONG).show();
            return false;
        }
        if (checkSubmix && isRoutedToSubmix()){
            Toast.makeText(context, R.string.screen_audio_recording_disabled_others_apps,Toast.LENGTH_LONG).show();
            return false;
        }
        if (!isRoutedOnlyToSpeakerOrHeadset()){
            Toast.makeText(context, R.string.screen_audio_recording_not_allowed,Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private static String getStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        return prefs.getString(KEY_RECORDING, PREF_RECORDING_NOTHING);
    }

    public static void setStatus(Context context, UiStatus status) {
        if (status.equals(UiStatus.SOUND)) {
            setStatus(context, PREF_RECORDING_SOUND);
        } else if (status.equals(UiStatus.SCREEN)) {
            setStatus(context, PREF_RECORDING_SCREEN);
        } else {
            setStatus(context, PREF_RECORDING_NOTHING);
        }
    }

    public static void setStatus(Context context, String status) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit().putString(KEY_RECORDING, status).apply();
    }

    public static boolean isRecording(Context context) {
        return !PREF_RECORDING_NOTHING.equals(getStatus(context));
    }

    public static boolean isSoundRecording(Context context) {
        return PREF_RECORDING_SOUND.equals(getStatus(context));
    }

    public static boolean isScreenRecording(Context context) {
        return PREF_RECORDING_SCREEN.equals(getStatus(context));
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

}
