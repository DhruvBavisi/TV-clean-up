package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Stage 2: Level 2 Title Page for My Favourites.
 * Shows a full-screen view with the character's darkened scene, character name top-left,
 * horizontal scrolling row of posters with actor/season group labels, focused glow,
 * title metadata, dock app logo, and launching via ACTION_VIEW intent.
 */
public class FavouritesTitlePageView extends View {

    public interface OnBackListener {
        void onBack();
    }

    public static class TitleItem {
        final String characterId;
        final String group;
        final String title;
        final String yearOrSeason;
        final String appPackage;
        final String link;
        final String posterFile;
        Bitmap posterBitmap;

        TitleItem(String characterId, String group, String title, String yearOrSeason,
                  String appPackage, String link, String posterFile) {
            this.characterId = characterId;
            this.group = group != null ? group.trim() : "";
            this.title = title != null ? title.trim() : "";
            this.yearOrSeason = yearOrSeason != null ? yearOrSeason.trim() : "";
            this.appPackage = appPackage != null ? appPackage.trim() : "";
            this.link = link != null ? link.trim() : "";
            this.posterFile = posterFile != null ? posterFile.trim() : "";
        }
    }

    private static final int CARD_W = 210;
    private static final int CARD_H = 315;
    private static final int CARD_SPACING = 30;
    private static final int CARD_TOP = 330;
    private static final int CORNER_RADIUS = 12;

    private FavouritesLineupView.Character currentCharacter;
    private Bitmap sceneBitmap;
    private final List<TitleItem> items = new ArrayList<>();
    private int focusedIndex = 0;
    private float currentScrollX = 0f;
    private float targetScrollX = 0f;

    private long noLinkNoticeTime = 0L;
    private OnBackListener backListener;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();
    private final Rect srcRect = new Rect();
    private final Rect dstRect = new Rect();

    public FavouritesTitlePageView(Context context) {
        super(context);
        init();
    }

    public FavouritesTitlePageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(0, 0);
    }

    @Override
    public boolean requestRectangleOnScreen(Rect rectangle, boolean immediate) {
        return false;
    }

    public void setOnBackListener(OnBackListener listener) {
        this.backListener = listener;
    }

    public void showForCharacter(FavouritesLineupView.Character character) {
        this.currentCharacter = character;
        this.focusedIndex = 0;
        this.currentScrollX = 0f;
        this.targetScrollX = 0f;
        this.noLinkNoticeTime = 0L;
        loadScene();
        loadTitles();
        updatePosterCache();
        setVisibility(View.VISIBLE);
        requestFocus();
        invalidate();
    }

    public void close() {
        setVisibility(View.GONE);
        clearBitmaps();
    }

    private void clearBitmaps() {
        for (TitleItem item : items) {
            if (item.posterBitmap != null && !item.posterBitmap.isRecycled()) {
                item.posterBitmap.recycle();
                item.posterBitmap = null;
            }
        }
        if (sceneBitmap != null && !sceneBitmap.isRecycled()) {
            sceneBitmap.recycle();
            sceneBitmap = null;
        }
    }

    private void loadScene() {
        if (sceneBitmap != null && !sceneBitmap.isRecycled()) {
            sceneBitmap.recycle();
            sceneBitmap = null;
        }
        if (currentCharacter == null || currentCharacter.sceneFileName == null) return;
        File dir = Config.dir(getContext());
        if (dir == null) return;
        File sceneFile = new File(dir, "favourites/scenes/" + currentCharacter.sceneFileName);
        if (!sceneFile.exists() || !sceneFile.isFile()) return;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            sceneBitmap = BitmapFactory.decodeFile(sceneFile.getAbsolutePath(), opts);
        } catch (Throwable ignored) {
            sceneBitmap = null;
        }
    }

    private void loadTitles() {
        clearBitmaps();
        items.clear();
        if (currentCharacter == null) return;

        File dir = Config.dir(getContext());
        if (dir == null) return;
        File jsonFile = new File(dir, "favourites/titles.json");
        if (!jsonFile.exists() || !jsonFile.isFile()) return;

        try (FileInputStream fis = new FileInputStream(jsonFile)) {
            byte[] data = new byte[(int) jsonFile.length()];
            fis.read(data);
            String jsonStr = new String(data, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(jsonStr);
            JSONArray arr = root.optJSONArray(currentCharacter.id);
            if (arr == null) return;

            PackageManager pm = getContext().getPackageManager();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String pkg = obj.optString("app_package", "").trim();

                // Hide title if app_package is specified and the app is not installed
                if (!pkg.isEmpty()) {
                    try {
                        pm.getPackageInfo(pkg, 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        continue; // Hidden
                    }
                }

                TitleItem item = new TitleItem(
                        obj.optString("character", currentCharacter.id),
                        obj.optString("group", ""),
                        obj.optString("title", ""),
                        obj.optString("year_or_season", ""),
                        pkg,
                        obj.optString("link", "").trim(),
                        obj.optString("poster_file", "").trim()
                );
                items.add(item);
            }
        } catch (Exception ignored) {
        }
    }

    private void updatePosterCache() {
        File dir = Config.dir(getContext());
        File postersDir = dir != null ? new File(dir, "favourites/posters") : null;

        // Keep visible items and one page either side in memory
        int minKeep = Math.max(0, focusedIndex - 6);
        int maxKeep = Math.min(items.size() - 1, focusedIndex + 6);

        for (int i = 0; i < items.size(); i++) {
            TitleItem item = items.get(i);
            if (i < minKeep || i > maxKeep) {
                if (item.posterBitmap != null) {
                    if (!item.posterBitmap.isRecycled()) item.posterBitmap.recycle();
                    item.posterBitmap = null;
                }
            } else {
                if (item.posterBitmap == null && postersDir != null && !item.posterFile.isEmpty()) {
                    File pFile = new File(postersDir, item.posterFile);
                    if (pFile.exists() && pFile.isFile()) {
                        item.posterBitmap = decodePoster(pFile);
                    }
                }
            }
        }
    }

    private Bitmap decodePoster(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, Math.min(bounds.outWidth / 315, bounds.outHeight / 472));
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (w <= 0) w = 1920;
        if (h <= 0) h = 1080;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0) width = 1920;
        if (height <= 0) height = 1080;

        // 1. Draw darkened scene background
        if (sceneBitmap != null && !sceneBitmap.isRecycled()) {
            srcRect.set(0, 0, sceneBitmap.getWidth(), sceneBitmap.getHeight());
            dstRect.set(0, 0, width, height);
            paint.reset();
            paint.setFilterBitmap(true);
            canvas.drawBitmap(sceneBitmap, srcRect, dstRect, paint);
        } else {
            canvas.drawColor(0xFF0F0F14);
        }

        // Subtle dark scrim so posters and text have high contrast while scene remains visible
        canvas.drawColor(0x660A0A0F);

        // 2. Header (Top Left): Franchise kicker and Character Name
        if (currentCharacter != null) {
            textPaint.reset();
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(currentCharacter.color);
            textPaint.setTextSize(14f);
            textPaint.setLetterSpacing(0.18f);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            String franchiseText = (currentCharacter.franchise != null ? currentCharacter.franchise : "MY FAVOURITES").toUpperCase(Locale.US);
            canvas.drawText(franchiseText, 90f, 96f, textPaint);

            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(42f);
            textPaint.setLetterSpacing(0.06f);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            String nameText = currentCharacter.name.toUpperCase(Locale.US);
            canvas.drawText(nameText, 90f, 150f, textPaint);
        }

        if (items.isEmpty()) {
            // Draw empty state
            textPaint.reset();
            textPaint.setAntiAlias(true);
            textPaint.setColor(0x80FFFFFF);
            textPaint.setTextSize(22f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No titles configured for this character", width / 2f, height / 2f, textPaint);
            return;
        }

        // Smooth scroll interpolation
        float diff = targetScrollX - currentScrollX;
        if (Math.abs(diff) > 0.5f) {
            currentScrollX += diff * 0.28f;
            postInvalidateOnAnimation();
        } else {
            currentScrollX = targetScrollX;
        }

        // 3. Draw Posters Row with Group Labels
        int startX = 90;
        int totalContentWidth = items.size() * (CARD_W + CARD_SPACING) - CARD_SPACING;

        canvas.save();
        canvas.translate(-currentScrollX, 0f);

        String lastGroup = null;
        for (int i = 0; i < items.size(); i++) {
            TitleItem item = items.get(i);
            int cardX = startX + i * (CARD_W + CARD_SPACING);
            int cardY = CARD_TOP;
            boolean isFocused = (i == focusedIndex);

            // Group header label above first item in group
            if (!item.group.isEmpty() && !item.group.equals(lastGroup)) {
                lastGroup = item.group;
                textPaint.reset();
                textPaint.setAntiAlias(true);
                textPaint.setTextAlign(Paint.Align.LEFT);
                textPaint.setColor(0xCCFFFFFF);
                textPaint.setTextSize(16f);
                textPaint.setLetterSpacing(0.10f);
                textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                canvas.drawText(item.group.toUpperCase(Locale.US), cardX, cardY - 24f, textPaint);

                // Subtle group line under label
                paint.reset();
                paint.setColor(currentCharacter != null ? currentCharacter.color : 0xFFFFFFFF);
                paint.setAlpha(180);
                canvas.drawRect(cardX, cardY - 14f, cardX + 32f, cardY - 11f, paint);
            }

            // Draw Poster Card
            canvas.save();
            if (isFocused) {
                float scale = 1.08f;
                float cx = cardX + CARD_W / 2f;
                float cy = cardY + CARD_H / 2f;
                canvas.scale(scale, scale, cx, cy);

                // Focused soft glow in character's color
                paint.reset();
                paint.setAntiAlias(true);
                paint.setColor(currentCharacter != null ? currentCharacter.color : 0xFFFFFFFF);
                paint.setAlpha(90);
                rectF.set(cardX - 10, cardY - 10, cardX + CARD_W + 10, cardY + CARD_H + 10);
                canvas.drawRoundRect(rectF, CORNER_RADIUS + 4, CORNER_RADIUS + 4, paint);
            }

            rectF.set(cardX, cardY, cardX + CARD_W, cardY + CARD_H);

            if (item.posterBitmap != null && !item.posterBitmap.isRecycled()) {
                // Draw poster image
                paint.reset();
                paint.setFilterBitmap(true);
                srcRect.set(0, 0, item.posterBitmap.getWidth(), item.posterBitmap.getHeight());
                dstRect.set(cardX, cardY, cardX + CARD_W, cardY + CARD_H);
                canvas.drawBitmap(item.posterBitmap, srcRect, dstRect, paint);

                if (isFocused) {
                    paint.reset();
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3f);
                    paint.setColor(0xFFFFFFFF);
                    canvas.drawRoundRect(rectF, CORNER_RADIUS, CORNER_RADIUS, paint);
                }
            } else {
                // Placeholder dark card
                paint.reset();
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF181822);
                canvas.drawRoundRect(rectF, CORNER_RADIUS, CORNER_RADIUS, paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(isFocused ? 3f : 1.5f);
                paint.setColor(isFocused ? (currentCharacter != null ? currentCharacter.color : 0xFFFFFFFF) : 0xFF2D2D3E);
                canvas.drawRoundRect(rectF, CORNER_RADIUS, CORNER_RADIUS, paint);

                // Title inside placeholder card
                textPaint.reset();
                textPaint.setAntiAlias(true);
                textPaint.setColor(0xFFFFFFFF);
                textPaint.setTextSize(17f);
                textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                textPaint.setTextAlign(Paint.Align.CENTER);

                // Word wrap title into card
                drawWrappedText(canvas, item.title, cardX + CARD_W / 2f, cardY + CARD_H * 0.42f, CARD_W - 24, textPaint);

                // Year or season
                if (!item.yearOrSeason.isEmpty()) {
                    textPaint.setColor(0x99FFFFFF);
                    textPaint.setTextSize(14f);
                    textPaint.setTypeface(Typeface.DEFAULT);
                    canvas.drawText(item.yearOrSeason, cardX + CARD_W / 2f, cardY + CARD_H - 24f, textPaint);
                }
            }

            canvas.restore();
        }
        canvas.restore();

        // 4. Details Under Focused Poster (Pinned on screen for clarity)
        if (focusedIndex >= 0 && focusedIndex < items.size()) {
            TitleItem focused = items.get(focusedIndex);
            float metaY = CARD_TOP + CARD_H + 54f;

            // Title
            textPaint.reset();
            textPaint.setAntiAlias(true);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(26f);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setLetterSpacing(0.02f);
            canvas.drawText(focused.title, 90f, metaY, textPaint);

            // Year or season & App package/logo
            float subY = metaY + 34f;
            textPaint.setColor(0x99FFFFFF);
            textPaint.setTextSize(17f);
            textPaint.setTypeface(Typeface.DEFAULT);
            String subText = focused.yearOrSeason;
            if (!focused.group.isEmpty()) {
                subText = focused.group + (subText.isEmpty() ? "" : " • " + subText);
            }
            canvas.drawText(subText, 90f, subY, textPaint);

            // App Logo or Name
            if (!focused.appPackage.isEmpty()) {
                Drawable logo = AppAdapter.getDockLogo(getContext(), focused.appPackage);
                float logoX = 90f + textPaint.measureText(subText) + 24f;
                if (logo != null) {
                    int logoH = 26;
                    int logoW = (int) (logo.getIntrinsicWidth() * ((float) logoH / Math.max(1, logo.getIntrinsicHeight())));
                    logo.setBounds((int) logoX, (int) (subY - logoH + 4), (int) (logoX + logoW), (int) (subY + 4));
                    logo.draw(canvas);
                } else {
                    String appLabel = AppAdapter.getDockLabel(focused.appPackage, focused.appPackage);
                    textPaint.setColor(currentCharacter != null ? currentCharacter.color : 0xFFFFFFFF);
                    canvas.drawText("•  " + appLabel, logoX, subY, textPaint);
                }
            }
        }

        // 5. "No link yet" State Notification Pill
        long elapsedNotice = SystemClock.uptimeMillis() - noLinkNoticeTime;
        if (elapsedNotice < 2500) {
            float alpha = 1f;
            if (elapsedNotice > 1800) {
                alpha = 1f - (elapsedNotice - 1800) / 700f;
            }
            int pillAlpha = (int) (240 * alpha);
            paint.reset();
            paint.setAntiAlias(true);
            paint.setColor(0xFF1E1E28);
            paint.setAlpha(pillAlpha);
            float pillW = 380f;
            float pillH = 50f;
            float pillX = (width - pillW) / 2f;
            float pillY = height - 90f;
            rectF.set(pillX, pillY, pillX + pillW, pillY + pillH);
            canvas.drawRoundRect(rectF, 25f, 25f, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(currentCharacter != null ? currentCharacter.color : 0xFFFFFFFF);
            paint.setAlpha((int) (200 * alpha));
            canvas.drawRoundRect(rectF, 25f, 25f, paint);

            textPaint.reset();
            textPaint.setAntiAlias(true);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setAlpha((int) (255 * alpha));
            textPaint.setTextSize(16f);
            textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No link yet for this title", width / 2f, pillY + 31f, textPaint);
            postInvalidateOnAnimation();
        }
    }

    private void drawWrappedText(Canvas canvas, String text, float cx, float topY, float maxWidth, TextPaint tp) {
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        float lineY = topY;
        float lineHeight = tp.getTextSize() * 1.3f;

        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (tp.measureText(test) <= maxWidth) {
                line = new StringBuilder(test);
            } else {
                canvas.drawText(line.toString(), cx, lineY, tp);
                line = new StringBuilder(word);
                lineY += lineHeight;
            }
        }
        if (line.length() > 0) {
            canvas.drawText(line.toString(), cx, lineY, tp);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.onKeyDown(keyCode, event);

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (focusedIndex > 0) {
                focusedIndex--;
                onFocusMoved();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (focusedIndex < items.size() - 1) {
                focusedIndex++;
                onFocusMoved();
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (focusedIndex >= 0 && focusedIndex < items.size()) {
                activateTitle(items.get(focusedIndex));
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (backListener != null) {
                backListener.onBack();
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void onFocusMoved() {
        int cardCenter = 90 + focusedIndex * (CARD_W + CARD_SPACING) + CARD_W / 2;
        targetScrollX = Math.max(0, cardCenter - 960);
        updatePosterCache();
        invalidate();
    }

    private void activateTitle(TitleItem item) {
        if (item.link.isEmpty()) {
            noLinkNoticeTime = SystemClock.uptimeMillis();
            invalidate();
            return;
        }

        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.link));
            if (!item.appPackage.isEmpty()) {
                intent.setPackage(item.appPackage);
            }
            getContext().startActivity(intent);
        } catch (Exception e) {
            noLinkNoticeTime = SystemClock.uptimeMillis();
            invalidate();
        }
    }
}
