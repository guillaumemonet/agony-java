package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Crache}, the plant rooted in the ground that spits, from the
 * PLANTE AU SOL CRACHE block of {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>The same plant as on the first level, without the palette it recoloured
 * the bands with there.  It enters from the right at the foot of the screen,
 * drifts left two pixels a frame and plays its eleven step animation every
 * other frame.  Every {@code P_SC_Fire_Rate} frames, if it is still whole, it
 * throws a fire ball that falls left along 38 steps of {@code Sin_Table2},
 * eight pixels a step further from it; then it goes back to waiting.  It
 * closes once it has drifted off the left edge.
 */
final class HighlandGroundPlant implements Routine {

    private static final int MODE = 0;
    private static final int ANIM_STEP = 1;
    private static final int ANIM_DELAY = 2;
    private static final int LAUNCH_DELAY = 3;
    private static final int FIRE_STEP = 4;

    private static final int P_FIRE_RATE = 0;

    /** {@code R_Sol_Crache_Shape}. */
    private static final int SHAPES = 0x04f870;
    /** {@code Sin_Table2}, the curve the ball falls along. */
    private static final int SIN_TABLE_2 = 0x050f62;

    /** {@code Obj_Sol_Crache_1} and {@code Obj_FireBall}, as the code loads them. */
    private final int plantShape = 0x59c;
    private final int fireBall = 0x5ea;

    private final int[] shapes;
    private final int[] curve;

    HighlandGroundPlant(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 11);
        curve = SeaRoutines.bytes(bin, SIN_TABLE_2, 38);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien plant = bank.aliens[0];
        Alien ball = bank.aliens[1];
        int mode = vars[MODE];

        if (mode == 0) {
            bank.count = 1;
            bank.first = 0;
            plant.x = 256 + 320;
            plant.y = 256 + 190;
            plant.objOff = plantShape;
            plant.energy = 8;
            plant.status = 0;
            plant.fireRateStat = 0;
            plant.fireRateDyn = 0;
            vars[MODE]++;
            vars[LAUNCH_DELAY] = 0;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        plant.x -= 2;
        if (plant.x <= 200) {
            HighlandRoutines.dropPalette(state);
            return false;
        }

        vars[ANIM_DELAY] ^= 1;
        if (vars[ANIM_DELAY] != 0) {
            int step = vars[ANIM_STEP];
            vars[ANIM_STEP] = step + 2;
            plant.objOff = shapes[step / 2];
            if (step == 2 * 10) {
                vars[ANIM_STEP] = 0;
            }
        }

        vars[LAUNCH_DELAY]++;
        if (vars[LAUNCH_DELAY] == HighlandRoutines.param(params, P_FIRE_RATE, 45)
                && plant.exploState() == 0) {
            vars[LAUNCH_DELAY] = 0;
            vars[MODE] = 2;
            vars[FIRE_STEP] = 0;
            ball.objOff = fireBall;
            ball.energy = 2;
            ball.status = 0;
            ball.fireRateStat = 0;
            ball.fireRateDyn = 0;
            return true;
        }

        if (mode != 2) {
            return true;
        }

        bank.count = 2;
        int from = plant.x;
        int step = vars[FIRE_STEP];
        vars[FIRE_STEP] = step + 1;
        if (step == 38) {
            vars[MODE] = 1;
            return true;
        }
        // sub.b: the curve comes off the low byte of 150 only.
        ball.y = 256 + ((150 - curve[step]) & 0xff);
        ball.x = from - step * 8 - 16;
        return true;
    }
}
