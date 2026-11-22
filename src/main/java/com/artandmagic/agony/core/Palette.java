package com.artandmagic.agony.core;

/**
 * The copper driven palette.
 *
 * <p>The original rewrites the colour registers six times per frame, once every
 * 32 scanlines, which gives each vertical band its own set of colours
 * ({@code Fr_Color0a_f0} .. {@code Fr_Color5b_f1} for the front scroll and
 * {@code Back_Color0} .. {@code Back_Color5} for the back scroll, see
 * {@code Ag_Copper_List.s}).  Sprite colours (16..31) are written once per
 * frame and are therefore the same for the whole screen.
 */
public final class Palette {

    /** ARGB colours, {@code [band][register]}. */
    private final int[][] bands = new int[Hw.BANDS][32];
    /** The $0RGB words behind them, so a byte sized write can be reproduced. */
    private final int[][] raw = new int[Hw.BANDS][32];
    /**
     * The background and the sky are not held for a whole band: the copper
     * rewrites colour 0 on every scanline and colour 9 every ten, which is the
     * vertical gradient the level is painted on.  Null falls back to the band.
     */
    private int[] lineColor0;
    private int[] lineColor9;

    public Palette() {
        for (int b = 0; b < Hw.BANDS; b++) {
            bands[b][0] = 0xff000000;
        }
    }

    /** Sets colour register {@code reg} of the given band from a $0RGB word. */
    public void set(int band, int reg, int rgb12) {
        raw[band][reg] = rgb12;
        bands[band][reg] = Hw.rgb12(rgb12);
    }

    /** Sets colour register {@code reg} of every band from a $0RGB word. */
    public void setAll(int reg, int rgb12) {
        int argb = Hw.rgb12(rgb12);
        for (int b = 0; b < Hw.BANDS; b++) {
            raw[b][reg] = rgb12;
            bands[b][reg] = argb;
        }
    }

    /**
     * Writes one byte of a colour register, which is what an {@code st} on an
     * odd or even half of a colour word does.
     */
    public void setHighByte(int reg, int value) {
        for (int b = 0; b < Hw.BANDS; b++) {
            int v = ((value & 0x0f) << 8) | (raw[b][reg] & 0xff);
            raw[b][reg] = v;
            bands[b][reg] = Hw.rgb12(v);
        }
    }

    /**
     * Loads the seven front scroll colours (registers 1..7) of one band.
     * Mirrors the {@code Fr_ColorNa/Nb} block copy of {@code Ag_Playability_.s}.
     */
    public void setFrontBand(int band, int[] rgb12, int offset) {
        for (int i = 0; i < 7; i++) {
            bands[band][1 + i] = Hw.rgb12(rgb12[offset + i]);
        }
    }

    /**
     * Loads the three back scroll colours of one band.  The original duplicates
     * each colour over two registers (10/11, 12/13, 14/15), so playfield 2 only
     * ever shows three distinct shades per band plus the sky.
     */
    public void setBackBand(int band, int c0, int c1, int c2) {
        int[] p = bands[band];
        p[10] = p[11] = Hw.rgb12(c0);
        p[12] = p[13] = Hw.rgb12(c1);
        p[14] = p[15] = Hw.rgb12(c2);
    }

    /** Background colour (register 0) of one band. */
    public void setBackground(int band, int rgb12) {
        bands[band][0] = Hw.rgb12(rgb12);
    }

    /** Sky colours, registers 8 and 9 (playfield 2 with only the sky plane set). */
    public void setSky(int band, int c8, int c9) {
        bands[band][8] = Hw.rgb12(c8);
        bands[band][9] = Hw.rgb12(c9);
    }

    /** Sprite colours 16..31, as written by {@code movem.l d0-d7,Color+32(C)}. */
    public void setSpritePalette(int[] rgb12) {
        for (int i = 0; i < 16 && i < rgb12.length; i++) {
            setAll(16 + i, rgb12[i]);
        }
    }

    /** Installs the per scanline gradient of colour 0, as $0RGB words. */
    public void setBackgroundGradient(int[] rgb12) {
        lineColor0 = toArgb(rgb12);
    }

    /** Installs the per scanline gradient of colour 9, as $0RGB words. */
    public void setSkyGradient(int[] rgb12) {
        lineColor9 = toArgb(rgb12);
    }

    private static int[] toArgb(int[] rgb12) {
        if (rgb12 == null) {
            return null;
        }
        int[] out = new int[rgb12.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = Hw.rgb12(rgb12[i]);
        }
        return out;
    }

    /** Colour 0 on a given scanline, the background the playfields sit on. */
    public int background(int y, int[] regs) {
        return lineColor0 != null && y < lineColor0.length ? lineColor0[y] : regs[0];
    }

    /** Colour 9 on a given scanline, which is the sky where nothing covers it. */
    public int sky(int y, int[] regs) {
        return lineColor9 != null && y < lineColor9.length ? lineColor9[y] : regs[9];
    }

    /** Direct access to one band's register file, used by the compositor. */
    public int[] band(int band) {
        return bands[band];
    }
}
