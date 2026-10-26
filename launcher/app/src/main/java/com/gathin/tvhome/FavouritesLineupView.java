package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Stage 1: My Favourites character lineup and Spider-Sense effect.
 * Renders 7 characters across two rows with real-time black-and-white to color transition,
 * soft color glow, legs fade to black at the canvas bottom, and procedural canvas Spider-Sense lines.
 */
public class FavouritesLineupView extends View {

    public interface OnSelectionChangedListener {
        void onCharacterSelected(Character character);
        void onNobodySelected();
        void onUpToDock();
        void onCharacterActivated(Character character);
    }

    public static class Character {
        public final String id;
        public final String name;
        public final String franchise;
        public final int color;
        public final boolean isFrontRow;
        public final String fileName;
        public final String sceneFileName;
        public int x, y, width, height;
        public float centerX;
        Bitmap bitmap;

        Character(String id, String name, String franchise, int color, boolean isFrontRow,
                  String fileName, String sceneFileName, int x, int y, int width, int height, float centerX) {
            this.id = id;
            this.name = name;
            this.franchise = franchise;
            this.color = color;
            this.isFrontRow = isFrontRow;
            this.fileName = fileName;
            this.sceneFileName = sceneFileName;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.centerX = centerX;
        }
    }

    // Back row: Jack Sparrow, Walter White (middle), Spider-Man
    public final Character[] backRow = new Character[] {
            new Character("jack-sparrow", "Jack Sparrow", "Pirates of the Caribbean",
                    0xFFC9A227, false, "jack-sparrow.png", "jack-sparrow.jpg", 417, 314, 541, 938, 687.5f),
            new Character("walter-white", "Walter White", "Breaking Bad",
                    0xFF3E8E41, false, "walter-white.png", "walter-white.jpg", 740, 215, 534, 705, 1007.0f),
            new Character("spider-man", "Spider-Man", "Spider-Man",
                    0xFFE5484D, false, "spider-man.png", "spider-man.jpg", 1083, 349, 405, 778, 1285.5f)
    };

    // Front row: Saul Goodman, Batman, Tony Stark, John Wick
    public final Character[] frontRow = new Character[] {
            new Character("saul-goodman", "Saul Goodman", "Better Call Saul",
                    0xFF1FB5C9, true, "saul-goodman.png", "saul-goodman.jpg", 234, 595, 387, 490, 427.5f),
            new Character("batman", "Batman", "Batman",
                    0xFF8A8F98, true, "batman.png", "batman.jpg", 510, 529, 568, 703, 794.0f),
            new Character("tony-stark", "Tony Stark", "Iron Man",
                    0xFFE5A100, true, "tony-stark.png", "tony-stark.jpg", 773, 581, 634, 707, 1090.0f),
            new Character("john-wick", "John Wick", "John Wick",
                    0xFFD62839, true, "john-wick.png", "john-wick.jpg", 1296, 573, 390, 518, 1491.0f)
    };

    private static final int ROW_BACK = 0;
    private static final int ROW_FRONT = 1;

    private int selectedRow = -1; // -1 = nobody selected
    private int selectedCol = -1;

    private OnSelectionChangedListener listener;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint();
    private final Paint spiderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final ColorMatrixColorFilter bwFilter;
    private final Random random = new Random();

    // Spider-Sense effect state
    private boolean spiderSenseActive = false;
    private long spiderSenseStart = 0;
    private static final int SPIDER_THUD_MS = 150;
    private static final int SPIDER_FADE_MS = 700;
    private static class TingleLine {
        float startX, startY, endX, endY;
        int color;
        long delayMs;
    }
    private TingleLine[] tingleLines;

    public FavouritesLineupView(Context context) {
        this(context, null);
    }

    public FavouritesLineupView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setFocusableInTouchMode(true);

        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        bwFilter = new ColorMatrixColorFilter(cm);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(36f);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        textPaint.setLetterSpacing(0.18f);

        spiderPaint.setStyle(Paint.Style.STROKE);
        spiderPaint.setStrokeCap(Paint.Cap.ROUND);
        spiderPaint.setStrokeWidth(4.5f);

        Shader fadeShader = new LinearGradient(0, 840, 0, 1080, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
        fadePaint.setShader(fadeShader);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (w <= 0) w = 1920;
        if (h <= 0) h = 1080;
        setMeasuredDimension(w, h);
    }

    public void setListener(OnSelectionChangedListener listener) {
        this.listener = listener;
    }

    public Character getSelectedCharacter() {
        if (selectedRow == ROW_BACK && selectedCol >= 0 && selectedCol < backRow.length) {
            return backRow[selectedCol];
        }
        if (selectedRow == ROW_FRONT && selectedCol >= 0 && selectedCol < frontRow.length) {
            return frontRow[selectedCol];
        }
        return null;
    }

    public void selectNobody() {
        selectedRow = -1;
        selectedCol = -1;
        spiderSenseActive = false;
        if (listener != null) listener.onNobodySelected();
        invalidate();
    }

    public void selectMiddleBackRow() {
        selectCharacter(ROW_BACK, 1); // Walter White
    }

    public void selectCharacter(String id) {
        for (int i = 0; i < backRow.length; i++) {
            if (backRow[i].id.equals(id)) {
                selectCharacter(ROW_BACK, i);
                return;
            }
        }
        for (int i = 0; i < frontRow.length; i++) {
            if (frontRow[i].id.equals(id)) {
                selectCharacter(ROW_FRONT, i);
                return;
            }
        }
    }

    public void selectCharacter(int row, int col) {
        this.selectedRow = row;
        this.selectedCol = col;
        Character selected = getSelectedCharacter();
        if (selected != null && "spider-man".equals(selected.id)) {
            triggerSpiderSense();
        } else {
            spiderSenseActive = false;
        }
        if (listener != null && selected != null) {
            listener.onCharacterSelected(selected);
        }
        invalidate();
    }

    private void triggerSpiderSense() {
        spiderSenseActive = true;
        spiderSenseStart = SystemClock.uptimeMillis();
        int count = 8 + random.nextInt(5); // 8 to 12
        tingleLines = new TingleLine[count];

        Character sm = null;
        for (Character c : backRow) {
            if ("spider-man".equals(c.id)) { sm = c; break; }
        }
        float headCx = (sm != null) ? sm.centerX : 1285.5f;
        float headCy = (sm != null) ? (sm.y + sm.height * 0.16f) : 473f;
        float baseRadius = (sm != null) ? (sm.width * 0.25f) : 100f;

        for (int i = 0; i < count; i++) {
            TingleLine tl = new TingleLine();
            // Radiating angles across top hemisphere (-2.8 to -0.34 rad)
            float angle = -2.8f + (float) i * (2.46f / (float) (count - 1)) + (random.nextFloat() - 0.5f) * 0.15f;
            float r1 = baseRadius + random.nextFloat() * 15f;
            float len = 35f + random.nextFloat() * 35f;
            float r2 = r1 + len;

            tl.startX = headCx + (float) Math.cos(angle) * r1;
            tl.startY = headCy + (float) Math.sin(angle) * r1;
            tl.endX = headCx + (float) Math.cos(angle) * r2;
            tl.endY = headCy + (float) Math.sin(angle) * r2;

            tl.color = random.nextBoolean() ? 0xFFFFFFFF : 0xFFE5484D;
            tl.delayMs = random.nextInt(60);
            tingleLines[i] = tl;
        }
    }

    private long lastJsonMtime = 0;

    public void reloadLineupIfChanged() {
        File dir = Config.dir(getContext());
        if (dir == null) return;
        File file = new File(dir, "favourites/lineup.json");
        if (!file.exists()) return;
        long mtime = file.lastModified();
        if (mtime == lastJsonMtime) return;
        lastJsonMtime = mtime;

        for (Character c : backRow) {
            if (c.bitmap != null && !c.bitmap.isRecycled()) c.bitmap.recycle();
            c.bitmap = null;
        }
        for (Character c : frontRow) {
            if (c.bitmap != null && !c.bitmap.isRecycled()) c.bitmap.recycle();
            c.bitmap = null;
        }

        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            fis.close();
            if (read > 0) {
                JSONObject root = new JSONObject(new String(data, "UTF-8"));
                JSONArray chars = root.optJSONArray("characters");
                if (chars != null) {
                    for (int i = 0; i < chars.length(); i++) {
                        JSONObject obj = chars.getJSONObject(i);
                        String id = obj.optString("id");
                        Character target = findCharacterById(id);
                        if (target != null) {
                            target.x = obj.optInt("x", target.x);
                            target.y = obj.optInt("y", target.y);
                            target.width = obj.optInt("width", target.width);
                            target.height = obj.optInt("height", target.height);
                            target.centerX = (float) obj.optDouble("center_x", target.centerX);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        invalidate();
    }

    private Character findCharacterById(String id) {
        if (id == null) return null;
        for (Character c : backRow) {
            if (id.equals(c.id)) return c;
        }
        for (Character c : frontRow) {
            if (id.equals(c.id)) return c;
        }
        return null;
    }

    private void ensureBitmap(Character c) {
        if (c.bitmap != null) return;
        File dir = Config.dir(getContext());
        if (dir == null) return;
        File file = new File(dir, "favourites/" + c.fileName);
        if (file.exists()) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            c.bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        reloadLineupIfChanged();

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        canvas.save();
        // Scale 1920x1080 design coordinate space to actual view dimensions
        canvas.scale(w / 1920f, h / 1080f);

        Character selected = getSelectedCharacter();

        // 1. Draw soft radial glow behind the group in selected character's color
        if (selected != null) {
            int glowColor = (selected.color & 0x00FFFFFF) | 0x66000000;
            RadialGradient glow = new RadialGradient(selected.centerX, selected.y + 220f, 480f,
                    glowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            glowPaint.setShader(glow);
            canvas.drawCircle(selected.centerX, selected.y + 220f, 480f, glowPaint);
        }

        // 2. Draw Franchise name above the group
        String title = (selected != null) ? selected.franchise.toUpperCase() : "MY FAVOURITES";
        canvas.drawText(title, 960f, 85f, textPaint);

        // Calculate Spider-Sense thud animation
        float spideyThudY = 0f;
        long elapsed = spiderSenseActive ? (SystemClock.uptimeMillis() - spiderSenseStart) : 0;
        if (spiderSenseActive && selected != null && "spider-man".equals(selected.id)) {
            if (elapsed < SPIDER_THUD_MS) {
                // Drop ~12px and settle back at 150ms
                spideyThudY = 12f * (float) Math.sin((elapsed / (float) SPIDER_THUD_MS) * Math.PI);
            }
        }

        // 3. Draw Characters with correct Layering:
        // Rule: Back-row characters grow but stay behind front row.
        // Selected front-row character steps in front of everyone.
        if (selected != null && selected.isFrontRow) {
            // Draw all back row characters (b&w)
            for (Character c : backRow) drawCharacter(canvas, c, false, 0f);
            // Draw unselected front row characters (b&w)
            for (Character c : frontRow) {
                if (c != selected) drawCharacter(canvas, c, false, 0f);
            }
            // Draw selected front row character (color, scaled, front-most)
            drawCharacter(canvas, selected, true, 0f);
        } else if (selected != null) {
            // Selected is back row:
            // Draw unselected back row characters (b&w)
            for (Character c : backRow) {
                if (c != selected) drawCharacter(canvas, c, false, 0f);
            }
            // Draw selected back row character (color, scaled, stays behind front row)
            float thud = "spider-man".equals(selected.id) ? spideyThudY : 0f;
            drawCharacter(canvas, selected, true, thud);
            // Draw all front row characters (b&w, on top of back row)
            for (Character c : frontRow) drawCharacter(canvas, c, false, 0f);
        } else {
            // Nobody selected: draw all b&w
            for (Character c : backRow) drawCharacter(canvas, c, false, 0f);
            for (Character c : frontRow) drawCharacter(canvas, c, false, 0f);
        }

        // 4. Spider-Sense radiating tingle lines (burst around Spider-Man's head, fades within 700ms)
        if (spiderSenseActive && selected != null && "spider-man".equals(selected.id) && tingleLines != null) {
            if (elapsed < SPIDER_FADE_MS) {
                float fadeAlpha = Math.max(0f, 1f - (elapsed / (float) SPIDER_FADE_MS));
                for (TingleLine tl : tingleLines) {
                    if (elapsed >= tl.delayMs) {
                        int baseA = Color.alpha(tl.color);
                        int lineA = (int) (baseA * fadeAlpha);
                        spiderPaint.setColor((tl.color & 0x00FFFFFF) | (lineA << 24));
                        canvas.drawLine(tl.startX, tl.startY + spideyThudY, tl.endX, tl.endY + spideyThudY, spiderPaint);
                    }
                }
                postInvalidateOnAnimation();
            } else {
                spiderSenseActive = false;
            }
        }

        // 5. Fade characters' legs into black at the bottom
        canvas.drawRect(0, 840, 1920, 1080, fadePaint);

        canvas.restore();
    }

    private void drawCharacter(Canvas canvas, Character c, boolean isSelected, float extraY) {
        ensureBitmap(c);
        if (c.bitmap == null) return;

        if (isSelected) {
            bitmapPaint.setColorFilter(null);
            canvas.save();
            float scale = 1.08f;
            float pivotX = c.centerX;
            float pivotY = c.y + c.height * 0.65f;
            canvas.scale(scale, scale, pivotX, pivotY);
            canvas.drawBitmap(c.bitmap, c.x, c.y + extraY, bitmapPaint);
            canvas.restore();
        } else {
            bitmapPaint.setColorFilter(bwFilter);
            canvas.drawBitmap(c.bitmap, c.x, c.y + extraY, bitmapPaint);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.onKeyDown(keyCode, event);

        if (selectedRow == -1) {
            // First activation
            selectMiddleBackRow();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (selectedCol > 0) {
                selectCharacter(selectedRow, selectedCol - 1);
            }
            return true; // Stop at end
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int maxCol = (selectedRow == ROW_BACK) ? backRow.length - 1 : frontRow.length - 1;
            if (selectedCol < maxCol) {
                selectCharacter(selectedRow, selectedCol + 1);
            }
            return true; // Stop at end
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (selectedRow == ROW_BACK) {
                // Return to dock
                if (listener != null) listener.onUpToDock();
                return true;
            }
            // Front row -> Back row (nearest horizontal center)
            Character current = getSelectedCharacter();
            int bestCol = 0;
            float minDiff = Float.MAX_VALUE;
            for (int i = 0; i < backRow.length; i++) {
                float diff = Math.abs(backRow[i].centerX - current.centerX);
                if (diff < minDiff) {
                    minDiff = diff;
                    bestCol = i;
                }
            }
            selectCharacter(ROW_BACK, bestCol);
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (selectedRow == ROW_BACK) {
                // Back row -> Front row (nearest horizontal center)
                Character current = getSelectedCharacter();
                int bestCol = 0;
                float minDiff = Float.MAX_VALUE;
                for (int i = 0; i < frontRow.length; i++) {
                    float diff = Math.abs(frontRow[i].centerX - current.centerX);
                    if (diff < minDiff) {
                        minDiff = diff;
                        bestCol = i;
                    }
                }
                selectCharacter(ROW_FRONT, bestCol);
                return true;
            }
            // Front row: stop at bottom
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            Character current = getSelectedCharacter();
            if (current != null && listener != null) {
                listener.onCharacterActivated(current);
                return true;
            }
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (SystemClock.uptimeMillis() - MainActivity.lastTitlePageCloseTime < 450) {
                return true;
            }
            if (listener != null) listener.onUpToDock();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}
