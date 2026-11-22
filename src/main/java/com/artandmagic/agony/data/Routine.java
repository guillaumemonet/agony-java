package com.artandmagic.agony.data;

import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * A level routine, one of the hand written pieces of 68000 that
 * {@code Rout_Struct} drives in {@code Ag_Playability_.s}.
 *
 * <p>The level script starts one with {@code Start_C}, a pointer with bit 31
 * set followed by {@code Par} words up to a {@code Par_End} of -1.  The slot
 * keeps four things for it, and the routine is handed all of them on every
 * 25 Hz frame: the parameters it was started with, the bank of sixteen aliens
 * it was given, eight words of its own state, and the game itself.  It stays
 * until it writes -1 into its code pointer, which is what returning false does
 * here.
 *
 * <p>A routine owns its bank outright: it sets the alien count in the header
 * and fills in position, shape, energy and status by hand, rather than letting
 * the track follower do it.
 */
public interface Routine {

    /**
     * Runs one frame of the routine.
     *
     * @param state  the game state
     * @param bank   the alien bank reserved for this routine
     * @param vars   the sixteen slots of {@code Rout_Variables}, which the
     *               original uses as words or as bytes depending on the routine
     * @param params the words the script started it with
     * @return true to stay active, false to release the slot
     */
    boolean update(GameState state, AlienBank bank, int[] vars, int[] params);
}
