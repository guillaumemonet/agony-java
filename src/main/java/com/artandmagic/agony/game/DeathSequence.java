package com.artandmagic.agony.game;

import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.Palette;
import com.artandmagic.agony.core.SpriteEngine;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.SpriteFrame;

/**
 * The death of the sorcerer, the MAIN CHAR DIE block of {@code Ag_Sprites.s}.
 *
 * <p>He bursts into eight pieces, one per sprite channel.  Each follows its own
 * path in {@code Die_Table}, a stream of signed byte pairs read two at a time;
 * a piece stops when its path reaches the $80 terminator or when it falls past
 * the bottom of the sprite area.  When the last one is gone the game either
 * ends, if no life is left, or hands the player a three second shield and takes
 * one weapon level and one axe away.
 */
public final class DeathSequence {

    /** Vertical offset of the sprite DMA on a PAL machine. */
    private static final int SPR_Y_OFF = 0x40;
    /** Hardware x of the left edge of the display window. */
    private static final int DIW_X = 0x90;

    private final GameState state;
    private final GameAssets assets;

    public DeathSequence(GameState state, GameAssets assets) {
        this.state = state;
        this.assets = assets;
    }

    /** One 50 Hz video frame. */
    public void update(SpriteEngine sprites, Palette palette) {
        if (!state.die || state.quitDelay != 0) {
            return;
        }
        if (state.dieMode == 0) {
            start(palette);
        } else {
            explode(sprites, palette);
        }
    }

    private void start(Palette palette) {
        state.life >>= 1;
        if (state.life == 0) {
            state.textNum = 11;
            state.textDelay = -1;
        }
        state.bonusMode = 0;
        java.util.Arrays.fill(state.dieStep, 0);

        state.sorcererOn = false;
        state.curentSpell = -1;
        state.fwFireOff = true;
        state.afOff = true;
        state.stop = false;

        // The colours the eight pieces burn with.
        int[][] burn = {{17, 0x0fff}, {18, 0x0cbe}, {19, 0x087a},
                        {21, 0x0fff}, {22, 0x0db9}, {23, 0x0975},
                        {25, 0x0cbe}, {26, 0x087a}, {27, 0x0547},
                        {29, 0x0db9}, {30, 0x0975}, {31, 0x0755}};
        for (int[] c : burn) {
            palette.setAll(c[0], c[1]);
        }

        // Die_X and Die_Y are hardware sprite coordinates, not screen ones.
        state.dieX = state.sorcererX - (256 - DIW_X - 16);
        state.dieY = state.sorcererY - (256 - SPR_Y_OFF - 42);
        state.dieMode = 1;
    }

    private void explode(SpriteEngine sprites, Palette palette) {
        state.sorcererOn = false;
        int finished = 0;

        for (int i = 0; i < 8; i++) {
            int step = state.dieStep[i];
            if (step < 0) {
                finished++;
                continue;
            }

            int[] path = assets.dieTraj != null && i < assets.dieTraj.length
                    ? assets.dieTraj[i] : null;
            if (path == null || step * 2 + 1 >= path.length) {
                state.dieStep[i] = -1;
                continue;
            }
            state.dieStep[i] = step + 1;

            // The pair is an offset from the burst point, not a delta.
            int hx = state.dieX + path[step * 2];
            int vy = state.dieY + path[step * 2 + 1];
            if (vy >= 256) {
                state.dieStep[i] = -1;
            }

            SpriteFrame piece = assets.diePieces[i];
            if (piece != null) {
                // One lone sprite per channel, so each pair has its own three
                // colours out of the set the death sequence installs.
                sprites.add(i, piece.toSprite(hx - DIW_X, vy - SPR_Y_OFF,
                        16 + (i / 2) * 4, false));
            }
        }

        if (finished < 8) {
            return;
        }

        if (state.life == 0) {
            state.quitDelay = 50;
            state.die = false;
            state.cleanUp = true;
            return;
        }

        // One more try: the player loses a weapon level and the lower axe.
        state.fwFireWeapon = Math.max(0, state.fwFireWeapon - 1);
        if (!state.axeDownOn) {
            state.axeUpOn = false;
        }
        state.axeDownOn = false;

        state.die = false;
        state.fwFireOff = false;
        state.curentSpell = 6;
        state.spellTime = 3;
        state.spellTimeDelay = 50;
        palette.setSpritePalette(assets.sorcererPalette);
    }
}
