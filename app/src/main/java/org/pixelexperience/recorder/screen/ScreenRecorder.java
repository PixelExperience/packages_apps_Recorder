package org.pixelexperience.recorder.screen;

/*
 * Copyright (c) 2016-2018. Vijai Chandra Prasad R.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses
 */

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.EncoderCapabilities;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import org.pixelexperience.recorder.utils.PreferenceUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ScreenRecorder {
    private static final File RECORDINGS_DIR =
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ScreenRecords");
    private static final List<EncoderCapabilities.VideoEncoderCap> videoEncoders =
            EncoderCapabilities.getVideoEncoders();
    private static final String TAG = "ScreeRecorder";
    private int mWidth, mHeight, mDensityDpi;
    private int mBitrate;
    private File mPath;
    private Context mContext;
    private DisplayManager mDisplayManager;
    private WindowManager mWindowManager;
    private MediaProjectionManager mProjectionManager;
    private PreferenceUtils mPreferenceUtils;
    private ScreenRecorderResultCallback mScreenRecorderCallback;
    private Intent mIntentData;
    private int mIntentResult;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionCallback mMediaProjectionCallback;
    private MediaRecorder mMediaRecorder;
    private boolean mIsPaused = false;
    private boolean mIsStopping;

    ScreenRecorder(Context context, Intent intentData, int intentResult, ScreenRecorderResultCallback callback) {
        mContext = context;
        // Prepare all the output metadata
        String videoDate = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                .format(new Date());
        // the directory which holds all recording files
        mPath = new File(RECORDINGS_DIR, "ScreenRecord-" + videoDate + ".mp4");

        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mProjectionManager = (MediaProjectionManager) mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mPreferenceUtils = new PreferenceUtils(context);
        mMediaProjectionCallback = new MediaProjectionCallback();
        mScreenRecorderCallback = callback;
        mIntentData = intentData;
        mIntentResult = intentResult;
    }

    void startRecording() {
        File recordingDir = mPath.getParentFile();
        recordingDir.mkdirs();
        if (!(recordingDir.exists() && recordingDir.canWrite())) {
            Log.e(TAG, "Cannot write to " + recordingDir);
            mScreenRecorderCallback.onRecordingError();
            return;
        }

        updateDisplayParams();

        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setOnErrorListener((mr, what, extra) -> fireErrorCallback());

        boolean mustRecAudio = false;
        try {
            switch (mPreferenceUtils.getAudioRecordingType()) {
                case PreferenceUtils.PREF_AUDIO_RECORDING_TYPE_MICROPHONE:
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    mMediaRecorder.setAudioEncodingBitRate(64 * 1024);
                    mMediaRecorder.setAudioSamplingRate(44100);
                    mMediaRecorder.setAudioChannels(1);
                    mustRecAudio = true;
                    break;
                case PreferenceUtils.PREF_AUDIO_RECORDING_TYPE_INTERNAL:
                    mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.REMOTE_SUBMIX);
                    mMediaRecorder.setAudioEncodingBitRate(128000);
                    mMediaRecorder.setAudioSamplingRate(48000);
                    mMediaRecorder.setAudioChannels(1);
                    mustRecAudio = true;
                    break;
            }
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setOutputFile(mPath.getPath());
            mMediaRecorder.setVideoSize(mWidth, mHeight);
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setMaxFileSize(getFreeSpaceInBytes());
            if (mustRecAudio)
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setVideoEncodingBitRate(mBitrate);
            mMediaRecorder.setVideoFrameRate(mPreferenceUtils.getVideoRecordingMaxFps());
            mMediaRecorder.prepare();
        } catch (IOException e) {
            mScreenRecorderCallback.onRecordingError();
            e.printStackTrace();
        }

        //Initialize MediaProjection using data received from Intent
        mMediaProjection = mProjectionManager.getMediaProjection(mIntentResult, mIntentData);
        mMediaProjection.registerCallback(mMediaProjectionCallback, null);

        /* Create a new virtual display with the actual default display
         * and pass it on to MediaRecorder to start recording */
        mVirtualDisplay = createVirtualDisplay();
        if (mVirtualDisplay == null) {
            fireErrorCallback();
        }
        try {
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Mediarecorder reached Illegal state exception. Did you start the recording twice?");
            fireErrorCallback();
        }
    }

    //Virtual display created by mirroring the actual physical display
    private VirtualDisplay createVirtualDisplay() {
        mIsPaused = false;
        return mMediaProjection.createVirtualDisplay("Screen recorder",
                mWidth, mHeight, mDensityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mMediaRecorder.getSurface(), new VirtualDisplay.Callback() {
                    @Override
                    public void onPaused() {
                        super.onPaused();
                        if (!mIsPaused && !mIsStopping){
                            mIsPaused = true;
                            mMediaRecorder.pause();
                            mScreenRecorderCallback.onRecordingPaused();
                        }
                    }

                    @Override
                    public void onResumed() {
                        super.onResumed();
                        if (mIsPaused && !mIsStopping){
                            mIsPaused = false;
                            mMediaRecorder.resume();
                            mScreenRecorderCallback.onRecordingResumed();
                        }
                    }

                    @Override
                    public void onStopped() {
                        super.onStopped();
                        mIsPaused = false;
                    }
                } /*Callbacks*/, null
                /*Handler*/);
    }

    private long getFreeSpaceInBytes() {
        StatFs FSStats = new StatFs(mPath.getParentFile().toString());
        return FSStats.getAvailableBytes();
    }

    //Get the device resolution in pixels
    private void updateDisplayParams() {
        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getRealMetrics(metrics);
        mDensityDpi = metrics.densityDpi;

        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Point size = new Point();
        try {
            display.getRealSize(size);
        } catch (Exception e) {
            try {
                //noinspection JavaReflectionMemberAccess
                Method mGetRawH = Display.class.getMethod("getRawHeight");
                //noinspection JavaReflectionMemberAccess
                Method mGetRawW = Display.class.getMethod("getRawWidth");
                size.x = (Integer) mGetRawW.invoke(display);
                size.y = (Integer) mGetRawH.invoke(display);
            } catch (Exception ex) {
                display.getSize(size);
            }
        }

        int width = size.x;
        int height = size.y;
        int bitrate = 2000000;

        for (EncoderCapabilities.VideoEncoderCap cap : videoEncoders) {
            if (cap.mCodec == MediaRecorder.VideoEncoder.H264) {
                bitrate = cap.mMaxBitRate;
            }
        }

        mBitrate = bitrate;

        int screenOrientation = mWindowManager.getDefaultDisplay().getRotation();
        switch (mPreferenceUtils.getVideoRecordingOrientation()) {
            case PreferenceUtils.PREF_SCREEN_ORIENTATION_AUTOMATIC:
                if (screenOrientation == Surface.ROTATION_0 || screenOrientation == Surface.ROTATION_180) {
                    mWidth = width;
                    mHeight = height;
                } else {
                    mWidth = height;
                    mHeight = width;
                }
                break;
            case PreferenceUtils.PREF_SCREEN_ORIENTATION_PORTRAIT:
                mWidth = width;
                mHeight = height;
                break;
            case PreferenceUtils.PREF_SCREEN_ORIENTATION_LANDSCAPE:
                mWidth = height;
                mHeight = width;
                break;
        }
    }

    private void fireErrorCallback() {
        if (mPath != null) {
            mPath.delete();
            mPath = null;
        }
        mScreenRecorderCallback.onRecordingError();
    }

    void stopRecording() {
        mIsPaused = false;
        try {
            mMediaRecorder.stop();
            indexFile();
        } catch (RuntimeException e) {
            Log.e(TAG, "Fatal exception! Destroying media projection failed." + "\n" + e.getMessage());
            if (mPath != null) {
                mPath.delete();
                mPath = null;
            }
        } finally {
            try {
                mMediaRecorder.reset();
            } catch (Exception ignored) {
            }
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
            try {
                mMediaRecorder.release();
            } catch (Exception ignored) {
            }
            if (mMediaProjection != null) {
                mMediaProjection.unregisterCallback(mMediaProjectionCallback);
                mMediaProjection.stop();
                mMediaProjection = null;
            }
        }
    }

    /* Its weird that android does not index the files immediately once its created and that causes
     * trouble for user in finding the video in gallery. Let's explicitly announce the file creation
     * to android and index it */
    private void indexFile() {
        //Create a new ArrayList and add the newly created video file path to it
        ArrayList<String> toBeScanned = new ArrayList<>();
        toBeScanned.add(mPath.getPath());
        String[] toBeScannedStr = new String[toBeScanned.size()];
        toBeScannedStr = toBeScanned.toArray(toBeScannedStr);

        //Request MediaScannerConnection to scan the new file and index it
        MediaScannerConnection.scanFile(mContext, toBeScannedStr, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {
            }
        });
    }

    String getRecordingFilePath() {
        return mPath == null ? null : mPath.getAbsolutePath();
    }

    void setStopping(boolean isStopping){
        mIsStopping = isStopping;
    }

    public interface ScreenRecorderResultCallback {
        void onRecordingError();
        void onRecordingPaused();
        void onRecordingResumed();
    }

    private class MediaProjectionCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            stopRecording();
        }
    }
}
