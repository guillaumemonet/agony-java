package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Spectre}, from the SPECTRE + FIOLLE block of
 * {@code Ag_Game_LFORET.s}.
 *
 * <p>The forest version of the ghost of the sea ({@link Ghost}): a vial drifts
 * in from the right, and if it reaches the x the script names unbroken the
 * ghost comes out, plays its five frames, then hunts the sorcerer for eight
 * seconds, taking a new bearing every eighteen frames or as soon as it gets
 * within one step of the old one.  Unlike the sea version it does not install
 * a palette, and it closes with {@link ForestRoutines#dropPalette}.
 */
final class ForestGhost implements Routine {

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

    /** {@code R_Spectre_Shape}, the five frames of the ghost coming out. */
    private static final int SHAPES = 0x04b762;

    /** {@code Obj_Spectre_Pot}, {@code Obj_Spectre_1}, {@code _4} and {@code _5}. */
    private final int vial = 0x300;
    private final int first = 0x312;
    private final int frame4 = 0x348;
    private final int frame5 = 0x35e;

    private final int[] shapes;

    ForestGhost(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 5);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien vialAlien = bank.aliens[0];
        Alien ghost = bank.aliens[1];
        int mode = vars[MODE];

        if (mode == 0) {
            bank.count = 1;
            bank.first = 0;
            vialAlien.x = 256 + 300;
            vialAlien.y = 256 + 160;
            vialAlien.objOff = vial;
            vialAlien.energy = 5;
            ForestRoutines.clearStatus(vialAlien);
            vars[MODE]++;
            return true;
        }

        // MODE 1, always: the vial drifts on whatever the ghost does.
        bank.count = 1;
        bank.first = 0;
        vialAlien.x -= 2;

        // A broken vial that has drifted past x 200 ends the routine.
        if (vialAlien.exploState() != 0 && vialAlien.x < 200) {
            return close(state, ghost);
        }

        int launchX = ForestRoutines.param(params, P_LAUNCH_X, 256 + 200);
        if (vialAlien.x == launchX && vialAlien.exploState() == 0) {
            vars[MODE] = 2;
            bank.count = 2;
            ghost.x = vialAlien.x;
            ghost.y = vialAlien.y;
            ghost.objOff = first;
            ghost.energy = 2;
            ForestRoutines.clearStatus(ghost);
            // move.b P_S_Fire_Rate+1(a1): the low byte of the parameter.
            ghost.fireRateStat = ForestRoutines.param(params, P_FIRE_RATE, 25) & 0xff;
            vars[ANIM_STEP] = 0;
            vars[ANIM_DELAY] = 0;
            return true;
        }

        if (mode == 2) {
            bank.count = 2;
            ghost.x -= 2;
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
            ghost.objOff = ghost.objOff == frame5 ? frame4 : frame5;
        }

        vars[TARGET_DELAY]--;
        if (vars[TARGET_DELAY] == 0) {
            vars[TARGET_DELAY] = 18;
            vars[TARGET_X] = state.sorcererX + 16;
            vars[TARGET_Y] = state.sorcererY + 40;
        }

        ghost.x = home(ghost.x, vars[TARGET_X],
                ForestRoutines.param(params, P_X_SPEED, 2), vars);
        ghost.y = home(ghost.y, vars[TARGET_Y],
                ForestRoutines.param(params, P_Y_SPEED, 2), vars);

        vars[TIME]++;
        if (vars[TIME] == 25 * 8) {
            return close(state, ghost);
        }
        return true;
    }

    /**
     * Moves one axis a step towards its target, and asks for a new bearing
     * once within a step of it.  The step is always taken, so the ghost
     * shivers around its target until the next bearing.
     */
    private int home(int current, int target, int speed, int[] vars) {
        if (Math.abs(target - current) <= speed) {
            vars[TARGET_DELAY] = 1;
        }
        return current < target ? current + speed : current - speed;
    }

    private boolean close(GameState state, Alien ghost) {
        ForestRoutines.dropPalette(state);
        ghost.status = 1;
        return false;
    }
}
