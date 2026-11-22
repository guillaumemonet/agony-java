package com.artandmagic.agony.core;

/**
 * Hardware / geometry constants of the original Amiga version.
 *
 * <p>The original runs a PAL lores screen with a dual playfield of 6 bitplanes:
 * <ul>
 *   <li>playfield 1 (odd planes) : the front scroll, 3 planes, colours 1..7,
 *       colour 0 being transparent so playfield 2 shows through,</li>
 *   <li>playfield 2 (even planes): the sky (1 plane) plus the back scroll
 *       (2 planes), colours 8..15.</li>
 * </ul>
 * Below the playfield sits a 19 line hires status bar (76 bytes per line,
 * one bitplane) drawn by a second part of the copper list.
 *
 * <p>Original sources: {@code Agony_Parent_.s} (screen buffers) and
 * {@code Ag_Copper_List.s} (DIWSTRT/DDFSTRT/BPLCON0).
 */
public final class Hw {

    private Hw() {
    }

    /**
     * Visible playfield width in lores pixels.  The game screen of
     * {@code Ag_Copper_List.s} opens the display window from DIWSTRT $90 to a
     * DIWSTOP of $b0, which the hardware reads as 432, so 288 pixels are shown.
     *
     * <p>The fetch is wider on purpose: DDFSTRT $38 to DDFSTOP $c8 is 19 words,
     * 304 pixels, and the two extra words are the run up the fine scroll of
     * BPLCON1 delays into.  That is what leaves the front playfield 64 pixels of
     * slack on its 44 byte rows (BPL1MOD 6) and the back 32 on its 40 byte ones
     * (BPL2MOD 2), which is exactly the travel each of their shifts has.
     */
    public static final int SCREEN_W = 36 * 8;
    /** Visible playfield height in scanlines. */
    public static final int SCREEN_H = 192;

    /** Width of a front scroll buffer, 44 bytes per bitplane row. */
    public static final int FRONT_W = 44 * 8;
    /** Width of a back scroll buffer, 40 bytes per bitplane row. */
    public static final int BACK_W = 40 * 8;

    /**
     * Status bar width in hires pixels.  Its own DIWSTOP of $a0 stops it at
     * 416, so it is 272 lores pixels wide and sits inside the playfield.
     */
    public static final int STATUS_W = 272 * 2;
    /** Status bar height, in scanlines. */
    public static final int STATUS_H = 19;

    /**
     * Output frame buffer width, hires equivalent.  The status bar fetches 38
     * hires words for its 76 byte rows, so both parts of the screen are exactly
     * this wide.
     */
    public static final int OUT_W = SCREEN_W * 2;
    /**
     * The status bar starts at the same hardware position as the playfield, so
     * its 608 hires pixels sit against the left edge of the 640 the playfield
     * covers.
     */
    public static final int STATUS_X = 0;
    /** Output frame buffer height (every scanline is doubled). */
    public static final int OUT_H = (SCREEN_H + STATUS_H) * 2;

    /**
     * The game keeps all sprite coordinates biased by 256 so that a plain
     * {@code move} keeps the sign bit free (see {@code Sorcerer_X} and friends
     * in {@code Agony_Parent_.s}).
     */
    public static final int BIAS = 256;

    /** Number of vertical colour bands of the copper palette (32 lines each). */
    public static final int BANDS = 6;
    /** Height of one colour band in scanlines. */
    public static final int BAND_H = SCREEN_H / BANDS;

    /** Number of hardware sprite channels. */
    public static final int SPRITES = 8;

    /** Logic runs at 25 Hz, the video/sprite interrupt at 50 Hz. */
    public static final int VBL_HZ = 50;

    /** Converts an Amiga $0RGB colour word into a packed ARGB value. */
    public static int rgb12(int c) {
        int r = (c >> 8) & 0xf;
        int g = (c >> 4) & 0xf;
        int b = c & 0xf;
        return 0xff000000 | (r * 17 << 16) | (g * 17 << 8) | (b * 17);
    }
}
