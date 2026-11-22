package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Crache}, the plant rooted in the ground that spits, from the
 * PLANTE AU SOL CRACHE block of {@code AG_GAME_LMARAIS.S}.
 *
 * <p>It enters from the right at the foot of the screen, drifts left two pixels
 * a frame and plays its eleven step animation every other frame.  Every
 * {@code P_SC_Fire_Rate} frames, while it is still alive, it throws a fire
 * ball that flies left eight pixels a frame along the arc of
 * {@code Sin_Table2} for 38 steps.  It closes once it is past x 200.  Unlike
 * the sea's it has no palette.
 */
final class MarshGroundPlant implements Routine {

    private static final int MODE = 0;
    private static final int ANIM_STEP = 1;
    private static final int ANIM_DELAY = 2;
    private static final int LAUNCH_DELAY = 3;
    private static final int FIRE_STEP = 4;

    private static final int P_FIRE_RATE = 0;

    /** {@code R_Sol_Crache_Shape}, eleven words. */
    static final int SHAPES = 0x04fa0c;
    /** {@code Sin_Table2}, as the {@code lea} of the routine names it. */
    static final int SIN_TABLE_2 = 0x0512c6;

    /** {@code Obj_Fire_Ball}. */
    private final int fireBall = 0x1f6;
    private final int[] shapes;
    private final int[] curve;

    MarshGroundPlant(AgonyBinary bin) {
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
            plant.objOff = shapes[0];
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
            MarshRoutines.closePalette(state);
            return false;
        }

        vars[ANIM_DELAY] ^= 1;
        if (vars[ANIM_DELAY] != 0) {
            int step = vars[ANIM_STEP];
            vars[ANIM_STEP] = step + 2;
            plant.objOff = shapes[(step / 2) % shapes.length];
            if (step == 2 * 10) {
                vars[ANIM_STEP] = 0;
            }
        }

        vars[LAUNCH_DELAY]++;
        if (vars[LAUNCH_DELAY] == MarshRoutines.param(params, P_FIRE_RATE, 45)
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

        // The ball is in flight: two aliens are live.
        bank.count = 2;
        int from = plant.x;
        int step = vars[FIRE_STEP];
        vars[FIRE_STEP] = step + 1;
        if (step == 38) {
            vars[MODE] = 1;
            return true;
        }
        // sub.b: the table byte comes off the low byte of 150 only.
        ball.y = 256 + ((150 - curve[step]) & 0xff);
        ball.x = from - step * 8 - 16;
        return true;
    }
}
