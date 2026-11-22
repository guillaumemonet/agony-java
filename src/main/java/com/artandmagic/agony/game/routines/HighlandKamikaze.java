package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Kamikaze}, the flying kamikaze, from the MONSTRE VOLANT KAMIKAZE
 * block of {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>It comes in from above the top right corner (its y is left unbiased in
 * the source) and for {@code P_VK_Launch_Time} frames it shadows the sorcerer,
 * keeping a hundred and fifty pixels to his right and forty below his origin,
 * at the speeds the script gives for each axis; it holds still on an axis once
 * it is within one step.  Fifty frames after it stops following, it shoots off
 * to the left eight pixels a frame and closes once it is past the edge.
 */
final class HighlandKamikaze implements Routine {

    private static final int MODE = 0;
    private static final int TIME = 3;

    private static final int P_LAUNCH_TIME = 0;
    private static final int P_X_SPEED = 1;
    private static final int P_Y_SPEED = 2;

    /** {@code Obj_Kamikaze}, as the code loads it. */
    private final int shape = 0x3bc;

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        bank.count = 1;
        bank.first = 0;

        if (vars[MODE] == 0) {
            alien.x = 256 + 300;
            alien.y = 200;
            alien.objOff = shape;
            alien.energy = 20;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[TIME] = 0;
            vars[MODE]++;
            return true;
        }

        int launch = HighlandRoutines.param(params, P_LAUNCH_TIME, 175);
        vars[TIME]++;
        if (vars[TIME] <= launch) {
            alien.x = follow(alien.x, state.sorcererX + 150,
                    HighlandRoutines.param(params, P_X_SPEED, 2));
            alien.y = follow(alien.y, state.sorcererY + 40,
                    HighlandRoutines.param(params, P_Y_SPEED, 2));
        }

        if (vars[TIME] < launch + 50) {
            return true;
        }
        alien.x -= 8;
        if (alien.x <= 200) {
            HighlandRoutines.dropPalette(state);
            return false;
        }
        return true;
    }

    /** One axis of the chase: a step towards the target unless already within one. */
    private static int follow(int current, int target, int speed) {
        if (Math.abs(target - current) <= speed) {
            return current;
        }
        return current < target ? current + speed : current - speed;
    }
}
