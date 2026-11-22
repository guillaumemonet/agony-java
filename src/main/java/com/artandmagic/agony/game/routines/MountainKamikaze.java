package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Kamikaze}, from the SOL FONSSE SUR LE PERSO block of
 * {@code AG_GAME_LMONTAGNES.S}: a thing on the ground that charges.
 *
 * <p>It rolls in from the right along the ground, two pixels a frame, cycling
 * through the six shapes of {@code R_SK_Shape}.  As soon as the sorcerer comes
 * down below line 120 it charges, adding eight pixels a frame, and it never
 * calms down again.  Past x 220 it closes.  The source has a commented out line
 * that would have let it fire; it does not.
 */
final class MountainKamikaze implements Routine {

    private static final int MODE = 0;
    private static final int SHAPE_NUM = 1;

    /** {@code R_SK_Shape}, the six words in front of the code. */
    private static final int SHAPES = 0x04c18a;

    /** {@code Obj_Sol_Kamikaze_1}. */
    private final int first = 0x4ca;
    private final int[] shapes;

    MountainKamikaze(AgonyBinary bin) {
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
            alien.objOff = first;
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

        if (alien.x <= 220) {
            MountainRoutines.close(state);
            return false;
        }
        return true;
    }
}
