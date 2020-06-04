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
package org.pixelexperience.recorder;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.StatFs;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.pixelexperience.recorder.encoders.EncoderConfig;
import org.pixelexperience.recorder.utils.LastRecordHelper;
import org.pixelexperience.recorder.utils.MediaProviderHelper;
import org.pixelexperience.recorder.utils.PreferenceUtils;
import org.pixelexperience.recorder.utils.Utils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenRecorderService extends Service implements MediaProviderHelper.OnContentWritten {

    public static final String ACTION_START_SCREENCAST =
            "org.pixelexperience.recorder.screen.ACTION_START_SCREENCAST";
    public static final String ACTION_STOP_SCREENCAST =
            "org.pixelexperience.recorder.screen.ACTION_STOP_SCREENCAST";
    public static final int NOTIFICATION_ID = 61;
    private static final String SCREENCAST_NOTIFICATION_CHANNEL =
            "screencast_notification_channel";
    private static final String TAG = ScreenRecorderService.class.getSimpleName();
    private NotificationCompat.Builder mBuilder;
    private ScreenRecorder mRecorder;
    private NotificationManager mNotificationManager;
    private Handler mHandler;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_USER_BACKGROUND.equals(action) ||
                    Intent.ACTION_SHUTDOWN.equals(action)) {
                stopRecording(true);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                stopRecording(true);
            }
        }
    };
    private PreferenceUtils mPreferenceUtils;
    private MediaProjectionManager mProjectionManager;
    private EncoderConfig mEncoderConfig;
    private File mVideoPath;
    private int mElapsedTimeInSeconds;
    private boolean mShouldUpdateNotification;

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
                showSavingNotification();
                stopRecording(true);
                return START_STICKY;
            default:
                return START_NOT_STICKY;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler(getApplicationContext().getMainLooper());
        mNotificationManager = getSystemService(NotificationManager.class);
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mEncoderConfig = new EncoderConfig(this);
        mPreferenceUtils = new PreferenceUtils(this);

        Utils.createShareNotificationChannel(this, mNotificationManager);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_BACKGROUND);
        filter.addAction(Intent.ACTION_SHUTDOWN);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
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
        stopForeground(true);
        super.onDestroy();
    }

    @SuppressLint("RestrictedApi")
    private void showSavingNotification() {
        mBuilder.setContentText(getString(R.string.saving_video_notification));
        mBuilder.mActions.clear();
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private int startRecording(Intent intent) {
        try {
            if (hasNoAvailableSpace()) {
                notifyError(getString(R.string.screen_insufficient_storage));
                return START_NOT_STICKY;
            }

            mNotificationManager.cancel(NOTIFICATION_ID);
            mNotificationManager.cancel(Utils.NOTIFICATION_ERROR_ID);

            assert mRecorder == null;

            // Prepare all the output metadata
            String videoDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                    .format(new Date());
            // the directory which holds all recording files
            mVideoPath = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES),
                    "ScreenRecords/ScreenRecord-" + videoDate + ".mp4");

            File videoDir = mVideoPath.getParentFile();
            if (videoDir == null) {
                throw new SecurityException("Cannot access scoped Movies/ScreenRecords directory");
            }
            //noinspection ResultOfMethodCallIgnored
            videoDir.mkdirs();
            if (!(videoDir.exists() && videoDir.canWrite())) {
                throw new SecurityException("Cannot write to " + videoDir);
            }

            int mediaProjectionIntentResult = intent.getIntExtra(Utils.SCREEN_RECORD_INTENT_RESULT, Activity.RESULT_OK);
            Intent mediaProjectionIntentData = intent.getParcelableExtra(Utils.SCREEN_RECORD_INTENT_DATA);
            assert mediaProjectionIntentData != null;

            mBuilder = createNotificationBuilder();
            startForeground(NOTIFICATION_ID, mBuilder.build());

            MediaProjection mediaProjection = mProjectionManager.getMediaProjection(mediaProjectionIntentResult, mediaProjectionIntentData);

            mRecorder = new ScreenRecorder(mEncoderConfig.getVideoConfig(), mEncoderConfig.getAudioConfig(), mVideoPath.getAbsolutePath(), mediaProjection);
            mRecorder.setCallback(new ScreenRecorder.Callback() {
                long startTime = 0;

                @Override
                public void onStop(Throwable error) {
                    if (error != null) {
                        Log.d(TAG, "Error on onStop");
                        error.printStackTrace();
                        deleteRecording();
                        notifyError(getString(R.string.unknow_error));
                    } else {
                        showSavingNotification();
                        mHandler.postDelayed(() -> {
                            new Thread(() -> MediaProviderHelper.addVideoToContentProvider(getContentResolver(), mVideoPath, ScreenRecorderService.this)).start();
                        }, 2000);
                    }
                    stopRecording(false);
                }

                @Override
                public void onStart() {
                    mElapsedTimeInSeconds = 0;
                    mShouldUpdateNotification = true;
                }

                @Override
                public void onRecording(long presentationTimeUs) {
                    if (startTime <= 0) {
                        startTime = presentationTimeUs;
                    }
                    int elapsedTimeInSeconds = (int) ((presentationTimeUs - startTime) / 1000000);
                    if (mShouldUpdateNotification && mElapsedTimeInSeconds != elapsedTimeInSeconds) {
                        mElapsedTimeInSeconds = elapsedTimeInSeconds;
                        updateNotification();
                    }
                }
            });

            new Thread(() -> mRecorder.start()).start();

            Utils.setStatus(Utils.PREF_RECORDING_SCREEN, this);

            Utils.refreshShowTouchesState(this);

            return START_STICKY;
        } catch (Exception e) {
            Log.d(TAG, "Error starting recorder");
            deleteRecording();
            notifyError(getString(R.string.unknow_error));
            stopRecording(true);
            stopForeground(true);
            e.printStackTrace();
        }

        return START_NOT_STICKY;
    }

    private void notifyError(String msg) {
        mHandler.post(() -> Utils.notifyError(msg, ScreenRecorderService.this, mNotificationManager));
    }

    private void deleteRecording() {
        if (mVideoPath != null && mVideoPath.exists()) {
            Log.d(TAG, "Deleting " + mVideoPath.getAbsolutePath());
            mVideoPath.delete();
        }
    }

    private boolean hasNoAvailableSpace() {
        StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
        long bytesAvailable = stat.getBlockSizeLong() * stat.getBlockCountLong();
        long megAvailable = bytesAvailable / 1048576;
        return megAvailable < 100;
    }

    private void updateNotification() {
        mBuilder.setContentText(getString(R.string.screen_notification_message,
                DateUtils.formatElapsedTime(mElapsedTimeInSeconds)));
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private void stopRecording(boolean stopForReal) {
        mShouldUpdateNotification = false;
        Utils.setStatus(Utils.PREF_RECORDING_NOTHING, this);
        if (hasNoAvailableSpace()) {
            notifyError(getString(R.string.screen_not_enough_storage));
        }
        Utils.refreshShowTouchesState(this);
        if (stopForReal && mRecorder != null) {
            new Thread(() -> mRecorder.quit()).start();
        }
    }

    private NotificationCompat.Builder createNotificationBuilder() {
        Intent stopRecordingIntent = new Intent(ACTION_STOP_SCREENCAST);
        stopRecordingIntent.setClass(this, ScreenRecorderService.class);

        return new NotificationCompat.Builder(this, SCREENCAST_NOTIFICATION_CHANNEL)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_notification_screen)
                .setContentTitle(getString(R.string.screen_notification_title))
                .setContentText(getString(R.string.screen_notification_message, DateUtils.formatElapsedTime(0)))
                .setContentIntent(PendingIntent.getActivity(this, 0, stopRecordingIntent, 0))
                .addAction(R.drawable.ic_stop, getString(R.string.stop),
                        PendingIntent.getService(this, 0, stopRecordingIntent, 0));
    }

    @Override
    public void onContentWritten(@Nullable String uri) {
        if (uri != null) {
            sendShareNotification(uri);
        }
        stopForeground(false);
    }

    private void sendShareNotification(String recordingFilePath) {
        mBuilder = createShareNotificationBuilder(recordingFilePath);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private NotificationCompat.Builder createShareNotificationBuilder(String uriStr) {
        Uri uri = Uri.parse(uriStr);
        PendingIntent playPIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getOpenIntent(uri, "video/mp4"),
                PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent sharePIntent = PendingIntent.getActivity(this, 0,
                LastRecordHelper.getShareIntent(uri, "video/mp4"),
                PendingIntent.FLAG_CANCEL_CURRENT);
        PendingIntent deletePIntent = PendingIntent.getBroadcast(this, 0,
                new Intent(this, DeleteVideoReceiver.class),
                PendingIntent.FLAG_CANCEL_CURRENT);

        LastRecordHelper.setLastItem(this, uriStr, mElapsedTimeInSeconds);

        Log.i(TAG, "Video complete: " + uriStr);

        return new NotificationCompat.Builder(this, Utils.RECORDING_DONE_NOTIFICATION_CHANNEL)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_notification_screen)
                .setContentTitle(getString(R.string.screen_notification_message_done))
                .setContentText(getString(R.string.screen_notification_message,
                        DateUtils.formatElapsedTime(mElapsedTimeInSeconds)))
                .addAction(R.drawable.ic_play, getString(R.string.play), playPIntent)
                .addAction(R.drawable.ic_share, getString(R.string.share), sharePIntent)
                .addAction(R.drawable.ic_delete, getString(R.string.delete), deletePIntent)
                .setAutoCancel(true)
                .setContentIntent(playPIntent);
    }
}
