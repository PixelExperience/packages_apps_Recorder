package org.pixelexperience.recorder;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.pixelexperience.recorder.utils.LastRecordHelper;
import org.pixelexperience.recorder.utils.MediaProviderHelper;

public class DeleteVideoReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Uri uri = LastRecordHelper.getLastItemUri(context);
        if (uri != null) {
            MediaProviderHelper.remove(context.getContentResolver(), uri);
        }
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.cancel(ScreenRecorderService.NOTIFICATION_ID);
    }
}