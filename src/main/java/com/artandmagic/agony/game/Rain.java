package com.artandmagic.agony.game;

import com.artandmagic.agony.core.HwSprite;
import com.artandmagic.agony.core.Palette;
import com.artandmagic.agony.core.SpriteEngine;
import com.artandmagic.agony.data.GameAssets;

/**
 * The rain of the first level, the RAIN block of the video interrupt in
 * {@code Agony_Parent_.s}.
 *
 * <p>Two sheets of twenty four drops share sprite channels 6 and 7, so the
 * effect only runs while no spell needs those channels.  Drop <i>i</i> of a
 * sheet sits eight lines below the one above it and takes its horizontal start
 * from {@code Rain_X_Table} minus <i>i</i> times the slant of the sheet, which
 * is one for the far sheet and two for the near one.  The two sheets walk the
 * table at different speeds, six bytes a frame against four.
 */
public final class Rain {

    private static final int DROPS = 24;
    /** Hardware x of the left edge of the display window. */
    private static final int DIW_X = 0x90;
    /** Hardware y the first drop of a sheet starts at. */
    private static final int FIRST_VY = 0x40;
    /** Vertical offset of the sprite DMA on a PAL machine. */
    private static final int SPR_Y_OFF = 0x40;

    /**
     * The drop itself, seven lines of two bitplanes, written out in
     * {@code Rain_Spr0}: a thin streak that thickens as it falls.
     */
    private static final int[][] DROP = {
        {0x0001, 0x0000},
        {0x0001, 0x0000},
        {0x0002, 0x0000},
        {0x0000, 0x0002},
        {0x0000, 0x0002},
        {0x0004, 0x0004},
        {0x0004, 0x0004},
    };

    private final GameState state;
    private final GameAssets assets;
    private final HwSprite drop = new HwSprite(16, DROP.length);

    public Rain(GameState state, GameAssets assets) {
        this.state = state;
        this.assets = assets;
        for (int y = 0; y < DROP.length; y++) {
            for (int x = 0; x < 16; x++) {
                int mask = 0x8000 >> x;
                int v = ((DROP[y][0] & mask) != 0 ? 1 : 0)
                        | ((DROP[y][1] & mask) != 0 ? 2 : 0);
                drop.pixels[y * 16 + x] = (byte) v;
            }
        }
    }

    /** One 50 Hz video frame. */
    public void update(SpriteEngine sprites, Palette palette) {
        if (!state.rainOn || state.iconesMode || state.die || state.curentSpell >= 0) {
            return;
        }

        palette.setAll(29, 0x0667);
        palette.setAll(30, 0x0778);
        palette.setAll(31, 0x0889);

        sheet(sprites, 6, state.rainXOff0, 1);
        sheet(sprites, 7, state.rainXOff1, 2);

        if (state.pause) {
            return;
        }
        state.rainXOff0 -= 6;
        if (state.rainXOff0 < 0) {
            state.rainXOff0 = 96;
        }
        state.rainXOff1 -= 4;
        if (state.rainXOff1 < 0) {
            state.rainXOff1 = 96;
        }
    }

    /**
     * One sheet.  The offset the original adds to the table pointer is a byte
     * count, so it indexes the table two bytes at a time.
     */
    private void sheet(SpriteEngine sprites, int channel, int offset, int slant) {
        int[] table = assets.rainX;
        if (table == null || table.length == 0) {
            return;
        }
        for (int i = 0; i < DROPS; i++) {
            int value = table[(offset / 2 + i) % table.length] - i * slant;
            HwSprite s = new HwSprite(drop.width, drop.height, drop.pixels);
            s.x = value * 2 - DIW_X;
            s.y = (FIRST_VY + i * 8) - SPR_Y_OFF;
            s.colorBase = 16 + (channel / 2) * 4;
            sprites.add(channel, s);
        }
    }
}
