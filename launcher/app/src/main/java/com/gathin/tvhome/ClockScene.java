package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock): the thin clock and date on black over a slow ambient
 * glow. Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** The glow's colour change: 16 steps over 8 s. Animated per frame it redrew the full-screen gradient ~480 times. */
    private static final int GLOW_STEPS = 16;
    private static final long GLOW_STEP_MS = 500L;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final View ambient;
    private final ImageView dreamWallpaper;
    private final View timeRow;
    private final TextView dreamClock;
    private final TextView dreamDate;
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    /** clock_wallpaper or clock_glow false: something else (a picture, or plain black) replaced the coloured glow. */
    private boolean wallpaperShown;
    /** clock_wallpaper: the picture start() decodes, its darkening, and the resources to draw it with. */
    private File wallpaperFile;
    private int wallpaperShade;
    private Resources wallpaperRes;
    /** Bumped by stop(): a decode started before it finds a different number and drops its result. Main thread only. */
    private int wallpaperGeneration;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            shiftGlow();
            handler.post(ClockScene.this::adjustDateLetterSpacing);
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        ambient = root.findViewById(R.id.dreamAmbient);
        dreamWallpaper = root.findViewById(R.id.dreamWallpaper);
        timeRow = root.findViewById(R.id.dreamTimeRow);
        dreamClock = root.findViewById(R.id.dreamClock);
        dreamDate = root.findViewById(R.id.dreamDate);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        View.OnLayoutChangeListener layoutListener = (v, l, t, r, b, ol, ot, or, ob) -> adjustDateLetterSpacing();
        if (timeRow != null) timeRow.addOnLayoutChangeListener(layoutListener);
        if (dreamClock != null) dreamClock.addOnLayoutChangeListener(layoutListener);
        if (dreamDate != null) dreamDate.addOnLayoutChangeListener(layoutListener);
        colorIndex = random.nextInt(PALETTE.length);
        // Do not set ambient background here: wait for useHomeWallpaper() to check clock_wallpaper and clock_glow
    }

    private void adjustDateLetterSpacing() {
        if (dreamDate == null || dreamClock == null) return;
        CharSequence cs = dreamDate.getText();
        if (cs == null || cs.length() <= 1) return;
        // TextClock has textAllCaps="true", so we must measure the uppercase string that is actually drawn!
        String text = cs.toString().toUpperCase(java.util.Locale.getDefault());

        float targetWidth = 0;
        if (timeRow != null && timeRow.getWidth() > 0) {
            targetWidth = timeRow.getWidth();
        } else if (dreamClock.getWidth() > 0) {
            targetWidth = dreamClock.getWidth();
        }
        if (targetWidth <= 0) return;

        // Leave a 2px safety buffer so the rightmost glyph is never clipped by parent bounds
        float target = Math.max(0f, targetWidth - 2f);

        if (dreamDate.getLayoutParams() != null) {
            int tw = Math.round(targetWidth);
            if (dreamDate.getLayoutParams().width != tw) {
                dreamDate.getLayoutParams().width = tw;
                dreamDate.requestLayout();
            }
        }

        TextPaint paint = new TextPaint(dreamDate.getPaint());
        paint.setLetterSpacing(0f);
        float baseWidth = paint.measureText(text);
        float em = dreamDate.getTextSize();
        if (em <= 0) return;

        float extra = target - baseWidth;
        if (extra > 0) {
            float spacing = extra / (text.length() * em);
            paint.setLetterSpacing(spacing);
            float actualWidth = paint.measureText(text);
            if (actualWidth > 0 && Math.abs(actualWidth - target) > 0.5f) {
                float diff = actualWidth - baseWidth;
                if (Math.abs(diff) > 0.1f) {
                    spacing = spacing * (extra / diff);
                }
            }
            if (Math.abs(dreamDate.getLetterSpacing() - spacing) > 0.002f) {
                dreamDate.setLetterSpacing(spacing);
            }
        } else {
            dreamDate.setLetterSpacing(0f);
        }
    }

    void start() {
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            adjustDateLetterSpacing();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
        if (wallpaperFile != null && !wallpaperShown) loadWallpaper();
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);   // the tick and any glow steps still queued
        content.animate().cancel();
        wallpaperGeneration++;   // a decode still running must not attach its picture to a stopped scene
    }

    /**
     * clock_wallpaper: put Home's wallpaper (the theme's single picture, or the first of its collection) behind
     * the clock, darkened by clock_wallpaper_dim, in place of the glow. Decoded off the main thread at screen size
     * in RGB_565 (4 MB at 1080p), like Home's own; until then, or if it cannot be read, the glow stays.
     * Call before start(), which starts the decode (again after a stop() that dropped an unfinished one).
     */
    void useHomeWallpaper(Context ctx) {
        Config cfg;
        try {
            cfg = Config.load(ctx);
        } catch (RuntimeException e) {
            return;
        }
        if (!cfg.clockWallpaper) {
            // clock_glow false: plain black behind the clock (OLED); the clock itself still moves every minute.
            if (!cfg.clockGlow) {
                wallpaperShown = true;   // also stops the glow's colour cycle
                ambient.setBackground(null);
            } else {
                ambient.setBackground(glow);
                setGlow(PALETTE[colorIndex]);
            }
            return;
        }
        // clock_wallpaper is true: ensure ambient has NO coloured glow layer
        ambient.setBackground(null);
        wallpaperFile = cfg.wallpaper != null ? cfg.wallpaper : cfg.wallpapers.isEmpty() ? null : cfg.wallpapers.get(0);
        if (wallpaperFile == null) {
            File direct = new File("/sdcard/wallpaper.png");
            if (direct.isFile() && direct.length() > 0) wallpaperFile = direct;
        }
        wallpaperShade = (cfg.clockWallpaperDim * 255 / 100) << 24;
        wallpaperRes = ctx.getResources();
        if (wallpaperFile != null && !wallpaperShown) {
            loadWallpaper();
        }
    }

    private void loadWallpaper() {
        File file = wallpaperFile;
        int shade = wallpaperShade;
        Resources res = wallpaperRes;
        DisplayMetrics dm = res.getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        int generation = wallpaperGeneration;
        new Thread(() -> {
            Bitmap bmp = decode(file, w, h);
            if (bmp == null) return;
            handler.post(() -> {
                if (generation != wallpaperGeneration || wallpaperShown) {
                    bmp.recycle();   // stopped meanwhile (or already shown): this picture was never attached
                    return;
                }
                wallpaperShown = true;
                if (dreamWallpaper != null) {
                    dreamWallpaper.setImageBitmap(bmp);
                } else {
                    ambient.setBackground(new LayerDrawable(new Drawable[]{
                            new BitmapDrawable(res, bmp), new ColorDrawable(shade)}));
                    return;
                }
                ambient.setBackgroundColor(shade);
            });
        }, "clock-wallpaper").start();
    }

    /** WallpaperPlayer's decode: sampled and scaled down to cover the screen, never bigger than 4K. */
    private static Bitmap decode(File file, int screenW, int screenH) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            int width = options.outWidth, height = options.outHeight;
            if (width <= 0 || height <= 0) return null;
            int sample = WallpaperSize.sampleSize(width, height, screenW, screenH);
            if (!WallpaperSize.accepted(width / sample, height / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(width / sample, height / sample, screenW, screenH);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                options.inPreferredColorSpace = android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB);
            }
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    private void moveClock() {
        // Kept fixed at the bottom left
    }

    /** Fixed at bottom-left corner with comfortable TV margins. */
    private void placeClock() {
        View parent = (View) content.getParent();
        float dp = content.getResources().getDisplayMetrics().density;
        float x = 60f * dp;
        int parentH = parent != null && parent.getHeight() > 0 ? parent.getHeight() : content.getResources().getDisplayMetrics().heightPixels;
        int contentH = content.getHeight() > 0 ? content.getHeight() : Math.round(180f * dp);
        float y = parentH - contentH - 44f * dp;
        content.setX(x);
        content.setY(y);
    }

    private void shiftGlow() {
        if (wallpaperShown || ambient.getBackground() != glow) return;
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        int to = PALETTE[colorIndex];
        ArgbEvaluator mix = new ArgbEvaluator();
        for (int i = 1; i <= GLOW_STEPS; i++) {
            float f = (float) i / GLOW_STEPS;
            handler.postDelayed(() -> setGlow((int) mix.evaluate(f, from, to)), i * GLOW_STEP_MS);
        }
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
