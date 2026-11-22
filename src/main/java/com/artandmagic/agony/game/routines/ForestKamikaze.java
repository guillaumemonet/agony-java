package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Kamikaze}, from the MONSTRE VOLANT KAMIKAZE block of
 * {@code Ag_Game_LFORET.s}.
 *
 * <p>A flying monster that comes in on the right and, for as many frames as
 * its first parameter says, holds itself a hundred and fifty pixels in front
 * of the sorcerer and level with him, at the speeds its two other parameters
 * give, standing still on an axis once within a step of its mark.  Two seconds
 * after it stops following it dives left eight pixels a frame, straight
 * through where he was, and closes off the left of the screen.
 */
final class ForestKamikaze implements Routine {

    private static final int MODE = 0;
    private static final int TIME = 3;

    private static final int P_LAUNCH_TIME = 0;
    private static final int P_X_SPEED = 1;
    private static final int P_Y_SPEED = 2;

    /** {@code Obj_Kamikaze}. */
    private final int shape = 0x2ee;

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien monster = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            monster.x = 256 + 300;
            monster.y = 200;
            monster.objOff = shape;
            monster.energy = 20;
            ForestRoutines.clearStatus(monster);
            vars[TIME] = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        int launch = ForestRoutines.param(params, P_LAUNCH_TIME, 25 * 7);
        vars[TIME]++;

        if (vars[TIME] <= launch) {
            monster.x = follow(monster.x, state.sorcererX + 150,
                    ForestRoutines.param(params, P_X_SPEED, 2));
            monster.y = follow(monster.y, state.sorcererY + 40,
                    ForestRoutines.param(params, P_Y_SPEED, 2));
        }

        if (vars[TIME] >= launch + 50) {
            monster.x -= 8;
            if (monster.x <= 200) {
                ForestRoutines.dropPalette(state);
                return false;
            }
        }
        return true;
    }

    /** One step towards the mark, none when within a step of it. */
    private static int follow(int current, int target, int speed) {
        if (Math.abs(target - current) <= speed) {
            return current;
        }
        return current < target ? current + speed : current - speed;
    }
}
