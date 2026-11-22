package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Guide}, the creature that leaps off the ground at the sorcerer,
 * from the SOL SAUTE GUIDE block of {@code Ag_Game_LFEUX.s}.
 *
 * <p>It walks in along the floor two pixels a frame.  When it reaches the x
 * the script names it picks one of five leaps, once and for all, from where the
 * sorcerer is at that moment: straight up when he is within forty pixels of it
 * across; otherwise a steep or a shallow leap, backwards or forwards depending
 * on which side of it he is.  Steep or shallow is decided by comparing the
 * distance across with {@code 350 - 40 - Sorcerer_Y}, taken on the biased
 * {@code Sorcerer_Y} exactly as the source does.  Each leap has its own shape,
 * and the routine closes once
 * the creature leaves the screen on any side but the bottom.
 */
final class FireLeaper implements Routine {

    private static final int MODE = 0;

    private static final int P_LAUNCH = 0;

    /** {@code Obj_Sol_Guide_0} on the ground, then {@code _1} to {@code _5}, one per leap. */
    private static final int GROUND = 0x5cc;
    private static final int[] LEAP = {0x5de, 0x808, 0x81e, 0x838, 0x84e};
    /** The step of each leap, modes 2 to 6. */
    private static final int[][] STEP = {{-3, -1}, {-3, -3}, {0, -3}, {3, -3}, {3, -1}};

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        int mode = vars[MODE];

        if (mode == 0) {
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 320;
            alien.y = 256 + 178;
            alien.objOff = GROUND;
            alien.energy = 3;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
            return end(state, alien);
        }

        bank.count = 1;
        bank.first = 0;

        if (mode >= 2 && mode <= 6) {
            alien.objOff = LEAP[mode - 2];
            alien.x += STEP[mode - 2][0];
            alien.y += STEP[mode - 2][1];
            return end(state, alien);
        }

        // Mode 1: walking in.
        alien.x -= 2;
        int d0 = alien.x;
        if (d0 != params[P_LAUNCH]) {
            return end(state, alien);
        }
        boolean right = true;
        d0 -= state.sorcererX + 20;
        if (d0 < 0) {
            d0 = -d0;
            right = false;
        }
        int d1 = 350 - 40 - state.sorcererY;

        if (d0 <= 40) {
            vars[MODE] = 4;
        } else if (right) {
            vars[MODE] = Math.abs(d0 - d1) <= 70 ? 3 : 2;
        } else {
            vars[MODE] = Math.abs(d0 - d1) <= 70 ? 5 : 6;
        }
        return end(state, alien);
    }

    /** Closes once it is off the left, the right or the top of the screen. */
    private boolean end(GameState state, Alien alien) {
        if (alien.x < 200 || alien.x > 256 + 330 || alien.y < 200) {
            return FireRoutines.close(state);
        }
        return true;
    }
}
