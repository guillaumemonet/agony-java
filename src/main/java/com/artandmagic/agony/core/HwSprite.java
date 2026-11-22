package com.artandmagic.agony.core;

/**
 * One hardware sprite object.
 *
 * <p>A channel of the Amiga can display any number of sprites one below the
 * other, each described by two control words followed by its bitplane data;
 * Agony uses that heavily (twelve alien shots share channel 5, twenty four rain
 * drops share channels 6 and 7).  Each object here is one of those.
 *
 * <p>A lone sprite has three colours, a pair of attached sprites has fifteen.
 * The sorcerer uses channels 0+1 and 2+3 attached, which is why he is 32 pixels
 * wide in 16 colours; see the {@code Spr0ptB..Spr3ptB} setup in
 * {@code Ag_Sprites.s}.
 */
public final class HwSprite {

    /** Left edge, in screen pixels. */
    public int x;
    /** Top edge, in screen pixels. */
    public int y;
    /** Sprite width in pixels (16 for a single sprite, 32 for an attached pair). */
    public final int width;
    /** Number of scanlines. */
    public final int height;
    /** Colour indices, {@code width * height} bytes, 0 being transparent. */
    public final byte[] pixels;
    /**
     * Base colour register.  A lone sprite pair {@code n} uses 16 + 4n, so its
     * three colours are base+1..base+3; an attached pair uses the whole 16..31
     * range and stores values 1..15 directly.
     */
    public int colorBase = 16;
    /** True when the object comes from an attached pair (16 colours). */
    public boolean attached;

    public HwSprite(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new byte[width * height];
    }

    public HwSprite(int width, int height, byte[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    /** Resolves a stored pixel value to a palette register. */
    public int register(int value) {
        return attached ? 16 + value : colorBase + value;
    }
}
