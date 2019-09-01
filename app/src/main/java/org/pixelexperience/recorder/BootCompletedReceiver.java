package org.pixelexperience.recorder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.pixelexperience.recorder.utils.Utils;

public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Utils.refreshShowTouchesState(context);
    }
}