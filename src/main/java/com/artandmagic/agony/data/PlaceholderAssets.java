package com.artandmagic.agony.data;

import com.artandmagic.agony.core.Sprite;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Stand in artwork.
 *
 * <p>The original graphics live in the data files that ship with the game
 * ({@code Main_Char/Sorcerer.bin}, {@code Objects.bin}, {@code Front.Bin} and so
 * on) and none of them are part of the source release, so the engine would have
 * nothing to draw.  This class builds a complete set of shapes in the same
 * formats, which lets the port run and be played as it stands; point
 * {@link AssetLoader} at a folder of original files to replace them.
 */
public final class PlaceholderAssets {

    private PlaceholderAssets() {
    }

    /** Builds the player, weapon, spell and panel artwork. */
    public static GameAssets create() {
        GameAssets a = new GameAssets();

        // Registers 16 to 23 dress the owl, which is an attached sprite pair and
        // therefore has all sixteen colours; 24 to 27 are also the three colours
        // of sprite pair 2, so they double as the colours of the shots.
        a.sorcererPalette = new int[]{
            0x0000, 0x0421, 0x0642, 0x0864, 0x0a86, 0x0cb8, 0x0eda, 0x0fff,
            0x0300, 0x0f40, 0x0fa0, 0x0fe6, 0x08ac, 0x0bdf, 0x0f95, 0x0fd7
        };
        a.iconesPalette = new int[]{
            0x0000, 0x0113, 0x0225, 0x0447, 0x0669, 0x088b, 0x0aad, 0x0cff,
            0x0300, 0x0700, 0x0b30, 0x0f60, 0x0fa0, 0x0fd4, 0x0ff8, 0x0fff
        };

        for (int i = 0; i < 16; i++) {
            a.sorcerer[i] = owl(i, false);
            a.sorcerer2[i] = owl(i, true);
        }
        for (int w = 0; w < 4; w++) {
            a.forwardFire[w] = shot(w);
        }
        a.axe = axe();
        a.alienFire = alienShot();
        a.arrow = arrow();
        a.lock = lock();

        for (int i = 0; i < 8; i++) {
            a.diePieces[i] = feather();
        }
        for (int i = 0; i < 4; i++) {
            a.bonus[i] = bonus(i);
        }
        for (int i = 0; i < 8; i++) {
            a.icons[i] = icon(i);
        }

        // Stand ins for the spell graphics and for the tables the original keeps
        // as data next to them.
        for (int i = 0; i < 3; i++) {
            a.backFireBall[i] = fireBall(16, i == 1 ? 5 : 13);
        }
        for (int i = 0; i < 4; i++) {
            a.rotFireBall0[i] = fireBall(16, 15);
            a.rotFireBall1[i] = fireBall(16, 15);
        }
        for (int i = 0; i < 16; i++) {
            a.stopTimeLeft[i] = hourGlass();
            a.stopTimeRight[i] = hourGlass();
        }
        for (int i = 0; i < 6; i++) {
            a.smartBombLeft[i] = column(16, 156);
            a.smartBombRight[i] = column(16, 156);
        }
        for (int i = 0; i < 4; i++) {
            a.megaBlast[i] = column(16, 150);
        }
        a.fwFireBall[0] = fireBall(16, 15);
        a.fwFireBall[1] = fireBall(16, 15);

        a.axeTraj = new int[96];
        for (int i = 0; i < 96; i++) {
            double t = (i - 46) / 46.0;
            a.axeTraj[i] = (int) Math.round(30 * Math.sqrt(Math.max(0, 1 - t * t)));
        }
        a.rotTable = new int[8 * 16];
        for (int step = 0; step < 8; step++) {
            for (int ball = 0; ball < 8; ball++) {
                double angle = step * Math.PI / 4 + (ball % 4) * Math.PI / 2
                        + (ball < 4 ? 0 : Math.PI / 4);
                a.rotTable[step * 16 + ball * 2] = (int) Math.round(Math.cos(angle) * 48);
                a.rotTable[step * 16 + ball * 2 + 1] = (int) Math.round(Math.sin(angle) * 48);
            }
        }
        a.sinTable = new int[16];
        for (int i = 0; i < 16; i++) {
            a.sinTable[i] = (int) Math.round(30 * Math.abs(Math.sin(i * Math.PI / 16)));
        }
        a.timeYTable = new int[256];
        for (int i = 0; i < 256; i++) {
            a.timeYTable[i] = (int) Math.round(80 + 70 * Math.sin(i / 38.0));
        }
        a.rainX = new int[144];
        java.util.Random rainRandom = new java.util.Random(0x4147);
        for (int i = 0; i < 72; i++) {
            int v = 0x48 + rainRandom.nextInt(160);
            a.rainX[i] = v;
            a.rainX[i + 72] = v;
        }
        a.dieTraj = new int[8][];
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            double vx = Math.cos(angle) * 3.5;
            double vy = Math.sin(angle) * 3.0 - 3.0;
            int steps = 48;
            int[] path = new int[steps * 2];
            double x = 0;
            double y = 0;
            for (int s = 0; s < steps; s++) {
                x += vx;
                y += vy;
                vy += 0.25;
                path[s * 2] = (int) Math.round(x);
                path[s * 2 + 1] = (int) Math.round(y);
            }
            a.dieTraj[i] = path;
        }

        a.font = buildFont();
        a.texts = buildTexts();
        a.explosion = explosionSprites();
        return a;
    }

    // ------------------------------------------------------------- the player

    /**
     * The hero of Agony is an owl; sixteen frames of a wing beat are enough to
     * carry the animation the original plays at 25 Hz.
     */
    private static SpriteFrame owl(int frame, boolean ghost) {
        int w = 32;
        int h = 56;
        byte[] px = new byte[w * h];
        double beat = Math.sin(frame * Math.PI / 8);
        int wing = (int) Math.round(beat * 10);
        int base = ghost ? 8 : 1;

        // Body.
        ellipse(px, w, 16, 34, 9, 14, base + 3);
        ellipse(px, w, 16, 34, 6, 10, base + 5);
        // Head.
        ellipse(px, w, 16, 20, 9, 8, base + 4);
        ellipse(px, w, 16, 20, 7, 6, base + 6);
        // Eyes.
        ellipse(px, w, 12, 19, 3, 3, 7);
        ellipse(px, w, 20, 19, 3, 3, 7);
        ellipse(px, w, 12, 19, 1, 1, base);
        ellipse(px, w, 20, 19, 1, 1, base);
        // Beak.
        triangle(px, w, h, 16, 24, 13, 29, 19, 29, base + 6);

        // Wings, rising and falling with the beat.
        for (int i = 0; i < 14; i++) {
            int y = 28 + i - Math.abs(wing) / 3;
            int spread = 2 + i / 2;
            hline(px, w, h, 16 - 9 - spread, 16 - 8, y + wing, base + 2 + (i & 1));
            hline(px, w, h, 16 + 8, 16 + 9 + spread, y + wing, base + 2 + (i & 1));
        }
        // Tail feathers.
        for (int i = 0; i < 8; i++) {
            hline(px, w, h, 13, 19, 46 + i, base + 1 + (i & 1));
        }

        if (ghost) {
            // The double is drawn in the cold half of the palette.
            for (int i = 0; i < px.length; i++) {
                if (px[i] != 0) {
                    px[i] = (byte) (8 + (px[i] & 7));
                }
            }
        }
        return new SpriteFrame(0, w, h, px);
    }

    /** The four levels of the forward shot grow taller and brighter. */
    private static SpriteFrame shot(int weapon) {
        int h = switch (weapon) {
            case 0 -> 24;
            case 1 -> 40;
            case 2 -> 60;
            default -> 100;
        };
        int w = 16;
        byte[] px = new byte[w * h];
        for (int y = 0; y < h; y++) {
            double t = y / (double) (h - 1);
            int half = (int) Math.round(Math.sin(t * Math.PI) * 7) + 1;
            for (int x = 8 - half; x <= 8 + half; x++) {
                int shade = 3 - Math.abs(x - 8) / 3;
                set(px, w, h, x, y, Math.max(1, shade));
            }
        }
        return new SpriteFrame(0, w, h, px);
    }

    private static SpriteFrame axe() {
        int w = 32;
        int h = 10;
        byte[] px = new byte[w * h];
        for (int x = 0; x < w; x++) {
            int thickness = x < 8 || x > 23 ? 4 : 2;
            for (int y = 5 - thickness / 2; y < 5 + thickness / 2 + 1; y++) {
                set(px, w, h, x, y, x < 8 || x > 23 ? 7 : 5);
            }
        }
        return new SpriteFrame(0, w, h, px);
    }

    private static SpriteFrame alienShot() {
        int w = 8;
        int h = 7;
        byte[] px = new byte[w * h];
        ellipse(px, w, 4, 3, 4, 3, 2);
        ellipse(px, w, 4, 3, 2, 2, 3);
        return new SpriteFrame(0, w, h, px);
    }

    private static SpriteFrame feather() {
        int w = 12;
        int h = 12;
        byte[] px = new byte[w * h];
        ellipse(px, w, 6, 6, 4, 5, 5);
        ellipse(px, w, 6, 6, 2, 3, 7);
        return new SpriteFrame(0, w, h, px);
    }

    private static SpriteFrame arrow() {
        int w = 16;
        int h = 16;
        byte[] px = new byte[w * h];
        for (int y = 0; y < 16; y++) {
            int len = 8 - Math.abs(y - 8);
            hline(px, w, h, 14 - len, 15, y, 14);
        }
        return new SpriteFrame(0, w, h, px);
    }

    private static SpriteFrame lock() {
        int w = 32;
        int h = 24;
        byte[] px = new byte[w * h];
        for (int i = 0; i < 24; i++) {
            set(px, w, h, 4 + i, i, 12);
            set(px, w, h, 27 - i, i, 12);
        }
        return new SpriteFrame(0, w, h, px);
    }

    /** The four bonus shapes: sword, money bag, fire ball, spell scroll. */
    private static SpriteFrame bonus(int shape) {
        int w = 16;
        int h = 16;
        byte[] px = new byte[w * h];
        switch (shape) {
            case 0 -> {
                for (int y = 1; y < 13; y++) {
                    set(px, w, h, 8, y, 3);
                    set(px, w, h, 7, y, 2);
                }
                hline(px, w, h, 4, 11, 13, 2);
            }
            case 1 -> {
                ellipse(px, w, 8, 9, 6, 6, 2);
                ellipse(px, w, 8, 9, 4, 4, 3);
                hline(px, w, h, 6, 9, 2, 3);
            }
            case 2 -> {
                ellipse(px, w, 8, 8, 7, 7, 1);
                ellipse(px, w, 8, 8, 5, 5, 2);
                ellipse(px, w, 8, 8, 2, 2, 3);
            }
            default -> {
                for (int y = 2; y < 14; y++) {
                    hline(px, w, h, 3, 12, y, y % 3 == 0 ? 2 : 3);
                }
            }
        }
        return new SpriteFrame(0, w, h, px);
    }

    /** One spell icon: a rune inside a frame, different for each spell. */
    private static SpriteFrame icon(int spell) {
        int w = 32;
        int h = 24;
        byte[] px = new byte[w * h];
        for (int x = 0; x < w; x++) {
            set(px, w, h, x, 0, 4);
            set(px, w, h, x, h - 2, 4);
        }
        for (int y = 0; y < h - 1; y++) {
            set(px, w, h, 0, y, 4);
            set(px, w, h, w - 1, y, 4);
        }
        // A different glyph per spell, so the menu can be read at a glance.
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4 + spell;
            int x = 16 + (int) Math.round(Math.cos(angle) * (4 + spell));
            int y = 11 + (int) Math.round(Math.sin(angle) * 6);
            ellipse(px, w, x, y, 2, 2, 9 + (spell & 5));
        }
        ellipse(px, w, 16, 11, 3, 3, 15);
        return new SpriteFrame(0, w, h, px);
    }

    private static SpriteFrame fireBall(int w, int h) {
        byte[] px = new byte[w * h];
        ellipse(px, w, w / 2, h / 2, w / 2, h / 2, 1);
        ellipse(px, w, w / 2, h / 2, w / 3, h / 3, 2);
        ellipse(px, w, w / 2, h / 2, w / 6, h / 6, 3);
        return new SpriteFrame(0, w, h, px);
    }

    private static SpriteFrame hourGlass() {
        int w = 16;
        int h = 32;
        byte[] px = new byte[w * h];
        for (int y = 0; y < h; y++) {
            int half = (int) Math.round(Math.abs(y - (h - 1) / 2.0) / 2) + 1;
            hline(px, w, h, 8 - half, 8 + half, y, y < h / 2 ? 2 : 3);
        }
        hline(px, w, h, 2, 13, 0, 3);
        hline(px, w, h, 2, 13, h - 1, 3);
        return new SpriteFrame(0, w, h, px);
    }

    private static SpriteFrame column(int w, int h) {
        byte[] px = new byte[w * h];
        for (int y = 0; y < h; y++) {
            double t = y / (double) (h - 1);
            int half = (int) Math.round(Math.sin(t * Math.PI) * (w / 2.0 - 1)) + 1;
            for (int x = w / 2 - half; x <= w / 2 + half; x++) {
                set(px, w, h, x, y, 1 + ((x + y) % 3));
            }
        }
        return new SpriteFrame(0, w, h, px);
    }

    /** The eight frames of the explosion the aliens play when they are hit. */
    private static Sprite[] explosionSprites() {
        Sprite[] frames = new Sprite[8];
        for (int f = 0; f < frames.length; f++) {
            int size = 16 + f * 4;
            Sprite s = new Sprite(size, size);
            int r = size / 2;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    double d = Math.hypot(x - r, y - r) / r;
                    if (d > 1.0) {
                        continue;
                    }
                    int shade = (int) Math.round((1 - d) * 7) - f / 2;
                    if (shade > 0) {
                        s.set(x, y, Math.min(7, shade));
                    }
                }
            }
            frames[f] = s;
        }
        return frames;
    }

    // -------------------------------------------------------------- the panel

    /**
     * The 8 by 16 status font.  The original keeps it as a bitmap in
     * {@code Status/Digit.bin}; here it is rasterised from a monospaced system
     * font, with index 10 reserved for the life symbol.
     */
    private static byte[][] buildFont() {
        byte[][] font = new byte[64][];
        BufferedImage image = new BufferedImage(8, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));

        for (int i = 0; i < font.length; i++) {
            String glyph = glyphFor(i);
            byte[] rows = new byte[16];
            if (glyph != null) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, 8, 16);
                g.setColor(Color.WHITE);
                g.drawString(glyph, 0, 12);
                for (int y = 0; y < 16; y++) {
                    int bits = 0;
                    for (int x = 0; x < 8; x++) {
                        if ((image.getRGB(x, y) & 0xff) > 100) {
                            bits |= 1 << (7 - x);
                        }
                    }
                    rows[y] = (byte) bits;
                }
            }
            font[i] = rows;
        }
        g.dispose();

        // Index 10 is the life symbol, index $2b the empty slot beside it.
        byte[] life = new byte[16];
        for (int y = 3; y < 12; y++) {
            int half = 3 - Math.abs(y - 7) / 2;
            int bits = 0;
            for (int x = 4 - half; x <= 3 + half; x++) {
                bits |= 1 << (7 - x);
            }
            life[y] = (byte) bits;
        }
        font[10] = life;
        font[0x2b] = new byte[16];
        return font;
    }

    private static String glyphFor(int index) {
        if (index <= 9) {
            return String.valueOf((char) ('0' + index));
        }
        if (index >= 11 && index <= 36) {
            return String.valueOf((char) ('A' + index - 11));
        }
        return null;
    }

    /** The status bar messages, {@code Status/Text.bin}. */
    private static String[] buildTexts() {
        String[] texts = new String[16];
        texts[1] = "BACK FIRE BALL";
        texts[2] = "ROTATIVE FIRE BALL";
        texts[3] = "STOP TIME";
        texts[4] = "SEEKER";
        texts[5] = "SMART BOMB";
        texts[6] = "MEGA BLAST";
        texts[7] = "SHIELD";
        texts[8] = "FORWARD FIRE BALL";
        texts[10] = "PAUSE";
        texts[11] = "GAME OVER";
        texts[12] = "EXTRA LIFE";
        texts[13] = "PRESS FIRE TO START";
        return texts;
    }

    // ------------------------------------------------------- drawing helpers

    static void set(byte[] px, int w, int h, int x, int y, int value) {
        if (x >= 0 && y >= 0 && x < w && y < h) {
            px[y * w + x] = (byte) value;
        }
    }

    static void hline(byte[] px, int w, int h, int x0, int x1, int y, int value) {
        for (int x = x0; x <= x1; x++) {
            set(px, w, h, x, y, value);
        }
    }

    static void ellipse(byte[] px, int w, int cx, int cy, int rx, int ry, int value) {
        int h = px.length / w;
        for (int y = cy - ry; y <= cy + ry; y++) {
            for (int x = cx - rx; x <= cx + rx; x++) {
                double dx = (x - cx) / (double) Math.max(1, rx);
                double dy = (y - cy) / (double) Math.max(1, ry);
                if (dx * dx + dy * dy <= 1.0) {
                    set(px, w, h, x, y, value);
                }
            }
        }
    }

    static void triangle(byte[] px, int w, int h, int x0, int y0, int x1, int y1,
                         int x2, int y2, int value) {
        int minY = Math.min(y0, Math.min(y1, y2));
        int maxY = Math.max(y0, Math.max(y1, y2));
        for (int y = minY; y <= maxY; y++) {
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;
            int[][] edges = {{x0, y0, x1, y1}, {x1, y1, x2, y2}, {x2, y2, x0, y0}};
            for (int[] e : edges) {
                if ((y >= e[1] && y <= e[3]) || (y >= e[3] && y <= e[1])) {
                    if (e[1] == e[3]) {
                        left = Math.min(left, Math.min(e[0], e[2]));
                        right = Math.max(right, Math.max(e[0], e[2]));
                    } else {
                        int x = e[0] + (e[2] - e[0]) * (y - e[1]) / (e[3] - e[1]);
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                    }
                }
            }
            if (left <= right) {
                hline(px, w, h, left, right, y, value);
            }
        }
    }
}
