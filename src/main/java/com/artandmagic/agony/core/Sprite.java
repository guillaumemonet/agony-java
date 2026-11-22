package com.artandmagic.agony.core;

/**
 * One entry of {@code Objects.bin} / {@code Objects.sst}: a blitter object with
 * its own size and its pixels stored as playfield colour indices (0 = mask off).
 *
 * <p>The original stores a 3 bitplane image plus a precomputed mask plane built
 * at startup by {@code Ag_Pre_comp.s}; a colour index of zero carries exactly
 * the same information, so the mask is implicit here.
 */
public final class Sprite {

    public final int width;
    public final int height;
    public final byte[] pixels;

    public Sprite(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new byte[width * height];
    }

    public Sprite(int width, int height, byte[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    public void set(int x, int y, int v) {
        if (x >= 0 && y >= 0 && x < width && y < height) {
            pixels[y * width + x] = (byte) v;
        }
    }
}
