/*
 * Copyright (c) 2020 PixelExperience
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.pixelexperience.recorder;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.SystemProperties;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenRecorder {
    private static final String TAG = "ScreenRecorder";
    private String mDstPath;

    private static final int AUDIO_BIT_RATE = 128000;
    private static final int SAMPLES_PER_FRAME = 1024;
    private static final int AUDIO_SAMPLE_RATE = 44100;
    private final Object mMuxerLock = new Object();
    private final Object mAudioEncoderLock = new Object();
    private VirtualDisplay mVirtualDisplay;
    private final Object mWriteVideoLock = new Object();
    private final Object mWriteAudioLock = new Object();
    private MediaMuxer mMuxer;
    private MediaProjection mMediaProjection;
    private Surface mInputSurface;
    private WindowManager mWindowManager;
    private boolean mMuxerStarted = false;
    private MediaRecorder mMediaRecorder;
    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private AudioRecord mInternalAudio;
    private boolean mPausedRecording = false;
    private boolean mAudioRecording;
    private boolean mAudioEncoding;
    private boolean mVideoEncoding;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private Callback mCallback;

    public ScreenRecorder(Context context, String dstPath, MediaProjection mediaProjection) {
        mDstPath = dstPath;
        mMediaProjection = mediaProjection;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void start() {
        try {
            // Set initial resources
            DisplayMetrics metrics = new DisplayMetrics();
            Display display = mWindowManager.getDefaultDisplay();
            display.getRealMetrics(metrics);
            int screenWidth = metrics.widthPixels;
            int screenHeight = metrics.heightPixels;
            int rotation = display.getRotation();
            switch (rotation) {
                case Surface.ROTATION_90:
                    rotation = 90;
                    break;
                case Surface.ROTATION_180:
                    rotation = 180;
                    break;
                case Surface.ROTATION_270:
                    rotation = 270;
                    break;
                case Surface.ROTATION_0:
                default:
                    rotation = 0;
            }

            boolean mIsLowRamEnabled = SystemProperties.get("ro.config.low_ram").equals("true");
            int video_bit_rate = mIsLowRamEnabled ? 10485760 : 20971520;

            int video_frame_rate = mIsLowRamEnabled ? 30 : 60;
            int total_num_tracks = 2;
            int audio_channel_type = AudioFormat.CHANNEL_IN_STEREO;
            String audio_codec = MediaFormat.MIMETYPE_AUDIO_AAC;
            String video_codec = mIsLowRamEnabled ? MediaFormat.MIMETYPE_VIDEO_AVC : MediaFormat.MIMETYPE_VIDEO_HEVC;

            // Preparing video encoder
            MediaFormat videoFormat = MediaFormat.createVideoFormat(video_codec, screenWidth, screenHeight);
            videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, video_bit_rate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, video_frame_rate);
            videoFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE, video_frame_rate);
            videoFormat.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / video_frame_rate);
            videoFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mVideoEncoder = MediaCodec.createEncoderByType(video_codec);
            mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            // Preparing audio encoder
            MediaFormat mAudioFormat = MediaFormat.createAudioFormat(audio_codec, AUDIO_SAMPLE_RATE, total_num_tracks);
            mAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mAudioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            mAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE);
            mAudioEncoder = MediaCodec.createEncoderByType(audio_codec);
            mAudioEncoder.configure(mAudioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMuxer = new MediaMuxer(mDstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mMuxer.setOrientationHint(rotation);

            // Preparing internal recorder
            AudioPlaybackCaptureConfiguration internalAudioConfig =
                    new AudioPlaybackCaptureConfiguration.Builder(mMediaProjection)
                            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                            .addMatchingUsage(AudioAttributes.USAGE_GAME)
                            .build();
            mInternalAudio = new AudioRecord.Builder()
                    .setAudioFormat(
                            new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(AUDIO_SAMPLE_RATE)
                                    .setChannelMask(audio_channel_type)
                                    .build())
                    .setAudioPlaybackCaptureConfig(internalAudioConfig)
                    .build();
            mInputSurface = mVideoEncoder.createInputSurface();

            // Create surface
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(
                    "Recording Display",
                    screenWidth,
                    screenHeight,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mInputSurface,
                    null,
                    null);

            // Let's get ready to record now
            // Start the encoders
            mVideoEncoder.start();
            new Thread(new VideoEncoderTask(), "VideoEncoderTask").start();
            mAudioEncoder.start();
            new Thread(new AudioEncoderTask(), "AudioEncoderTask").start();
            mInternalAudio.startRecording();
            mAudioRecording = true;
            new Thread(new AudioRecorderTask(), "AudioRecorderTask").start();

            mCallback.onStart();
        } catch (IOException e) {
            Log.e(TAG, "Error starting screen recording: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void stop(Throwable error) {
        try {
            mAudioRecording = false;
            mAudioEncoding = false;
            mVideoEncoding = false;
            mMediaRecorder.stop();
            mMediaRecorder.release();
            mMediaRecorder = null;
            mMediaProjection.stop();
            mMediaProjection = null;
            mInputSurface.release();
            mVirtualDisplay.release();
        } catch (Exception ignored) {
        }
        mCallback.onStop(error);
    }

    void setCallback(Callback callback) {
        mCallback = callback;
    }

    private class AudioRecorderTask implements Runnable {
        ByteBuffer inputBuffer;
        int readResult;

        @Override
        public void run() {
            long audioPresentationTimeNs;
            byte[] mTempBuffer = new byte[SAMPLES_PER_FRAME];
            while (mAudioRecording) {
                if (!mPausedRecording) {
                    audioPresentationTimeNs = System.nanoTime();
                    readResult = mInternalAudio.read(mTempBuffer, 0, SAMPLES_PER_FRAME);
                    if (readResult == AudioRecord.ERROR_BAD_VALUE || readResult == AudioRecord.ERROR_INVALID_OPERATION) {
                        continue;
                    }
                    // send current frame data to encoder
                    try {
                        synchronized (mAudioEncoderLock) {
                            if (mAudioEncoding) {
                                int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
                                if (inputBufferIndex >= 0) {
                                    inputBuffer = mAudioEncoder.getInputBuffer(inputBufferIndex);
                                    inputBuffer.clear();
                                    inputBuffer.put(mTempBuffer);

                                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, mTempBuffer.length, audioPresentationTimeNs / 1000, 0);
                                }
                            }
                        }
                    } catch (Throwable t) {
                        stop(t);
                    }
                }
            }
            // finished recording -> send it to the encoder
            audioPresentationTimeNs = System.nanoTime();
            readResult = mInternalAudio.read(mTempBuffer, 0, SAMPLES_PER_FRAME);
            if (readResult == AudioRecord.ERROR_BAD_VALUE
                    || readResult == AudioRecord.ERROR_INVALID_OPERATION)
                // send current frame data to encoder
                try {
                    synchronized (mAudioEncoderLock) {
                        if (mAudioEncoding) {
                            int inputBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
                            if (inputBufferIndex >= 0) {
                                inputBuffer = mAudioEncoder.getInputBuffer(inputBufferIndex);
                                inputBuffer.clear();
                                inputBuffer.put(mTempBuffer);
                                mAudioEncoder.queueInputBuffer(inputBufferIndex, 0, mTempBuffer.length, audioPresentationTimeNs / 1000, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            }
                        }
                    }
                } catch (Throwable t) {
                    stop(t);
                }
            mInternalAudio.stop();
            mInternalAudio.release();
            mInternalAudio = null;
        }
    }

    // Encoders tasks to do both screen capture and audio recording
    private class VideoEncoderTask implements Runnable {

        @Override
        public void run() {
            mVideoEncoding = true;
            videoTrackIndex = -1;
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            while (mVideoEncoding) {
                if (!mPausedRecording) {
                    int bufferIndex = mVideoEncoder.dequeueOutputBuffer(videoBufferInfo, 10);
                    if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // nothing available yet
                    } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // should happen before receiving buffers, and should only happen once
                        if (videoTrackIndex >= 0) {
                            throw new RuntimeException("format changed twice");
                        }
                        synchronized (mMuxerLock) {
                            videoTrackIndex = mMuxer.addTrack(mVideoEncoder.getOutputFormat());

                            if (!mMuxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                                mMuxer.start();
                                mMuxerStarted = true;
                            }
                        }
                    } else if (bufferIndex < 0) {
                        // not sure what's going on, ignore it
                    } else {
                        ByteBuffer videoData = mVideoEncoder.getOutputBuffer(bufferIndex);
                        if (videoData == null) {
                            throw new RuntimeException("couldn't fetch buffer at index " + bufferIndex);
                        }
                        if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            videoBufferInfo.size = 0;
                        }
                        if (videoBufferInfo.size != 0) {
                            if (mMuxerStarted) {
                                videoData.position(videoBufferInfo.offset);
                                videoData.limit(videoBufferInfo.offset + videoBufferInfo.size);
                                synchronized (mWriteVideoLock) {
                                    if (mMuxerStarted) {
                                        mMuxer.writeSampleData(videoTrackIndex, videoData, videoBufferInfo);
                                    }
                                }
                            } else {
                                // muxer not started
                            }
                        }
                        mVideoEncoder.releaseOutputBuffer(bufferIndex, false);
                        if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            mVideoEncoding = false;
                            break;
                        } else {
                            mCallback.onRecording(videoBufferInfo.presentationTimeUs);
                        }
                    }
                }
            }
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mVideoEncoder = null;

            if (mInputSurface != null) {
                mInputSurface.release();
                mInputSurface = null;
            }
            if (mMediaProjection != null) {
                mMediaProjection.stop();
                mMediaProjection = null;
            }
            synchronized (mWriteAudioLock) {
                synchronized (mMuxerLock) {
                    if (mMuxer != null) {
                        if (mMuxerStarted) {
                            mMuxer.stop();
                        }
                        mMuxer.release();
                        mMuxer = null;
                        mMuxerStarted = false;
                    }
                }
            }
        }
    }

    private class AudioEncoderTask implements Runnable {

        @Override
        public void run() {
            mAudioEncoding = true;
            audioTrackIndex = -1;
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            while (mAudioEncoding) {
                if (!mPausedRecording) {
                    int bufferIndex = mAudioEncoder.dequeueOutputBuffer(audioBufferInfo, 10);
                    if (bufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                    } else if (bufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // should happen before receiving buffers, and should only happen once
                        if (audioTrackIndex >= 0) {
                            throw new RuntimeException("format changed twice");
                        }
                        synchronized (mMuxerLock) {
                            audioTrackIndex = mMuxer.addTrack(mAudioEncoder.getOutputFormat());

                            if (!mMuxerStarted && videoTrackIndex >= 0 && audioTrackIndex >= 0) {
                                mMuxer.start();
                                mMuxerStarted = true;
                            }
                        }
                    } else if (bufferIndex < 0) {
                        // let's ignore it
                    } else {
                        if (mMuxerStarted && audioTrackIndex >= 0) {
                            ByteBuffer encodedData = mAudioEncoder.getOutputBuffer(bufferIndex);
                            if (encodedData == null) {
                                throw new RuntimeException("encoderOutputBuffer " + bufferIndex + " was null");
                            }
                            if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                // The codec config data was pulled out and fed to the muxer when we got
                                // the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
                                audioBufferInfo.size = 0;
                            }
                            if (audioBufferInfo.size != 0) {
                                if (mMuxerStarted) {
                                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                                    encodedData.position(audioBufferInfo.offset);
                                    encodedData.limit(audioBufferInfo.offset + audioBufferInfo.size);
                                    synchronized (mWriteAudioLock) {
                                        if (mMuxerStarted) {
                                            mMuxer.writeSampleData(audioTrackIndex, encodedData, audioBufferInfo);
                                        }
                                    }
                                }
                            }
                            mAudioEncoder.releaseOutputBuffer(bufferIndex, false);
                            if ((audioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                // reached EOS
                                mAudioEncoding = false;
                                break;
                            }
                        }
                    }
                }
            }

            synchronized (mAudioEncoderLock) {
                mAudioEncoder.stop();
                mAudioEncoder.release();
                mAudioEncoder = null;
            }

            synchronized (mWriteVideoLock) {
                synchronized (mMuxerLock) {
                    if (mMuxer != null) {
                        if (mMuxerStarted) {
                            mMuxer.stop();
                        }
                        mMuxer.release();
                        mMuxer = null;
                        mMuxerStarted = false;
                    }
                }
            }
        }
    }

    public interface Callback {
        void onStop(Throwable error);

        void onStart();

        void onRecording(long presentationTimeUs);
    }

}
