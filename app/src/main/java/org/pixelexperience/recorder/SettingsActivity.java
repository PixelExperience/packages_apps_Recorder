package org.pixelexperience.recorder;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import org.pixelexperience.recorder.utils.PermissionUtils;
import org.pixelexperience.recorder.utils.PreferenceUtils;
import org.pixelexperience.recorder.utils.Utils;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {// app icon in action bar clicked; go home
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, RecorderActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        super.onBackPressed();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener{
        private static final int REQUEST_RECORD_AUDIO_PERMS = 213;
        private PreferenceCategory mScreenCategory;
        private ListPreference mAudioSource;
        private ListPreference mScreenOrientation;
        private ListPreference mFramerate;
        private SwitchPreference mShowTouches;
        private SwitchPreference mStopRecordingWhenScreenOff;
        private SwitchPreference mShowFloatingWindow;
        private PreferenceUtils mPreferenceUtils;

        private String KEY_SCREEN_CATEGORY = "screen_category";

        private final BroadcastReceiver mRecordingStateChanged = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshState();
            }
        };
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings, rootKey);
            mPreferenceUtils = new PreferenceUtils(getContext());
            mScreenCategory = findPreference(KEY_SCREEN_CATEGORY);
            mAudioSource = findPreference(PreferenceUtils.PREF_AUDIO_RECORDING_TYPE);
            mScreenOrientation = findPreference(PreferenceUtils.PREF_SCREEN_ORIENTATION);
            mFramerate = findPreference(PreferenceUtils.PREF_FRAME_RATE);
            mShowTouches = findPreference(PreferenceUtils.PREF_SHOW_TOUCHES);
            mStopRecordingWhenScreenOff = findPreference(PreferenceUtils.PREF_STOP_SCREEN_OFF);
            mShowFloatingWindow = findPreference(PreferenceUtils.PREF_SHOW_FLOATING_WINDOW);
            mAudioSource.setOnPreferenceChangeListener(this);
            mScreenOrientation.setOnPreferenceChangeListener(this);
            mFramerate.setOnPreferenceChangeListener(this);
            mShowTouches.setOnPreferenceChangeListener(this);
            mStopRecordingWhenScreenOff.setOnPreferenceChangeListener(this);
            mShowFloatingWindow.setOnPreferenceChangeListener(this);
            if (!mPreferenceUtils.isInternalAudioRecordingSupported()){
                String[] entries = getContext().getResources().getStringArray(R.array.screen_audio_recording_entries_alt);
                String[] values = getContext().getResources().getStringArray(R.array.screen_audio_recording_values_alt);
                mAudioSource.setEntries(entries);
                mAudioSource.setEntryValues(values);
            }
            mAudioSource.setValueIndex(mPreferenceUtils.getAudioRecordingType());
            mScreenOrientation.setValue(String.valueOf(mPreferenceUtils.getVideoRecordingOrientation()));
            mFramerate.setValue(String.valueOf(mPreferenceUtils.getVideoRecordingMaxFps()));
            mShowTouches.setChecked(mPreferenceUtils.getShouldShowTouches());
            mStopRecordingWhenScreenOff.setChecked(mPreferenceUtils.getShouldStopWhenScreenOff());
            mShowFloatingWindow.setChecked(mPreferenceUtils.getShouldShowFloatingWindow());
            if (!mPreferenceUtils.canControlShowTouches()){
                getPreferenceScreen().removePreference(mShowTouches);
            }
            refreshState();
        }

        @Override
        public void onResume() {
            super.onResume();
            refreshState();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Utils.ACTION_RECORDING_STATE_CHANGED);
            LocalBroadcastManager.getInstance(getContext()).registerReceiver(mRecordingStateChanged, filter);
        }

        @Override
        public void onPause() {
            LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mRecordingStateChanged);
            super.onPause();
        }

        private void refreshState() {
            if (mScreenCategory != null) {
                mScreenCategory.setEnabled(!Utils.isScreenRecording());
            }
        }

        private void askAudioPermission() {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMS);
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                               @NonNull int[] results) {
            if (requestCode != REQUEST_RECORD_AUDIO_PERMS) {
                return;
            }

            if (!PermissionUtils.hasAudioPermission(getContext())) {
                mPreferenceUtils.setAudioRecordingType(PreferenceUtils.PREF_AUDIO_RECORDING_TYPE_DISABLED);
                mAudioSource.setValueIndex(PreferenceUtils.PREF_AUDIO_RECORDING_TYPE_DISABLED);
            }
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference == mAudioSource){
                int value = Integer.valueOf((String) newValue);
                mPreferenceUtils.setAudioRecordingType(value);
                if (!PermissionUtils.hasAudioPermission(getContext()) && value != PreferenceUtils.PREF_AUDIO_RECORDING_TYPE_DISABLED) {
                    askAudioPermission();
                }
            }else if(preference == mScreenOrientation){
                int value = Integer.valueOf((String) newValue);
                mPreferenceUtils.setVideoRecordingOrientation(value);
            }else if(preference == mFramerate){
                int value = Integer.valueOf((String) newValue);
                mPreferenceUtils.setVideoRecordingMaxFps(value);
            }else if(preference == mShowTouches){
                boolean value = (Boolean) newValue;
                mPreferenceUtils.setShouldShowTouches(value);
            }else if(preference == mStopRecordingWhenScreenOff){
                boolean value = (Boolean) newValue;
                mPreferenceUtils.setShouldStopWhenScreenOff(value);
            }else if (preference == mShowFloatingWindow){
                boolean value = (Boolean) newValue;
                mPreferenceUtils.setShouldShowFloatingWindow(value);
            }
            return true;
        }
    }

}