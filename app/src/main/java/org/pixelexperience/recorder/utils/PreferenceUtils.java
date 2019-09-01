package org.pixelexperience.recorder.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.internal.util.custom.recorder.InternalAudioRecorder;

public class PreferenceUtils {

    public static final String PREFS = "preferences";
    public static final String PREF_AUDIO_RECORDING_TYPE = "audio_recording_type";
    public static final int PREF_AUDIO_RECORDING_TYPE_DISABLED = 0;
    public static final int PREF_AUDIO_RECORDING_TYPE_INTERNAL = 1;
    public static final int PREF_AUDIO_RECORDING_TYPE_MICROPHONE = 2;
    public static final int PREF_AUDIO_RECORDING_TYPE_DEFAULT = PREF_AUDIO_RECORDING_TYPE_DISABLED;

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

    public int getVideoRecordingMaxFps() {
        return 30;
    }

    public boolean isInternalAudioRecordingSupported(){
        return InternalAudioRecorder.isSupported(mContext);
    }


}
