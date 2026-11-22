package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Spectre}, from the SPECTRE + FIOLLE block of
 * {@code Ag_Game_LMER.s}.
 *
 * <p>A vial drifts in from the right.  Reach the x the script names without
 * having broken it and the ghost comes out: it flies left playing its five
 * frames, and once they are through it turns and hunts the sorcerer, taking a
 * new bearing on him every eighteen frames and closing at the speed the script
 * gives it.  Eight seconds later it gives up and blows itself up.
 */
final class Ghost implements Routine {

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

    private final int[] palette;
    private final int[] shapes;
    private final int vial = 0x35a;
    private final int first = 0x370;
    private final int wide = 0x3d4;
    private final int narrow = 0x3b6;

    Ghost(AgonyBinary bin) {
        palette = SeaRoutines.palette(bin, 0x04e9d2);
        shapes = SeaRoutines.words(bin, 0x04e9e6, 5);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien vialAlien = bank.aliens[0];
        Alien ghost = bank.aliens[1];
        int mode = vars[MODE];

        if (mode == 0) {
            SeaRoutines.takePalette(state, palette);
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

        // A broken vial that has drifted off the left ends the routine.
        if (vialAlien.exploState() != 0 && vialAlien.x < 200) {
            return close(state, ghost);
        }

        if (vialAlien.x == param(params, P_LAUNCH_X, 256 + 200) && vialAlien.exploState() == 0) {
            vars[MODE] = 2;
            bank.count = 2;
            ghost.x = vialAlien.x;
            ghost.y = vialAlien.y;
            ghost.objOff = first;
            ghost.energy = 2;
            ghost.status = 0;
            ghost.fireRateStat = param(params, P_FIRE_RATE, 25) & 0xff;
            ghost.fireRateDyn = 0;
            vars[ANIM_STEP] = 0;
            vars[ANIM_DELAY] = 0;
            return true;
        }

        if (mode == 2) {
            bank.count = 2;
            ghost.x -= 2;
            // eor #1,R_S_Anim_Delay / bne .no: the shape moves on when the
            // flip leaves zero, every other frame starting with the second.
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

        ghost.x = home(ghost.x, vars[TARGET_X], param(params, P_X_SPEED, 2), vars);
        ghost.y = home(ghost.y, vars[TARGET_Y], param(params, P_Y_SPEED, 2), vars);

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

    private boolean close(GameState state, Alien ghost) {
        SeaRoutines.releasePalette(state);
        ghost.status = 1;
        return false;
    }

    private static int param(int[] params, int index, int fallback) {
        return params != null && params.length > index ? params[index] : fallback;
    }
}
