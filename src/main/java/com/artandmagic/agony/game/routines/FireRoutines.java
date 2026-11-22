package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.data.original.AgonyBinary;

import java.util.HashMap;
import java.util.Map;

/**
 * The level routines of the fire, level 6, {@code Game/Ag_Game_LFEUX.s}.
 *
 * <p>Not ported yet: every {@code Start_C} of the level script finds nothing
 * here and its slot stays free.
 */
public final class FireRoutines implements RoutineLibrary {

    private final Map<Integer, Routine> byAddress = new HashMap<>();

    public FireRoutines(AgonyBinary bin) {
    }

    @Override
    public Routine forAddress(int address) {
        return byAddress.get(address);
    }
}
