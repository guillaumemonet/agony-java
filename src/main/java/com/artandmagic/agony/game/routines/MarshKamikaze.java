package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Kamikaze}, from the MONSTRE VOLANT KAMIKAZE block of
 * {@code AG_GAME_LMARAIS.S}.
 *
 * <p>A flier comes in from above the top right corner (its y of 200 is left
 * unbiased by the source) and, for as many frames as the script's launch time,
 * closes on the point a hundred and fifty pixels right of the sorcerer and
 * forty below his top, at the x and y speeds the script gives; an axis that is
 * already within its speed of the target stays put.  Then it hangs where it is
 * for fifty frames, and finally dives left eight pixels a frame until it is
 * past x 200, where it closes.
 */
final class MarshKamikaze implements Routine {

    private static final int MODE = 0;
    private static final int TIME = 3;

    private static final int P_LAUNCH_TIME = 0;
    private static final int P_X_SPEED = 1;
    private static final int P_Y_SPEED = 2;

    /** {@code Obj_Kamikaze}. */
    private final int body = 0x330;

    MarshKamikaze(AgonyBinary bin) {
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 300;
            alien.y = 200;
            alien.objOff = body;
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
        int launch = MarshRoutines.param(params, P_LAUNCH_TIME, 25 * 6);
        vars[TIME]++;

        if (vars[TIME] <= launch) {
            alien.x = close(alien.x, state.sorcererX + 150, MarshRoutines.param(params, P_X_SPEED, 3));
            alien.y = close(alien.y, state.sorcererY + 40, MarshRoutines.param(params, P_Y_SPEED, 2));
        }

        if (vars[TIME] >= launch + 50) {
            alien.x -= 8;
            if (alien.x <= 200) {
                MarshRoutines.closePalette(state);
                return false;
            }
        }
        return true;
    }

    /** One axis of the chase: a step towards the target, unless within a step of it. */
    private static int close(int current, int target, int speed) {
        if (Math.abs(target - current) <= speed) {
            return current;
        }
        return current < target ? current + speed : current - speed;
    }
}
