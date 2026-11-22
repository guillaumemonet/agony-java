package com.artandmagic.agony.core;

/**
 * The compositor: it turns the two playfields, the sky, the eight sprite
 * channels and the status bar into one ARGB frame.
 *
 * <p>Priority follows the dual playfield rules used by the copper list:
 * playfield 1 (the front scroll) wins wherever it is not colour 0, otherwise
 * playfield 2 (sky + back scroll) shows through, and colour 0 of playfield 2 is
 * the background colour of the band.  Sprites are drawn above both.
 */
public final class Screen {

    /** Composed playfield, 320x192, one ARGB value per lores pixel. */
    private final int[] playfield = new int[Hw.SCREEN_W * Hw.SCREEN_H];
    /** Output frame, lores pixels doubled in both directions. */
    private final int[] frame = new int[Hw.OUT_W * Hw.OUT_H];
    /**
     * Brightness the frame is scaled by, 256 being untouched.  The original
     * leaves a level by calling {@code SetFade} on each of the four colour
     * banks and waiting fifty frames; that routine is in {@code WORK:Global}
     * and not in the source release, so the port simply ramps the whole frame
     * down over the same delay.
     */
    private int fade = 256;

    /** Sets the fade, 0 for black and 256 for the full picture. */
    public void setFade(int level) {
        fade = Math.max(0, Math.min(256, level));
    }

    /**
     * Colour 3 of the status bar, stepped down the gradient the copper writes.
     *
     * <p>{@code Ag_Copper_List.s} repeats a pair of waits eight times from line
     * $2d, writing $653 and adding $111 after each pair.  That covers the first
     * sixteen lines only; the three that are left before the playfield starts
     * at $3f keep the last colour the copper wrote.
     */
    private static final int STATUS_STEPS = 8;

    private final int[] statusGradient = new int[Hw.STATUS_H];

    {
        int colour = 0x653;
        for (int y = 0; y < Hw.STATUS_H; y++) {
            statusGradient[y] = Hw.rgb12(colour);
            if ((y & 1) == 1 && y / 2 < STATUS_STEPS - 1) {
                colour += 0x111;
            }
        }
    }

    public int[] frame() {
        return frame;
    }

    /**
     * Composes the playfields.
     *
     * @param front     front scroll ring buffer (playfield 1, colours 1..7)
     * @param frontScroll horizontal offset into that ring
     * @param back      back scroll ring buffer (playfield 2 high bits)
     * @param backScroll  horizontal offset into that ring
     * @param sky       sky ring buffer (playfield 2 low bit), may be null
     * @param skyScroll   per band horizontal offset into the sky
     */
    public void compose(Bitmap front, int frontScroll,
                        Bitmap back, int backScroll,
                        Bitmap sky, int[] skyScroll,
                        Palette palette) {

        int frontOff = Math.floorMod(frontScroll, front.width);
        int backOff = Math.floorMod(backScroll, back.width);

        for (int y = 0; y < Hw.SCREEN_H; y++) {
            int band = y / Hw.BAND_H;
            int[] regs = palette.band(band);
            int frontRow = y * front.width;
            int backRow = y * back.width;
            int skyRow = sky != null ? y * sky.width : 0;
            int skyOff = (sky != null && skyScroll != null)
                    ? Math.floorMod(skyScroll[band], sky.width) : 0;
            int out = y * Hw.SCREEN_W;

            for (int x = 0; x < Hw.SCREEN_W; x++) {
                int fx = (x + frontOff) % front.width;
                int pf1 = front.pixels[frontRow + fx] & 0x7;
                int argb;
                if (pf1 != 0) {
                    argb = regs[pf1];
                } else {
                    int bx = (x + backOff) % back.width;
                    int pf2 = (back.pixels[backRow + bx] & 0x3) << 1;
                    if (sky != null) {
                        int sx = (x + skyOff) % sky.width;
                        pf2 |= sky.pixels[skyRow + sx] & 1;
                    }
                    // Colour 0 is the background gradient, colour 9 the sky on
                    // its own; the rest belong to the band.
                    argb = switch (pf2) {
                        case 0 -> palette.background(y, regs);
                        case 1 -> palette.sky(y, regs);
                        default -> regs[8 + pf2];
                    };
                }
                playfield[out + x] = argb;
            }
        }
    }

    /** Draws the sprite channels over the composed playfield. */
    public void renderSprites(SpriteEngine sprites, Palette palette) {
        sprites.render(playfield, Hw.SCREEN_W, palette);
    }

    /**
     * Scales the playfield into the output frame and puts the status bar above
     * it, which is where the display window starts.
     */
    public void present(Bitmap status) {
        int statusRows = Hw.STATUS_H * 2 * Hw.OUT_W;
        java.util.Arrays.fill(frame, 0, statusRows, 0xff000000);

        if (status != null) {
            for (int y = 0; y < Hw.STATUS_H && y < status.height; y++) {
                int src = y * status.width;
                int d0 = (y * 2) * Hw.OUT_W + Hw.STATUS_X;
                int d1 = d0 + Hw.OUT_W;
                int colour = statusGradient[y];
                int wide = Math.min(Math.min(Hw.STATUS_W, status.width), Hw.OUT_W - Hw.STATUS_X);
                for (int x = 0; x < wide; x++) {
                    if (status.pixels[src + x] != 0) {
                        frame[d0 + x] = colour;
                        frame[d1 + x] = colour;
                    }
                }
            }
        }

        for (int y = 0; y < Hw.SCREEN_H; y++) {
            int src = y * Hw.SCREEN_W;
            int d0 = statusRows + (y * 2) * Hw.OUT_W;
            int d1 = d0 + Hw.OUT_W;
            for (int x = 0; x < Hw.SCREEN_W; x++) {
                int c = playfield[src + x];
                int dx = x * 2;
                frame[d0 + dx] = c;
                frame[d0 + dx + 1] = c;
                frame[d1 + dx] = c;
                frame[d1 + dx + 1] = c;
            }
        }

        if (fade < 256) {
            for (int i = 0; i < frame.length; i++) {
                int c = frame[i];
                int r = ((c >> 16 & 0xff) * fade) >> 8;
                int g = ((c >> 8 & 0xff) * fade) >> 8;
                int bl = ((c & 0xff) * fade) >> 8;
                frame[i] = 0xff000000 | (r << 16) | (g << 8) | bl;
            }
        }
    }
}
