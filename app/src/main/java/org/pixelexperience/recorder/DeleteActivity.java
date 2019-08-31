package org.pixelexperience.recorder;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

import org.pixelexperience.recorder.utils.LastRecordHelper;

public class DeleteActivity extends Activity {
    public static final String EXTRA_LAST_SOUND = "lastSoundItem";

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        deleteLastItem(getIntent().getBooleanExtra(EXTRA_LAST_SOUND, false));
    }

    private void deleteLastItem(boolean isSound) {
        String path = LastRecordHelper.getLastItemPath(this, isSound);
        AlertDialog dialog = LastRecordHelper.deleteFile(this, path, isSound);
        dialog.setOnDismissListener(d -> finish());
        dialog.show();
    }
}
