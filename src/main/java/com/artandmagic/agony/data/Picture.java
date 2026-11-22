package com.artandmagic.agony.data;

import com.artandmagic.agony.core.Hw;

/**
 * A full screen picture as the Amiga shows it: chunky colour indices over a
 * palette of 12 bit colour registers.
 *
 * <p>Pictures of six bitplanes without HAM or dual playfield are extra half
 * brite: thirty two registers, and indices 32 to 63 show the colour of index
 * {@code n - 32} at half brightness.  The hardware halves the value held in the
 * register, so a fade written into the registers fades both halves at once,
 * which is what {@link #rgb} reproduces.
 */
public final class Picture {

    public final int width;
    public final int height;
    /** One colour index per lores pixel, row by row. */
    public final byte[] pixels;
    /** The colour registers, 12 bit $0RGB values. */
    public final int[] palette;
    /** True when indices 32..63 are the half brite copies of 0..31. */
    public final boolean ehb;

    public Picture(int width, int height, byte[] pixels, int[] palette, boolean ehb) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.palette = palette;
        this.ehb = ehb;
    }

    /**
     * Converts bitplanes to chunky pixels.
     *
     * @param data     the bytes holding the planes
     * @param offset   where the first plane starts
     * @param stride   bytes from one plane to the next
     * @param rowBytes bytes per row of a plane
     * @param planes   how many planes
     */
    public static byte[] chunky(byte[] data, int offset, int stride, int rowBytes,
                                int width, int height, int planes) {
        byte[] out = new byte[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int at = offset + y * rowBytes + (x >> 3);
                int bit = 7 - (x & 7);
                int v = 0;
                for (int p = 0; p < planes; p++) {
                    v |= ((data[at + p * stride] >> bit) & 1) << p;
                }
                out[y * width + x] = (byte) v;
            }
        }
        return out;
    }

    /**
     * The colour of an index, faded the way the loading screens do it: each
     * component of the register multiplied by {@code step} and shifted down by
     * four, sixteen being the full picture.
     */
    public int rgb(int index, int step) {
        int base = index & 31;
        int c = base < palette.length ? palette[base] : 0;
        int r = (((c >> 8) & 15) * step) >> 4;
        int g = (((c >> 4) & 15) * step) >> 4;
        int b = ((c & 15) * step) >> 4;
        if (ehb && index >= 32) {
            r >>= 1;
            g >>= 1;
            b >>= 1;
        }
        return Hw.rgb12((r << 8) | (g << 4) | b);
    }

    /** Draws the picture into an ARGB buffer, lores pixels doubled both ways. */
    public void render(int[] out, int step) {
        render(pixels, out, step);
    }

    /**
     * Draws other pixels of the same size through this picture's colours: a
     * copy of the picture something was blitted over.
     */
    public void render(byte[] pixels, int[] out, int step) {
        int[] lut = new int[64];
        for (int i = 0; i < lut.length; i++) {
            lut[i] = rgb(i, step);
        }
        int outW = width * 2;
        for (int y = 0; y < height; y++) {
            int d0 = (y * 2) * outW;
            int d1 = d0 + outW;
            for (int x = 0; x < width; x++) {
                int c = lut[pixels[y * width + x] & 63];
                out[d0 + x * 2] = c;
                out[d0 + x * 2 + 1] = c;
                out[d1 + x * 2] = c;
                out[d1 + x * 2 + 1] = c;
            }
        }
    }
}
