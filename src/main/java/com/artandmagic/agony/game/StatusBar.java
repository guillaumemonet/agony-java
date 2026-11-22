package com.artandmagic.agony.game;

import com.artandmagic.agony.core.Bitmap;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.data.GameAssets;

/**
 * The status line, {@code Ag_Status.s}.
 *
 * <p>It is a single hires bitplane of 76 characters by 19 lines, drawn with the
 * same eight by sixteen font as the messages.  The score sits at character 38,
 * the spell timer at character 12 and the five life symbols at character 68;
 * a message takes the whole line over for as long as {@code Text_Delay} lasts.
 *
 * <p>Score and bonus counter are packed decimal, added with the 68000
 * {@code abcd} instruction, and every 80000 points award an extra life.
 */
public final class StatusBar {

    /** Characters per line. */
    private static final int COLUMNS = 76;
    /** Character cell size. */
    private static final int CHAR_W = 8;
    private static final int CHAR_H = 16;

    /** Font index of the life symbol and of the empty life slot. */
    private static final int LIFE_ON = 10;
    private static final int LIFE_OFF = 0x2b;

    public final Bitmap bitmap = new Bitmap(Hw.STATUS_W, Hw.STATUS_H);

    private final GameState state;
    private final GameAssets assets;

    public StatusBar(GameState state, GameAssets assets) {
        this.state = state;
        this.assets = assets;
    }

    /** One 25 Hz game frame. */
    public void update() {
        if (state.textDelay == 0) {
            score();
            spellTime();
            life();
        }
        text();
    }

    // ---------------------------------------------------------------- scoring

    private void score() {
        if (state.point != 0) {
            state.score = Bcd.add(state.score, state.point);
            state.extraLife = Bcd.add(state.extraLife, state.point);
            state.point = 0;

            if (state.extraLife >= 0x80000) {
                state.life = ((state.life << 1) | 1) & 0b11111;
                state.textNum = 12;
                state.textDelay = 50;
                state.extraLife = 0;
            }
            state.refreshStatus = true;
        }

        if (!state.refreshStatus) {
            state.statusDelay++;
            if (state.statusDelay < 20) {
                return;
            }
            state.statusDelay = 0;
        }
        state.refreshStatus = false;

        if (state.score == state.oldScore) {
            return;
        }
        state.oldScore = state.score;
        for (int digit = 0; digit < 6; digit++) {
            int value = (state.score >>> ((5 - digit) * 4)) & 0xf;
            drawChar(38 + digit, value);
        }
    }

    private void spellTime() {
        if (state.spellTime == state.oldSpellTime) {
            return;
        }
        state.oldSpellTime = state.spellTime;
        drawChar(12, (state.spellTime >>> 4) & 0xf);
        drawChar(13, state.spellTime & 0xf);
    }

    private void life() {
        if (state.life == state.oldLife) {
            return;
        }
        state.oldLife = state.life;
        for (int i = 0; i < 5; i++) {
            boolean on = (state.life & (1 << (4 - i))) != 0;
            drawChar(68 + i, on ? LIFE_ON : LIFE_OFF);
        }
    }

    // --------------------------------------------------------------- messages

    private void text() {
        if (state.textDelay == 0) {
            return;
        }
        state.textDelay--;

        if (state.textNum != 0 && state.textNum != state.oldTextNum) {
            state.oldTextNum = state.textNum;
            int num = state.textNum;
            state.textNum = 0;

            bitmap.clear();
            int[] indices = assets.textIndices != null && num > 0 && num < assets.textIndices.length
                    ? assets.textIndices[num] : null;
            if (indices != null) {
                for (int i = 0; i < indices.length; i++) {
                    drawChar(10 + i, indices[i]);
                }
            } else {
                String sentence = num > 0 && num < assets.texts.length ? assets.texts[num] : null;
                if (sentence != null) {
                    drawText(10, sentence);
                }
            }
        }

        if (state.textDelay == 1) {
            // The message is over: force a full redraw of the panel.
            bitmap.clear();
            state.oldScore = -1;
            state.oldSpellTime = -1;
            state.oldLife = -1;
            state.refreshStatus = true;
            state.oldTextNum = -1;
        }
    }

    private void drawText(int column, String sentence) {
        for (int i = 0; i < sentence.length() && column + i < COLUMNS; i++) {
            drawChar(column + i, fontIndex(sentence.charAt(i)));
        }
    }

    /**
     * Maps a character onto the font; the original stores sentences already
     * encoded as font indices in {@code Status/Text.bin}.
     */
    private static int fontIndex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'Z') {
            return 11 + (c - 'A');
        }
        if (c >= 'a' && c <= 'z') {
            return 11 + (c - 'a');
        }
        return 37;
    }

    private void drawChar(int column, int index) {
        if (column < 0 || column >= COLUMNS) {
            return;
        }
        byte[] glyph = index >= 0 && index < assets.font.length ? assets.font[index] : null;
        int x = column * CHAR_W;
        for (int y = 0; y < CHAR_H && y < Hw.STATUS_H; y++) {
            int bits = glyph != null ? glyph[y] & 0xff : 0;
            for (int b = 0; b < CHAR_W; b++) {
                bitmap.set(x + b, y, (bits >> (7 - b)) & 1);
            }
        }
    }

    /** Wipes the panel, as the original does when the level starts. */
    public void clear() {
        bitmap.clear();
        state.oldScore = -1;
        state.oldSpellTime = -1;
        state.oldLife = -1;
        state.oldTextNum = -1;
    }
}
