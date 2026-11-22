package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Jumper}, from the SOL SAUTE SUR LE PERSO block of
 * {@code AG_GAME_LMARAIS.S}.
 *
 * <p>A creature walks in along the ground from the right, two pixels a frame.
 * Once it is within 128 pixels to the right of the sorcerer it crouches, and
 * from the next frame it leaps up and left, eight up and six left a frame,
 * until it is above the top of the screen, where the routine closes.
 */
final class MarshJumper implements Routine {

    private static final int MODE = 0;

    /** {@code Obj_Jumper_1} to {@code Obj_Jumper_3}: walking, crouching, leaping. */
    private final int walk = 0x15a;
    private final int crouch = 0x174;
    private final int leap = 0x18e;

    MarshJumper(AgonyBinary bin) {
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 340;
            alien.y = 256 + 190;
            alien.objOff = walk;
            alien.energy = 5;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        alien.x -= 2;

        if (vars[MODE] == 1) {
            if (state.sorcererX + 128 >= alien.x) {
                vars[MODE]++;
                alien.objOff = crouch;
            }
            return true;
        }

        alien.objOff = leap;
        alien.y -= 8;
        alien.x -= 4;
        if (alien.y <= 240) {
            MarshRoutines.closePalette(state);
            return false;
        }
        return true;
    }
}
