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
package org.pixelexperience.recorder.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import org.pixelexperience.recorder.R;

public class OverlayLayer extends View implements View.OnTouchListener {

    private final FrameLayout mLayout;
    private final WindowManager mManager;
    private final WindowManager.LayoutParams mParams;
    private final ImageButton mButton;
    private final ImageButton mSettingsButton;
    private final ImageButton mCloseButton;
    private final TextView mTimerView;
    private int origX;
    private int origY;
    private int touchX;
    private int touchY;
    private final float movimentThreshold = 10;
    private boolean isClick;
    private Context mContext;

    @SuppressLint("ClickableViewAccessibility")
    public OverlayLayer(Context context) {
        super(context);
        mContext = context;

        LayoutInflater inflater = context.getSystemService(LayoutInflater.class);
        mLayout = new FrameLayout(context);
        mManager = context.getSystemService(WindowManager.class);
        mParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.START;
        if (mManager != null) {
            mManager.addView(mLayout, mParams);
        }
        if (inflater != null) {
            inflater.inflate(R.layout.window_screen_recorder_overlay, mLayout);
        }

        mButton = mLayout.findViewById(R.id.overlay_button);
        mSettingsButton = mLayout.findViewById(R.id.overlay_settings);
        mCloseButton = mLayout.findViewById(R.id.overlay_close);
        mTimerView = mLayout.findViewById(R.id.timer_view);
        mLayout.setOnTouchListener(this);
        mButton.setOnTouchListener(this);
        mSettingsButton.setOnTouchListener(this);
        mCloseButton.setOnTouchListener(this);
        mTimerView.setOnTouchListener(this);
    }

    public void setIsRecording(boolean isRecording) {
        if (isRecording) {
            mSettingsButton.setVisibility(View.GONE);
            mCloseButton.setVisibility(View.GONE);
            mTimerView.setVisibility(View.VISIBLE);
            mButton.setImageDrawable(mContext.getDrawable(R.drawable.ic_stop_screen));
        } else {
            mSettingsButton.setVisibility(View.VISIBLE);
            mCloseButton.setVisibility(View.VISIBLE);
            mTimerView.setVisibility(View.GONE);
            mButton.setImageDrawable(mContext.getDrawable(R.drawable.ic_action_screen_record));
        }
        updateTimerView(0);
    }

    public void updateTimerView(long sec) {
        mTimerView.setText(DateUtils.formatElapsedTime(sec));
    }

    public void destroy() {
        mLayout.setVisibility(GONE);
        mManager.removeView(mLayout);
    }

    public void setOnActionClickListener(ActionClickListener listener) {
        mButton.setOnClickListener(v -> listener.onClick());
    }

    public void setSettingsButtonOnClickListener(ActionClickListener listener) {
        mSettingsButton.setOnClickListener(v -> listener.onClick());
    }


    public void setCloseButtonOnClickListener(ActionClickListener listener) {
        mCloseButton.setOnClickListener(v -> listener.onClick());
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int x = (int) event.getRawX();
        int y = (int) event.getRawY();

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                origX = mParams.x;
                origY = mParams.y;
                touchX = x;
                touchY = y;
                isClick = true;
                break;
            case MotionEvent.ACTION_MOVE:
                mParams.x = origX + x - touchX;
                mParams.y = origY + y - touchY;
                if (mManager != null) {
                    mManager.updateViewLayout(mLayout, mParams);
                }
                if (isClick && (Math.abs(origX - mParams.x) > movimentThreshold ||
                        Math.abs(origY - mParams.y) > movimentThreshold)) {
                    isClick = false;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (isClick) {
                    v.performClick();
                }
                break;
            default:
                break;
        }

        return true;
    }

    public interface ActionClickListener {
        void onClick();
    }
}
