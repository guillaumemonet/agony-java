package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Tir_Etoile}, the monster that bursts into a star of shots, from the
 * TIR EN ETOILE block of {@code Ag_Game_LFEUX.s}.
 *
 * <p>The same as the first level's {@link StarFire}: it drifts in from the
 * right playing a nine frame animation, and the moment it reaches the x the
 * script names it lets eight shots go, one to each point of the compass,
 * spreading three pixels a frame from where it stood; four seconds into the
 * burst it climbs away until it is off the left of the screen.
 *
 * <p>Here it installs no palette, its shots die at five hits instead of ten,
 * and the monster itself is given 32766 points of energy: it is not meant to
 * be shot down, only its shots are.
 */
final class FireStarFire implements Routine {

    private static final int MODE = 0;
    private static final int ANIM_STEP = 1;
    private static final int ANIM_DELAY = 2;
    private static final int FIRE_STEP = 3;
    private static final int FIRE_X = 4;
    private static final int FIRE_Y = 5;

    private static final int P_LAUNCH_X = 0;
    private static final int P_POS_Y = 1;

    /** {@code R_Tir_Etoile_Shape}, nine words in front of the code. */
    private static final int SHAPES = 0x050d40;

    /** The eight directions the shots take, in the order the routine writes them. */
    private static final int[][] DIRECTIONS = {
        {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    /** {@code Obj_Tir_1} to {@code Obj_Tir_8}. */
    private static final int[] SHOT_SHAPES = {0x4a0, 0x4b2, 0x4cc, 0x4de, 0x4f4, 0x506, 0x51c, 0x52e};

    private final int[] shapes;

    FireStarFire(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 9);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien monster = bank.aliens[0];
        int mode = vars[MODE];

        if (mode == 0) {
            bank.count = 1;
            bank.first = 0;
            monster.x = 256 + 300;
            monster.y = params[P_POS_Y];
            monster.objOff = shapes[0];
            monster.energy = 32766;
            monster.status = 0;
            monster.fireRateStat = 0;
            monster.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;

        vars[ANIM_DELAY] ^= 1;
        if (vars[ANIM_DELAY] != 0) {
            int step = vars[ANIM_STEP];
            vars[ANIM_STEP] = step + 2;
            monster.objOff = shapes[step / 2];
            if (step == 8 * 2) {
                vars[ANIM_STEP] = 0;
            }
        }

        if (mode == 1) {
            monster.x -= 2;
            if (monster.x == params[P_LAUNCH_X]) {
                vars[MODE]++;
                for (int i = 0; i < 8; i++) {
                    Alien shot = bank.aliens[1 + i];
                    shot.energy = 5;
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

        bank.count = 9;
        vars[FIRE_STEP] += 3;
        int spread = vars[FIRE_STEP];
        if (spread > 25 * 4) {
            monster.y -= 2;
            monster.x -= 4;
        } else {
            monster.x -= 1;
        }

        for (int i = 0; i < 8; i++) {
            Alien shot = bank.aliens[1 + i];
            shot.x = vars[FIRE_X] + DIRECTIONS[i][0] * spread;
            shot.y = vars[FIRE_Y] + DIRECTIONS[i][1] * spread;
        }

        if (monster.x <= 256 - 32) {
            return FireRoutines.close(state);
        }
        return true;
    }
}
