package org.pixelexperience.recorder;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

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

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private static final int REQUEST_RECORD_AUDIO_PERMS = 213;
        private ListPreference mAudioSource;
        private PreferenceUtils mPreferenceUtils;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings, rootKey);
            mPreferenceUtils = new PreferenceUtils(getContext());
            mAudioSource = findPreference(PreferenceUtils.PREF_AUDIO_RECORDING_TYPE);
            mAudioSource.setOnPreferenceChangeListener((preference, newValue) -> {
                int value = Integer.valueOf((String) newValue);
                mPreferenceUtils.setAudioRecordingType(value);
                if (!PermissionUtils.hasAudioPermission(getContext()) && value != PreferenceUtils.PREF_AUDIO_RECORDING_TYPE_DISABLED) {
                    askAudioPermission();
                }
                return true;
            });
            if (!mPreferenceUtils.isInternalAudioRecordingSupported()){
                String[] entries = getContext().getResources().getStringArray(R.array.screen_audio_recording_entries_alt);
                String[] values = getContext().getResources().getStringArray(R.array.screen_audio_recording_values_alt);
                mAudioSource.setEntries(entries);
                mAudioSource.setEntryValues(values);
            }
            mAudioSource.setValueIndex(mPreferenceUtils.getAudioRecordingType());
            if (Utils.isScreenRecording()) {
                mAudioSource.setEnabled(false);
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
    }

}