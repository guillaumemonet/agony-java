package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Volant_Grossi}, the flyer that swells as it is worn down, from the
 * MONSTRE VOLANT GROSSI SI TOUCHE block of {@code Ag_Game_LMER.s}.
 *
 * <p>It crosses from the right at the height and speed the script gives it, and
 * takes one of three shapes depending on what is left of its ten points of
 * energy: the first above seven, the second above three, the last below that.
 */
final class Swelling implements Routine {

    private static final int MODE = 0;

    private static final int P_Y = 0;
    private static final int P_SPEED = 1;

    private final int[] palette;
    /** The three shapes, from unhurt to nearly gone. */
    private final int whole;
    private final int hurt;
    private final int swollen;

    Swelling(AgonyBinary bin) {
        palette = SeaRoutines.palette(bin, 0x04f682);
        whole = 0x5a8;
        hurt = 0x58e;
        swollen = 0x570;
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];

        if (vars[MODE] == 0) {
            SeaRoutines.takePalette(state, palette);
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 340;
            alien.y = params.length > P_Y ? params[P_Y] : 256 + 150;
            alien.objOff = whole;
            alien.energy = 10;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        alien.x -= params.length > P_SPEED ? params[P_SPEED] : 3;

        alien.objOff = alien.energy >= 7 ? whole : alien.energy >= 3 ? hurt : swollen;

        if (alien.x <= 200) {
            SeaRoutines.releasePalette(state);
            return false;
        }
        return true;
    }
}
