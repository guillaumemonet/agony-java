package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Final_2}, the copy the boss of the highlands throws, from the
 * FINAL 2 (Slave) block of {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>The script never names it: {@link HighlandFinalMonster} starts it every
 * eighteen frames, at its own position, which it reads from {@code P_F2_X} and
 * {@code P_F2_Y}.  It looks like the boss, plays the same fourteen shapes from
 * a table of its own, {@code R_F2_Shape}, but has only four points of energy.
 * It flies left eight pixels a frame, closing on the sorcerer's height one
 * pixel a frame, and closes once it is past x 220.
 *
 * <p>All the slaves share the original's single {@code Par_Buffer}; a slave
 * reads it on its first frame only, before the boss writes it again, so handing
 * each one its own copy of the two words comes to the same thing.
 */
final class HighlandFinalSlave implements Routine {

    private static final int MODE = 0;
    private static final int ANIM = 1;

    private static final int P_X = 0;
    private static final int P_Y = 1;

    /** {@code R_F2_Shape}. */
    private static final int SHAPES = 0x050a98;

    /** {@code Obj_Final_1}, as the code loads it. */
    private final int firstShape = 0xae;

    private final int[] shapes;

    HighlandFinalSlave(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 14);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        bank.count = 1;
        bank.first = 0;

        if (vars[MODE] == 0) {
            alien.x = HighlandRoutines.param(params, P_X, 256 + 240);
            alien.y = HighlandRoutines.param(params, P_Y, 256 + 100);
            alien.objOff = firstShape;
            alien.energy = 4;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        alien.x -= 8;
        vars[ANIM] += 2;
        if (vars[ANIM] == 28) {
            vars[ANIM] = 0;
        }
        alien.objOff = shapes[vars[ANIM] / 2];

        if (state.sorcererY > alien.y) {
            alien.y++;
        } else if (state.sorcererY < alien.y) {
            alien.y--;
        }

        if (alien.x >= 220) {
            return true;
        }
        HighlandRoutines.dropPalette(state);
        return false;
    }
}
