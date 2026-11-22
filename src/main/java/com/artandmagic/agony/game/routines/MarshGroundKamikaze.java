package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Kamikaze}, from the SOL FONSSE SUR LE PERSO block of
 * {@code AG_GAME_LMARAIS.S}.
 *
 * <p>A creature runs in along the ground from the right, two pixels a frame,
 * cycling three frames held two frames each.  As soon as the sorcerer flies
 * low, at y 120 or below the top of the playfield, it charges: ten pixels a
 * frame from then on.  It closes once it is past x 220.
 */
final class MarshGroundKamikaze implements Routine {

    private static final int MODE = 0;
    private static final int SHAPE_NUM = 1;

    /** {@code R_SK_Shape}, six words. */
    static final int SHAPES = 0x04fd70;

    private final int[] shapes;

    MarshGroundKamikaze(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 6);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 340;
            alien.y = 256 + 190;
            alien.objOff = shapes[0];
            alien.energy = 3;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
        } else {
            bank.count = 1;
            bank.first = 0;
            vars[SHAPE_NUM]++;
            if (vars[SHAPE_NUM] == 6) {
                vars[SHAPE_NUM] = 0;
            }
            alien.objOff = shapes[vars[SHAPE_NUM]];
            alien.x -= 2;
            if (vars[MODE] == 1) {
                if (state.sorcererY >= 256 + 120) {
                    vars[MODE]++;
                }
            } else {
                alien.x -= 8;
            }
        }

        // .end, which the init goes through as well.
        if (alien.x <= 220) {
            MarshRoutines.closePalette(state);
            return false;
        }
        return true;
    }
}
