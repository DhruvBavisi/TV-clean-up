package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stage 2: Childhood (Cartoon Favourites) lineup view.
 * Displays 14 cartoon characters across two rows:
 * - Back row (7): Sho Yamato, Gingka Hagane, Ben 10 (Classic), Ben 10 (Alien Force), Ash Ketchum, Kick Buttowski, Oswald
 * - Front row (7): Doraemon, Shin-chan, Ninja Hattori, Tom & Jerry, Roll No 21, Oggy, Courage
 * Features real-time B&W to color bloom, character scaling, soft ambient radial glow behind active character,
 * and seamless D-pad navigation to/from "My Favourites".
 */
public class CartoonFavouritesLineupView extends View {

    public interface OnSelectionChangedListener {
        void onCharacterSelected(Character character);
        void onNobodySelected();
        void onUpToDock();
        void onNavigateLeftToMainFavourites(int currentRow);
        void onCharacterActivated(Character character);
    }

    public static class Item {
        public final String name;
        public final String url;
        public final String url2;

        public Item(String name, String url, String url2) {
            this.name = name;
            this.url = url;
            this.url2 = url2;
        }

        public boolean hasServer(int server) {
            if (server == 2) {
                return url2 != null && !url2.isEmpty() && !"null".equalsIgnoreCase(url2);
            }
            return url != null && !url.isEmpty() && !"null".equalsIgnoreCase(url);
        }

        public String getUrl(int server) {
            if (server == 2 && hasServer(2)) {
                return url2;
            }
            if (server == 1 && hasServer(1)) {
                return url;
            }
            return hasServer(1) ? url : url2;
        }
    }

    public static class Tab {
        public final String title;
        public final List<Item> items = new ArrayList<>();
        public Tab(String title) {
            this.title = title;
        }

        public List<Item> getItemsForServer(int server) {
            List<Item> filtered = new ArrayList<>();
            for (Item it : items) {
                if (it.hasServer(server)) {
                    filtered.add(it);
                }
            }
            return filtered;
        }

        public boolean hasItemsForServer(int server) {
            for (Item it : items) {
                if (it.hasServer(server)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static class CharacterContent {
        public final String id;
        public final String title;
        public final String singleUrl;
        public final String singleUrl2;
        public final List<Tab> tabs = new ArrayList<>();

        public CharacterContent(String id, String title, String singleUrl, String singleUrl2) {
            this.id = id;
            this.title = title;
            this.singleUrl = singleUrl;
            this.singleUrl2 = singleUrl2;
        }

        public boolean isSingleLink() {
            return (singleUrl != null && !singleUrl.isEmpty()) || (singleUrl2 != null && !singleUrl2.isEmpty());
        }

        public String getSingleUrl(int server) {
            if (server == 2 && singleUrl2 != null && !singleUrl2.isEmpty()) {
                return singleUrl2;
            }
            if (server == 1 && singleUrl != null && !singleUrl.isEmpty()) {
                return singleUrl;
            }
            return (singleUrl != null && !singleUrl.isEmpty()) ? singleUrl : singleUrl2;
        }

        public boolean hasServer1() {
            if (singleUrl != null && !singleUrl.isEmpty() && !"null".equalsIgnoreCase(singleUrl)) return true;
            for (Tab t : tabs) {
                for (Item it : t.items) {
                    if (it.hasServer(1)) return true;
                }
            }
            return false;
        }

        public boolean hasServer2() {
            if (singleUrl2 != null && !singleUrl2.isEmpty() && !"null".equalsIgnoreCase(singleUrl2)) return true;
            for (Tab t : tabs) {
                for (Item it : t.items) {
                    if (it.hasServer(2)) return true;
                }
            }
            return false;
        }

        public List<Tab> getTabsForServer(int server) {
            List<Tab> visible = new ArrayList<>();
            for (Tab t : tabs) {
                if (t.hasItemsForServer(server)) {
                    visible.add(t);
                }
            }
            return visible;
        }
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
        // Back row
        GLOW_CONFIGS.put("sho-yamato",         new GlowConfig("sho-yamato",         0xFFFF3B30, 720f, 0.18f, 416.8f, 707.5f, 0f, 0f));
        GLOW_CONFIGS.put("jinga-hagane",       new GlowConfig("jinga-hagane",       0xFF007AFF, 750f, 0.17f, 588.2f, 603.0f, 0f, 0f));
        GLOW_CONFIGS.put("ben-10-classic",     new GlowConfig("ben-10-classic",     0xFF34C759, 700f, 0.16f, 724.9f, 692.9f, 0f, 0f));
        GLOW_CONFIGS.put("ben-10-alienforce", new GlowConfig("ben-10-alienforce", 0xFF30D158, 750f, 0.16f, 856.4f, 617.4f, 0f, 0f));
        GLOW_CONFIGS.put("ash-ketchem",        new GlowConfig("ash-ketchem",        0xFFFFD60A, 720f, 0.17f, 1150.9f, 637.4f, 0f, 0f));
        GLOW_CONFIGS.put("kick-buttowski",     new GlowConfig("kick-buttowski",     0xFFFF9F0A, 680f, 0.18f, 1443.1f, 540.6f, 0f, 0f));
        GLOW_CONFIGS.put("oswald",             new GlowConfig("oswald",             0xFF0A84FF, 740f, 0.17f, 1680.6f, 697.6f, 0f, 0f));

        // Front row
        GLOW_CONFIGS.put("doremon",            new GlowConfig("doremon",            0xFF40C4FF, 700f, 0.16f, 180.2f, 787.3f, 0f, 0f));
        GLOW_CONFIGS.put("shin-chan",          new GlowConfig("shin-chan",          0xFFFF375F, 650f, 0.19f, 827.9f, 867.8f, 0f, 0f));
        GLOW_CONFIGS.put("ninja-hattori",      new GlowConfig("ninja-hattori",      0xFF5856D6, 680f, 0.18f, 971.9f, 815.6f, 0f, 0f));
        GLOW_CONFIGS.put("tom-and-jerry",      new GlowConfig("tom-and-jerry",      0xFFFF9500, 700f, 0.18f, 1227.1f, 838.3f, 0f, 0f));
        GLOW_CONFIGS.put("roll-no-21",         new GlowConfig("roll-no-21",         0xFF64D2FF, 700f, 0.16f, 1392.7f, 774.9f, 0f, 0f));
        GLOW_CONFIGS.put("oggy-cockroaches",   new GlowConfig("oggy-cockroaches",   0xFF30B0C7, 700f, 0.16f, 1553.0f, 864.3f, 0f, 0f));
        GLOW_CONFIGS.put("courage",            new GlowConfig("courage",            0xFFFF2D55, 680f, 0.19f, 1841.7f, 873.7f, 0f, 0f));
    }

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
        public final int color;
        public final boolean isFrontRow;
        public final String fileName;
        public final GlowConfig glowConfig;
        public final int x, y, width, height;
        public final float centerX;
        Bitmap bitmap;

        Character(String id, String name, String franchise, String title,
                  int color, boolean isFrontRow,
                  String fileName, int x, int y, int width, int height, float centerX) {
            this.id = id;
            this.name = name;
            this.franchise = franchise;
            this.title = title;
            this.color = color;
            this.isFrontRow = isFrontRow;
            this.fileName = fileName;
            this.glowConfig = GLOW_CONFIGS.get(id);
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.centerX = centerX;
        }
    }

    // Back row (8): Doraemon, Sho Yamato, Gingka Hagane, Ben 10 (Classic), Ben 10 (Alien Force), Ash Ketchum, Kick Buttowski, Oswald
    public final Character[] backRow = new Character[] {
            new Character("doremon", "Doraemon", "Doraemon", "Doraemon",
                    0xFF40C4FF, false, "doremon.png", 30, 714, 298, 348, 179.0f),
            new Character("sho-yamato", "Sho Yamato", "Idaten Jump", "Idaten Jump",
                    0xFFFF3B30, false, "sho-yamato.png", 255, 618, 316, 462, 413.0f),
            new Character("jinga-hagane", "Gingka Hagane", "Beyblade: Metal Fusion", "Beyblade: Metal Fusion",
                    0xFF007AFF, false, "jinga-hagane.png", 371, 495, 389, 585, 565.5f),
            new Character("ben-10-classic", "Ben Tennyson", "Ben 10", "Ben 10",
                    0xFF34C759, false, "ben-10-classic.png", 635, 602, 185, 478, 727.5f),
            new Character("ben-10-alienforce", "Ben Tennyson", "Ben 10: Alien Force", "Ben 10: Alien Force",
                    0xFF30D158, false, "ben-10-alienforce.png", 746, 495, 301, 585, 896.5f),
            new Character("ash-ketchem", "Ash Ketchum", "Pokémon", "Pokémon",
                    0xFFFFD60A, false, "ash-ketchem.png", 1005, 540, 294, 451, 1152.0f),
            new Character("kick-buttowski", "Kick Buttowski", "Kick Buttowski", "Kick Buttowski",
                    0xFFFF9F0A, false, "kick-buttowski.png", 1314, 497, 283, 212, 1455.5f),
            new Character("oswald", "Oswald", "Oswald", "Oswald",
                    0xFF0A84FF, false, "oswald.png", 1501, 633, 315, 319, 1658.5f)
    };

    // Front row (6): Shin-chan, Ninja Hattori, Tom & Jerry, Roll No 21, Oggy, Courage
    public final Character[] frontRow = new Character[] {
            new Character("shin-chan", "Shin-chan", "Shin-chan", "Shin-chan",
                    0xFFFF375F, true, "shin-chan.png", 749, 820, 156, 235, 827.0f),
            new Character("ninja-hattori", "Ninja Hattori", "Ninja Hattori", "Ninja Hattori",
                    0xFF5856D6, true, "ninja-hattori.png", 836, 748, 249, 333, 960.5f),
            new Character("tom-and-jerry", "Tom & Jerry", "Tom and Jerry", "Tom and Jerry",
                    0xFFFF9500, true, "tom-and-jerry.png", 1055, 782, 300, 282, 1205.0f),
            new Character("roll-no-21", "Kris", "Roll No 21", "Roll No 21",
                    0xFF64D2FF, true, "roll-no-21.png", 1310, 689, 148, 388, 1384.0f),
            new Character("oggy-cockroaches", "Oggy", "Oggy and the Cockroaches", "Oggy and the Cockroaches",
                    0xFF30B0C7, true, "oggy-cockroaches.png", 1381, 825, 422, 228, 1592.0f),
            new Character("courage", "Courage", "Courage the Cowardly Dog", "Courage the Cowardly Dog",
                    0xFFFF2D55, true, "courage.png", 1732, 826, 220, 242, 1842.0f)
    };

    // Exact PSD layer order for back row characters (bottom to top):
    public static final String[] BACK_ROW_DRAW_ORDER = {
            "ash-ketchem",
            "oswald",
            "ben-10-alienforce",
            "jinga-hagane",
            "ben-10-classic",
            "sho-yamato",
            "doremon",
            "kick-buttowski"
    };
    public final Character[] backRowDrawOrder = new Character[8];

    // Exact PSD layer order for front row characters (bottom to top):
    public static final String[] FRONT_ROW_DRAW_ORDER = {
            "oggy-cockroaches",
            "roll-no-21",
            "tom-and-jerry",
            "ninja-hattori",
            "shin-chan",
            "courage"
    };
    public final Character[] frontRowDrawOrder = new Character[6];

    public static final int ROW_BACK = 0;
    public static final int ROW_FRONT = 1;

    private int selectedRow = -1;
    private int selectedCol = -1;

    private OnSelectionChangedListener listener;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fadePaint = new Paint();

    private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tabPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tabTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF tabRect = new RectF();
    private final RectF cardRect = new RectF();
    private final RectF glowRect = new RectF();
    private final RectF strokeRect = new RectF();

    // Floating Top Shelf state
    private boolean isTopShelfFocused = false;
    private int shelfRow = 1; // 0 = Tabs / Server Pills, 1 = Cards
    private int activeTabIdx = 0;
    private int focusedCardIdx = 0;
    private int currentServer = 1; // 1 or 2
    private int row0Col = 0; // index in row 0: tabs first, then Server 1, then Server 2 (if present)
    private float scrollCardOffset = 0f;
    private float targetCardOffset = 0f;

    private final Map<String, CharacterContent> characterContentMap = new HashMap<>();

    private final ColorMatrixColorFilter bwFilter;
    private final ColorMatrix bloomMatrix = new ColorMatrix();

    private long selectAnimStart = 0;

    // Shared ALPHA_8 radial glow mask (512x512 pixels = 256 KB memory footprint, well under 15 MB)
    private static volatile Bitmap sharedRadialGlowMask = null;
    private final RectF dstGlowRect = new RectF();

    private static float win(float t, float a, float b) {
        float v = (t - a) / (b - a);
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
    private static float easeOut2(float x) { float u = 1f - x; return 1f - u * u; }

    public CartoonFavouritesLineupView(Context context) {
        this(context, null);
    }

    public CartoonFavouritesLineupView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setFocusableInTouchMode(true);

        for (int i = 0; i < BACK_ROW_DRAW_ORDER.length; i++) {
            backRowDrawOrder[i] = getCharacterById(BACK_ROW_DRAW_ORDER[i]);
        }
        for (int i = 0; i < FRONT_ROW_DRAW_ORDER.length; i++) {
            frontRowDrawOrder[i] = getCharacterById(FRONT_ROW_DRAW_ORDER[i]);
        }

        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0f);
        bwFilter = new ColorMatrixColorFilter(cm);

        Typeface mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        Typeface boldTypeface = Typeface.create("sans-serif", Typeface.BOLD);

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

        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setTextAlign(Paint.Align.LEFT);
        titlePaint.setTextSize(38f);
        titlePaint.setTypeface(boldTypeface);
        titlePaint.setLetterSpacing(0.01f);

        subTitlePaint.setColor(0x99FFFFFF);
        subTitlePaint.setTextAlign(Paint.Align.LEFT);
        subTitlePaint.setTextSize(18f);
        subTitlePaint.setTypeface(boldTypeface);
        subTitlePaint.setLetterSpacing(0.04f);

        tabTextPaint.setColor(0xFFFFFFFF);
        tabTextPaint.setTextAlign(Paint.Align.LEFT);
        tabTextPaint.setTextSize(20f);
        tabTextPaint.setTypeface(mediumTypeface);
        tabTextPaint.setLetterSpacing(0.02f);

        cardTextPaint.setColor(0xFFFFFFFF);
        cardTextPaint.setTextAlign(Paint.Align.LEFT);
        cardTextPaint.setTextSize(22f);
        cardTextPaint.setTypeface(mediumTypeface);
        cardTextPaint.setLetterSpacing(0.02f);

        hintPaint.setColor(0x88FFFFFF);
        hintPaint.setTextAlign(Paint.Align.LEFT);
        hintPaint.setTextSize(17f);
        hintPaint.setTypeface(mediumTypeface);
        hintPaint.setLetterSpacing(0.02f);

        Shader fadeShader = new LinearGradient(0, 840, 0, 1080, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
        fadePaint.setShader(fadeShader);

        initSharedGlowMaskBackground();
        loadContentLinks();
    }

    private static void initSharedGlowMaskBackground() {
        if (sharedRadialGlowMask != null) return;
        Thread t = new Thread(() -> {
            int size = 512;
            byte[] pixels = new byte[size * size];
            float center = size / 2.0f;
            float maxR = center;
            float invR = 1.0f / maxR;
            Random rnd = new Random(42);

            for (int y = 0; y < size; y++) {
                float dy = y - center;
                float dy2 = dy * dy;
                int rowOffset = y * size;
                for (int x = 0; x < size; x++) {
                    float dx = x - center;
                    float d2 = dx * dx + dy2;
                    if (d2 < maxR * maxR) {
                        float d = (float) Math.sqrt(d2);
                        float u = d * invR;
                        int lutIdx = (int) (u * 1000f);
                        if (lutIdx > 1000) lutIdx = 1000;
                        float f = FALLOFF_LUT[lutIdx];
                        float noise = (rnd.nextFloat() - 0.5f) * 4.0f;
                        int val = Math.round(f * 255f + noise);
                        if (val < 0) val = 0;
                        else if (val > 255) val = 255;
                        pixels[rowOffset + x] = (byte) val;
                    } else {
                        pixels[rowOffset + x] = 0;
                    }
                }
            }
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8);
            bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels));
            sharedRadialGlowMask = bmp;
        }, "CartoonGlowMaskBuilder");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.setDaemon(true);
        t.start();
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

    public void selectNobody() {
        selectedRow = -1;
        selectedCol = -1;
        isTopShelfFocused = false;
        shelfRow = 1;
        activeTabIdx = 0;
        focusedCardIdx = 0;
        row0Col = 0;
        scrollCardOffset = 0f;
        targetCardOffset = 0f;
        invalidate();
        if (listener != null) listener.onNobodySelected();
    }

    public void selectCharacter(int row, int col) {
        if (row < 0 || col < 0) {
            selectNobody();
            return;
        }
        selectedRow = row;
        selectedCol = col;
        selectAnimStart = SystemClock.uptimeMillis();
        isTopShelfFocused = false;
        shelfRow = 1;
        activeTabIdx = 0;
        focusedCardIdx = 0;
        row0Col = 0;
        scrollCardOffset = 0f;
        targetCardOffset = 0f;

        Character c = getSelectedCharacter();
        if (c != null) {
            CharacterContent cContent = characterContentMap.get(c.id);
            if (cContent != null) {
                if (!cContent.hasServer1() && cContent.hasServer2()) {
                    currentServer = 2;
                } else if (cContent.hasServer1()) {
                    currentServer = 1;
                }
            }
        }
        invalidate();
        if (listener != null) {
            if (c != null) listener.onCharacterSelected(c);
            else listener.onNobodySelected();
        }
    }

    private void clampSelectionAfterServerChange(CharacterContent content) {
        if (content == null || content.isSingleLink()) return;
        List<Tab> visibleTabs = content.getTabsForServer(currentServer);
        if (visibleTabs.isEmpty()) {
            activeTabIdx = 0;
            focusedCardIdx = 0;
            targetCardOffset = 0f;
            scrollCardOffset = 0f;
            return;
        }
        if (activeTabIdx >= visibleTabs.size()) {
            activeTabIdx = visibleTabs.size() - 1;
        }
        if (activeTabIdx < 0) {
            activeTabIdx = 0;
        }
        Tab curTab = visibleTabs.get(activeTabIdx);
        List<Item> curItems = curTab.getItemsForServer(currentServer);
        if (curItems.isEmpty()) {
            focusedCardIdx = 0;
        } else if (focusedCardIdx >= curItems.size()) {
            focusedCardIdx = curItems.size() - 1;
        }
        if (focusedCardIdx < 0) {
            focusedCardIdx = 0;
        }
        targetCardOffset = 0f;
        scrollCardOffset = 0f;
    }

    public void selectLeftmost(int targetRow) {
        selectCharacter("doremon");
    }

    public Character getCharacterById(String id) {
        if (id == null) return null;
        for (Character c : backRow) {
            if (id.equals(c.id)) return c;
        }
        for (Character c : frontRow) {
            if (id.equals(c.id)) return c;
        }
        return null;
    }

    public void selectCharacter(String id) {
        if (id == null) return;
        for (int i = 0; i < backRow.length; i++) {
            if (id.equals(backRow[i].id)) {
                selectCharacter(ROW_BACK, i);
                return;
            }
        }
        for (int i = 0; i < frontRow.length; i++) {
            if (id.equals(frontRow[i].id)) {
                selectCharacter(ROW_FRONT, i);
                return;
            }
        }
    }

    public void selectMiddleBackRow() {
        selectCharacter("doremon");
    }

    public void selectDefault() {
        selectCharacter("doremon");
    }

    private void loadContentLinks() {
        Thread t = new Thread(() -> {
            try {
                String jsonStr = null;
                // 1. Check external storage, but only use if it contains updated dual-server links
                File dir = Config.dir(getContext());
                File jsonFile = (dir != null) ? new File(dir, "favourites/cartoon/cartoon_links.json") : null;
                if (jsonFile == null || !jsonFile.exists()) {
                    jsonFile = (dir != null) ? new File(dir, "favourites/cartoon_links.json") : null;
                }
                if (jsonFile != null && jsonFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(jsonFile);
                         InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                         BufferedReader reader = new BufferedReader(isr)) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        String candidate = sb.toString();
                        if (candidate.contains("\"url2\"") || candidate.contains("\"single_url2\"")) {
                            jsonStr = candidate;
                        }
                    } catch (Exception ignored) {}
                }

                // 2. Canonical APK assets fallback
                if (jsonStr == null || jsonStr.isEmpty()) {
                    try (InputStream is = getContext().getAssets().open("cartoon_links.json");
                         BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line);
                        }
                        jsonStr = sb.toString();
                    } catch (Exception ignored) {}
                }

                if (jsonStr != null && !jsonStr.isEmpty()) {
                    JSONObject root = new JSONObject(jsonStr);
                    Map<String, CharacterContent> map = new HashMap<>();
                    Iterator<String> keys = root.keys();
                    while (keys.hasNext()) {
                        String cid = keys.next();
                        JSONObject cObj = root.optJSONObject(cid);
                        if (cObj == null) continue;
                        String title = cObj.optString("title", "");
                        String singleUrl = (cObj.isNull("single_url") || "null".equalsIgnoreCase(cObj.optString("single_url", ""))) ? null : cObj.optString("single_url", null);
                        String singleUrl2 = (cObj.isNull("single_url2") || "null".equalsIgnoreCase(cObj.optString("single_url2", ""))) ? null : cObj.optString("single_url2", null);
                        CharacterContent content = new CharacterContent(cid, title, singleUrl, singleUrl2);
                        JSONArray tabsArr = cObj.optJSONArray("tabs");
                        if (tabsArr != null) {
                            for (int ti = 0; ti < tabsArr.length(); ti++) {
                                JSONObject tabObj = tabsArr.optJSONObject(ti);
                                if (tabObj == null) continue;
                                String tabTitle = tabObj.optString("title", "Tab");
                                Tab tab = new Tab(tabTitle);
                                JSONArray itemsArr = tabObj.optJSONArray("items");
                                if (itemsArr != null) {
                                    for (int ii = 0; ii < itemsArr.length(); ii++) {
                                        JSONObject itemObj = itemsArr.optJSONObject(ii);
                                        if (itemObj == null) continue;
                                        String name = itemObj.optString("name", "Episode");
                                        String url = (itemObj.isNull("url") || "null".equalsIgnoreCase(itemObj.optString("url", ""))) ? null : itemObj.optString("url", null);
                                        String url2 = (itemObj.isNull("url2") || "null".equalsIgnoreCase(itemObj.optString("url2", ""))) ? null : itemObj.optString("url2", null);
                                        tab.items.add(new Item(name, url, url2));
                                    }
                                }
                                content.tabs.add(tab);
                            }
                        }
                        map.put(cid, content);
                    }
                    post(() -> {
                        characterContentMap.clear();
                        characterContentMap.putAll(map);
                        Character c = getSelectedCharacter();
                        if (c != null) {
                            CharacterContent cContent = characterContentMap.get(c.id);
                            if (cContent != null) {
                                if (!cContent.hasServer1() && cContent.hasServer2()) {
                                    currentServer = 2;
                                } else if (cContent.hasServer1()) {
                                    currentServer = 1;
                                }
                            }
                        }
                        invalidate();
                    });
                }
            } catch (Exception ignored) {}
        }, "CartoonLinksLoader");
        t.setDaemon(true);
        t.start();
    }

    public void launchUrl(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                getContext().getPackageManager().getPackageInfo("com.phlox.tvwebbrowser", 0);
                intent.setPackage("com.phlox.tvwebbrowser");
            } catch (Exception ignored) {}
            getContext().startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void ensureBitmap(Character c) {
        if (c.bitmap != null) return;
        File dir = Config.dir(getContext());
        if (dir == null) return;
        File file = new File(dir, "favourites/cartoon/" + c.fileName);
        if (!file.exists()) {
            file = new File(dir, "favourites/" + c.fileName);
        }
        if (file.exists()) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            c.bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        canvas.save();
        canvas.scale(w / 1920f, h / 1080f);

        Character selected = getSelectedCharacter();
        float t = (selected == null) ? 99f : (SystemClock.uptimeMillis() - selectAnimStart) / 1000f;

        // 1. Top-left header requested by user: "Childhood"
        canvas.drawText("Childhood", 72f, 60f, headerPaint);

        // 2. Ambient radial glow behind the active character
        if (selected != null && selected.glowConfig != null && sharedRadialGlowMask != null) {
            GlowConfig cfg = selected.glowConfig;
            int alpha = (int) (255f * cfg.peakAlpha);
            int color = cfg.color & 0x00FFFFFF;
            glowPaint.setColor((alpha << 24) | color);
            float cx = cfg.getCenterX();
            float cy = cfg.getCenterY();
            float r = cfg.radius;
            dstGlowRect.set(cx - r, cy - r, cx + r, cy + r);
            canvas.drawBitmap(sharedRadialGlowMask, null, dstGlowRect, glowPaint);
        }

        // 3. Floating Top Shelf (Franchise Title, Subtitle, Tabs, and Season Pill Badges)
        if (selected != null) {
            CharacterContent content = characterContentMap.get(selected.id);
            if (content != null) {
                drawTopShelf(canvas, selected, content, t);
            } else {
                String title = selected.title != null ? selected.title : selected.franchise;
                float titleAlpha = easeOut2(win(t, 0f, 0.20f));
                titlePaint.setColor(0xFFFFFFFF);
                titlePaint.setAlpha((int) (255 * titleAlpha));
                canvas.drawText(title, 80f, 140f, titlePaint);
            }
        }

        // 4. Draw Characters with correct row layering:
        // Rule: Behind-row characters grow/bloom but STAY BEHIND front row when active.
        // Selected front-row character steps in front of everyone.
        if (selected != null && selected.isFrontRow) {
            // Back row (all unselected, b&w, in PSD back-row order)
            for (Character c : backRowDrawOrder) {
                drawCharacter(canvas, c, false, t);
            }
            // Front row (unselected, b&w, in PSD front-row order)
            for (Character c : frontRowDrawOrder) {
                if (c != selected) {
                    drawCharacter(canvas, c, false, t);
                }
            }
            // Front row selected character (color, scaled, front-most)
            drawCharacter(canvas, selected, true, t);
        } else if (selected != null) {
            // Selected is in the behind row:
            // Draw unselected behind-row characters (b&w, in PSD back-row order)
            for (Character c : backRowDrawOrder) {
                if (c != selected) {
                    drawCharacter(canvas, c, false, t);
                }
            }
            // Draw selected behind-row character (color, scaled, stays behind front row)
            drawCharacter(canvas, selected, true, t);
            // Draw all front-row characters ON TOP of behind row (in PSD front-row order)
            for (Character c : frontRowDrawOrder) {
                drawCharacter(canvas, c, false, t);
            }
        } else {
            // Nobody selected: draw back row, then front row on top
            for (Character c : backRowDrawOrder) {
                drawCharacter(canvas, c, false, t);
            }
            for (Character c : frontRowDrawOrder) {
                drawCharacter(canvas, c, false, t);
            }
        }

        // 5. Fade character bottom into black
        canvas.drawRect(0, 840, 1920, 1080, fadePaint);

        canvas.restore();

        if (selected != null && t < 0.80f) {
            postInvalidateOnAnimation();
        }
    }

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

    private void drawTopShelf(Canvas canvas, Character selected, CharacterContent content, float t) {
        if (content == null) return;

        float alpha = easeOut2(win(t, 0f, 0.22f));
        if (alpha <= 0f) return;

        int baseAlpha = (int) (255 * alpha);

        // 1. Franchise Title at (80, 140)
        String displayTitle = (content.title != null && !content.title.isEmpty()) ? content.title : selected.franchise;
        titlePaint.setColor(0xFFFFFFFF);
        titlePaint.setAlpha(baseAlpha);
        float startX = 80f;
        float titleY = 140f;
        canvas.drawText(displayTitle, startX, titleY, titlePaint);
        float titleW = titlePaint.measureText(displayTitle);

        // Category Subtitle / Counts badge
        String sub = "";
        if (content.isSingleLink()) {
            sub = "FULL SHOW";
        } else {
            int totalSeasons = 0;
            int totalMovies = 0;
            List<Tab> serverTabs = content.getTabsForServer(currentServer);
            for (Tab tab : serverTabs) {
                List<Item> sItems = tab.getItemsForServer(currentServer);
                if (tab.title.toLowerCase().contains("movie")) {
                    totalMovies += sItems.size();
                } else {
                    totalSeasons += sItems.size();
                }
            }
            if (totalSeasons > 0 && totalMovies > 0) {
                sub = totalSeasons + " SEASONS  •  " + totalMovies + " MOVIE" + (totalMovies > 1 ? "S" : "");
            } else if (totalSeasons > 0) {
                sub = totalSeasons + " SEASONS";
            } else if (totalMovies > 0) {
                sub = totalMovies + " MOVIES";
            }
        }
        if (!sub.isEmpty()) {
            subTitlePaint.setColor(0x99FFFFFF);
            subTitlePaint.setAlpha((int) (153 * alpha));
            canvas.drawText(sub, startX + titleW + 28f, titleY - 2f, subTitlePaint);
        }

        if (content.isSingleLink()) {
            drawSingleLinkCard(canvas, content, selected, startX, 175f, alpha);
            return;
        }

        List<Tab> tabs = content.getTabsForServer(currentServer);
        if (tabs.isEmpty()) return;

        if (activeTabIdx < 0) activeTabIdx = 0;
        if (activeTabIdx >= tabs.size()) activeTabIdx = tabs.size() - 1;
        Tab currentTab = tabs.get(activeTabIdx);

        float currentY = 175f;
        float tabH = 38f;
        float tabRadius = 19f;
        float tabX = startX;

        int visibleTabCount = tabs.size() > 1 ? tabs.size() : 0;

        // 2. Row 0: Tabs (if > 1 tab) + Server Switcher Pills
        if (visibleTabCount > 0) {
            for (int i = 0; i < tabs.size(); i++) {
                Tab tab = tabs.get(i);
                boolean isTabActive = (i == activeTabIdx);
                boolean isTabFocused = (isTopShelfFocused && shelfRow == 0 && i == row0Col);

                float textW = tabTextPaint.measureText(tab.title);
                float tabW = textW + 36f;
                tabRect.set(tabX, currentY, tabX + tabW, currentY + tabH);

                drawPillBadge(canvas, tabRect, tabRadius, isTabActive, isTabFocused, selected.color, tab.title, baseAlpha);

                tabX += tabW + 14f;
            }

            // Sleek divider between category tabs and server switcher
            tabPaint.setStyle(Paint.Style.STROKE);
            tabPaint.setStrokeWidth(1.5f);
            tabPaint.setColor(0x38FFFFFF);
            canvas.drawLine(tabX + 4f, currentY + 7f, tabX + 4f, currentY + tabH - 7f, tabPaint);
            tabX += 24f;
        }

        // Server 1 Pill
        if (content.hasServer1()) {
            String s1Text = "Server 1";
            float s1W = tabTextPaint.measureText(s1Text) + 36f;
            tabRect.set(tabX, currentY, tabX + s1W, currentY + tabH);
            boolean isS1Active = (currentServer == 1);
            boolean isS1Focused = (isTopShelfFocused && shelfRow == 0 && row0Col == visibleTabCount);
            drawPillBadge(canvas, tabRect, tabRadius, isS1Active, isS1Focused, selected.color, s1Text, baseAlpha);
            tabX += s1W + 14f;
        } else {
            String s1Text = "Server 1 (N/A)";
            float s1W = tabTextPaint.measureText(s1Text) + 36f;
            tabRect.set(tabX, currentY, tabX + s1W, currentY + tabH);
            drawDisabledPillBadge(canvas, tabRect, tabRadius, s1Text, baseAlpha);
            tabX += s1W + 14f;
        }

        // Server 2 Pill
        if (content.hasServer2()) {
            String s2Text = "Server 2";
            float s2W = tabTextPaint.measureText(s2Text) + 36f;
            tabRect.set(tabX, currentY, tabX + s2W, currentY + tabH);
            boolean isS2Active = (currentServer == 2);
            boolean isS2Focused = (isTopShelfFocused && shelfRow == 0 && row0Col == visibleTabCount + 1);
            drawPillBadge(canvas, tabRect, tabRadius, isS2Active, isS2Focused, selected.color, s2Text, baseAlpha);
            tabX += s2W + 14f;
        } else {
            String s2Text = "Server 2 (N/A)";
            float s2W = tabTextPaint.measureText(s2Text) + 36f;
            tabRect.set(tabX, currentY, tabX + s2W, currentY + tabH);
            drawDisabledPillBadge(canvas, tabRect, tabRadius, s2Text, baseAlpha);
            tabX += s2W + 14f;
        }

        currentY += tabH + 22f;

        // 3. Season / Movie Pill Badges Carousel
        List<Item> items = currentTab.getItemsForServer(currentServer);
        if (!items.isEmpty()) {
            if (focusedCardIdx < 0) focusedCardIdx = 0;
            if (focusedCardIdx >= items.size()) focusedCardIdx = items.size() - 1;

            float cardH = 70f;
            float cardRadius = 18f;
            float cardSpacing = 16f;

            float[] cardWidths = new float[items.size()];
            float totalWidth = 0f;
            for (int i = 0; i < items.size(); i++) {
                float labelW = cardTextPaint.measureText(items.get(i).name);
                cardWidths[i] = Math.max(145f, labelW + 48f);
                totalWidth += cardWidths[i] + cardSpacing;
            }

            float focusedCardLeft = 0f;
            for (int i = 0; i < focusedCardIdx; i++) {
                focusedCardLeft += cardWidths[i] + cardSpacing;
            }
            float focusedCardRight = focusedCardLeft + cardWidths[focusedCardIdx];

            float viewPortWidth = 1760f; // x=80 to x=1840

            if (focusedCardRight - targetCardOffset > viewPortWidth - 120f) {
                targetCardOffset = focusedCardRight - viewPortWidth + 180f;
            } else if (focusedCardLeft - targetCardOffset < 80f) {
                targetCardOffset = Math.max(0f, focusedCardLeft - 80f);
            }
            float maxOffset = Math.max(0f, totalWidth - viewPortWidth);
            if (targetCardOffset > maxOffset) targetCardOffset = maxOffset;
            if (targetCardOffset < 0f) targetCardOffset = 0f;

            if (Math.abs(targetCardOffset - scrollCardOffset) > 0.5f) {
                scrollCardOffset += (targetCardOffset - scrollCardOffset) * 0.25f;
                postInvalidateOnAnimation();
            } else {
                scrollCardOffset = targetCardOffset;
            }

            canvas.save();
            canvas.clipRect(40f, currentY - 20f, 1844f, currentY + cardH + 20f);

            float cardX = startX - scrollCardOffset;

            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                float cardW = cardWidths[i];
                boolean isFocused = (isTopShelfFocused && shelfRow == 1 && i == focusedCardIdx);

                if (cardX + cardW >= 40f && cardX <= 1850f) {
                    cardRect.set(cardX, currentY, cardX + cardW, currentY + cardH);

                    if (isFocused) {
                        canvas.save();
                        canvas.scale(1.06f, 1.06f, cardRect.centerX(), cardRect.centerY());

                        // Ambient glow behind focused card
                        cardPaint.setStyle(Paint.Style.FILL);
                        cardPaint.setColor((selected.color & 0x00FFFFFF) | 0x30000000);
                        glowRect.set(cardRect.left - 6f, cardRect.top - 6f, cardRect.right + 6f, cardRect.bottom + 6f);
                        canvas.drawRoundRect(glowRect, cardRadius + 4f, cardRadius + 4f, cardPaint);

                        // Focused pill fill
                        cardPaint.setColor((selected.color & 0x00FFFFFF) | 0x48000000);
                        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint);

                        // Text
                        cardTextPaint.setColor(0xFFFFFFFF);
                        cardTextPaint.setAlpha(baseAlpha);
                        Paint.FontMetrics fm = cardTextPaint.getFontMetrics();
                        float textY = cardRect.centerY() - (fm.descent + fm.ascent) / 2f;
                        float textX = cardRect.centerX() - cardTextPaint.measureText(item.name) / 2f;
                        canvas.drawText(item.name, textX, textY, cardTextPaint);

                        canvas.restore();
                    } else {
                        // Unfocused frosted glass pill
                        cardPaint.setStyle(Paint.Style.FILL);
                        cardPaint.setColor(0x1CFFFFFF);
                        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint);

                        // Unfocused border (CSS border-box: inset stroke)
                        float strokeW = 1.2f;
                        float halfStroke = strokeW / 2f;
                        cardPaint.setStyle(Paint.Style.STROKE);
                        cardPaint.setStrokeWidth(strokeW);
                        cardPaint.setColor(0x33FFFFFF);
                        strokeRect.set(cardRect.left + halfStroke, cardRect.top + halfStroke, cardRect.right - halfStroke, cardRect.bottom - halfStroke);
                        canvas.drawRoundRect(strokeRect, cardRadius - halfStroke, cardRadius - halfStroke, cardPaint);

                        // Text
                        cardTextPaint.setColor(0xD0FFFFFF);
                        cardTextPaint.setAlpha((int) (208 * alpha));
                        Paint.FontMetrics fm = cardTextPaint.getFontMetrics();
                        float textY = cardRect.centerY() - (fm.descent + fm.ascent) / 2f;
                        float textX = cardRect.centerX() - cardTextPaint.measureText(item.name) / 2f;
                        canvas.drawText(item.name, textX, textY, cardTextPaint);
                    }
                }

                cardX += cardW + cardSpacing;
            }

            canvas.restore();

            // 4. Subtle Hint below cards
            currentY += cardH + 18f;
            hintPaint.setColor(0x77FFFFFF);
            hintPaint.setAlpha((int) (119 * alpha));
            String serverLabel = (currentServer == 1) ? "Server 1" : "Server 2";
            String hint = isTopShelfFocused 
                    ? ("Press OK to Play (" + serverLabel + ")  •  UP for Tabs/Servers  •  DOWN to Characters") 
                    : "Press UP or OK to View Seasons & Movies";
            canvas.drawText(hint, startX, currentY + 10f, hintPaint);
        }
    }

    private void drawPillBadge(Canvas canvas, RectF rect, float radius, boolean isActive, boolean isFocused,
                               int themeColor, String text, int baseAlpha) {
        if (isFocused) {
            canvas.save();
            canvas.scale(1.06f, 1.06f, rect.centerX(), rect.centerY());
        }

        if (isActive) {
            // Ambient glow behind active pill
            tabPaint.setStyle(Paint.Style.FILL);
            tabPaint.setColor((themeColor & 0x00FFFFFF) | 0x32000000);
            glowRect.set(rect.left - 5f, rect.top - 5f, rect.right + 5f, rect.bottom + 5f);
            canvas.drawRoundRect(glowRect, radius + 3f, radius + 3f, tabPaint);

            // Active pill fill with theme color
            tabPaint.setColor((themeColor & 0x00FFFFFF) | (isFocused ? 0x58000000 : 0x44000000));
            canvas.drawRoundRect(rect, radius, radius, tabPaint);

            // Active pill border (CSS border-box inset stroke)
            float strokeW = isFocused ? 1.5f : 1.0f;
            float halfStroke = strokeW / 2f;
            tabPaint.setStyle(Paint.Style.STROKE);
            tabPaint.setStrokeWidth(strokeW);
            tabPaint.setColor(isFocused ? 0x88FFFFFF : ((themeColor & 0x00FFFFFF) | 0x66000000));
            strokeRect.set(rect.left + halfStroke, rect.top + halfStroke, rect.right - halfStroke, rect.bottom - halfStroke);
            canvas.drawRoundRect(strokeRect, radius - halfStroke, radius - halfStroke, tabPaint);

            tabTextPaint.setColor(0xFFFFFFFF);
        } else {
            // Inactive frosted glass pill
            tabPaint.setStyle(Paint.Style.FILL);
            tabPaint.setColor(isFocused ? 0x2AFFFFFF : 0x18FFFFFF);
            canvas.drawRoundRect(rect, radius, radius, tabPaint);

            // Inactive CSS border-box inset stroke
            float strokeW = isFocused ? 1.5f : 1.0f;
            float halfStroke = strokeW / 2f;
            tabPaint.setStyle(Paint.Style.STROKE);
            tabPaint.setStrokeWidth(strokeW);
            tabPaint.setColor(isFocused ? 0x66FFFFFF : 0x28FFFFFF);
            strokeRect.set(rect.left + halfStroke, rect.top + halfStroke, rect.right - halfStroke, rect.bottom - halfStroke);
            canvas.drawRoundRect(strokeRect, radius - halfStroke, radius - halfStroke, tabPaint);

            tabTextPaint.setColor(isFocused ? 0xFFFFFFFF : 0x88FFFFFF);
        }

        tabTextPaint.setAlpha(baseAlpha);
        Paint.FontMetrics fm = tabTextPaint.getFontMetrics();
        float textY = rect.centerY() - (fm.descent + fm.ascent) / 2f;
        float textX = rect.centerX() - tabTextPaint.measureText(text) / 2f;
        canvas.drawText(text, textX, textY, tabTextPaint);

        if (isFocused) {
            canvas.restore();
        }
    }

    private void drawDisabledPillBadge(Canvas canvas, RectF rect, float radius, String text, int baseAlpha) {
        // Disabled frosted pill: subtle translucent fill and stroke
        tabPaint.setStyle(Paint.Style.FILL);
        tabPaint.setColor(0x0EFFFFFF);
        canvas.drawRoundRect(rect, radius, radius, tabPaint);

        float strokeW = 1.0f;
        float halfStroke = strokeW / 2f;
        tabPaint.setStyle(Paint.Style.STROKE);
        tabPaint.setStrokeWidth(strokeW);
        tabPaint.setColor(0x18FFFFFF);
        strokeRect.set(rect.left + halfStroke, rect.top + halfStroke, rect.right - halfStroke, rect.bottom - halfStroke);
        canvas.drawRoundRect(strokeRect, radius - halfStroke, radius - halfStroke, tabPaint);

        tabTextPaint.setColor(0x55FFFFFF);
        tabTextPaint.setAlpha((int) (85 * (baseAlpha / 255f)));
        Paint.FontMetrics fm = tabTextPaint.getFontMetrics();
        float textY = rect.centerY() - (fm.descent + fm.ascent) / 2f;
        float textX = rect.centerX() - tabTextPaint.measureText(text) / 2f;
        canvas.drawText(text, textX, textY, tabTextPaint);
    }

    private void drawSingleLinkCard(Canvas canvas, CharacterContent content, Character selected, float startX, float startY, float alpha) {
        int baseAlpha = (int) (255 * alpha);

        // 1. Server Switcher Row at startY (175f)
        float tabH = 38f;
        float tabRadius = 19f;
        float tabX = startX;

        // Server 1
        if (content.hasServer1()) {
            String s1Text = "Server 1";
            float s1W = tabTextPaint.measureText(s1Text) + 36f;
            tabRect.set(tabX, startY, tabX + s1W, startY + tabH);
            boolean isS1Active = (currentServer == 1);
            boolean isS1Focused = (isTopShelfFocused && shelfRow == 0 && row0Col == 0);
            drawPillBadge(canvas, tabRect, tabRadius, isS1Active, isS1Focused, selected.color, s1Text, baseAlpha);
            tabX += s1W + 14f;
        } else {
            String s1Text = "Server 1 (N/A)";
            float s1W = tabTextPaint.measureText(s1Text) + 36f;
            tabRect.set(tabX, startY, tabX + s1W, startY + tabH);
            drawDisabledPillBadge(canvas, tabRect, tabRadius, s1Text, baseAlpha);
            tabX += s1W + 14f;
        }

        // Server 2
        if (content.hasServer2()) {
            String s2Text = "Server 2";
            float s2W = tabTextPaint.measureText(s2Text) + 36f;
            tabRect.set(tabX, startY, tabX + s2W, startY + tabH);
            boolean isS2Active = (currentServer == 2);
            boolean isS2Focused = (isTopShelfFocused && shelfRow == 0 && row0Col == 1);
            drawPillBadge(canvas, tabRect, tabRadius, isS2Active, isS2Focused, selected.color, s2Text, baseAlpha);
        } else {
            String s2Text = "Server 2 (N/A)";
            float s2W = tabTextPaint.measureText(s2Text) + 36f;
            tabRect.set(tabX, startY, tabX + s2W, startY + tabH);
            drawDisabledPillBadge(canvas, tabRect, tabRadius, s2Text, baseAlpha);
        }

        // 2. Direct Play Card at startY + tabH + 20f
        float cardY = startY + tabH + 20f;
        float cardW = 340f;
        float cardH = 68f;
        float cardRadius = 18f;
        cardRect.set(startX, cardY, startX + cardW, cardY + cardH);

        boolean isPlayFocused = (isTopShelfFocused && shelfRow == 1);
        if (isPlayFocused) {
            canvas.save();
            canvas.scale(1.06f, 1.06f, cardRect.centerX(), cardRect.centerY());
        }

        // Ambient glow behind direct play card
        cardPaint.setStyle(Paint.Style.FILL);
        cardPaint.setColor((selected.color & 0x00FFFFFF) | 0x28000000);
        glowRect.set(cardRect.left - 6f, cardRect.top - 6f, cardRect.right + 6f, cardRect.bottom + 6f);
        canvas.drawRoundRect(glowRect, cardRadius + 4f, cardRadius + 4f, cardPaint);

        // Direct play card fill
        cardPaint.setColor((selected.color & 0x00FFFFFF) | (isPlayFocused ? 0x58000000 : 0x3E000000));
        canvas.drawRoundRect(cardRect, cardRadius, cardRadius, cardPaint);

        // Inset stroke
        float strokeW = isPlayFocused ? 1.5f : 1.0f;
        float halfStroke = strokeW / 2f;
        cardPaint.setStyle(Paint.Style.STROKE);
        cardPaint.setStrokeWidth(strokeW);
        cardPaint.setColor(isPlayFocused ? 0x88FFFFFF : ((selected.color & 0x00FFFFFF) | 0x55000000));
        strokeRect.set(cardRect.left + halfStroke, cardRect.top + halfStroke, cardRect.right - halfStroke, cardRect.bottom - halfStroke);
        canvas.drawRoundRect(strokeRect, cardRadius - halfStroke, cardRadius - halfStroke, cardPaint);

        cardTextPaint.setColor(0xFFFFFFFF);
        cardTextPaint.setAlpha(baseAlpha);
        Paint.FontMetrics fm = cardTextPaint.getFontMetrics();
        String label = (currentServer == 2 && content.hasServer2()) 
                ? "▶  Direct Play (Server 2)" 
                : "▶  Direct Play (Server 1)";
        float textY = cardRect.centerY() - (fm.descent + fm.ascent) / 2f;
        float textX = cardRect.centerX() - cardTextPaint.measureText(label) / 2f;
        canvas.drawText(label, textX, textY, cardTextPaint);

        if (isPlayFocused) {
            canvas.restore();
        }

        hintPaint.setColor(0x99FFFFFF);
        hintPaint.setAlpha((int) (153 * alpha));
        String hint = isTopShelfFocused 
                ? "Press OK to Play  •  UP for Servers  •  DOWN to Characters" 
                : "Press OK on character to launch directly  •  UP for Servers";
        canvas.drawText(hint, startX, cardY + cardH + 22f, hintPaint);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return super.onKeyDown(keyCode, event);

        if (selectedRow == -1) {
            selectMiddleBackRow();
            return true;
        }

        if (isTopShelfFocused) {
            Character selected = getSelectedCharacter();
            CharacterContent content = (selected != null) ? characterContentMap.get(selected.id) : null;
            if (content == null) {
                isTopShelfFocused = false;
                invalidate();
                return true;
            }

            List<Tab> visibleTabs = content.isSingleLink() ? new ArrayList<>() : content.getTabsForServer(currentServer);
            if (!content.isSingleLink() && visibleTabs.isEmpty()) {
                isTopShelfFocused = false;
                invalidate();
                return true;
            }

            int visibleTabCount = (!content.isSingleLink() && visibleTabs.size() > 1) ? visibleTabs.size() : 0;
            int serverCount = 2;
            int row0Total = visibleTabCount + serverCount;

            if (keyCode == KeyEvent.KEYCODE_BACK) {
                isTopShelfFocused = false;
                invalidate();
                return true;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (shelfRow == 0) {
                    if (row0Col < visibleTabCount) {
                        activeTabIdx = row0Col;
                        focusedCardIdx = 0;
                        targetCardOffset = 0f;
                    }
                    shelfRow = 1;
                    invalidate();
                    return true;
                } else {
                    isTopShelfFocused = false;
                    invalidate();
                    return true;
                }
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (shelfRow == 1) {
                    shelfRow = 0;
                    if (visibleTabCount > 0) {
                        row0Col = (activeTabIdx >= 0 && activeTabIdx < visibleTabCount) ? activeTabIdx : 0;
                    } else {
                        row0Col = (currentServer == 2 && content.hasServer2()) ? 1 : 0;
                    }
                    invalidate();
                    return true;
                } else {
                    isTopShelfFocused = false;
                    if (listener != null) listener.onUpToDock();
                    return true;
                }
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (shelfRow == 0) {
                    if (row0Col > 0) {
                        row0Col--;
                        invalidate();
                    }
                    return true;
                } else {
                    if (focusedCardIdx > 0) {
                        focusedCardIdx--;
                        invalidate();
                    }
                    return true;
                }
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (shelfRow == 0) {
                    if (row0Col < row0Total - 1) {
                        row0Col++;
                        invalidate();
                    }
                    return true;
                } else {
                    if (!content.isSingleLink()) {
                        Tab curTab = (activeTabIdx >= 0 && activeTabIdx < visibleTabs.size()) 
                                ? visibleTabs.get(activeTabIdx) : null;
                        if (curTab != null) {
                            List<Item> curItems = curTab.getItemsForServer(currentServer);
                            if (focusedCardIdx < curItems.size() - 1) {
                                focusedCardIdx++;
                                invalidate();
                            }
                        }
                    }
                    return true;
                }
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (shelfRow == 0) {
                    if (row0Col < visibleTabCount) {
                        activeTabIdx = row0Col;
                        shelfRow = 1;
                        focusedCardIdx = 0;
                        targetCardOffset = 0f;
                        invalidate();
                        return true;
                    } else if (row0Col == visibleTabCount) {
                        if (content.hasServer1() && currentServer != 1) {
                            currentServer = 1;
                            clampSelectionAfterServerChange(content);
                            List<Tab> newTabs = content.getTabsForServer(1);
                            int newVisibleTabCount = newTabs.size() > 1 ? newTabs.size() : 0;
                            row0Col = newVisibleTabCount;
                        }
                        invalidate();
                        return true;
                    } else if (row0Col == visibleTabCount + 1 && content.hasServer2()) {
                        if (currentServer != 2) {
                            currentServer = 2;
                            clampSelectionAfterServerChange(content);
                            List<Tab> newTabs = content.getTabsForServer(2);
                            int newVisibleTabCount = newTabs.size() > 1 ? newTabs.size() : 0;
                            row0Col = newVisibleTabCount + 1;
                        }
                        invalidate();
                        return true;
                    }
                    return true;
                } else {
                    if (content.isSingleLink()) {
                        launchUrl(content.getSingleUrl(currentServer));
                        return true;
                    } else {
                        Tab curTab = (activeTabIdx >= 0 && activeTabIdx < visibleTabs.size()) 
                                ? visibleTabs.get(activeTabIdx) : null;
                        if (curTab != null) {
                            List<Item> curItems = curTab.getItemsForServer(currentServer);
                            if (focusedCardIdx >= 0 && focusedCardIdx < curItems.size()) {
                                Item item = curItems.get(focusedCardIdx);
                                launchUrl(item.getUrl(currentServer));
                            }
                        }
                        return true;
                    }
                }
            }

            return super.onKeyDown(keyCode, event);
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            Character current = getSelectedCharacter();
            if (current != null && "shin-chan".equals(current.id)) {
                selectCharacter("ben-10-classic");
                return true;
            }
            if (selectedCol > 0) {
                selectCharacter(selectedRow, selectedCol - 1);
            } else {
                // At leftmost edge: navigate back to My Favourites!
                if (listener != null) {
                    listener.onNavigateLeftToMainFavourites(selectedRow);
                }
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            Character current = getSelectedCharacter();
            if (current != null && "oswald".equals(current.id)) {
                selectCharacter("courage");
                return true;
            }
            int maxCol = (selectedRow == ROW_BACK) ? backRow.length - 1 : frontRow.length - 1;
            if (selectedCol < maxCol) {
                selectCharacter(selectedRow, selectedCol + 1);
            }
            return true; // Stop at right edge
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (selectedRow == ROW_BACK) {
                Character current = getSelectedCharacter();
                CharacterContent content = (current != null) ? characterContentMap.get(current.id) : null;
                if (content != null && (!content.getTabsForServer(currentServer).isEmpty() || content.isSingleLink())) {
                    isTopShelfFocused = true;
                    shelfRow = 1;
                    clampSelectionAfterServerChange(content);
                    invalidate();
                    return true;
                }
                // Return to dock
                if (listener != null) listener.onUpToDock();
                return true;
            }
            // Front row -> Back row
            Character current = getSelectedCharacter();
            if (current != null && "ninja-hattori".equals(current.id)) {
                selectCharacter("ash-ketchem");
                return true;
            }
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
                // Back row -> Front row
                Character current = getSelectedCharacter();
                if (current != null && "ben-10-alienforce".equals(current.id)) {
                    selectCharacter("shin-chan");
                    return true;
                }
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
            return true; // Stop at front row
        }

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            Character current = getSelectedCharacter();
            if (current != null) {
                CharacterContent content = characterContentMap.get(current.id);
                if (content != null && content.isSingleLink()) {
                    launchUrl(content.getSingleUrl(currentServer));
                    return true;
                } else if (content != null && !content.getTabsForServer(currentServer).isEmpty()) {
                    isTopShelfFocused = true;
                    shelfRow = 1;
                    clampSelectionAfterServerChange(content);
                    invalidate();
                    return true;
                }
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (listener != null) listener.onUpToDock();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }
}
