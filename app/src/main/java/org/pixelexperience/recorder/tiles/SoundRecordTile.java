package org.pixelexperience.recorder.tiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.pixelexperience.recorder.R;
import org.pixelexperience.recorder.RecorderActivity;
import org.pixelexperience.recorder.utils.GlobalSettings;
import org.pixelexperience.recorder.utils.Utils;

public class SoundRecordTile extends TileService {
    private final BroadcastReceiver mRecordingStateChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Utils.ACTION_RECORDING_STATE_CHANGED.equals(intent.getAction())) {
                updateTile();
            }
        }
    };

    @Override
    public void onClick() {
        unlockAndRun(() -> {
            Intent intent = new Intent(this, RecorderActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(RecorderActivity.EXTRA_UI_TYPE, Utils.UiStatus.SOUND.toString());
            startActivityAndCollapse(intent);
        });
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
        LocalBroadcastManager.getInstance(this).registerReceiver(mRecordingStateChanged,
                new IntentFilter(Utils.ACTION_RECORDING_STATE_CHANGED));
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mRecordingStateChanged);
    }

    @Override
    public void onTileAdded(){
        super.onTileAdded();
        updateTile();
    }

    private void updateTile(){
        Tile qsTile = getQsTile();
        if (GlobalSettings.sRecordingStatus.equals(Utils.PREF_RECORDING_SCREEN)){
            qsTile.setState(Tile.STATE_UNAVAILABLE);
        }else {
            qsTile.setState(GlobalSettings.sRecordingStatus.equals(Utils.PREF_RECORDING_SOUND)
                    ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        qsTile.setLabel(getString(Utils.isSoundRecording() ?
                R.string.sound_recording_title_working : R.string.sound_notification_title));
        qsTile.updateTile();
    }

}
