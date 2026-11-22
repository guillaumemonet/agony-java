package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Etoile}, the ground creature that throws a fan of three shots,
 * from the SOL TIR EN ETOILE block of {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>It crawls in along the floor two pixels a frame, cycling ten shapes, each
 * held for two frames.  The first frame it is left of the x the script names,
 * if it is still whole, it lets three shots go from where it stands: one
 * straight up, one up and right, one up and left, spreading three pixels a
 * frame.  The shots keep the point they left from while the creature crawls on,
 * and they last as long as the routine does, which closes once the creature
 * has left the screen.
 */
final class HighlandGroundStar implements Routine {

    private static final int MODE = 0;
    private static final int FIRE_STEP = 1;
    private static final int FIRE_X = 2;
    private static final int FIRE_Y = 3;
    private static final int ANIM_STEP = 4;

    private static final int P_LAUNCH_X = 0;

    /** {@code R_SE_Shape}. */
    private static final int SHAPES = 0x0504fe;

    /** {@code Obj_Sol_Etoile_1}, as the code loads it. */
    private final int firstShape = 0x6ea;
    /** {@code Obj_Tir_1}, {@code Obj_Tir_2} and {@code Obj_Tir_8}: up, up right, up left. */
    private final int[] shotShapes = {0x4e4, 0x4f6, 0x562};
    /** The direction of each shot, in the order the routine writes them. */
    private static final int[][] DIRECTIONS = {{0, -1}, {1, -1}, {-1, -1}};

    private final int[] shapes;

    HighlandGroundStar(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 20);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien creature = bank.aliens[0];
        int mode = vars[MODE];
        bank.count = 1;
        bank.first = 0;

        if (mode == 0) {
            creature.x = 256 + 300;
            creature.y = 256 + 191;
            creature.objOff = firstShape;
            creature.energy = 10;
            creature.status = 0;
            creature.fireRateStat = 0;
            creature.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        vars[ANIM_STEP]++;
        if (vars[ANIM_STEP] == 20) {
            vars[ANIM_STEP] = 0;
        }
        creature.objOff = shapes[vars[ANIM_STEP]];
        creature.x -= 2;
        if (creature.x <= 200) {
            HighlandRoutines.dropPalette(state);
            return false;
        }

        if (mode == 1) {
            if (creature.exploState() == 0
                    && HighlandRoutines.param(params, P_LAUNCH_X, 256 + 120) > creature.x) {
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
                vars[FIRE_X] = creature.x;
                vars[FIRE_Y] = creature.y;
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
