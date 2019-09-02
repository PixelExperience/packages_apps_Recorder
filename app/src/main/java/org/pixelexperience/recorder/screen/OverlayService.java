/*
 * Copyright (C) 2017 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pixelexperience.recorder.screen;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.pixelexperience.recorder.R;
import org.pixelexperience.recorder.RecorderActivity;
import org.pixelexperience.recorder.SettingsActivity;
import org.pixelexperience.recorder.ui.OverlayLayer;
import org.pixelexperience.recorder.utils.PreferenceUtils;
import org.pixelexperience.recorder.utils.Utils;

public class OverlayService extends Service {

    private static final String SCREENCAST_OVERLAY_NOTIFICATION_CHANNEL =
            "screencast_overlay_notification_channel";

    private final static int FG_ID = 123;

    /* Horrible hack to determine whether the service is running:
     * the ActivityManager.getRunningServices() method has been nuked on api 26+
     * so we're unable to properly determine if this service is currently running
     */
    public static boolean isRunning = false;

    private OverlayLayer mLayer;

    private int mDensity;
    private float mFontScale;
    private int mUiMode;
    private LocalBroadcastManager mLocalBroadcastManager;
    private PreferenceUtils mPreferenceUtils;

    private final BroadcastReceiver mRecordingStateChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Utils.ACTION_RECORDING_STATE_CHANGED.equals(intent.getAction())) {
                if (Utils.isScreenRecording() && mPreferenceUtils.getShouldShowFloatingWindow()){
                    mLayer.setIsRecording(true);
                    stopForeground(true);
                }
            }else if (Utils.ACTION_RECORDING_TIME_TICK.equals(intent.getAction()) && mLayer != null) {
                mLayer.updateTimerView(ScreencastService.sElapsedTimeInSeconds);
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        boolean shouldHideOverlay = Utils.isScreenRecording() && !mPreferenceUtils.getShouldShowFloatingWindow();
        if (isRunning || shouldHideOverlay) {
            stopSelf();
            return START_NOT_STICKY;
        }
        Configuration configuration = getResources().getConfiguration();
        mDensity = configuration.densityDpi;
        mFontScale = configuration.fontScale;
        mUiMode = configuration.uiMode;
        createOverlayLayer();
        registerReceiver();

        Notification notification = new NotificationCompat.Builder(
                this, SCREENCAST_OVERLAY_NOTIFICATION_CHANNEL)
                .setContentTitle(getString(R.string.screen_overlay_notif_title))
                .setContentText(getString(R.string.screen_overlay_notif_message))
                .setSmallIcon(R.drawable.ic_action_screen_record)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, RecorderActivity.class), 0))
                .build();

        if (Utils.isScreenRecording()) {
            stopForeground(true);
        }else{
            startForeground(FG_ID, notification);
        }
        isRunning = true;
        return START_NOT_STICKY;
    }

    private void createOverlayLayer() {
        mLayer = new OverlayLayer(this);
        mLayer.setOnActionClickListener(() -> {
            if (Utils.isScreenRecording()){
                Utils.setStatus(Utils.UiStatus.NOTHING, this);
                startService(new Intent(ScreencastService.ACTION_STOP_SCREENCAST)
                        .setClass(this, ScreencastService.class));
                onDestroy();
            }else {
                Utils.preventTwoClick(mLayer.mButton);
                registerReceiver();
                Intent intent_ = new Intent(this, StartScreenRecorder.class);
                intent_.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent_);
            }
        });
        mLayer.setSettingsButtonOnClickListener(() -> {
            Utils.preventTwoClick(mLayer.mSettingsButton);
            Intent intent_ = new Intent(this, SettingsActivity.class);
            intent_.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent_);
            onDestroy();
        });
        mLayer.setCloseButtonOnClickListener(this::onDestroy);
        mLayer.setIsRecording(Utils.isScreenRecording());
        mLayer.updateTimerView(ScreencastService.sElapsedTimeInSeconds);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        mPreferenceUtils = new PreferenceUtils(this);

        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        if (notificationManager
                .getNotificationChannel(SCREENCAST_OVERLAY_NOTIFICATION_CHANNEL) != null) {
            return;
        }

        CharSequence name = getString(R.string.screen_overlay_channel_title);
        String description = getString(R.string.screen_overlay_channel_desc);
        NotificationChannel notificationChannel =
                new NotificationChannel(SCREENCAST_OVERLAY_NOTIFICATION_CHANNEL,
                        name, NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setDescription(description);
        notificationManager.createNotificationChannel(notificationChannel);
    }

    private void registerReceiver(){
        IntentFilter filter = new IntentFilter();
        filter.addAction(Utils.ACTION_RECORDING_STATE_CHANGED);
        filter.addAction(Utils.ACTION_RECORDING_TIME_TICK);
        mLocalBroadcastManager.registerReceiver(mRecordingStateChanged, filter);
    }

    private void unregisterReceiver(){
        try{
            mLocalBroadcastManager.unregisterReceiver(mRecordingStateChanged);
        }catch (Exception e){
        }
    }

    @Override
    public void onDestroy() {
        if (mLayer != null) {
            mLayer.destroy();
            mLayer = null;
        }
        unregisterReceiver();

        stopForeground(true);
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        boolean shouldRefresh = false;
        if (mDensity != newConfig.densityDpi) {
            mDensity = newConfig.densityDpi;
            shouldRefresh = true;
        }
        if (mFontScale != newConfig.fontScale) {
            mFontScale = newConfig.fontScale;
            shouldRefresh = true;
        }
        if (mUiMode != newConfig.uiMode) {
            mUiMode = newConfig.uiMode;
            shouldRefresh = true;
        }
        if (shouldRefresh) {
            if (mLayer != null) {
                mLayer.destroy();
                mLayer = null;
            }
            createOverlayLayer();
        }
    }
}
