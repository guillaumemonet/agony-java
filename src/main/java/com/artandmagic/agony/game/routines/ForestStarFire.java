package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Tir_Etoile}, the monster that bursts into a star of shots, from the
 * TIR EN ETOILE block of {@code Ag_Game_LFORET.s}.
 *
 * <p>It drifts in from the right at the height the script gives it, playing
 * its nine frames.  At the x the script names it lets eight shots go, one to
 * each point of the compass, spreading three pixels a frame from where it
 * stood; meanwhile it creeps left one pixel a frame, then, once the burst has
 * spread past a hundred pixels, climbs away two up and four left a frame.  The
 * sea version ({@link StarFire}) installs a palette; this one does not.
 *
 * <p>The burst is let go whether or not the monster has been shot by then, as
 * in the original: the launch test only looks at its x.
 */
final class ForestStarFire implements Routine {

    private static final int MODE = 0;
    private static final int ANIM_STEP = 1;
    private static final int ANIM_DELAY = 2;
    private static final int FIRE_STEP = 3;
    private static final int FIRE_X = 4;
    private static final int FIRE_Y = 5;

    private static final int P_LAUNCH_X = 0;
    private static final int P_POS_Y = 1;

    /** {@code R_Tir_Etoile_Shape}, nine frames. */
    private static final int SHAPES = 0x04b952;

    /** The eight directions of the shots, in the order the routine writes them. */
    private static final int[][] DIRECTIONS = {
        {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    /** {@code Obj_Tir_1} to {@code Obj_Tir_8}. */
    static final int[] SHOTS = {0x416, 0x428, 0x43a, 0x44c, 0x45e, 0x470, 0x482, 0x494};

    private final int[] shapes;

    ForestStarFire(AgonyBinary bin) {
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
            monster.y = ForestRoutines.param(params, P_POS_Y, 256 + 80);
            monster.objOff = shapes[0];
            monster.energy = 50;
            ForestRoutines.clearStatus(monster);
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
            if (monster.x == ForestRoutines.param(params, P_LAUNCH_X, 256 + 200)) {
                vars[MODE]++;
                for (int i = 0; i < 8; i++) {
                    Alien shot = bank.aliens[1 + i];
                    shot.energy = 10;
                    ForestRoutines.clearStatus(shot);
                    shot.fireRateStat = 0xff;
                    shot.objOff = SHOTS[i];
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
            ForestRoutines.dropPalette(state);
            return false;
        }
        return true;
    }
}
