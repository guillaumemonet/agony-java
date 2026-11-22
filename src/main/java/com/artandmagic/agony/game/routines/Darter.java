package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code R_Rapide}, the one that darts straight across, from the DROITE GAUCHE
 * RAPIDE block of {@code Ag_Game_LMER.s}.
 *
 * <p>Everything about it comes from the script: which animation it plays, how
 * fast it crosses, which palette it recolours the bands with, and the height it
 * flies at.  It walks its animation one word a frame and starts it over when it
 * meets the negative word that ends the list.
 */
final class Darter implements Routine {

    private static final int MODE = 0;
    /** The original keeps a pointer here; an index into the list does the same. */
    private static final int ANIM_INDEX = 1;

    private static final int P_ANIM = 0;
    private static final int P_SPEED = 1;
    private static final int P_PAL_HIGH = 2;
    private static final int P_PAL_LOW = 3;
    private static final int P_Y = 4;

    private final AgonyBinary bin;
    private final int animBase;
    private final Map<Integer, int[]> palettes = new HashMap<>();

    Darter(AgonyBinary bin) {
        this.bin = bin;
        this.animBase = bin.has("Anim_Base") ? bin.address("Anim_Base") : 0;
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        int anim = param(params, P_ANIM, 0);

        if (vars[MODE] == 0) {
            int address = (param(params, P_PAL_HIGH, 0) << 16) | param(params, P_PAL_LOW, 0);
            SeaRoutines.takePalette(state, palettes.computeIfAbsent(address,
                    a -> SeaRoutines.palette(bin, a)));
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 340;
            alien.y = param(params, P_Y, 256 + 130);
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
        alien.x -= param(params, P_SPEED, 8);

        int value = frame(anim, vars[ANIM_INDEX]);
        vars[ANIM_INDEX]++;
        if ((value & 0x8000) != 0) {
            // The list ended: start it again and take its first entry.
            value = frame(anim, 0);
            vars[ANIM_INDEX] = 1;
        }
        alien.objOff = value;

        if (alien.x <= 200) {
            SeaRoutines.releasePalette(state);
            return false;
        }
        return true;
    }

    private int frame(int anim, int index) {
        return bin.u16(animBase + anim + index * 2);
    }

    private static int param(int[] params, int index, int fallback) {
        return params != null && params.length > index ? params[index] : fallback;
    }
}
