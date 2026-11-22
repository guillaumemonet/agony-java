package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Etoile}, the ground plant that fires a fan of shots, from the
 * SOL TIR EN ETOILE block of {@code AG_GAME_LMARAIS.S}.
 *
 * <p>It drifts in from the right along the ground, two pixels a frame, playing
 * ten frames held for two frames each.  The first frame it is alive and left
 * of the x the script names, it lets three shots go upwards, straight up, up
 * right and up left, which spread three pixels a frame from where it stood.  It
 * closes once it is past x 200.
 */
final class MarshGroundStar implements Routine {

    private static final int MODE = 0;
    private static final int FIRE_STEP = 1;
    private static final int FIRE_X = 2;
    private static final int FIRE_Y = 3;
    private static final int ANIM_STEP = 4;

    private static final int P_LAUNCH_X = 0;

    /** {@code R_SE_Shape}, twenty words. */
    static final int SHAPES = 0x04fb52;

    /** {@code Obj_Tir_1}, {@code Obj_Tir_2} and {@code Obj_Tir_8}: up, up right, up left. */
    private final int[] shotShapes = {0x4a4, 0x4b6, 0x522};
    /** The directions of the three shots, in the order the routine writes them. */
    private static final int[][] DIRECTIONS = {{0, -1}, {1, -1}, {-1, -1}};

    private final int[] shapes;

    MarshGroundStar(AgonyBinary bin) {
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
            plant.status = 0;
            plant.fireRateStat = 0;
            plant.fireRateDyn = 0;
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
            MarshRoutines.closePalette(state);
            return false;
        }

        if (mode == 1) {
            if (plant.exploState() == 0
                    && MarshRoutines.param(params, P_LAUNCH_X, 256 + 140) > plant.x) {
                vars[MODE] = 2;
                for (int i = 0; i < 3; i++) {
                    Alien shot = bank.aliens[1 + i];
                    shot.energy = 10;
                    shot.status = 0;
                    shot.fireRateStat = 0xff;
                    shot.fireRateDyn = 0;
                    shot.objOff = shotShapes[i];
                }
                vars[FIRE_STEP] = 0;
                vars[FIRE_X] = plant.x;
                vars[FIRE_Y] = plant.y;
            }
            return true;
        }

        if (mode != 2) {
            return true;
        }

        bank.count = 4;
        vars[FIRE_STEP] += 3;
        int spread = vars[FIRE_STEP];
        for (int i = 0; i < 3; i++) {
            Alien shot = bank.aliens[1 + i];
            shot.x = vars[FIRE_X] + DIRECTIONS[i][0] * spread;
            shot.y = vars[FIRE_Y] + DIRECTIONS[i][1] * spread;
        }
        return true;
    }
}
