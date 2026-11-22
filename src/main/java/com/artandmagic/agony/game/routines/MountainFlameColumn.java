package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Colonne_Flamme}, from the COLONNE DE FLAMME block of
 * {@code AG_GAME_LMONTAGNES.S}: a column of fire rising from the ground.
 *
 * <p>Four flames are stacked thirty five pixels apart from the ground up, all
 * drifting left two pixels a frame and cycling through the eight shapes of
 * {@code R_CF_Shape}; they have 32767 points of energy, so they are meant to
 * be dodged, not shot.  How many of them show is the alien count of the bank,
 * and every three frames the routine sets it from the next word of
 * {@code R_CF_Hight}, so the column stands one flame high for a while, grows to
 * four and sinks back.  It closes when the column has drifted past x 200.
 *
 * <p>The source means to start the four flames at different shapes, but its
 * {@code move.l #$00000002,R_CF_Shape_Num_0} has no {@code (a3)}: it writes
 * to absolute address 2 and 6 instead of the routine variables, which the slot
 * has just cleared.  All four flames therefore flicker in step, and the port
 * keeps it that way.
 */
final class MountainFlameColumn implements Routine {

    private static final int MODE = 0;
    /** {@code R_CF_Shape_Num_0} to {@code _3}, byte offsets into the shapes. */
    private static final int SHAPE_NUM = 1;
    private static final int HIGHT_NUM = 5;
    private static final int HIGHT_DELAY = 6;

    /** {@code R_CF_Shape}, then {@code R_CF_Hight} right behind it. */
    private static final int SHAPES = 0x04c246;
    private static final int HIGHTS = 0x04c256;

    /** {@code Obj_Grande_Flamme_1}. */
    private final int first = 0x198;
    private final int[] shapes;
    private final int[] hights;

    MountainFlameColumn(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 8);
        hights = SeaRoutines.words(bin, HIGHTS, 16);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            int y = 256 + 190;
            for (int i = 0; i < 4; i++) {
                Alien flame = bank.aliens[i];
                flame.x = 256 + 360;
                flame.y = y;
                flame.objOff = first;
                flame.energy = 32767;
                flame.status = 0;
                flame.fireRateStat = 0;
                flame.fireRateDyn = 0;
                y -= 35;
            }
            // The two move.l meant for the shape numbers land at absolute
            // addresses 2 and 6; see the class comment.
            vars[MODE]++;
            vars[HIGHT_NUM] = 0;
            vars[HIGHT_DELAY] = 0;
        } else {
            // The header is not rewritten here: the count stays whatever the
            // height table last put in it.
            for (int i = 0; i < 4; i++) {
                int num = (vars[SHAPE_NUM + i] + 2) & 0xf;
                vars[SHAPE_NUM + i] = num;
                Alien flame = bank.aliens[i];
                flame.objOff = shapes[num / 2];
                flame.x -= 2;
            }

            vars[HIGHT_DELAY]++;
            if (vars[HIGHT_DELAY] == 3) {
                vars[HIGHT_DELAY] = 0;
                vars[HIGHT_NUM] = (vars[HIGHT_NUM] + 2) & 0x1f;
                bank.count = hights[vars[HIGHT_NUM] / 2];
            }
        }

        if (bank.aliens[0].x <= 200) {
            MountainRoutines.close(state);
            return false;
        }
        return true;
    }
}
