package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Rapide}, from the DROITE GAUCHE RAPIDE block of
 * {@code AG_GAME_LMONTAGNES.S}: an alien that crosses the screen from right to
 * left at the speed its parameters give it, playing one of the animations of
 * {@code Anim_Base}.
 *
 * <p>The mountain script never starts it, so it is bound only for the sake of
 * completeness.  Its parameters are the offset of the animation in
 * {@code Anim_Base}, the speed, a long pointer to a palette that the code never
 * reads ({@code Dummy_Pal} is its only candidate), and the height.
 */
final class MountainRapide implements Routine {

    private static final int MODE = 0;
    /** {@code R_R_Anim_D_Ptr}, kept as a word index from the animation start. */
    private static final int ANIM_INDEX = 1;

    private static final int P_ANIM = 0;
    private static final int P_SPEED = 1;
    /** Word index of {@code P_R_Y}, behind the long palette pointer. */
    private static final int P_Y = 4;

    /** {@code Anim_Base}, as the code's {@code lea Anim_Base,a4} names it. */
    private static final int ANIM_BASE = 0x04bce8;

    private final AgonyBinary bin;

    MountainRapide(AgonyBinary bin) {
        this.bin = bin;
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        bank.count = 1;
        bank.first = 0;
        int anim = ANIM_BASE + (short) MountainRoutines.param(params, P_ANIM, 0);

        if (vars[MODE] == 0) {
            alien.x = 256 + 340;
            alien.y = MountainRoutines.param(params, P_Y, 256 + 100);
            vars[ANIM_INDEX] = 0;
            alien.objOff = bin.u16(anim);
            alien.energy = 2;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        alien.x -= (short) MountainRoutines.param(params, P_SPEED, 2);
        // The pointer is read, then moved on; a negative word sends it back to
        // the start of the animation, which is read again at once.
        int shape = bin.s16(anim + vars[ANIM_INDEX] * 2);
        vars[ANIM_INDEX]++;
        if (shape < 0) {
            shape = bin.s16(anim);
            vars[ANIM_INDEX] = 1;
        }
        alien.objOff = shape;

        if (alien.x <= 200) {
            MountainRoutines.close(state);
            return false;
        }
        return true;
    }
}
