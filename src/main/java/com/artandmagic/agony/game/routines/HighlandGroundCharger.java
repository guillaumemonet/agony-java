package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Kamikaze}, the creature that rushes along the ground at the
 * sorcerer, from the SOL FONSSE SUR LE PERSO block of
 * {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>It enters at the right edge on the floor and crawls left two pixels a
 * frame, cycling the six shapes of {@code R_SK_Shape} one a frame.  The moment
 * the sorcerer comes down to the lower part of the screen, below line 120, it
 * charges: ten pixels a frame from then on, whatever the sorcerer does next.
 * It closes once it has left the screen.
 */
final class HighlandGroundCharger implements Routine {

    private static final int MODE = 0;
    private static final int SHAPE_NUM = 1;

    /** {@code R_SK_Shape}. */
    private static final int SHAPES = 0x04fdee;

    /** {@code Obj_Sol_Kamikaze_1}, as the code loads it. */
    private final int firstShape = 0x898;
    private final int[] shapes;

    HighlandGroundCharger(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 6);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        int mode = vars[MODE];

        bank.count = 1;
        bank.first = 0;
        if (mode == 0) {
            alien.x = 256 + 340;
            alien.y = 256 + 190;
            alien.objOff = firstShape;
            alien.energy = 3;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
        } else {
            vars[SHAPE_NUM]++;
            if (vars[SHAPE_NUM] == 6) {
                vars[SHAPE_NUM] = 0;
            }
            alien.objOff = shapes[vars[SHAPE_NUM]];
            alien.x -= 2;
            if (mode == 1) {
                if (state.sorcererY >= 256 + 120) {
                    vars[MODE]++;
                }
            } else {
                alien.x -= 8;
            }
        }

        // The init falls through to the same test, which it never meets.
        if (alien.x <= 220) {
            HighlandRoutines.dropPalette(state);
            return false;
        }
        return true;
    }
}
