package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Kamikaze}, from the MONSTRE VOLANT KAMIKAZE block of
 * {@code Ag_Game_LFEUX.s}.
 *
 * <p>It comes in from above the screen (its y of 200 is unbiased in the
 * source) and, for as many frames as the script says, homes on a point a
 * hundred and fifty pixels to the right of the sorcerer and forty below his
 * top, at the speeds the script gives each axis, stopping on an axis once it
 * is within one step of it.  Then it hangs where it is for two seconds and
 * charges, eight pixels a frame to the left, until it is past x 200.
 */
final class FireKamikaze implements Routine {

    private static final int MODE = 0;
    private static final int TIME = 3;

    private static final int P_LAUNCH_TIME = 0;
    private static final int P_X_SPEED = 1;
    private static final int P_Y_SPEED = 2;

    /** {@code Obj_Kamikaze}. */
    private static final int KAMIKAZE = 0x390;

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 300;
            alien.y = 200;
            alien.objOff = KAMIKAZE;
            alien.energy = 20;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[TIME] = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        int launch = (short) params[P_LAUNCH_TIME];
        vars[TIME]++;

        if (vars[TIME] <= launch) {
            alien.x = home(alien.x, state.sorcererX + 150, (short) params[P_X_SPEED]);
            alien.y = home(alien.y, state.sorcererY + 40, (short) params[P_Y_SPEED]);
        }

        if (vars[TIME] >= launch + 50) {
            alien.x -= 8;
            if (alien.x <= 200) {
                return FireRoutines.close(state);
            }
        }
        return true;
    }

    /** One step towards the target, none when it is within a step of it. */
    private static int home(int current, int target, int speed) {
        if (Math.abs(target - current) <= speed) {
            return current;
        }
        return current < target ? current + speed : current - speed;
    }
}
