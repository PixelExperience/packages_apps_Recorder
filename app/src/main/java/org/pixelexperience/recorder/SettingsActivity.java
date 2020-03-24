package org.pixelexperience.recorder;

import android.app.ActionBar;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import org.pixelexperience.recorder.utils.PreferenceUtils;
import org.pixelexperience.recorder.utils.Utils;

public class SettingsActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getActionBar();
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

    public static class SettingsFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {
        private static final int REQUEST_RECORD_AUDIO_PERMS = 213;
        private PreferenceCategory mScreenCategory;
        private final BroadcastReceiver mRecordingStateChanged = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshState();
            }
        };
        private SwitchPreference mShowTouches;
        private SwitchPreference mStopRecordingWhenScreenOff;
        private PreferenceUtils mPreferenceUtils;
        private String KEY_SCREEN_CATEGORY = "screen_category";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.settings, rootKey);
            mPreferenceUtils = new PreferenceUtils(getContext());
            mScreenCategory = findPreference(KEY_SCREEN_CATEGORY);
            mShowTouches = findPreference(PreferenceUtils.PREF_SHOW_TOUCHES);
            mStopRecordingWhenScreenOff = findPreference(PreferenceUtils.PREF_STOP_SCREEN_OFF);
            mShowTouches.setOnPreferenceChangeListener(this);
            mStopRecordingWhenScreenOff.setOnPreferenceChangeListener(this);
            mShowTouches.setChecked(mPreferenceUtils.getShouldShowTouches());
            mStopRecordingWhenScreenOff.setChecked(mPreferenceUtils.getShouldStopWhenScreenOff());
            if (!mPreferenceUtils.canControlShowTouches()) {
                mScreenCategory.removePreference(mShowTouches);
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

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference == mShowTouches) {
                boolean value = (Boolean) newValue;
                mPreferenceUtils.setShouldShowTouches(value);
            } else if (preference == mStopRecordingWhenScreenOff) {
                boolean value = (Boolean) newValue;
                mPreferenceUtils.setShouldStopWhenScreenOff(value);
            }
            return true;
        }
    }

}