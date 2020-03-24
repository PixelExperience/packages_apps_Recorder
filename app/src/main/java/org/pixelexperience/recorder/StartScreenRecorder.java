package org.pixelexperience.recorder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Toast;

import org.pixelexperience.recorder.utils.PermissionUtils;
import org.pixelexperience.recorder.utils.Utils;

public class StartScreenRecorder extends Activity {

    private static final int REQUEST_SCREEN_REC_PERMS_CODE = 439;
    private static final int REQUEST_MEDIA_PROJECTION_CODE = 4839;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!PermissionUtils.hasAudioPermission(this)) {
            final String[] permissions = new String[]{Manifest.permission.RECORD_AUDIO};
            requestPermissions(permissions, REQUEST_SCREEN_REC_PERMS_CODE);
            return;
        }
        startMediaProjection();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_SCREEN_REC_PERMS_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMediaProjection();
                return;
            } else {
                Toast.makeText(this, getString(R.string.no_permission_error_message), Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MEDIA_PROJECTION_CODE) {
            if (resultCode == RESULT_OK) {
                Intent recorderService = new Intent(ScreenRecorderService.ACTION_START_SCREENCAST);
                recorderService.putExtra(Utils.SCREEN_RECORD_INTENT_DATA, data);
                recorderService.putExtra(Utils.SCREEN_RECORD_INTENT_RESULT, resultCode);
                startService(recorderService.setClass(this, ScreenRecorderService.class));
            } else {
                Utils.setStatus(Utils.PREF_RECORDING_NOTHING, this);
            }
        }
        finish();
    }

    private void startMediaProjection() {
        MediaProjectionManager mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION_CODE);
    }
}
