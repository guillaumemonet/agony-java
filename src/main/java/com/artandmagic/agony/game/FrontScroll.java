package com.artandmagic.agony.game;

import com.artandmagic.agony.core.Bitmap;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.Palette;
import com.artandmagic.agony.core.Sprite;
import com.artandmagic.agony.data.LevelData;

/**
 * The front playfield, {@code Ag_Front_Scroll_.s}.
 *
 * <p>The original keeps three 44 byte wide screens: one being shown, one being
 * built and one pristine copy used to rub the aliens out again.  A character
 * column is blitted into the build screen over sixteen 25 Hz frames, two pixels
 * of fine scroll at a time, and the screens rotate once the column is complete.
 *
 * <p>This port keeps the same 352 pixel wide buffer but treats it as a ring,
 * and builds a column in one go rather than over sixteen frames.  Where it
 * goes is what {@code Front_Table} says: 40 bytes into the build screen, the
 * 32 pixels just right of the 304 the window shows.  The level starts on an
 * empty front playfield and the map comes in from the right, one column per
 * character of scroll, so the scenery, the level script and the back scroll
 * stay in step; the final monster of the sea, a breaking wave in the last
 * columns of the map, is on screen when the script brings it in.  The
 * pristine copy survives as {@link #scenery}, the build screen as
 * {@link #work}.
 */
public final class FrontScroll {

    /** Width of a character, in pixels. */
    public static final int CHAR_W = 32;
    /** Height of a character, in pixels. */
    public static final int CHAR_H = 32;
    /** Ring width: eleven characters, the 44 bytes per row of the original. */
    public static final int RING_W = 11 * CHAR_W;

    /** The scenery alone, the counterpart of {@code Front_Rest_Scr}. */
    public final Bitmap scenery = new Bitmap(RING_W, Hw.SCREEN_H);
    /** The screen the aliens are drawn into, {@code Cur_Front_Build}. */
    public final Bitmap work = new Bitmap(RING_W, Hw.SCREEN_H);

    private final LevelData level;
    private final GameState state;

    /** Index of the next map column to install. */
    private int mapColumn;
    /** Index of the next palette entry, one every ten columns. */
    private int paletteIndex;
    private int paletteCount;

    public FrontScroll(LevelData level, GameState state) {
        this.level = level;
        this.state = state;
    }

    /** Ring column of the 40th byte of the build screen, {@code Front_Table}. */
    private static final int ENTRY_COLUMN = 40 * 8 / CHAR_W;

    /**
     * Sets the level up: the front playfield starts empty, the first column
     * of the map is built just right of the window, and the first palette
     * step is in.
     */
    public void prime(Palette palette) {
        installColumn(ENTRY_COLUMN);
        applyPalette(palette);
    }

    /**
     * One 25 Hz frame.  Advances the fine scroll and, every sixteen frames,
     * installs the column that has just left the screen.
     */
    public void update(Palette palette) {
        if (state.pause2 || state.stop2) {
            return;
        }

        state.frontPhase++;
        state.frontShift = (16 - state.frontPhase) * 2;
        state.vdoSInc = false;

        if (state.frontPhase >= 16) {
            state.frontShift = 32;

            paletteCount++;
            if (paletteCount >= 10) {
                paletteCount = 0;
                paletteIndex++;
                state.refreshPal = GameState.PAL_ASKED;
                applyPalette(palette);
            }
        }
    }

    /**
     * FRONT SCROLL CONTROL, the tail of the main loop.
     *
     * <p>The original waits until here, past the object pass, to add the four
     * bytes that move the three screen pointers on by a character; the objects
     * of this frame were laid down against the old start and the window has
     * already been pulled the same character to the left by a {@code
     * Front_Shift} of 32, so the two meet.  Doing it any earlier moves
     * everything on the screen a character to the right for one frame.
     */
    public void control() {
        if (state.pause2 || state.stop2 || state.frontPhase < 16) {
            return;
        }
        state.frontPhase = 0;

        // The screens move on by a character, and the next column of the map
        // is built 40 bytes into the new build screen.  The window does not
        // reach it before the middle of the column's sixteen frames, by which
        // time the original has blitted its top half; the port lays it all at
        // once.
        state.frontScroll = (state.frontScroll + CHAR_W) % RING_W;
        installColumn((state.frontScroll / CHAR_W + ENTRY_COLUMN) % (RING_W / CHAR_W));
    }

    /**
     * Copies the scenery over the build screen: the big restore blit that opens
     * {@code Ag_Object_.s}.
     */
    public void restore() {
        work.copyFrom(scenery);
    }

    /**
     * Horizontal offset the compositor must sample the ring at.
     *
     * <p>The original reaches the same place with the low nibble of
     * {@code Front_Shift} in {@code BPLCON1} and the word the bitplane pointer
     * is bumped by when bit 4 of it is clear, which together put the window at
     * {@code 32 - Front_Shift} of the screen being shown.
     */
    public int scrollOffset() {
        return Math.floorMod(state.frontScroll + (32 - state.frontShift), RING_W);
    }

    /**
     * Where the ring holds what the original calls the start of the build
     * screen.  Objects are blitted at offsets from there, not from the window:
     * the fine scroll is already in the position {@code Ag_Object_.s} works out
     * for them.
     */
    public int bufferOrigin() {
        return state.frontScroll;
    }

    /** Rewinds to the start of the map, for a restart of the level. */
    public void reset() {
        mapColumn = 0;
        paletteIndex = 0;
        paletteCount = 0;
        scenery.clear();
        work.clear();
    }

    /**
     * Installs one map column into the given ring column.
     *
     * <p>Each of the six cells is two characters.  CHAR INIT of
     * {@code Ag_Front_Scroll_.s} copies the three planes of the first one flat
     * into the screen, then ORs the three planes of the second into
     * {@code Front_Mask}; CHAR COPY blits that second character through the
     * mask with a minterm of $f2, which keeps what is underneath everywhere the
     * character has nothing.  A colour index of zero says the same thing here,
     * so the pair is a plain copy followed by a cookie cut.
     */
    private void installColumn(int ringColumn) {
        int x = ringColumn * CHAR_W;
        int cell = LevelData.FRONT_CELL;
        for (int row = 0; row < LevelData.FRONT_ROWS; row++) {
            int at = (mapColumn * LevelData.FRONT_ROWS + row) * cell;
            Sprite base = character(at);
            Sprite over = character(at + 1);
            int y = row * CHAR_H;
            if (base == null) {
                scenery.clearRect(x, y, CHAR_W, CHAR_H);
            } else {
                scenery.blitOpaque(base, x, y);
            }
            if (over != null) {
                scenery.blit(over, x, y);
            }
        }
        mapColumn++;
        if (level.frontMap.length > 0
                && mapColumn * LevelData.FRONT_ROWS * cell >= level.frontMap.length) {
            // The map ran out: the original sets Stop and Prg_Pause so the level
            // freezes and waits for the end of level routine.
            state.stop = true;
            state.prgPause = true;
            mapColumn = 0;
        }
    }

    private Sprite character(int index) {
        if (level.frontChars.length == 0 || index < 0 || index >= level.frontMap.length) {
            return null;
        }
        int ch = level.frontMap[index] & 0xff;
        return ch < level.frontChars.length ? level.frontChars[ch] : null;
    }

    /** Copies the current map palette into the six colour bands. */
    public void applyPalette(Palette palette) {
        if (level.frontPalettes.length == 0) {
            return;
        }
        int[] pal = level.frontPalettes[paletteIndex % level.frontPalettes.length];
        for (int band = 0; band < Hw.BANDS; band++) {
            palette.setFrontBand(band, pal, band * 7);
        }
    }

    /** The palette the waves and routines modulate, seven colours per band. */
    public int[] currentPalette() {
        if (level.frontPalettes.length == 0) {
            return new int[Hw.BANDS * 7];
        }
        return level.frontPalettes[paletteIndex % level.frontPalettes.length];
    }
}
