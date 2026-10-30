package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * The clock screensaver shown by Home itself after home_idle_clock_minutes without input. Some TVs' own
 * screens (CVTE's live TV) do not keep the screen on, so the system screensaver would cut into programmes;
 * this one only ever starts from Home. Any key returns to Home.
 * Automatically dismisses if media or video playback is active.
 */
public class ClockActivity extends Activity {

    private ClockScene scene;

    private boolean isMediaPlaying() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.isMusicActive()) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isMediaPlaying()) {
            finish();
            return;
        }
        overridePendingTransition(R.anim.screensaver_fade_in, R.anim.screensaver_fade_out);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.dream_clock);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
        scene = new ClockScene(getWindow().getDecorView());
        scene.useHomeWallpaper(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isMediaPlaying()) {
            finish();
            return;
        }
        scene.start();
    }

    @Override
    protected void onPause() {
        if (scene != null) scene.stop();
        super.onPause();
        finish();   // never left behind in the back stack
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // The first button pressed immediately wakes and dismisses the screensaver.
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            finish();
            return true;
        }
        return true;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        finish();
        return true;
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.screensaver_fade_in, R.anim.screensaver_fade_out);
    }
}
