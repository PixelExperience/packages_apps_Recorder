/*
 * Copyright (C) 2013 The CyanogenMod Project
 * Copyright (C) 2017-2018 The LineageOS Project
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

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioSystem;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.StatFs;
import android.telephony.TelephonyManager;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.pixelexperience.recorder.R;
import org.pixelexperience.recorder.RecorderActivity;
import org.pixelexperience.recorder.utils.LastRecordHelper;
import org.pixelexperience.recorder.utils.PreferenceUtils;
import org.pixelexperience.recorder.utils.Utils;

import java.util.Timer;
import java.util.TimerTask;

public class ScreencastService extends Service implements ScreenRecorder.ScreenRecorderResultCallback {

    private static final String SCREENCAST_NOTIFICATION_CHANNEL =
            "screencast_notification_channel";

    public static final String ACTION_START_SCREENCAST =
            "org.pixelexperience.recorder.screen.ACTION_START_SCREENCAST";
    public static final String ACTION_STOP_SCREENCAST =
            "org.pixelexperience.recorder.screen.ACTION_STOP_SCREENCAST";
    public static final int NOTIFICATION_ID = 61;
    private static final String LOGTAG = "ScreencastService";
    public static long sElapsedTimeInSeconds;
    private Timer mTimer;
    private NotificationCompat.Builder mBuilder;
    private ScreenRecorder mRecorder;
    private NotificationManager mNotificationManager;
    private boolean mAlreadyNotified = false;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_USER_BACKGROUND.equals(action) ||
                    Intent.ACTION_SHUTDOWN.equals(action)) {
                stopRecording();
            } else if ("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED".equals(action) &&
                    mAudioSource == PreferenceUtils.PREF_AUDIO_RECORDING_TYPE_INTERNAL && Utils.isBluetoothHeadsetConnected()) {
                stopRecording();
                Utils.notifyError(getString(R.string.screen_audio_recording_not_allowed), true, ScreencastService.this, mNotificationManager, false);
                mAlreadyNotified = true;
            } else if (Intent.ACTION_SCREEN_OFF.equals(action) && mStopOnScreenOff) {
                stopRecording();
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                if (state.equals(TelephonyManager.EXTRA_STATE_OFFHOOK) &&
                        mAudioSource == PreferenceUtils.PREF_AUDIO_RECORDING_TYPE_INTERNAL) {
                    stopRecording();
                    Utils.notifyError(getString(R.string.screen_audio_recording_not_allowed_in_call), true, ScreencastService.this, mNotificationManager, false);
                    mAlreadyNotified = true;
                }
            }
        }
    };

    private int mCurrentDevices;
    private int mAudioSource;
    private boolean mStopOnScreenOff;
    private Handler mHandler = new Handler();
    private PreferenceUtils mPreferenceUtils;
    private LocalBroadcastManager mLocalBroadcastManager;
    private Runnable stopCastRunnable = () -> {
        if (Utils.isBluetoothHeadsetConnected()) {
            return;
        }
        mAlreadyNotified = true;
        if (Utils.isInternalAudioRecordingAllowed(ScreencastService.this, false, false)) {
            Utils.notifyError(getString(R.string.screen_audio_recording_route_changed), true, ScreencastService.this, mNotificationManager, false);
        }
        stopRecording();
    };
    private Runnable currentDevicesCheckerRunnable = new Runnable() {
        @Override
        public void run() {
            if (Utils.isBluetoothHeadsetConnected()) {
                return;
            }
            int currentDevices = Utils.filterDevices(AudioSystem.getDevicesForStream(AudioSystem.STREAM_MUSIC));
            if (mCurrentDevices != currentDevices) {
                if (mRecorder != null){
                    mRecorder.setStopping(true);
                }
                mHandler.postDelayed(stopCastRunnable, 500);
                return;
            }
            mHandler.postDelayed(this, 100);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        final String action = intent.getAction();
        if (action == null) {
            return START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_START_SCREENCAST:
                return startRecording(intent);
            case ACTION_STOP_SCREENCAST:
                stopRecording();
                return START_STICKY;
            default:
                return START_NOT_STICKY;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mNotificationManager = getSystemService(NotificationManager.class);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(ScreencastService.this);

        Utils.createShareNotificationChannel(this, mNotificationManager);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED");
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        registerReceiver(mBroadcastReceiver, filter);

        if (mNotificationManager.getNotificationChannel(
                SCREENCAST_NOTIFICATION_CHANNEL) != null) {
            return;
        }

        CharSequence name = getString(R.string.screen_channel_title);
        String description = getString(R.string.screen_channel_desc);
        NotificationChannel notificationChannel =
                new NotificationChannel(SCREENCAST_NOTIFICATION_CHANNEL,
                        name, NotificationManager.IMPORTANCE_LOW);
        notificationChannel.setDescription(description);
        mNotificationManager.createNotificationChannel(notificationChannel);
    }

    @Override
    public void onDestroy() {
        stopRecording(true);
        unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    private void startTimer(boolean reset){
        if (reset){
            sElapsedTimeInSeconds = 0;
        }
        mTimer = new Timer();
        mTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sElapsedTimeInSeconds++;
                updateNotification();
                mLocalBroadcastManager.sendBroadcast(new Intent(Utils.ACTION_RECORDING_TIME_TICK));
            }
        }, 1000, 1000);
    }

    private void stopTimer(){
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
        }
    }

    private int startRecording(Intent intent) {
        try {
            mPreferenceUtils = new PreferenceUtils(this);
            if (hasNoAvailableSpace()) {
                Utils.notifyError(getString(R.string.screen_insufficient_storage), true, ScreencastService.this, mNotificationManager, false);
                return START_NOT_STICKY;
            }

            if (mPreferenceUtils.getAudioRecordingType() == PreferenceUtils.PREF_AUDIO_RECORDING_TYPE_INTERNAL) {
                if (!Utils.isInternalAudioRecordingAllowed(this, true, true)) {
                    return START_NOT_STICKY;
                }
            }

            mNotificationManager.cancel(Utils.NOTIFICATION_ERROR_ID);

            mCurrentDevices = 0;
            mAlreadyNotified = false;
            mAudioSource = mPreferenceUtils.getAudioRecordingType();
            mStopOnScreenOff = mPreferenceUtils.getShouldStopWhenScreenOff();

            assert mRecorder == null;
            mRecorder = new ScreenRecorder(this, intent.getParcelableExtra(Utils.SCREEN_RECORD_INTENT_DATA),
                    intent.getIntExtra(Utils.SCREEN_RECORD_INTENT_RESULT, Activity.RESULT_OK), this);
            mRecorder.startRecording();
            mBuilder = createNotificationBuilder();

            startTimer(true);

            Utils.setStatus(Utils.PREF_RECORDING_SCREEN, this);

            startForeground(NOTIFICATION_ID, mBuilder.build());

            mCurrentDevices = Utils.filterDevices(AudioSystem.getDevicesForStream(AudioSystem.STREAM_MUSIC));

            if (mAudioSource == PreferenceUtils.PREF_AUDIO_RECORDING_TYPE_INTERNAL) {
                mHandler.postDelayed(currentDevicesCheckerRunnable, 100);
            }

            Utils.refreshShowTouchesState(this);

            return START_STICKY;
        } catch (Exception e) {
            stopRecording();
            Log.e(LOGTAG, e.getMessage());
        }

        return START_NOT_STICKY;
    }

    private boolean hasNoAvailableSpace() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long bytesAvailable = stat.getBlockSizeLong() * stat.getBlockCountLong();
        long megAvailable = bytesAvailable / 1048576;
        return megAvailable < 100;
    }

    private void updateNotification() {
        mBuilder.setContentText(getString(R.string.screen_notification_message,
                DateUtils.formatElapsedTime(sElapsedTimeInSeconds)));
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private void stopRecording() {
        stopRecording(false);
    }

    private void stopRecording(boolean fromOnDestroy) {
        Utils.stopOverlayService(this);
        Utils.setStatus(Utils.PREF_RECORDING_NOTHING, this);
        mHandler.removeCallbacksAndMessages(null);

        String recorderPath = null;
        if (mRecorder != null) {
            mRecorder.setStopping(true);
            recorderPath = mRecorder.getRecordingFilePath();
            mRecorder.stopRecording();
            mRecorder = null;
        }
        stopTimer();
        stopForeground(true);
        if (!fromOnDestroy && recorderPath != null) {
            sendShareNotification(recorderPath);
        } else if (!fromOnDestroy && !mAlreadyNotified) {
            Utils.notifyError(getString(R.string.unknow_error), true, ScreencastService.this, mNotificationManager, false);
        }

        if (!fromOnDestroy && hasNoAvailableSpace()) {
            Utils.notifyError(getString(R.string.screen_not_enough_storage), true, ScreencastService.this, mNotificationManager, false);
        }
        Utils.refreshShowTouchesState(this);
    }

    private NotificationCompat.Builder createNotificationBuilder() {
        Intent intent = new Intent(this, RecorderActivity.class);
        Intent stopRecordingIntent = new Intent(ACTION_STOP_SCREENCAST);
        stopRecordingIntent.setClass(this, ScreencastService.class);

        return new NotificationCompat.Builder(this, SCREENCAST_NOTIFICATION_CHANNEL)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification_screen)
                .setContentTitle(getString(R.string.screen_notification_title))
                .setContentText(getString(R.string.screen_notification_message))
                .setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
                .addAction(R.drawable.ic_stop, getString(R.string.stop),
                        PendingIntent.getService(this, 0, stopRecordingIntent, 0));
    }

    private void sendShareNotification(String recordingFilePath) {
        mBuilder = createShareNotificationBuilder(recordingFilePath);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private NotificationCompat.Builder createShareNotificationBuilder(String file) {
        PendingIntent playPIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getOpenIntent(this, file, "video/mp4"),
                PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent sharePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getShareIntent(this, file, "video/mp4"),
                PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent deletePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getDeleteIntent(this, false),
                PendingIntent.FLAG_CANCEL_CURRENT);

        LastRecordHelper.setLastItem(this, file, sElapsedTimeInSeconds, false);

        Log.i(LOGTAG, "Video complete: " + file);

        return new NotificationCompat.Builder(this, Utils.RECORDING_DONE_NOTIFICATION_CHANNEL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_notification_screen)
                .setContentTitle(getString(R.string.screen_notification_message_done))
                .setContentText(getString(R.string.screen_notification_message,
                        DateUtils.formatElapsedTime(sElapsedTimeInSeconds)))
                .addAction(R.drawable.ic_play, getString(R.string.play), playPIntent)
                .addAction(R.drawable.ic_share, getString(R.string.share), sharePIntent)
                .addAction(R.drawable.ic_delete, getString(R.string.delete), deletePIntent)
                .setContentIntent(playPIntent);
    }

    @Override
    public void onRecordingError() {
        stopRecording();
    }

    @Override
    public void onRecordingPaused() {
        stopTimer();
    }

    @Override
    public void onRecordingResumed() {
        startTimer(false);
    }
}
