package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Rapide}, the one that darts straight across, from the DROITE GAUCHE
 * RAPIDE block of {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>The script still passes it a palette, {@code Dummy_Pal} every time, but
 * this version of the routine never reads that parameter: the recolouring of
 * the first level is gone.  The rest is the same: the animation, its speed and
 * its height come from the script; it starts at the right edge, walks its
 * animation one word a frame from {@code Anim_Base}, starting over when it
 * meets the negative word that ends the list, and closes once it is off the
 * left edge.
 */
final class HighlandDarter implements Routine {

    private static final int MODE = 0;
    /** The original keeps a pointer here; an index into the list does the same. */
    private static final int ANIM_INDEX = 1;

    private static final int P_ANIM = 0;
    private static final int P_SPEED = 1;
    // Words 2 and 3 are P_R_Pal_Ptr, which this build does not use.
    private static final int P_Y = 4;

    private final AgonyBinary bin;
    private final int animBase;

    HighlandDarter(AgonyBinary bin) {
        this.bin = bin;
        this.animBase = bin.has("Anim_Base") ? bin.address("Anim_Base") : 0;
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        int anim = HighlandRoutines.param(params, P_ANIM, 0);

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 340;
            alien.y = HighlandRoutines.param(params, P_Y, 256 + 130);
            vars[ANIM_INDEX] = 0;
            alien.objOff = frame(anim, 0);
            alien.energy = 3;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        alien.x -= HighlandRoutines.param(params, P_SPEED, 4);

        int value = frame(anim, vars[ANIM_INDEX]);
        vars[ANIM_INDEX]++;
        if ((value & 0x8000) != 0) {
            // The list ended: start it again and take its first entry.
            value = frame(anim, 0);
            vars[ANIM_INDEX] = 1;
        }
        alien.objOff = value;

        if (alien.x <= 200) {
            HighlandRoutines.dropPalette(state);
            return false;
        }
        return true;
    }

    private int frame(int anim, int index) {
        return bin.u16(animBase + anim + index * 2);
    }
}
