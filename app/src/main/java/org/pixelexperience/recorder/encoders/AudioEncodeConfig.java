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

import android.media.MediaFormat;

import java.util.Objects;

public class AudioEncodeConfig {
    public final String codecName;
    public final String mimeType;
    public final int bitRate;
    public final int sampleRate;
    public final int channelCount;
    public final int profile;

    public AudioEncodeConfig(String codecName, String mimeType,
                             int bitRate, int sampleRate, int channelCount, int profile) {
        this.codecName = codecName;
        this.mimeType = Objects.requireNonNull(mimeType);
        this.bitRate = bitRate;
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        this.profile = profile;
    }

    MediaFormat toFormat() {
        MediaFormat format = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        return format;
    }

    @Override
    public String toString() {
        return "AudioEncodeConfig{" +
                "codecName='" + codecName + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", bitRate=" + bitRate +
                ", sampleRate=" + sampleRate +
                ", channelCount=" + channelCount +
                ", profile=" + profile +
                '}';
    }
}