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

import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

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
        default void onNavigateRightToSecondPage(int currentRow) {}
    }

    public static class GlowConfig {
        public final String id;
        public int color;
        public float radius;
        public float peakAlpha;
        public float headX;
        public float headY;
        public float offsetX;
        public float offsetY;

        public GlowConfig(String id, int color, float radius, float peakAlpha,
                          float headX, float headY, float offsetX, float offsetY) {
            this.id = id;
            this.color = color;
            this.radius = radius;
            this.peakAlpha = peakAlpha;
            this.headX = headX;
            this.headY = headY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        public float getCenterX() { return headX + offsetX; }
        public float getCenterY() { return headY + offsetY; }
    }

    public static final Map<String, GlowConfig> GLOW_CONFIGS = new LinkedHashMap<>();
    static {
        // Tunable per-character config: Color, radius in px on 1920x1080, peakAlpha (luminance-compensated),
        // head center coordinates derived from PSD cutout alpha channels, and fine offsets.
        GLOW_CONFIGS.put("walter-white", new GlowConfig("walter-white", 0xFF2E9B47, 800f, 0.15f, 977f, 302f, 0f, 40f));
        GLOW_CONFIGS.put("saul-goodman", new GlowConfig("saul-goodman", 0xFFE8A317, 720f, 0.13f, 636f, 433f, 0f, 40f));
        GLOW_CONFIGS.put("jack-sparrow", new GlowConfig("jack-sparrow", 0xFF1E6585, 680f, 0.16f, 832f, 659f, 0f, 30f)); // Deep blue sea
        GLOW_CONFIGS.put("spider-man",  new GlowConfig("spider-man",  0xFFE62429, 800f, 0.22f, 1353f, 442f, 0f, 40f));
        GLOW_CONFIGS.put("john-wick",    new GlowConfig("john-wick",    0xFFA60F14, 700f, 0.22f, 1580f, 693f, 0f, 30f)); // Bloody red
        GLOW_CONFIGS.put("batman",       new GlowConfig("batman",       0xFF3B5B8C, 740f, 0.14f, 450f, 662f, 0f, 30f));
        GLOW_CONFIGS.put("tony-stark",   new GlowConfig("tony-stark",   0xFF00BFFF, 720f, 0.15f, 1206f, 675f, 0f, 30f)); // Tech blue
    }

    // Spec falloff curve:
    // 0.00-0.20R -> 1.00; 0.40R -> 0.67; 0.52R -> 0.56; 0.65R -> 0.37; 0.87R -> 0.10; 1.00R -> 0.00
    private static final float[] U_PTS = { 0.0f, 0.20f, 0.40f, 0.52f, 0.65f, 0.87f, 1.00f };
    private static final float[] Y_PTS = { 1.0f, 1.00f, 0.67f, 0.56f, 0.37f, 0.10f, 0.00f };
    private static final float[] FALLOFF_LUT = new float[1001];
    static {
        for (int i = 0; i <= 1000; i++) {
            float u = i / 1000.0f;
            if (u <= 0.20f) {
                FALLOFF_LUT[i] = 1.0f;
            } else if (u >= 1.0f) {
                FALLOFF_LUT[i] = 0.0f;
            } else {
                int seg = 0;
                while (seg < U_PTS.length - 1 && u > U_PTS[seg + 1]) {
                    seg++;
                }
                float u0 = U_PTS[seg];
                float u1 = U_PTS[seg + 1];
                float y0 = Y_PTS[seg];
                float y1 = Y_PTS[seg + 1];
                float s = (u - u0) / (u1 - u0);
                float w = s * s * (3f - 2f * s);
                FALLOFF_LUT[i] = y0 + (y1 - y0) * w;
            }
        }
    }

    public static class Character {
        public final String id;
        public final String name;
        public final String franchise;
        public final String title;
        public final String logoName;
        public final int color;
        public final boolean isFrontRow;
        public final String fileName;
        public final String sceneFileName;
        public final GlowConfig glowConfig;
        public int x, y, width, height;
        public float centerX;
        Bitmap bitmap;

        Character(String id, String name, String franchise, String title, String logoName,
                  int color, boolean isFrontRow,
                  String fileName, String sceneFileName, int x, int y, int width, int height, float centerX) {
            this.id = id;
            this.name = name;
            this.franchise = franchise;
            this.title = title;
            this.logoName = logoName;
            this.color = color;
            this.isFrontRow = isFrontRow;
            this.fileName = fileName;
            this.sceneFileName = sceneFileName;
            this.glowConfig = GLOW_CONFIGS.get(id);
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.centerX = centerX;
        }
    }

    // Back row: Saul Goodman, Walter White (middle), Spider-Man
    public final Character[] backRow = new Character[] {
            new Character("saul-goodman", "Saul Goodman", "Better Call Saul", "Better Call Saul", "Netflix.png",
                    0xFFFFB800, false, "saul-goodman.png", "saul-goodman.jpg", 425, 357, 427, 613, 638.5f),
            new Character("walter-white", "Walter White", "Breaking Bad", "Breaking Bad", "Netflix.png",
                    0xFF3E8E41, false, "walter-white.png", "walter-white.jpg", 733, 215, 534, 705, 1000.0f),
            new Character("spider-man", "Spider-Man", "Spider-Man", "Spider-Man: No Way Home", "Apple.png",
                    0xFFE5484D, false, "spider-man.png", "spider-man.jpg", 1112, 355, 385, 725, 1304.5f)
    };

    // Front row: Batman, Jack Sparrow, Tony Stark, John Wick
    public final Character[] frontRow = new Character[] {
            new Character("batman", "Batman", "The Dark Knight", "The Dark Knight", "Prime.png",
                    0xFFD2D8E0, true, "batman.png", "batman.jpg", 230, 591, 483, 489, 471.5f),
            new Character("jack-sparrow", "Jack Sparrow", "Pirates of the Caribbean", "Pirates of the Caribbean", "JioHotstar.png",
                    0xFF1E6585, true, "jack-sparrow.png", "jack-sparrow.jpg", 651, 592, 345, 488, 823.5f),
            new Character("tony-stark", "Tony Stark", "Iron Man", "Iron Man", "JioHotstar.png",
                    0xFF00BFFF, true, "tony-stark.png", "tony-stark.jpg", 943, 618, 443, 462, 1164.5f),
            new Character("john-wick", "John Wick", "John Wick", "John Wick", "Prime.png",
                    0xFFA60F14, true, "john-wick.png", "john-wick.jpg", 1326, 637, 364, 443, 1508.0f)
    };

    private static final int ROW_BACK = 0;
    private static final int ROW_FRONT = 1;

    private int selectedRow = -1; // -1 = nobody selected
    private int selectedCol = -1;

    private OnSelectionChangedListener listener;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
    // Note: outStart and outEnd reduced by 0.30s per user request
    private static final float[][] SQ = {
     /*A*/ {-0.54f, 0.06f, 0.37f, -125f, 1.6f, 0.14f, 0.52f, 0.95f, 1.55f, 0.0f, 0.7f},
     /*B*/ {-0.21f,-0.11f, 0.23f, -100f, 1.5f, 0.09f, 0.42f, 0.92f, 1.50f, 1.3f, 2.1f},
     /*C*/ { 0.18f,-0.12f, 0.27f,  -80f, 1.5f, 0.09f, 0.42f, 0.92f, 1.50f, 2.6f, 0.2f},
     /*D*/ { 0.57f, 0.11f, 0.32f,  -45f, 1.6f, 0.14f, 0.52f, 0.95f, 1.53f, 3.9f, 1.5f},
     /*E*/ { 0.79f, 0.58f, 0.33f,  -35f, 1.6f, 0.24f, 0.57f, 0.95f, 1.53f, 5.0f, 2.8f},
     /*F*/ {-0.90f, 0.88f, 0.33f,  172f, 1.6f, 0.24f, 0.57f, 0.95f, 1.53f, 0.9f, 0.9f},
     /*G*/ {-0.81f, 1.43f, 0.20f,  175f, 1.5f, 0.28f, 0.57f, 0.95f, 1.53f, 2.2f, 1.9f},
     /*H*/ { 0.79f, 1.25f, 0.18f,  -10f, 1.5f, 0.28f, 0.57f, 0.95f, 1.53f, 3.3f, 0.4f}
    };

    // --- Squiggle paints (Step 4c) ---
    private final Paint sqFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sqHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path sqPath = new Path();

    // Reusable ColorMatrix for bloom animation
    private final ColorMatrix bloomMatrix = new ColorMatrix();

    // Logo cache for streaming app badges
    private final Map<String, Bitmap> logoCache = new HashMap<>();
    private final RectF dstLogoRect = new RectF();

    private Bitmap getLogo(String logoName) {
        if (logoName == null || logoName.isEmpty()) return null;
        if (logoCache.containsKey(logoName)) return logoCache.get(logoName);
        File dir = Config.dir(getContext());
        if (dir == null) return null;
        File file = new File(dir, "favourites/logos/" + logoName);
        if (!file.exists()) return null;
        Bitmap b = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (b != null) logoCache.put(logoName, b);
        return b;
    }

    // Total glow memory: 7 * 1920 * 1080 * 1 byte = 14,515,200 bytes (13.84 MB), strictly under 15 MB
    private final Map<String, Bitmap> glowMasks = new ConcurrentHashMap<>();
    private volatile Bitmap displayedGlowMask = null;
    private volatile GlowConfig displayedGlowConfig = null;

    private void initGlowMasksBackground() {
        Thread thread = new Thread(() -> {
            // Priority 1: build first-focused character: Walter White (breaking-bad)
            buildGlowMask("walter-white");
            // Priority 2: build remaining 6 characters
            for (String id : GLOW_CONFIGS.keySet()) {
                if (!"walter-white".equals(id)) {
                    buildGlowMask(id);
                }
            }
        }, "GlowMaskBuilder");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.setDaemon(true);
        thread.start();
    }

    private void buildGlowMask(String id) {
        GlowConfig cfg = GLOW_CONFIGS.get(id);
        if (cfg == null) return;
        int w = 1920;
        int h = 1080;
        float cx = cfg.getCenterX();
        float cy = cfg.getCenterY();
        float R = cfg.radius;
        float R2 = R * R;
        float invR = 1.0f / R;

        int minX = Math.max(0, (int) Math.floor(cx - R));
        int maxX = Math.min(w, (int) Math.ceil(cx + R) + 1);
        int minY = Math.max(0, (int) Math.floor(cy - R));
        int maxY = Math.min(h, (int) Math.ceil(cy + R) + 1);

        byte[] pixels = new byte[w * h];
        Random rnd = new Random(id.hashCode());

        for (int y = minY; y < maxY; y++) {
            float dy = y - cy;
            float dy2 = dy * dy;
            int rowOffset = y * w;
            for (int x = minX; x < maxX; x++) {
                float dx = x - cx;
                float d2 = dx * dx + dy2;
                if (d2 < R2) {
                    float d = (float) Math.sqrt(d2);
                    float u = d * invR;
                    int lutIdx = (int) (u * 1000f);
                    if (lutIdx > 1000) lutIdx = 1000;
                    float f = FALLOFF_LUT[lutIdx];
                    // 1.5% amplitude dithering noise
                    float noise = (rnd.nextFloat() - 0.5f) * 4.0f;
                    int val = Math.max(0, Math.min(255, Math.round(f * 255f + noise)));
                    pixels[rowOffset + x] = (byte) val;
                }
            }
        }

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8);
        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
        glowMasks.put(id, bmp);

        post(() -> {
            Character sel = getSelectedCharacter();
            if (sel != null && id.equals(sel.id)) {
                displayedGlowMask = bmp;
                displayedGlowConfig = cfg;
                invalidate();
            }
        });
    }

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

        Typeface mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(46f);
        textPaint.setTypeface(mediumTypeface);
        textPaint.setLetterSpacing(0.01f);

        headerPaint.setColor(0xB3FFFFFF);
        headerPaint.setTextAlign(Paint.Align.LEFT);
        headerPaint.setTextSize(26f);
        headerPaint.setTypeface(mediumTypeface);
        headerPaint.setLetterSpacing(0.02f);

        sqHalo.setStyle(Paint.Style.STROKE);
        sqHalo.setStrokeCap(Paint.Cap.ROUND);
        sqHalo.setStrokeJoin(Paint.Join.ROUND);

        Shader fadeShader = new LinearGradient(0, 840, 0, 1080, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
        fadePaint.setShader(fadeShader);

        initGlowMasksBackground();
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

    public int getSelectedRow() {
        return selectedRow;
    }

    public int getSelectedCol() {
        return selectedCol;
    }

    public void selectRightmost(int targetRow) {
        if (targetRow == ROW_BACK) {
            selectCharacter(ROW_BACK, backRow.length - 1);
        } else {
            selectCharacter(ROW_FRONT, frontRow.length - 1);
        }
    }

    public void selectNobody() {
        selectedRow = -1;
        selectedCol = -1;
        selectAnimStart = 0;
        prevTitle = null;
        displayedGlowMask = null;
        displayedGlowConfig = null;
        if (listener != null) listener.onNobodySelected();
        invalidate();
    }

    public void selectMiddleBackRow() {
        selectMiddleBackRow(false);
    }

    public void selectMiddleBackRow(boolean force) {
        selectCharacter(ROW_BACK, 1, force); // Walter White
    }

    public void selectCharacter(String id) {
        for (int i = 0; i < backRow.length; i++) {
            if (backRow[i].id.equals(id)) {
                selectCharacter(ROW_BACK, i, false);
                return;
            }
        }
        for (int i = 0; i < frontRow.length; i++) {
            if (frontRow[i].id.equals(id)) {
                selectCharacter(ROW_FRONT, i, false);
                return;
            }
        }
    }

    public void selectCharacter(int row, int col) {
        selectCharacter(row, col, false);
    }

    public void selectCharacter(int row, int col, boolean force) {
        if (!force && this.selectedRow == row && this.selectedCol == col) return;
        Character previous = getSelectedCharacter();
        prevTitle = (previous != null) ? (previous.title != null ? previous.title : previous.franchise) : "My Favourites";

        this.selectedRow = row;
        this.selectedCol = col;
        selectAnimStart = SystemClock.uptimeMillis();

        Character selected = getSelectedCharacter();
        if (selected != null) {
            GlowConfig cfg = selected.glowConfig;
            if (cfg != null) {
                displayedGlowConfig = cfg;
                Bitmap mask = glowMasks.get(selected.id);
                if (mask != null) {
                    displayedGlowMask = mask;
                }
                // If mask not yet finished generating on background thread, keep previous mask
            }
        } else {
            displayedGlowMask = null;
            displayedGlowConfig = null;
        }

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

        // Top-left persistent section header (matching reference design)
        canvas.drawText("My Favourites", 72f, 60f, headerPaint);

        // 1. Draw active character glow behind that character, on the background layer, above background image, below cutouts and text
        if (displayedGlowMask != null && displayedGlowConfig != null) {
            int color = displayedGlowConfig.color & 0x00FFFFFF;
            int alpha = (int) (255f * displayedGlowConfig.peakAlpha);
            glowPaint.setColor((alpha << 24) | color);
            glowPaint.setDither(true);
            glowPaint.setFilterBitmap(true);
            canvas.drawBitmap(displayedGlowMask, 0f, 0f, glowPaint);
        }

        // 2. Draw Franchise/Movie Title & Streaming Service Logo above the selected character's area
        if (selected != null) {
            String title = selected.title != null ? selected.title : selected.franchise;
            Bitmap logo = getLogo(selected.logoName);

            float titleAlpha = easeOut2(win(t, 0f, 0.20f));
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setAlpha((int) (255 * titleAlpha));
            float textW = textPaint.measureText(title);

            float logoW = 0f;
            float logoH = 0f;
            float logoGap = 16f;
            if (logo != null && logo.getWidth() > 0 && logo.getHeight() > 0) {
                logoH = 40f;
                logoW = logo.getWidth() * (logoH / logo.getHeight());
            }

            float totalW = textW + (logo != null ? (logoGap + logoW) : 0f);

            // Center title and logo compound block along the character's vertical head axis,
            // while keeping safe margins inside TV boundaries (at least 80px from screen edges)
            float charAxis = (selected.glowConfig != null) ? selected.glowConfig.getCenterX() : selected.centerX;
            float startX = charAxis - totalW / 2f;
            float minMargin = 80f;
            float maxRight = 1920f - minMargin;
            if (startX < minMargin) {
                startX = minMargin;
            } else if (startX + totalW > maxRight) {
                startX = maxRight - totalW;
            }

            // Vertical placement tailored to available headroom:
            // - Corner characters (Batman & John Wick in second row) have vast open headroom: titleY = 200f
            // - Mid-flank characters with ample headroom (Tony Stark in second row, Saul Goodman in back row): titleY = 150f
            // - Central & wide-spanning characters (Walter White hat apex ~y=178, Jack Sparrow & Spider-Man): titleY = 115f
            float titleY = 115f;
            if ("batman".equals(selected.id) || "john-wick".equals(selected.id)) {
                titleY = 200f;
            } else if ("tony-stark".equals(selected.id) || "saul-goodman".equals(selected.id)) {
                titleY = 150f;
            }
            canvas.drawText(title, startX, titleY, textPaint);

            if (logo != null) {
                float lx = startX + textW + logoGap;
                float ly = titleY - logoH * 0.88f;
                dstLogoRect.set(lx, ly, lx + logoW, ly + logoH);
                bitmapPaint.setAlpha((int) (255 * titleAlpha));
                canvas.drawBitmap(logo, null, dstLogoRect, bitmapPaint);
                bitmapPaint.setAlpha(255);
            }
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

        canvas.restore(); // 1920x1080 scale

        // --- KEEP DRAWING (Step 3d) ---
        float animEnd = isSpider ? 1.65f : 0.80f;
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
            float inStart  = q[5];
            float inEnd    = q[6];
            float outStart = q[7];
            float outEnd   = q[8];

            float grow   = easeOut2(win(t, inStart, inEnd));
            float shrink = easeOut2(win(t, outStart, outEnd));
            if (grow <= 0.02f || shrink >= 0.98f) continue;

            // Fade from inside (base u=0 near head) to outside (tip u=1)
            float uStart = shrink;
            float uEnd   = grow;
            if (uEnd - uStart <= 0.03f) continue;

            float alpha  = easeOut2(win(t, inStart, inStart + 0.10f)) * (1f - shrink);
            if (alpha <= 0.01f) continue;

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

            // Exit animation: thinned as shrink progresses
            float wmax = 4.25f * (1f - 0.75f * shrink);

            int N = 28;
            sqPath.reset();
            for (int k = 0; k <= N; k++) {
                float seg = k / (float) N;
                float f = uStart + seg * (uEnd - uStart);
                float wave = amp * (float) Math.sin(2 * Math.PI * q[4] * f - phase);
                float px = bx + dx * f * len + nx * wave;
                float py = by + dy * f * len + ny * wave;
                float w = wmax * (float) Math.pow(Math.sin(Math.PI * seg), 0.7);
                float lx = px + nx * w / 2f;
                float ly = py + ny * w / 2f;
                if (k == 0) sqPath.moveTo(lx, ly);
                else sqPath.lineTo(lx, ly);
            }
            for (int k = N; k >= 0; k--) {
                float seg = k / (float) N;
                float f = uStart + seg * (uEnd - uStart);
                float wave = amp * (float) Math.sin(2 * Math.PI * q[4] * f - phase);
                float px = bx + dx * f * len + nx * wave;
                float py = by + dy * f * len + ny * wave;
                float w = wmax * (float) Math.pow(Math.sin(Math.PI * seg), 0.7);
                sqPath.lineTo(px - nx * w / 2f, py - ny * w / 2f);
            }
            sqPath.close();

            // Core pass (borderless)
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
            } else {
                if (listener != null) listener.onNavigateRightToSecondPage(selectedRow);
            }
            return true;
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
