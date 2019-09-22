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
    // Standard resolution tables, removed values that aren't multiples of 8
    private final int[][] validResolutions = {
            // CEA Resolutions
            {640, 480},
            {720, 480},
            {720, 576},
            {1280, 720},
            {1920, 1080},
            // VESA Resolutions
            {800, 600},
            {1024, 768},
            {1152, 864},
            {1280, 768},
            {1280, 800},
            {1360, 768},
            {1366, 768},
            {1280, 1024},
            //{ 1400, 1050 },
            //{ 1440, 900 },
            //{ 1600, 900 },
            {1600, 1200},
            //{ 1680, 1024 },
            //{ 1680, 1050 },
            {1920, 1200},
            // HH Resolutions
            {800, 480},
            {854, 480},
            {864, 480},
            {640, 360},
            //{ 960, 540 },
            {848, 480}
    };
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
        int maxWidth = 640;
        int maxHeight = 480;
        int bitrate = 2000000;

        for (EncoderCapabilities.VideoEncoderCap cap : videoEncoders) {
            if (cap.mCodec == MediaRecorder.VideoEncoder.H264) {
                maxWidth = cap.mMaxFrameWidth;
                maxHeight = cap.mMaxFrameHeight;
                bitrate = cap.mMaxBitRate;
            }
        }

        int max = Math.max(maxWidth, maxHeight);
        int min = Math.min(maxWidth, maxHeight);
        int resConstraint = mContext.getResources().getInteger(
                R.integer.config_maxDimension);

        double ratio;
        boolean landscape = false;
        boolean resizeNeeded = false;

        // see if we need to resize

        // Figure orientation and ratio first
        if (width > height) {
            // landscape
            landscape = true;
            ratio = (double) width / (double) height;
            if (resConstraint >= 0 && height > resConstraint) {
                min = resConstraint;
            }
            if (width > max || height > min) {
                resizeNeeded = true;
            }
        } else {
            // portrait
            ratio = (double) height / (double) width;
            if (resConstraint >= 0 && width > resConstraint) {
                min = resConstraint;
            }
            if (height > max || width > min) {
                resizeNeeded = true;
            }
        }

        if (resizeNeeded) {
            boolean matched = false;
            for (int[] resolution : validResolutions) {
                // All res are in landscape. Find the highest match
                if (resolution[0] <= max && resolution[1] <= min &&
                        (!matched || (resolution[0] > (landscape ? width : height)))) {
                    if (((double) resolution[0] / (double) resolution[1]) == ratio) {
                        // Got a valid one
                        if (landscape) {
                            width = resolution[0];
                            height = resolution[1];
                        } else {
                            height = resolution[0];
                            width = resolution[1];
                        }
                        matched = true;
                    }
                }
            }
            if (!matched) {
                // No match found. Go for the lowest... :(
                width = landscape ? 640 : 480;
                height = landscape ? 480 : 640;
            }
        }

        mWidth = width;
        mHeight = height;
        mBitrate = bitrate;
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
