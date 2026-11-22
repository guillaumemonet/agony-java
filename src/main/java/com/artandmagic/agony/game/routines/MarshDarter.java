package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Rapide}, the one that darts straight across, from the DROITE GAUCHE
 * RAPIDE block of {@code AG_GAME_LMARAIS.S}.
 *
 * <p>The script gives the animation, the speed, a palette and the height.  This
 * level's version never reads the palette parameter (the script passes
 * {@code Dummy_Pal}, which nothing installs), and its alien has two points of
 * energy where the sea's has three.  It walks its animation one word a frame,
 * starting the list over at the negative word that ends it, and closes once it
 * is past x 200.
 */
final class MarshDarter implements Routine {

    private static final int MODE = 0;
    /** The original keeps a pointer here; an index into the list does the same. */
    private static final int ANIM_INDEX = 1;

    private static final int P_ANIM = 0;
    private static final int P_SPEED = 1;
    private static final int P_Y = 4;

    private final AgonyBinary bin;
    private final int animBase;

    MarshDarter(AgonyBinary bin) {
        this.bin = bin;
        this.animBase = bin.has("Anim_Base") ? bin.address("Anim_Base") : 0;
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        int anim = MarshRoutines.param(params, P_ANIM, 0);

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 340;
            alien.y = MarshRoutines.param(params, P_Y, 256 + 100);
            vars[ANIM_INDEX] = 0;
            alien.objOff = frame(anim, 0);
            alien.energy = 2;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        alien.x -= MarshRoutines.param(params, P_SPEED, 8);

        int value = frame(anim, vars[ANIM_INDEX]);
        vars[ANIM_INDEX]++;
        if ((value & 0x8000) != 0) {
            // .reread: the list ended, start it again and take its first entry.
            value = frame(anim, 0);
            vars[ANIM_INDEX] = 1;
        }
        alien.objOff = value;

        if (alien.x <= 200) {
            MarshRoutines.closePalette(state);
            return false;
        }
        return true;
    }

    private int frame(int anim, int index) {
        return bin.u16(animBase + anim + index * 2);
    }
}
