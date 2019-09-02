package org.pixelexperience.recorder.tiles;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.pixelexperience.recorder.R;
import org.pixelexperience.recorder.RecorderActivity;
import org.pixelexperience.recorder.screen.OverlayService;
import org.pixelexperience.recorder.screen.ScreencastService;
import org.pixelexperience.recorder.utils.GlobalSettings;
import org.pixelexperience.recorder.utils.PermissionUtils;
import org.pixelexperience.recorder.utils.Utils;

public class ScreenRecordTile extends TileService {

    private final BroadcastReceiver mRecordingStateChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Utils.ACTION_RECORDING_STATE_CHANGED.equals(intent.getAction())) {
                updateTile();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onClick() {
        boolean wasLocked = isLocked();
        unlockAndRun(() -> {
            if (Utils.isScreenRecording()) {
                Utils.collapseStatusBar(this, wasLocked);
                new Handler().postDelayed(() -> {
                    Utils.setStatus(Utils.UiStatus.NOTHING, this);
                    startService(new Intent(ScreencastService.ACTION_STOP_SCREENCAST)
                            .setClass(this, ScreencastService.class));
                }, wasLocked ? 1000 : 500);
            } else if (hasPerms()) {
                Utils.collapseStatusBar(this, wasLocked);
                LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(Utils.ACTION_HIDE_ACTIVITY));
                Intent intent = new Intent(this, OverlayService.class);
                startService(intent);
            } else {
                Intent intent = new Intent(this, RecorderActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(RecorderActivity.EXTRA_UI_TYPE, Utils.UiStatus.SCREEN.toString());
                startActivityAndCollapse(intent);
            }
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
    public void onTileAdded() {
        super.onTileAdded();
        updateTile();
    }

    private void updateTile() {
        Tile qsTile = getQsTile();
        if (GlobalSettings.sRecordingStatus.equals(Utils.PREF_RECORDING_SOUND)) {
            qsTile.setState(Tile.STATE_UNAVAILABLE);
        } else {
            qsTile.setState(GlobalSettings.sRecordingStatus.equals(Utils.PREF_RECORDING_SCREEN)
                    ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }
        qsTile.setLabel(getString(Utils.isScreenRecording() ?
                R.string.screen_recording_message : R.string.screen_notification_title));
        qsTile.updateTile();
    }


    private boolean hasPerms() {
        if (!PermissionUtils.hasDrawOverOtherAppsPermission(this)) {
            return false;
        }

        if (!PermissionUtils.hasStoragePermission(this)) {
            return false;
        }
        return true;
    }
}
