package com.artandmagic.agony.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The eight sprite channels (DMA sprite lists) rebuilt every video frame.
 *
 * <p>{@code Ag_Sprites.s} rewrites {@code Spr0ptB..Spr7ptB} on each 50 Hz
 * interrupt and the last block of that file copies them into the copper list.
 * Here the game simply clears the channels and pushes the objects it wants for
 * the frame.
 */
public final class SpriteEngine {

    private final List<List<HwSprite>> channels = new ArrayList<>(Hw.SPRITES);

    public SpriteEngine() {
        for (int i = 0; i < Hw.SPRITES; i++) {
            channels.add(new ArrayList<>());
        }
    }

    /** Drops every object of every channel; called once per video frame. */
    public void clear() {
        for (List<HwSprite> c : channels) {
            c.clear();
        }
    }

    public void add(int channel, HwSprite sprite) {
        channels.get(channel).add(sprite);
    }

    /** Draws every channel over the composed playfields, low channel last. */
    public void render(int[] frame, int stride, Palette palette) {
        for (int c = Hw.SPRITES - 1; c >= 0; c--) {
            for (HwSprite s : channels.get(c)) {
                draw(frame, stride, palette, s);
            }
        }
    }

    private void draw(int[] frame, int stride, Palette palette, HwSprite s) {
        int y0 = Math.max(0, -s.y);
        int y1 = Math.min(s.height, Hw.SCREEN_H - s.y);
        int x0 = Math.max(0, -s.x);
        int x1 = Math.min(s.width, Hw.SCREEN_W - s.x);

        for (int y = y0; y < y1; y++) {
            int sy = s.y + y;
            int[] regs = palette.band(Math.min(Hw.BANDS - 1, sy / Hw.BAND_H));
            int srcRow = y * s.width;
            int dstRow = sy * stride + s.x;
            for (int x = x0; x < x1; x++) {
                int v = s.pixels[srcRow + x] & 0xff;
                if (v != 0) {
                    frame[dstRow + x] = regs[s.register(v) & 31];
                }
            }
        }
    }
}
