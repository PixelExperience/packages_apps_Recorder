/*
 * Copyright (c) 2017 Yrom Wang <http://www.yrom.net>
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

package org.pixelexperience.recorder.encoders;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import static android.media.MediaFormat.MIMETYPE_AUDIO_AAC;

public class EncoderConfig {

    WindowManager mWindowManager;

    public EncoderConfig(Context context) {
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public AudioEncodeConfig getAudioConfig() {
        int bitrate = 128 * 1000;
        int samplerate = 44100;
        int channelCount = 2;
        int profile = MediaCodecInfo.CodecProfileLevel.AACObjectLC;

        return new AudioEncodeConfig(null, MIMETYPE_AUDIO_AAC, bitrate, samplerate, channelCount, profile);
    }

    public VideoEncodeConfig getVideoConfig() {
        DisplayMetrics metrics = new DisplayMetrics();
        Display display = mWindowManager.getDefaultDisplay();
        display.getRealMetrics(metrics);
        mWindowManager.getDefaultDisplay().getRealMetrics(metrics);

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
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
        int densityDpi = metrics.densityDpi;
        int frame_rate = 30;
        int iframe_interval = 1;
        int bitrate = 6000000;

        return new VideoEncodeConfig(width, height, densityDpi, rotation, bitrate,
                frame_rate, iframe_interval, null, MediaFormat.MIMETYPE_VIDEO_AVC, null);
    }
}
