package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Etoile}, the thing on the ground that throws a fan of three
 * shots, from the SOL TIR EN ETOILE block of {@code Ag_Game_LFEUX.s}.
 *
 * <p>It crawls in along the floor two pixels a frame, cycling ten shapes two
 * frames each.  The first frame it is left of the x the script names, and
 * still in one piece, it throws three shots: one straight up, one up and to
 * the right, one up and to the left, spreading three pixels a frame from where
 * it stood.  The shots go with it when it closes, past x 200.
 */
final class FireGroundStar implements Routine {

    private static final int MODE = 0;
    private static final int FIRE_STEP = 1;
    private static final int FIRE_X = 2;
    private static final int FIRE_Y = 3;
    private static final int ANIM_STEP = 4;

    private static final int P_LAUNCH_X = 0;

    /** {@code R_SE_Shape}, twenty words in front of the code. */
    private static final int SHAPES = 0x050f3a;

    /** {@code Obj_Tir_1}, {@code Obj_Tir_2} and {@code Obj_Tir_8}: up, up right, up left. */
    private static final int[] SHOT_SHAPES = {0x4a0, 0x4b2, 0x52e};
    private static final int[][] DIRECTIONS = {{0, -1}, {1, -1}, {-1, -1}};

    private final int[] shapes;

    FireGroundStar(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 20);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien monster = bank.aliens[0];
        int mode = vars[MODE];

        if (mode == 0) {
            bank.count = 1;
            bank.first = 0;
            monster.x = 256 + 300;
            monster.y = 256 + 191;
            monster.objOff = shapes[0];
            monster.energy = 6;
            monster.status = 0;
            monster.fireRateStat = 0;
            monster.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        vars[ANIM_STEP]++;
        if (vars[ANIM_STEP] == 20) {
            vars[ANIM_STEP] = 0;
        }
        monster.objOff = shapes[vars[ANIM_STEP]];

        monster.x -= 2;
        if (monster.x <= 200) {
            return FireRoutines.close(state);
        }

        if (mode == 1) {
            if (monster.exploState() == 0 && params[P_LAUNCH_X] > monster.x) {
                vars[MODE] = 2;
                for (int i = 0; i < 3; i++) {
                    Alien shot = bank.aliens[1 + i];
                    shot.energy = 10;
                    shot.status = 0;
                    shot.fireRateStat = 0xff;
                    shot.fireRateDyn = 0;
                    shot.objOff = SHOT_SHAPES[i];
                }
                vars[FIRE_STEP] = 0;
                vars[FIRE_X] = monster.x;
                vars[FIRE_Y] = monster.y;
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
