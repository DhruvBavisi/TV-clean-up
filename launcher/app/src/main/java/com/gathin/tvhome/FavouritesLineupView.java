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
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;

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

    // Back row: Saul Goodman, Walter White (middle), Spider-Man
    public final Character[] backRow = new Character[] {
            new Character("saul-goodman", "Saul Goodman", "Better Call Saul",
                    0xFF1FB5C9, false, "saul-goodman.png", "saul-goodman.jpg", 426, 357, 427, 613, 639.5f),
            new Character("walter-white", "Walter White", "Breaking Bad",
                    0xFF3E8E41, false, "walter-white.png", "walter-white.jpg", 750, 215, 534, 705, 1017.0f),
            new Character("spider-man", "Spider-Man", "Spider-Man",
                    0xFFE5484D, false, "spider-man.png", "spider-man.jpg", 1155, 347, 389, 733, 1349.5f)
    };

    // Front row: Jack Sparrow, Batman, Tony Stark, John Wick
    public final Character[] frontRow = new Character[] {
            new Character("jack-sparrow", "Jack Sparrow", "Pirates of the Caribbean",
                    0xFFC9A227, true, "jack-sparrow.png", "jack-sparrow.jpg", 184, 639, 399, 441, 383.5f),
            new Character("batman", "Batman", "Batman",
                    0xFF8A8F98, true, "batman.png", "batman.jpg", 535, 583, 492, 497, 781.0f),
            new Character("tony-stark", "Tony Stark", "Iron Man",
                    0xFFE5A100, true, "tony-stark.png", "tony-stark.jpg", 946, 618, 443, 462, 1167.5f),
            new Character("john-wick", "John Wick", "John Wick",
                    0xFFD62839, true, "john-wick.png", "john-wick.jpg", 1372, 637, 364, 443, 1554.0f)
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

    private final ColorMatrixColorFilter bwFilter;

    // --- Animation clock and helpers (Step 2) ---
    private long selectAnimStart = 0;        // uptimeMillis at selection
    private String prevTitle = null;

    private static float win(float t, float a, float b) {
        float v = (t - a) / (b - a);
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
    private static float easeOut3(float x) { float u = 1f - x; return 1f - u * u * u; }
    private static float easeOut2(float x) { float u = 1f - x; return 1f - u * u; }

    // --- Squiggle geometry (Step 4a) ---
    private static final float HEAD_W = 170f;

    // --- Squiggle table (Step 4b) ---
    // Columns: cx, cy (head widths from head-top centre, y down), length (head widths),
    // angleDeg (direction base to tip, 0=right, 90=down, -90=up), cycles,
    // inStart, inEnd, outStart, outEnd (seconds), wavePhase, swayPhase
    private static final float[][] SQ = {
     /*A*/ {-0.54f, 0.06f, 0.37f, -125f, 1.6f, 0.14f, 0.52f, 0.75f, 1.35f, 0.0f, 0.7f},
     /*B*/ {-0.21f,-0.11f, 0.23f, -100f, 1.5f, 0.09f, 0.42f, 0.72f, 1.30f, 1.3f, 2.1f},
     /*C*/ { 0.18f,-0.12f, 0.27f,  -80f, 1.5f, 0.09f, 0.42f, 0.72f, 1.30f, 2.6f, 0.2f},
     /*D*/ { 0.57f, 0.11f, 0.32f,  -45f, 1.6f, 0.14f, 0.52f, 0.75f, 1.33f, 3.9f, 1.5f},
     /*E*/ { 0.79f, 0.58f, 0.33f,  -35f, 1.6f, 0.24f, 0.57f, 0.75f, 1.33f, 5.0f, 2.8f},
     /*F*/ {-0.90f, 0.88f, 0.33f,  172f, 1.6f, 0.24f, 0.57f, 0.75f, 1.33f, 0.9f, 0.9f},
     /*G*/ {-0.81f, 1.43f, 0.20f,  175f, 1.5f, 0.28f, 0.57f, 0.75f, 1.33f, 2.2f, 1.9f},
     /*H*/ { 0.79f, 1.25f, 0.18f,  -10f, 1.5f, 0.28f, 0.57f, 0.75f, 1.33f, 3.3f, 0.4f}
    };

    // --- Squiggle paints (Step 4c) ---
    private final Paint sqFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sqHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path sqPath = new Path();

    // Reusable ColorMatrix for bloom animation
    private final ColorMatrix bloomMatrix = new ColorMatrix();

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

        sqHalo.setStyle(Paint.Style.STROKE);
        sqHalo.setStrokeCap(Paint.Cap.ROUND);
        sqHalo.setStrokeJoin(Paint.Join.ROUND);

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
        selectAnimStart = 0;
        prevTitle = null;
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
        if (this.selectedRow == row && this.selectedCol == col) return;
        // Capture previous title before overwriting
        Character previous = getSelectedCharacter();
        prevTitle = (previous != null) ? previous.franchise.toUpperCase() : "MY FAVOURITES";

        this.selectedRow = row;
        this.selectedCol = col;
        selectAnimStart = SystemClock.uptimeMillis();

        Character selected = getSelectedCharacter();
        if (listener != null && selected != null) {
            listener.onCharacterSelected(selected);
        }
        invalidate();
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

        // --- Compute animation time once (Step 2c) ---
        float t = (selected == null) ? 99f
            : (SystemClock.uptimeMillis() - selectAnimStart) / 1000f;

        boolean isSpider = selected != null && "spider-man".equals(selected.id);

        float settle = (selected != null) ? 0.96f + 0.04f * easeOut3(win(t, 0f, 0.75f)) : 1f;
        canvas.save();
        canvas.scale(settle, settle, 960f, 1080f);

        // 1. Draw soft radial glow behind the group in selected character's color
        if (selected != null) {
            int glowColor = (selected.color & 0x00FFFFFF) | 0x66000000;
            RadialGradient glow = new RadialGradient(selected.centerX, selected.y + 220f, 480f,
                    glowColor, Color.TRANSPARENT, Shader.TileMode.CLAMP);
            glowPaint.setShader(glow);
            canvas.drawCircle(selected.centerX, selected.y + 220f, 480f, glowPaint);
        }

        // 2. Draw Franchise name above the group --- TITLE CROSSFADE (Step 3c) ---
        String title = (selected != null) ? selected.franchise.toUpperCase() : "MY FAVOURITES";
        float p = win(t, 0f, 0.17f);
        if (prevTitle != null && !prevTitle.equals(title) && p < 1f) {
            textPaint.setAlpha((int) (255 * (1f - p)));
            canvas.drawText(prevTitle, 960f, 85f, textPaint);
            textPaint.setAlpha((int) (255 * p));
            canvas.drawText(title, 960f, 85f, textPaint);
            textPaint.setAlpha(255);
        } else {
            canvas.drawText(title, 960f, 85f, textPaint);
        }

        // 3. Draw Characters with correct Layering:
        // Rule: Back-row characters grow but stay behind front row.
        // Selected front-row character steps in front of everyone.
        if (selected != null && selected.isFrontRow) {
            // Draw all back row characters (b&w)
            for (Character c : backRow) drawCharacter(canvas, c, false, t);
            // Draw unselected front row characters (b&w)
            for (Character c : frontRow) {
                if (c != selected) drawCharacter(canvas, c, false, t);
            }
            // Draw selected front row character (color, scaled, front-most)
            drawCharacter(canvas, selected, true, t);
        } else if (selected != null) {
            // Selected is back row:
            // Draw unselected back row characters (b&w)
            for (Character c : backRow) {
                if (c != selected) drawCharacter(canvas, c, false, t);
            }
            // Draw selected back row character (color, scaled, stays behind front row)
            drawCharacter(canvas, selected, true, t);
            // Draw all front row characters (b&w, on top of back row)
            for (Character c : frontRow) drawCharacter(canvas, c, false, t);
        } else {
            // Nobody selected: draw all b&w
            for (Character c : backRow) drawCharacter(canvas, c, false, t);
            for (Character c : frontRow) drawCharacter(canvas, c, false, t);
        }

        // 4. Spider-Sense squiggles (Step 4e) — only for Spider-Man, inside the 1.08 scale
        if (isSpider && selected != null) {
            canvas.save();
            canvas.scale(1.08f, 1.08f, selected.centerX, selected.y + selected.height * 0.65f);
            drawSquiggles(canvas, selected, t);
            canvas.restore();
        }

        // 5. Fade characters' legs into black at the bottom
        canvas.drawRect(0, 840, 1920, 1080, fadePaint);

        canvas.restore(); // settle scale

        canvas.restore(); // 1920x1080 scale

        // --- KEEP DRAWING (Step 3d) ---
        float animEnd = isSpider ? 1.45f : 0.80f;
        if (selected != null && t < animEnd) {
            postInvalidateOnAnimation();
        }
    }

    // --- COLOUR BLOOM + drawCharacter (Step 3b) ---
    private void drawCharacter(Canvas canvas, Character c, boolean isSelected, float t) {
        ensureBitmap(c);
        if (c.bitmap == null) return;

        if (isSelected) {
            float bloom = easeOut2(win(t, 0f, 0.65f));
            if (bloom < 1f) {
                bloomMatrix.setSaturation(bloom);
                bitmapPaint.setColorFilter(new ColorMatrixColorFilter(bloomMatrix));
            } else {
                bitmapPaint.setColorFilter(null);
            }
            canvas.save();
            canvas.scale(1.08f, 1.08f, c.centerX, c.y + c.height * 0.65f);
            canvas.drawBitmap(c.bitmap, c.x, c.y, bitmapPaint);
            canvas.restore();
            bitmapPaint.setColorFilter(null);
        } else {
            bitmapPaint.setColorFilter(bwFilter);
            canvas.drawBitmap(c.bitmap, c.x, c.y, bitmapPaint);
            bitmapPaint.setColorFilter(null);
        }
    }

    // --- drawSquiggles (Step 4d) ---
    private void drawSquiggles(Canvas canvas, Character sm, float t) {
        float ox = sm.x + 243f;
        float oy = sm.y;

        for (float[] q : SQ) {
            float grow   = easeOut2(win(t, q[5], q[6]));
            float shrink = (float) Math.pow(win(t, q[7], q[8]), 1.3);
            float vis    = grow * (1f - shrink);        // fraction drawn
            if (vis < 0.02f) continue;
            float alpha  = easeOut2(win(t, q[5], q[5] + 0.10f)) * (1f - shrink);
            float len    = q[2] * HEAD_W;

            // base from the UNSWAYED direction, then sway the direction
            float a0 = (float) Math.toRadians(q[3]);
            float cx = ox + q[0] * HEAD_W;
            float cy = oy + q[1] * HEAD_W;
            float bx = cx - (float) Math.cos(a0) * len / 2f;
            float by = cy - (float) Math.sin(a0) * len / 2f;
            float ang = a0 + (float) (0.05236 * Math.sin(2.61799 * t + q[10]));
            float dx = (float) Math.cos(ang);
            float dy = (float) Math.sin(ang);
            float nx = -dy;
            float ny = dx;
            float amp = 0.20f * len;
            float phase = (float) (Math.PI * t) + q[9];
            float wmax = 4.25f;

            int N = 28;
            sqPath.reset();
            for (int k = 0; k <= N; k++) {
                float u = k / (float) N;
                float f = u * vis;
                float wave = amp * (float) Math.sin(2 * Math.PI * q[4] * f - phase);
                float px = bx + dx * f * len + nx * wave;
                float py = by + dy * f * len + ny * wave;
                float w = wmax * (float) Math.pow(Math.sin(Math.PI * u), 0.7);
                float lx = px + nx * w / 2f;
                float ly = py + ny * w / 2f;
                if (k == 0) sqPath.moveTo(lx, ly);
                else sqPath.lineTo(lx, ly);
            }
            for (int k = N; k >= 0; k--) {
                float u = k / (float) N;
                float f = u * vis;
                float wave = amp * (float) Math.sin(2 * Math.PI * q[4] * f - phase);
                float px = bx + dx * f * len + nx * wave;
                float py = by + dy * f * len + ny * wave;
                float w = wmax * (float) Math.pow(Math.sin(Math.PI * u), 0.7);
                sqPath.lineTo(px - nx * w / 2f, py - ny * w / 2f);
            }
            sqPath.close();

            // Halo pass: width x2.2 effect via slightly larger path (18% of core alpha)
            int haloAlpha = (int) (45.9f * alpha);
            if (haloAlpha > 0) {
                sqHalo.setColor(Color.argb(haloAlpha, 0xD9, 0xC8, 0xFF));
                sqHalo.setStrokeWidth(wmax * 2.2f);
                canvas.drawPath(sqPath, sqHalo);
            }

            // Core pass: solid white #FFFFFF
            int coreAlpha = (int) (255 * alpha);
            sqFill.setColor(Color.argb(coreAlpha, 255, 255, 255));
            canvas.drawPath(sqPath, sqFill);
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
