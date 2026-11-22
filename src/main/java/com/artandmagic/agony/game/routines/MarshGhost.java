package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Spectre}, from the SPECTRE + FIOLLE block of
 * {@code AG_GAME_LMARAIS.S}.
 *
 * <p>The same ghost as on the sea, without a palette of its own.  A vial drifts
 * in from the right along the ground; if it reaches the x the script names
 * unbroken, the ghost comes out of it, flies left playing its five frames and
 * then hunts the sorcerer, taking a new bearing every eighteen frames at the
 * speeds the script gives.  Eight seconds later it blows up and the routine
 * closes; a broken vial that has drifted past x 200 closes it as well.
 *
 * <p>The five frames advance on the frames where the {@code eor #1} of the
 * delay leaves zero ({@code bne .no}), which is every other frame starting
 * with the second.
 */
final class MarshGhost implements Routine {

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

    /** {@code R_Spectre_Shape}: {@code Obj_Spectre_1} to {@code Obj_Spectre_5}. */
    static final int SHAPES = 0x04ef20;

    /** {@code Obj_Spectre_Pot}, the vial. */
    private final int vial = 0x342;
    private final int[] shapes;
    /** {@code Obj_Spectre_4} and {@code Obj_Spectre_5}, the two hunting frames. */
    private final int narrow;
    private final int wide;

    MarshGhost(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 5);
        narrow = shapes[3];
        wide = shapes[4];
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
            vialAlien.status = 0;
            vialAlien.fireRateStat = 0;
            vialAlien.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        vialAlien.x -= 2;

        if (vialAlien.exploState() != 0 && vialAlien.x < 200) {
            return close(state, ghost);
        }

        if (vialAlien.x == MarshRoutines.param(params, P_LAUNCH_X, 256 + 180)
                && vialAlien.exploState() == 0) {
            vars[MODE] = 2;
            bank.count = 2;
            ghost.x = vialAlien.x;
            ghost.y = vialAlien.y;
            ghost.objOff = shapes[0];
            ghost.energy = 2;
            ghost.status = 0;
            ghost.fireRateStat = MarshRoutines.param(params, P_FIRE_RATE, 25) & 0xff;
            ghost.fireRateDyn = 0;
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
            ghost.objOff = shapes[(step / 2) % shapes.length];
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

        ghost.x = home(ghost.x, vars[TARGET_X], MarshRoutines.param(params, P_X_SPEED, 2), vars);
        ghost.y = home(ghost.y, vars[TARGET_Y], MarshRoutines.param(params, P_Y_SPEED, 1), vars);

        vars[TIME]++;
        if (vars[TIME] == 25 * 8) {
            return close(state, ghost);
        }
        return true;
    }

    /** Moves one axis towards its target, and asks for a new bearing on arrival. */
    private int home(int current, int target, int speed, int[] vars) {
        if (Math.abs(target - current) <= speed) {
            vars[TARGET_DELAY] = 1;
        }
        return current < target ? current + speed : current - speed;
    }

    /** {@code .close}: the bank goes, and the ghost is set exploding. */
    private boolean close(GameState state, Alien ghost) {
        MarshRoutines.closePalette(state);
        ghost.status = 1;
        return false;
    }
}
