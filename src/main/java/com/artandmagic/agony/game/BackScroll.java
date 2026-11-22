package com.artandmagic.agony.game;

import com.artandmagic.agony.core.Bitmap;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.Palette;
import com.artandmagic.agony.core.Sprite;
import com.artandmagic.agony.data.LevelData;

/**
 * The back playfield and the sky, {@code Ag_Back_Scroll.s}.
 *
 * <p>The back scroll runs at half the speed of the front one: its fine scroll
 * {@code Back_Shift} is decremented once per 25 Hz frame and wraps every 32
 * pixels, where the original transfers half a character into the bitmap on each
 * of its two phases.  Behind it the sky is made of six bands that slide at
 * their own speed, which is what gives the level its depth.
 *
 * <p>The scenery is a sequence of patterns ({@code Back.pat}); each pattern
 * carries its own colours, which the copper reloads at the top of every band.
 */
public final class BackScroll {

    public static final int CHAR_W = 32;
    public static final int CHAR_H = 32;
    /**
     * Ten characters, the 40 byte rows of the back bitplanes.  {@code
     * Write_Table} fills exactly those ten columns, and the shift travels the
     * 32 pixels between them and the 288 the screen shows.
     */
    public static final int RING_W = 10 * CHAR_W;
    /** The sky rides in the same playfield, so it is the same width. */
    public static final int SKY_RING_W = 10 * CHAR_W;
    /** Where the blitter pre shift leaves the sky against the window. */
    private static final int SKY_OFFSET = 1;

    /** The screen being shown, {@code Cur_Back_Show}. */
    public Bitmap back = new Bitmap(RING_W, Hw.SCREEN_H);
    /** The screen being filled, {@code Cur_Back_Build}. */
    private Bitmap build = new Bitmap(RING_W, Hw.SCREEN_H);
    public final Bitmap sky = new Bitmap(SKY_RING_W, Hw.SCREEN_H);

    private final LevelData level;
    private final GameState state;

    /**
     * The three pattern layers the original shows at once, {@code Pat0} to
     * {@code Pat2}: which pattern each one plays, where it is in that pattern's
     * animation, the frame it currently shows and its delay counter.
     */
    private final int[] layerPattern = new int[3];
    private final int[] layerStep = new int[3];
    private final int[] layerFrame = new int[3];
    private final int[] layerDelay = new int[3];
    /** Index of the next pattern to feed into layer 2, {@code Pat_Ptr}. */
    private int nextPattern;
    /** True when the level carries the original back scroll tables. */
    private final boolean original;

    /**
     * The shift the screen on display was built for, {@code Back_Shift} as the
     * copper update reads it.  That update writes BPLCON1 and the bitplane
     * pointer and swaps the two screens in one go, before FRAME CONTROL moves
     * the shift and the configuration on, so the picture and the offset it is
     * shown at always come from the same frame.  Taking the offset after the
     * advance instead puts the two a whole character apart every time the
     * configuration changes.
     */
    private int displayShift = 31;

    /** Index into {@link LevelData#backSequence}. */
    private int sequenceIndex;
    /** Column inside the current pattern. */
    private int patternColumn;
    /** Column of the sky map. */
    private int skyColumn;
    /** Sub pixel accumulators of the six sky bands. */
    private final int[] skyFraction = new int[Hw.BANDS];

    public BackScroll(LevelData level, GameState state) {
        this.level = level;
        this.state = state;
        this.original = level.backReadTable != null && level.backFrames != null
                && level.backPatterns.length > 0;
    }

    /** Fills the ring and loads the colours of the first pattern. */
    public void prime(Palette palette) {
        if (original) {
            for (int i = 0; i < 3; i++) {
                layerPattern[i] = i;
                layerStep[i] = 0;
                layerDelay[i] = 1;
                layerFrame[i] = frameOf(i);
            }
            nextPattern = 3;
            if (level.skyBitmap != null) {
                System.arraycopy(level.skyBitmap, 0, sky.pixels, 0,
                        Math.min(level.skyBitmap.length, sky.pixels.length));
            }
            transfer(back, 0);
            transfer(back, 1);
            transfer(build, 0);
            transfer(build, 1);
            applyPalette(palette);
            return;
        }
        for (int c = 0; c < RING_W / CHAR_W; c++) {
            installColumn(c);
            installSkyColumn(c);
        }
        applyPalette(palette);
    }

    /** One 25 Hz frame. */
    public void update(Palette palette) {
        if (state.pause2) {
            return;
        }

        if (original) {
            // The sky does not move.  It is blitted into the same bitplanes as
            // the back scroll, but pre shifted right by 31 - Back_Shift, which
            // is the mirror of the 32 - Back_Shift the window is read at: the
            // two cancel and leave it a pixel off the screen origin, animating
            // in place while the scenery slides underneath it.
            java.util.Arrays.fill(state.skyScroll, SKY_OFFSET);
            updateOriginal(palette);
            return;
        }

        // Sky parallax: each band drifts at its own speed, sixteenths of a pixel.
        for (int b = 0; b < Hw.BANDS; b++) {
            int speed = b < level.skySpeed.length ? level.skySpeed[b] : 1;
            skyFraction[b] += speed;
            state.skyScroll[b] = (state.skyScroll[b] + skyFraction[b] / 16) % SKY_RING_W;
            skyFraction[b] %= 16;
        }

        if (state.stop2) {
            return;
        }

        state.backPhase ^= 1;
        if (state.backPhase != 0) {
            return;
        }

        state.backShift = (state.backShift - 1) & 31;
        state.backScroll = (state.backScroll + 1) % RING_W;

        if (state.backShift == 31) {
            state.backConfig += 240;
            if (state.backConfig >= 5 * 240) {
                state.backConfig = 0;
            }
            installColumn(((state.backScroll + Hw.SCREEN_W) / CHAR_W) % (RING_W / CHAR_W));
            installSkyColumn(((state.backScroll + Hw.SCREEN_W) / CHAR_W) % (SKY_RING_W / CHAR_W));
            applyPalette(palette);
        }
    }

    /** Horizontal offset the compositor must sample the back ring at. */
    public int scrollOffset() {
        if (original) {
            // Playfield 2 reaches its window the same way playfield 1 does: the
            // low nibble of Back_Shift in BPLCON1 and the word the pointer is
            // bumped by when bit 4 of it is clear.
            return Math.floorMod(32 - displayShift, RING_W);
        }
        return Math.floorMod(state.backScroll, RING_W);
    }

    /**
     * One 25 Hz frame of the original back scroll.
     *
     * <p>Half of the sixty character cells are redrawn every frame, alternating,
     * and every other frame the fine scroll advances.  Each 32 pixels the
     * configuration moves on, which slides the whole backdrop by one character,
     * and once the five configurations are through, the three pattern layers
     * shift down and a new pattern comes in at the top.
     */
    private void updateOriginal(Palette palette) {
        if (state.backPhase == 0) {
            // The copper update: the shift on show is the one this screen was
            // built with, and the two screens change hands at the same moment.
            displayShift = state.backShift;
            Bitmap shown = back;
            back = build;
            build = shown;

            // FRAME CONTROL: Stop2 freezes the shift and the configuration,
            // and with them the pattern rotation, but the screens keep
            // changing hands and the patterns keep animating.  The end of the
            // level relies on it: the backdrop the boss sits on stays put.
            if (!state.stop2) {
                state.backShift = (state.backShift - 1) & 31;
                if (state.backShift == 31) {
                    state.backConfig += 240;
                    if (state.backConfig >= 5 * 240) {
                        state.backConfig = 0;
                        rotatePatterns();
                        applyPalette(palette);
                    }
                }
            }
            advanceFrames();
        }
        transfer(build, state.backPhase);
        state.backPhase ^= 1;
    }

    /** Pat0 takes Pat1, Pat1 takes Pat2 and a new pattern arrives in Pat2. */
    private void rotatePatterns() {
        layerPattern[0] = layerPattern[1];
        layerStep[0] = layerStep[1];
        layerFrame[0] = layerFrame[1];
        layerDelay[0] = layerDelay[1];
        layerPattern[1] = layerPattern[2];
        layerStep[1] = layerStep[2];
        layerFrame[1] = layerFrame[2];
        layerDelay[1] = layerDelay[2];

        if (nextPattern >= level.backPatterns.length) {
            // The backdrop ran out: the original stops the level here.  It
            // does not start the list again; the scroll freezes on the last
            // two patterns, so the third layer just repeats the last one
            // rather than bringing the first pattern of the level back in.
            state.stop = true;
            state.prgPause = true;
            layerPattern[2] = level.backPatterns.length - 1;
        } else {
            layerPattern[2] = nextPattern++;
        }
        layerStep[2] = 0;
        layerDelay[2] = 1;
        layerFrame[2] = frameOf(2);
    }

    /** Runs the delay counters of the three layers and picks their frame. */
    private void advanceFrames() {
        for (int i = 0; i < 3; i++) {
            if (--layerDelay[i] > 0) {
                continue;
            }
            int[] seq = sequenceOf(i);
            if (seq.length < 2) {
                layerDelay[i] = 1;
                continue;
            }
            layerStep[i] += 2;
            if (layerStep[i] >= seq.length) {
                layerStep[i] = 0;
            }
            layerFrame[i] = seq[layerStep[i]];
            layerDelay[i] = Math.max(1, seq[layerStep[i] + 1]);
        }
    }

    private int[] sequenceOf(int layer) {
        int p = layerPattern[layer];
        if (p < 0 || p >= level.backPatterns.length) {
            return new int[0];
        }
        return level.backPatterns[p].sequence;
    }

    private int frameOf(int layer) {
        int[] seq = sequenceOf(layer);
        return seq.length >= 2 ? seq[0] : 0;
    }

    /**
     * Redraws thirty character cells, the half of the screen this phase owns,
     * reading each one through {@code Read_Table} and placing it through
     * {@code Write_Table}.
     */
    private void transfer(Bitmap target, int phase) {
        int[] read = level.backReadTable;
        int[] write = level.backWriteTable;
        int base = (state.backConfig / 240) * 120 + phase * 60;

        for (int i = 0; i < 30; i++) {
            int layer = read[base + i * 2];
            int inFrame = read[base + i * 2 + 1];
            if (layer > 2) {
                layer = 2;
            }
            int cell = layerFrame[layer] * 30 + inFrame / 2;
            int ch = cell >= 0 && cell < level.backFrames.length ? level.backFrames[cell] : 0;

            int slot = phase * 30 + i;
            int column = write[slot * 2];
            int band = write[slot * 2 + 1];
            int x = column * CHAR_W;
            int y = band * CHAR_H;

            if (ch >= 0 && ch < level.backChars.length) {
                target.blitOpaque(level.backChars[ch], x, y);
            } else {
                target.clearColumns(x, CHAR_W);
            }
        }
    }

    /** Rewinds to the first pattern, for a restart of the level. */
    public void reset() {
        sequenceIndex = 0;
        patternColumn = 0;
        skyColumn = 0;
        java.util.Arrays.fill(skyFraction, 0);
        back.clear();
        build.clear();
        sky.clear();
    }

    private LevelData.BackPattern pattern() {
        if (level.backPatterns.length == 0) {
            return null;
        }
        int[] seq = level.backSequence;
        if (seq.length == 0) {
            return level.backPatterns[0];
        }
        int index = seq[sequenceIndex % seq.length];
        if (index < 0) {
            sequenceIndex = 0;
            index = seq[0];
            // The original raises Stop and Prg_Pause when the pattern list ends.
            state.stop = true;
            state.prgPause = true;
        }
        return level.backPatterns[Math.floorMod(index, level.backPatterns.length)];
    }

    private void installColumn(int ringColumn) {
        LevelData.BackPattern pat = pattern();
        int x = ringColumn * CHAR_W;
        if (pat == null || pat.map.length == 0) {
            back.clearColumns(x, CHAR_W);
            return;
        }
        int columns = pat.map.length / LevelData.FRONT_ROWS;
        if (patternColumn >= columns) {
            patternColumn = 0;
            sequenceIndex++;
            pat = pattern();
            if (pat == null || pat.map.length == 0) {
                back.clearColumns(x, CHAR_W);
                return;
            }
        }
        for (int row = 0; row < LevelData.FRONT_ROWS; row++) {
            int index = patternColumn * LevelData.FRONT_ROWS + row;
            int ch = index < pat.map.length ? pat.map[index] & 0xff : 0;
            if (ch < level.backChars.length) {
                back.blitOpaque(level.backChars[ch], x, row * CHAR_H);
            } else {
                back.clearColumns(x, CHAR_W);
            }
        }
        patternColumn++;
    }

    private void installSkyColumn(int ringColumn) {
        if (level.skyChars.length == 0 || level.skyMap.length == 0) {
            return;
        }
        int x = ringColumn * CHAR_W;
        for (int row = 0; row < LevelData.FRONT_ROWS; row++) {
            int index = (skyColumn * LevelData.FRONT_ROWS + row) % level.skyMap.length;
            int ch = level.skyMap[index] & 0xff;
            if (ch < level.skyChars.length) {
                sky.blitOpaque(level.skyChars[ch], x, row * CHAR_H);
            }
        }
        skyColumn++;
    }

    /** Loads the colours of the current pattern into the six bands. */
    public void applyPalette(Palette palette) {
        LevelData.BackPattern pat = original
                ? level.backPatterns[Math.floorMod(layerPattern[0], level.backPatterns.length)]
                : pattern();
        if (pat == null) {
            return;
        }
        for (int band = 0; band < Hw.BANDS; band++) {
            palette.setBackground(band, pat.background[band]);
            palette.setBackBand(band, pat.colors[band][0], pat.colors[band][1], pat.colors[band][2]);
            palette.setSky(band, pat.sky[band][0], pat.sky[band][1]);
        }
    }
}
