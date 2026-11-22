package com.artandmagic.agony.core;

/**
 * A chunky replacement for an Amiga bitplane buffer.
 *
 * <p>The original keeps the screens as interleaved bitplanes and pushes pixels
 * around with the blitter ({@code Ag_Object_.s} holds twelve hand written blit
 * routines, one per sprite size and shift).  A byte per pixel is a faithful and
 * far simpler model in Java: the stored value is the playfield colour index,
 * exactly what the bitplanes encode, and index 0 stays transparent.
 */
public final class Bitmap {

    public final int width;
    public final int height;
    public final byte[] pixels;

    public Bitmap(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new byte[width * height];
    }

    public void clear() {
        java.util.Arrays.fill(pixels, (byte) 0);
    }

    /** Copies the whole of {@code src} over this bitmap (the restore blit). */
    public void copyFrom(Bitmap src) {
        System.arraycopy(src.pixels, 0, pixels, 0, Math.min(pixels.length, src.pixels.length));
    }

    public void set(int x, int y, int value) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        pixels[y * width + x] = (byte) value;
    }

    /**
     * Cookie cut blit with clipping on all four edges: the counterpart of the
     * {@code N_Blit}, {@code H_C_Blit} and {@code V_C_Blit} families.
     * Source pixels equal to zero are transparent.
     */
    public void blit(Sprite sprite, int dx, int dy) {
        byte[] src = sprite.pixels;
        int sw = sprite.width;
        int sh = sprite.height;

        int x0 = Math.max(0, -dx);
        int y0 = Math.max(0, -dy);
        int x1 = Math.min(sw, width - dx);
        int y1 = Math.min(sh, height - dy);

        for (int y = y0; y < y1; y++) {
            int srcRow = y * sw;
            int dstRow = (dy + y) * width + dx;
            for (int x = x0; x < x1; x++) {
                byte v = src[srcRow + x];
                if (v != 0) {
                    pixels[dstRow + x] = v;
                }
            }
        }
    }

    /** Opaque blit, used when building the scroll buffers out of characters. */
    public void blitOpaque(Sprite sprite, int dx, int dy) {
        byte[] src = sprite.pixels;
        int sw = sprite.width;
        int sh = sprite.height;

        int x0 = Math.max(0, -dx);
        int y0 = Math.max(0, -dy);
        int x1 = Math.min(sw, width - dx);
        int y1 = Math.min(sh, height - dy);

        for (int y = y0; y < y1; y++) {
            System.arraycopy(src, y * sw + x0, pixels, (dy + y) * width + dx + x0, x1 - x0);
        }
    }

    /** Fills one character cell with zero. */
    public void clearRect(int x, int y, int w, int h) {
        for (int row = Math.max(0, y); row < Math.min(height, y + h); row++) {
            int at = row * width;
            for (int i = Math.max(0, x); i < Math.min(width, x + w); i++) {
                pixels[at + i] = 0;
            }
        }
    }

    /** Fills a column range with zero, used when recycling scroll buffers. */
    public void clearColumns(int x, int w) {
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int i = 0; i < w; i++) {
                int xx = x + i;
                if (xx >= 0 && xx < width) {
                    pixels[row + xx] = 0;
                }
            }
        }
    }
}
