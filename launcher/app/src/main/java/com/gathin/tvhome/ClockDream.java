package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import java.util.List;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow (ClockScene).
 * Automatically suppresses/finishes if video or audio is actively playing (e.g. in TV Bro or media apps).
 */
public class ClockDream extends DreamService {

    private ClockScene scene;
    private AudioManager audioManager;
    private AudioManager.AudioPlaybackCallback audioCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable audioCheckRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAudioOrVideoPlaying()) {
                finish();
            } else {
                handler.postDelayed(this, 1000);
            }
        }
    };

    private boolean isAudioOrVideoPlaying() {
        try {
            if (audioManager == null) {
                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            }
            if (audioManager != null && audioManager.isMusicActive()) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isAudioOrVideoPlaying()) {
            finish();
            return;
        }
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);
        scene = new ClockScene(getWindow().getDecorView());
        scene.useHomeWallpaper(this);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (isAudioOrVideoPlaying()) {
            finish();
            return;
        }
        if (scene != null) scene.start();
        registerAudioCallback();
        handler.postDelayed(audioCheckRunnable, 1000);
    }

    private void registerAudioCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            try {
                if (audioCallback == null) {
                    audioCallback = new AudioManager.AudioPlaybackCallback() {
                        @Override
                        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
                            if (isAudioOrVideoPlaying()) {
                                finish();
                            }
                        }
                    };
                }
                audioManager.registerAudioPlaybackCallback(audioCallback, handler);
            } catch (Throwable ignored) {}
        }
    }

    private void unregisterAudioCallback() {
        handler.removeCallbacks(audioCheckRunnable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null && audioCallback != null) {
            try {
                audioManager.unregisterAudioPlaybackCallback(audioCallback);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void onDreamingStopped() {
        unregisterAudioCallback();
        if (scene != null) scene.stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        unregisterAudioCallback();
        if (scene != null) scene.stop();
        super.onDetachedFromWindow();
    }
}
