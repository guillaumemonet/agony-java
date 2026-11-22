package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.data.original.AgonyBinary;

/**
 * The level routines of each level, the {@code Ag_Game_*.s} file assembled
 * into its overlay: {@code LMER} for the sea, {@code LFORET} for the forest,
 * {@code LMARAIS} for the marshes, {@code LMONTAGNES} for the mountains,
 * {@code LPLATEAUX} for the highlands and {@code LFEUX} for the fire.
 */
public final class Routines {

    private Routines() {
    }

    /** The library of a level, bound to that level's overlay. */
    public static RoutineLibrary forLevel(int level, AgonyBinary bin) {
        return switch (level) {
            case 1 -> new SeaRoutines(bin);
            case 2 -> new ForestRoutines(bin);
            case 3 -> new MarshRoutines(bin);
            case 4 -> new MountainRoutines(bin);
            case 5 -> new HighlandRoutines(bin);
            case 6 -> new FireRoutines(bin);
            default -> RoutineLibrary.NONE;
        };
    }
}
