package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.GameState;

import java.util.HashMap;
import java.util.Map;

/**
 * The level routines of the mountains, level 4, {@code Game/AG_GAME_LMONTAGNES.S}.
 *
 * <p>Eight of them are called by the script: the flier with its guided missile,
 * the charging ground thing, the column of fire, the spider, the bomb dropper,
 * the ground creature that leaps at the sorcerer, the fire breathing dragon and
 * the final monster with its mace.  A ninth, {@code R_Rapide}, is assembled in
 * but never called.
 *
 * <p>The addresses below are where each routine sits in this build, read off
 * the {@code Start_C} entries of the script (trigger and parameters agree with
 * the source call for call) and, for {@code R_Rapide}, off the code between its
 * neighbours.  None of them installs a palette, and none launches an attack wave
 * of its own, so there are no wave records to bind.  Their shape lists and
 * curves are read from the overlay.
 */
public final class MountainRoutines implements RoutineLibrary {

    private final Map<Integer, Routine> byAddress = new HashMap<>();

    public MountainRoutines(AgonyBinary bin) {
        byAddress.put(0x04bd54, new MountainMissileFlyer(bin));
        byAddress.put(0x04c0ec, new MountainRapide(bin));
        byAddress.put(0x04c196, new MountainKamikaze(bin));
        byAddress.put(0x04c276, new MountainFlameColumn(bin));
        byAddress.put(0x04c368, new MountainSpider(bin));
        byAddress.put(0x04c412, new MountainBomber(bin));
        byAddress.put(0x04c55c, new MountainLeaper(bin));
        byAddress.put(0x04c710, new MountainDragon(bin));
        byAddress.put(0x04c888, new MountainFinal(bin));
    }

    @Override
    public Routine forAddress(int address) {
        return byAddress.get(address);
    }

    /**
     * The colour half of the close every routine of this level but the boss
     * ends with: the routine palette is dropped only if no routine still
     * counts itself in {@code Rout_Mod_Pal_Counter}.
     */
    static void close(GameState state) {
        if (state.routModPalCounter == 0) {
            state.routPalette = null;
            state.refreshPal = GameState.PAL_ASKED;
        }
    }

    /** A script parameter, or the fallback when the call has fewer. */
    static int param(int[] params, int index, int fallback) {
        return params != null && params.length > index ? params[index] : fallback;
    }
}
