package com.artandmagic.agony.data;

/**
 * Binds the level routines of a level to their Java implementations.
 *
 * <p>A {@code Start_C} entry of the script names its routine by the address of
 * its 68000 code, which is the only handle the compiled level has.  The source
 * of {@code Ag_Game_*.s} gives those routines names and bodies, and matching the
 * two is unambiguous: every call in the source carries a {@code WAIT} trigger
 * and its parameters, and so does every entry in the binary.
 */
public interface RoutineLibrary {

    /** The routine at that address, or null when it has not been ported. */
    Routine forAddress(int address);

    /**
     * Addresses of the {@code AWS} records the routines launch themselves.
     *
     * <p>A routine such as the transporter does not wait for the script: it
     * grabs a free track slot and starts an attack wave of its own, out of a
     * record kept next to its code.  Those records are converted with the rest
     * and handed back through {@link #bindWaves}.
     */
    default int[] waveAddresses() {
        return new int[0];
    }

    /** Tells the library which wave index each of those addresses became. */
    default void bindWaves(java.util.Map<Integer, Integer> indexByAddress) {
    }

    /** A library that knows nothing, for levels whose routines are not ported. */
    RoutineLibrary NONE = address -> null;
}
