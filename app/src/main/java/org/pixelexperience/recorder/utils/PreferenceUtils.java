package org.pixelexperience.recorder.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;

public class PreferenceUtils {

    public static final String PREFS = "preferences";
    public static final String PREF_AUDIO_RECORDING_TYPE = "audio_recording_type";
    public static final int PREF_AUDIO_RECORDING_TYPE_DISABLED = 0;
    public static final int PREF_AUDIO_RECORDING_TYPE_INTERNAL = 1;
    public static final int PREF_AUDIO_RECORDING_TYPE_MICROPHONE = 2;
    public static final int PREF_AUDIO_RECORDING_TYPE_DEFAULT = PREF_AUDIO_RECORDING_TYPE_DISABLED;
    public static final String PREF_SHOW_TOUCHES = "show_touches";
    public static final Boolean PREF_SHOW_TOUCHES_DEFAULT = false;
    public static final String PREF_STOP_SCREEN_OFF = "stop_recording_screen_off";
    public static final Boolean PREF_STOP_SCREEN_OFF_DEFAULT = false;
    public static final String PREF_FRAME_RATE = "frame_rate";
    public static final int PREF_FRAME_RATE_DEFAULT = 30;
    public static final String PREF_SCREEN_ORIENTATION = "screen_orientation";
    public static final int PREF_SCREEN_ORIENTATION_AUTOMATIC = 0;
    public static final int PREF_SCREEN_ORIENTATION_PORTRAIT = 1;
    public static final int PREF_SCREEN_ORIENTATION_LANDSCAPE = 2;
    public static final int PREF_SCREEN_ORIENTATION_DEFAULT = PREF_SCREEN_ORIENTATION_AUTOMATIC;
    public static final String PREF_SHOW_FLOATING_WINDOW = "show_floating_window";
    public static final Boolean PREF_SHOW_FLOATING_WINDOW_DEFAULT = false;

    SharedPreferences mSharedPrefs;
    Context mContext;

    public PreferenceUtils(Context context) {
        mContext = context;
        mSharedPrefs = context.getSharedPreferences(PREFS, 0);
    }

    public int getAudioRecordingType() {
        int type = mSharedPrefs.getInt(PREF_AUDIO_RECORDING_TYPE, PREF_AUDIO_RECORDING_TYPE_DEFAULT);
        if (!isInternalAudioRecordingSupported() && type == PREF_AUDIO_RECORDING_TYPE_INTERNAL){
            setAudioRecordingType(PREF_AUDIO_RECORDING_TYPE_DISABLED);
            return PREF_AUDIO_RECORDING_TYPE_DISABLED;
        }
        return type;
    }

    public void setAudioRecordingType(int type) {
        mSharedPrefs.edit().putInt(PREF_AUDIO_RECORDING_TYPE, type).apply();
    }

    public int getVideoRecordingOrientation() {
        return mSharedPrefs.getInt(PREF_SCREEN_ORIENTATION, PREF_SCREEN_ORIENTATION_DEFAULT);
    }

    public void setVideoRecordingOrientation(int orientation) {
        mSharedPrefs.edit().putInt(PREF_SCREEN_ORIENTATION, orientation).apply();
    }

    public int getVideoRecordingMaxFps() {
        return mSharedPrefs.getInt(PREF_FRAME_RATE, PREF_FRAME_RATE_DEFAULT);
    }

    public void setVideoRecordingMaxFps(int fps) {
        mSharedPrefs.edit().putInt(PREF_FRAME_RATE, fps).apply();
    }

    public boolean isInternalAudioRecordingSupported(){
        return true;
    }

    public boolean canControlShowTouches(){
        UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        //return userManager.isAdminUser();
        return true;
    }

    public boolean getShouldShowTouches(){
        return mSharedPrefs.getBoolean(PREF_SHOW_TOUCHES, PREF_SHOW_TOUCHES_DEFAULT);
    }

    public boolean getShouldStopWhenScreenOff(){
        return mSharedPrefs.getBoolean(PREF_STOP_SCREEN_OFF, PREF_STOP_SCREEN_OFF_DEFAULT);
    }

    public void setShouldShowTouches(boolean value){
        mSharedPrefs.edit().putBoolean(PREF_SHOW_TOUCHES, value).apply();
    }

    public void setShouldStopWhenScreenOff(boolean value){
        mSharedPrefs.edit().putBoolean(PREF_STOP_SCREEN_OFF, value).apply();
    }

    public boolean getShouldShowFloatingWindow(){
        return mSharedPrefs.getBoolean(PREF_SHOW_FLOATING_WINDOW, PREF_SHOW_FLOATING_WINDOW_DEFAULT);
    }

    public void setShouldShowFloatingWindow(boolean value){
        mSharedPrefs.edit().putBoolean(PREF_SHOW_FLOATING_WINDOW, value).apply();
    }
}
