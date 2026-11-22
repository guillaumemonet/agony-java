package com.artandmagic.agony.data;

import com.artandmagic.agony.core.HwSprite;

/**
 * One frame of a hardware sprite animation.
 *
 * <p>{@code Sorcerer.bin} stores, for every frame, the vertical offset of the
 * first non empty line and the height of the frame followed by the four sprite
 * data pointers; {@code Ag_Sprites.s} reads them back with
 * {@code move (a0)+,d5 / move (a0)+,d6}.  The same two values are kept here so
 * the sprite can be positioned exactly as the original does.
 */
public final class SpriteFrame {

    /** Offset of the first visible line inside the frame. */
    public final int yOffset;
    public final int width;
    public final int height;
    /** Colour indices, 0 transparent; 1..15 for an attached pair. */
    public final byte[] pixels;

    public SpriteFrame(int yOffset, int width, int height, byte[] pixels) {
        this.yOffset = yOffset;
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    /**
     * Builds a hardware sprite object for this frame, sharing the pixel data.
     *
     * @param x    left edge on screen
     * @param y    top edge on screen, before {@link #yOffset} is applied
     * @param base base colour register
     * @param attached true when the frame comes from an attached sprite pair
     */
    public HwSprite toSprite(int x, int y, int base, boolean attached) {
        HwSprite s = new HwSprite(width, height, pixels);
        s.x = x;
        s.y = y + yOffset;
        s.colorBase = base;
        s.attached = attached;
        return s;
    }
}
