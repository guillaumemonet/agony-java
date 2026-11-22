package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Tir_Etoile}, the monster that bursts into a star of shots, from the
 * TIR EN ETOILE block of {@code AG_GAME_LMARAIS.S}.
 *
 * <p>It enters at the right, at the height the script gives, playing a nine
 * frame animation every other frame.  The moment it reaches the x the script
 * names it lets eight shots go, one to each point of the compass, spreading
 * three pixels a frame from where it stood; the monster then drifts left a
 * pixel a frame, and once the spread passes a hundred it climbs away, two up
 * and four left a frame, until it is off the left edge.  Unlike the sea's it
 * has no palette.
 */
final class MarshStarFire implements Routine {

    private static final int MODE = 0;
    private static final int ANIM_STEP = 1;
    private static final int ANIM_DELAY = 2;
    private static final int FIRE_STEP = 3;
    private static final int FIRE_X = 4;
    private static final int FIRE_Y = 5;

    private static final int P_LAUNCH_X = 0;
    private static final int P_POS_Y = 1;

    /** {@code R_Tir_Etoile_Shape}, nine words. */
    static final int SHAPES = 0x04f110;

    /** The eight directions the shots take, in the order the routine writes them. */
    private static final int[][] DIRECTIONS = {
        {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}
    };

    private final int[] shapes;
    /** {@code Obj_Tir_1} to {@code Obj_Tir_8}, the immediates of this build. */
    private final int[] shotShapes = {0x4a4, 0x4b6, 0x4c8, 0x4da, 0x4ec, 0x4fe, 0x510, 0x522};

    MarshStarFire(AgonyBinary bin) {
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
            monster.y = MarshRoutines.param(params, P_POS_Y, 256 + 80);
            monster.objOff = shapes[0];
            monster.energy = 50;
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
            monster.objOff = shapes[(step / 2) % shapes.length];
            if (step == 8 * 2) {
                vars[ANIM_STEP] = 0;
            }
        }

        if (mode == 1) {
            monster.x -= 2;
            if (monster.x == MarshRoutines.param(params, P_LAUNCH_X, 256 + 200)) {
                vars[MODE]++;
                for (int i = 0; i < 8; i++) {
                    Alien shot = bank.aliens[1 + i];
                    shot.energy = 10;
                    shot.status = 0;
                    shot.fireRateStat = 0xff;
                    shot.fireRateDyn = 0;
                    shot.objOff = shotShapes[i];
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
            MarshRoutines.closePalette(state);
            return false;
        }
        return true;
    }
}
