package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Rapide}, the one that darts straight across, from the DROITE GAUCHE
 * RAPIDE block of {@code Ag_Game_LFEUX.s}.
 *
 * <p>The script calls it seventy one times, mostly in volleys a few frames
 * apart, and gives each call its animation, its speed and its height.  It
 * comes in at the right edge, walks its animation one word a frame from
 * {@code Anim_Base}, starting it over at the negative word that ends the list,
 * and closes once it is past x 200.
 *
 * <p>The parameter block still carries a palette pointer, which here always
 * points at {@code Dummy_Pal}, a palette with every colour at -1; unlike the
 * first level's {@link Darter}, this version never reads it.  It also dies at
 * two hits rather than three.
 */
final class FireDarter implements Routine {

    private static final int MODE = 0;
    /** The original keeps a pointer here; an index into the list does the same. */
    private static final int ANIM_INDEX = 1;

    private static final int P_ANIM = 0;
    private static final int P_SPEED = 1;
    private static final int P_Y = 4;

    private final AgonyBinary bin;
    private final int animBase;

    FireDarter(AgonyBinary bin) {
        this.bin = bin;
        this.animBase = bin.address("Anim_Base");
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        // add.w to an address register: the offset is sign extended.
        int anim = (short) params[P_ANIM];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 340;
            alien.y = params[P_Y];
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
        alien.x -= (short) params[P_SPEED];

        int value = frame(anim, vars[ANIM_INDEX]);
        vars[ANIM_INDEX]++;
        if ((value & 0x8000) != 0) {
            // The list ended: start it again and take its first entry.
            value = frame(anim, 0);
            vars[ANIM_INDEX] = 1;
        }
        alien.objOff = value;

        if (alien.x <= 200) {
            return FireRoutines.close(state);
        }
        return true;
    }

    private int frame(int anim, int index) {
        return bin.u16(animBase + anim + index * 2);
    }
}
