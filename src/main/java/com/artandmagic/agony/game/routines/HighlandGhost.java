package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Spectre}, from the SPECTRE + FIOLLE block of
 * {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>The level one ghost without its palette.  A vial drifts in from the right
 * along the ground.  Reach the x the script names without having broken it and
 * the ghost comes out: it flies left playing its five frames, one every other
 * frame, and once they are through it turns and hunts the sorcerer, taking a
 * new bearing on him every eighteen frames, or as soon as it gets within one
 * step of the last one, and closing at the speeds the script gives it.  Eight
 * seconds later it gives up and blows itself up.  A broken vial that has
 * drifted off the left edge ends the routine too, ghost and all.
 */
final class HighlandGhost implements Routine {

    private static final int MODE = 0;
    private static final int ANIM_STEP = 1;
    private static final int ANIM_DELAY = 2;
    private static final int TARGET_X = 3;
    private static final int TARGET_Y = 4;
    private static final int TARGET_DELAY = 5;
    private static final int TIME = 6;

    private static final int P_LAUNCH_X = 0;
    private static final int P_FIRE_RATE = 1;
    private static final int P_X_SPEED = 2;
    private static final int P_Y_SPEED = 3;

    /** {@code R_Spectre_Shape}. */
    private static final int SHAPES = 0x050114;

    /** {@code Obj_Spectre_Pot}, {@code _1}, {@code _4} and {@code _5}. */
    private final int vial = 0x3ce;
    private final int first = 0x3e0;
    private final int narrow = 0x416;
    private final int wide = 0x42c;

    private final int[] shapes;

    HighlandGhost(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 5);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien vialAlien = bank.aliens[0];
        Alien ghost = bank.aliens[1];
        int mode = vars[MODE];

        bank.count = 1;
        bank.first = 0;
        if (mode == 0) {
            vialAlien.x = 256 + 300;
            vialAlien.y = 256 + 160;
            vialAlien.objOff = vial;
            vialAlien.energy = 5;
            vialAlien.status = 0;
            vialAlien.fireRateStat = 0;
            vialAlien.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        vialAlien.x -= 2;
        if (vialAlien.exploState() != 0 && vialAlien.x < 200) {
            return close(state, ghost);
        }

        if (vialAlien.x == HighlandRoutines.param(params, P_LAUNCH_X, 256 + 200)
                && vialAlien.exploState() == 0) {
            vars[MODE] = 2;
            bank.count = 2;
            ghost.x = vialAlien.x;
            ghost.y = vialAlien.y;
            ghost.objOff = first;
            ghost.energy = 2;
            ghost.status = 0;
            ghost.fireRateStat = HighlandRoutines.param(params, P_FIRE_RATE, 25) & 0xff;
            ghost.fireRateDyn = 0;
            vars[ANIM_STEP] = 0;
            vars[ANIM_DELAY] = 0;
            return true;
        }

        if (mode == 2) {
            bank.count = 2;
            ghost.x -= 2;
            // eor then bne: the frame changes when the toggle comes back to 0.
            vars[ANIM_DELAY] ^= 1;
            if (vars[ANIM_DELAY] != 0) {
                return true;
            }
            int step = vars[ANIM_STEP];
            vars[ANIM_STEP] = step + 2;
            ghost.objOff = shapes[step / 2];
            if (step == 2 * 4) {
                vars[MODE]++;
                vars[TARGET_DELAY] = 1;
                vars[TIME] = 0;
            }
            return true;
        }

        if (mode != 3) {
            return true;
        }

        bank.count = 2;
        vars[ANIM_DELAY] ^= 1;
        if (vars[ANIM_DELAY] != 0) {
            ghost.objOff = ghost.objOff == wide ? narrow : wide;
        }

        vars[TARGET_DELAY]--;
        if (vars[TARGET_DELAY] == 0) {
            vars[TARGET_DELAY] = 18;
            vars[TARGET_X] = state.sorcererX + 16;
            vars[TARGET_Y] = state.sorcererY + 40;
        }

        ghost.x = home(ghost.x, vars[TARGET_X], HighlandRoutines.param(params, P_X_SPEED, 2), vars);
        ghost.y = home(ghost.y, vars[TARGET_Y], HighlandRoutines.param(params, P_Y_SPEED, 2), vars);

        vars[TIME]++;
        if (vars[TIME] == 25 * 8) {
            return close(state, ghost);
        }
        return true;
    }

    /**
     * Moves one axis towards its target, and asks for a new bearing once within
     * one step.  Unlike the kamikaze it always moves, so it can overshoot.
     */
    private int home(int current, int target, int speed, int[] vars) {
        if (Math.abs(target - current) <= speed) {
            vars[TARGET_DELAY] = 1;
        }
        return current < target ? current + speed : current - speed;
    }

    /** Closes the routine and sets the ghost off exploding. */
    private boolean close(GameState state, Alien ghost) {
        HighlandRoutines.dropPalette(state);
        ghost.status = 1;
        return false;
    }
}
