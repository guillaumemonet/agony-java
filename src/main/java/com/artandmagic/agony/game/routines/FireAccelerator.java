package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Volant_Accelaire}, the flier that speeds up when it is hit, from the
 * VOLANT ACCELAIRE SI TOUCHE block of {@code Ag_Game_LFEUX.s}.
 *
 * <p>It crosses the screen from the right at the height the script gives,
 * starting at two pixels a frame and playing a twenty step shape list that
 * swells and shrinks back.  Every frame its status byte reads exactly
 * {@code $10} — the value the collision test writes there on a hit, before the
 * flash counter moves it on — it goes one pixel a frame faster, so each hit
 * that does not kill it speeds it up.  It closes once it is past x 220.
 */
final class FireAccelerator implements Routine {

    private static final int MODE = 0;
    private static final int ANIM = 1;
    private static final int SPEED = 2;

    private static final int P_Y = 0;

    /** {@code R_VA_Shape}, twenty words in front of the code. */
    private static final int SHAPES = 0x051242;

    private final int[] shapes;

    FireAccelerator(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 20);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 340;
            alien.y = params[P_Y];
            alien.objOff = shapes[0];
            alien.energy = 6;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
            vars[SPEED] = 2;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        alien.x -= vars[SPEED];

        vars[ANIM] += 2;
        if (vars[ANIM] == 40) {
            vars[ANIM] = 0;
        }
        alien.objOff = shapes[vars[ANIM] / 2];

        if ((alien.status & 0xff) == 0x10) {
            vars[SPEED]++;
        }
        if (alien.x < 220) {
            return FireRoutines.close(state);
        }
        return true;
    }
}
