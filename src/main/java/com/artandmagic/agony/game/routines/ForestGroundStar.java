package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Etoile}, from the SOL TIR EN ETOILE block of
 * {@code Ag_Game_LFORET.s}.
 *
 * <p>A plant on the ground that rides in with the scenery, two pixels a frame,
 * cycling through the twenty steps of {@code R_SE_Shape} (ten frames, each
 * held twice).  Once it has passed the x the script names, and as long as it
 * has not been shot, it throws a fan of three shots — straight up, up to the
 * right and up to the left — which spread three pixels a frame from where it
 * stood.  It closes when it reaches x 200, its shots with it.
 */
final class ForestGroundStar implements Routine {

    private static final int MODE = 0;
    private static final int FIRE_STEP = 1;
    private static final int FIRE_X = 2;
    private static final int FIRE_Y = 3;
    private static final int ANIM_STEP = 4;

    private static final int P_LAUNCH_X = 0;

    /** {@code R_SE_Shape}, twenty words. */
    private static final int SHAPES = 0x04c138;

    /** {@code Obj_Tir_1}, {@code Obj_Tir_2} and {@code Obj_Tir_8}, with their directions. */
    private static final int[] SHOTS = {ForestStarFire.SHOTS[0], ForestStarFire.SHOTS[1],
        ForestStarFire.SHOTS[7]};
    private static final int[] SHOT_DX = {0, 1, -1};

    private final int[] shapes;

    ForestGroundStar(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 20);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien plant = bank.aliens[0];
        int mode = vars[MODE];

        if (mode == 0) {
            bank.count = 1;
            bank.first = 0;
            plant.x = 256 + 300;
            plant.y = 256 + 191;
            plant.objOff = shapes[0];
            plant.energy = 10;
            ForestRoutines.clearStatus(plant);
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        vars[ANIM_STEP]++;
        if (vars[ANIM_STEP] == 20) {
            vars[ANIM_STEP] = 0;
        }
        plant.objOff = shapes[vars[ANIM_STEP]];

        plant.x -= 2;
        if (plant.x <= 200) {
            ForestRoutines.dropPalette(state);
            return false;
        }

        if (mode == 1) {
            int launchX = ForestRoutines.param(params, P_LAUNCH_X, 256 + 150);
            if (plant.exploState() == 0 && plant.x < launchX) {
                vars[MODE] = 2;
                for (int i = 0; i < SHOTS.length; i++) {
                    Alien shot = bank.aliens[1 + i];
                    shot.energy = 10;
                    ForestRoutines.clearStatus(shot);
                    shot.fireRateStat = 0xff;
                    shot.objOff = SHOTS[i];
                }
                vars[FIRE_STEP] = 0;
                vars[FIRE_X] = plant.x;
                vars[FIRE_Y] = plant.y;
            }
            return true;
        }

        if (mode == 2) {
            bank.count = 4;
            vars[FIRE_STEP] += 3;
            int spread = vars[FIRE_STEP];
            for (int i = 0; i < SHOTS.length; i++) {
                Alien shot = bank.aliens[1 + i];
                shot.x = vars[FIRE_X] + SHOT_DX[i] * spread;
                shot.y = vars[FIRE_Y] - spread;
            }
        }
        return true;
    }
}
