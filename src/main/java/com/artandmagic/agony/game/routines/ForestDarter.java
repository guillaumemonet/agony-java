package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Rapide}, from the DROITE GAUCHE RAPIDE block of
 * {@code Ag_Game_LFORET.s}.
 *
 * <p>A fast creature that crosses the screen from right to left on a straight
 * line, at the height and the speed the script gives it, playing an animation
 * of {@code Anim_Base} — the script names it by its offset there,
 * {@code Anim_Speedy} — which it restarts whenever it reaches the negative word
 * that ends it.  It takes two hits.
 *
 * <p>Its palette parameter ({@code PAR_L Dummy_Pal}) is never read: the
 * routine only counts itself in {@code Rout_Mod_Pal_Counter} while it lives,
 * which holds back the base palette copy while two or more are on screen.
 */
final class ForestDarter implements Routine {

    private static final int MODE = 0;
    /** {@code R_R_Anim_D_Ptr}, kept as a word index into the animation. */
    private static final int ANIM_PTR = 1;

    private static final int P_ANIM = 0;
    private static final int P_SPEED = 1;
    /** {@code P_R_Pal_Ptr} is a long, words 2 and 3. */
    private static final int P_Y = 4;

    private final AgonyBinary bin;
    private final int animBase;

    ForestDarter(AgonyBinary bin) {
        this.bin = bin;
        animBase = bin.address("Anim_Base");
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        int anim = animBase + ForestRoutines.param(params, P_ANIM, 0);

        if (vars[MODE] == 0) {
            state.routModPalCounter++;
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 340;
            alien.y = ForestRoutines.param(params, P_Y, 256 + 100);
            vars[ANIM_PTR] = 0;
            alien.objOff = bin.u16(anim);
            alien.energy = 2;
            ForestRoutines.clearStatus(alien);
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        alien.x -= ForestRoutines.param(params, P_SPEED, 8);

        // The pointer moves on before the word is looked at, so the first
        // frame of the list is shown twice; past the end it starts over.
        int shape = bin.s16(anim + vars[ANIM_PTR] * 2);
        vars[ANIM_PTR]++;
        if (shape < 0) {
            shape = bin.s16(anim);
            vars[ANIM_PTR] = 1;
        }
        alien.objOff = shape;

        if (alien.x <= 200) {
            state.routModPalCounter--;
            ForestRoutines.dropPalette(state);
            return false;
        }
        return true;
    }
}
