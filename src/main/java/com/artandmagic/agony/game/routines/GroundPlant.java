package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Crache}, the plant rooted in the ground that spits, from the
 * PLANTE AU SOL CRACHE block of {@code Ag_Game_LMER.s}.
 *
 * <p>It enters from the right at the foot of the screen, drifts left two pixels
 * a frame and cycles a short animation.  Every {@code P_SC_Fire_Rate} frames it
 * opens its mouth and throws a fire ball, which flies left and down along a
 * curve of 38 steps; when the ball is done the plant goes back to waiting.  It
 * closes itself once it has drifted off the left edge, and gives back the
 * palette it recoloured the lower bands with.
 */
final class GroundPlant implements Routine {

    // Variable slots, the RSRESET block in front of the routine.
    private static final int MODE = 0;
    private static final int ANIM_STEP = 1;
    private static final int ANIM_DELAY = 2;
    private static final int LAUNCH_DELAY = 3;
    private static final int FIRE_STEP = 4;

    /** The one parameter the script passes: how often it spits. */
    private static final int P_FIRE_RATE = 0;

    /** Shape of the fire ball, resolved in this build to object 496. */
    private final int fireBall;
    /** Object the plant starts as, the first entry of its shape list. */
    private final int firstShape;
    private final int[] shapes;
    private final int[] palette;
    private final int[] curve;

    GroundPlant(AgonyBinary bin) {
        palette = SeaRoutines.palette(bin, 0x04f170);
        shapes = SeaRoutines.words(bin, 0x04f184, 11);
        curve = SeaRoutines.bytes(bin, 0x050a24, 38);
        firstShape = shapes[0];
        fireBall = 496;
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien plant = bank.aliens[0];
        Alien ball = bank.aliens[1];
        int mode = vars[MODE];

        if (mode == 0) {
            SeaRoutines.takePalette(state, palette);
            bank.count = 1;
            bank.first = 0;
            plant.x = 256 + 320;
            plant.y = 256 + 190;
            plant.objOff = firstShape;
            plant.energy = 8;
            plant.status = 0;
            plant.fireRateStat = 0;
            plant.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        plant.x -= 2;
        if (plant.x <= 200) {
            SeaRoutines.releasePalette(state);
            return false;
        }

        // The animation advances every other frame.
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
        int rate = params.length > P_FIRE_RATE ? params[P_FIRE_RATE] : 45;
        if (vars[LAUNCH_DELAY] == rate && plant.exploState() == 0) {
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
        ball.y = 256 + 150 - curve[step];
        ball.x = from - step * 8 - 16;
        return true;
    }
}
