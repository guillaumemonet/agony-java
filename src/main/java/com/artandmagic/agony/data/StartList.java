package com.artandmagic.agony.data;

import java.util.ArrayList;
import java.util.List;

/**
 * The level script, {@code Start_list} of {@code Ag_Game_*.s}.
 *
 * <p>The macros of {@code Ag_Game_*.s} spell the format out: {@code Wait} writes
 * the {@code Level_X} value the next entry fires at, {@code Start_R} a plain
 * pointer to an attack wave, {@code Start_A} the same with bit 30 set followed
 * by the start coordinates, {@code Start_C} a pointer to level code with bit 31
 * set followed by {@code Par} words up to a {@code Par_End} of -1, and
 * {@code SL_End} a trigger of $7fff that never comes.  {@code Level_X} advances
 * by two on every 25 Hz frame.
 */
public final class StartList {

    /** One instruction of the level script. */
    public static final class Entry {
        /** Value of {@code Level_X} at which this entry fires. */
        public final int trigger;
        /** Attack wave to launch, or -1 for a routine entry. */
        public final int wave;
        /** Routine to start, or null for a wave entry. */
        public final Routine routine;
        /** The words between {@code Start_C} and {@code Par_End}. */
        public final int[] params;

        public Entry(int trigger, int wave) {
            this.trigger = trigger;
            this.wave = wave;
            this.routine = null;
            this.params = new int[0];
        }

        public Entry(int trigger, Routine routine, int[] params) {
            this.trigger = trigger;
            this.wave = -1;
            this.routine = routine;
            this.params = params;
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public StartList wave(int trigger, int wave) {
        entries.add(new Entry(trigger, wave));
        return this;
    }

    public StartList routine(int trigger, Routine routine, int... params) {
        entries.add(new Entry(trigger, routine, params));
        return this;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** {@code Level_X} of the first entry, or {@link Integer#MAX_VALUE}. */
    public int firstTrigger() {
        return entries.isEmpty() ? Integer.MAX_VALUE : entries.get(0).trigger;
    }
}
