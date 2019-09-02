package org.pixelexperience.recorder.screen;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

import org.pixelexperience.recorder.utils.Utils;

public class StartScreenRecorder extends Activity {

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MediaProjectionManager mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), Utils.SCREEN_RECORD_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_CANCELED || requestCode != Utils.SCREEN_RECORD_REQUEST_CODE) {
            Utils.setStatus(Utils.UiStatus.NOTHING, this);
            finish();
            return;
        }
        Intent recorderService = new Intent(ScreencastService.ACTION_START_SCREENCAST);
        recorderService.putExtra(Utils.SCREEN_RECORD_INTENT_DATA, data);
        recorderService.putExtra(Utils.SCREEN_RECORD_INTENT_RESULT, resultCode);
        startService(recorderService.setClass(this, ScreencastService.class));
        finish();
    }
}
