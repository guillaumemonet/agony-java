package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

import java.util.Map;

/**
 * {@code R_Final}, the boss of the forest, from the MONSTRE FINAL block of
 * {@code Ag_Game_LFORET.s}.
 *
 * <p>Two aliens make it: a body of a hundred and fifty points that cycles
 * through {@code R_F_Anim_Up}, and below it a base of twenty thousand that
 * cannot really be worn down and flaps between two frames every eight.  The
 * pair sways from side to side between x 160 and x 260, two pixels a frame.
 * Every eighteen frames it throws a pair of boomerangs out of the six
 * {@code AWS} records of {@code R_T_Table}, in turn, from twenty pixels above
 * and to the left of the body.  The routine grabs the track and the bank for
 * them itself, exactly as the script would.
 *
 * <p>When the body is shot down the routine runs the end of the level: four
 * big explosions over fifty two frames, the last three with the destruction
 * sound, and {@code Clean_Up} at the last one.
 *
 * <p>The compiled overlay is a slightly later build than the source file, and
 * the port follows the overlay, which is what the game runs:
 * <ul>
 *   <li>the body fires every 20 frames ({@code move.b #$14}), not every 30;</li>
 *   <li>{@code Quit_Delay} is set to 100 at the first explosion
 *       ({@code move.w #$64,Quit_Delay}), so the level ends a hundred frames
 *       after the boss gives; the source sets it to 25 at the last explosion
 *       instead, and the overlay drops that instruction.</li>
 * </ul>
 */
final class ForestFinalMonster implements Routine {

    private static final int MODE = 0;
    private static final int STEP = 1;
    private static final int EXPLO_STEP = 2;
    private static final int X_MODE = 3;
    private static final int ANIM_DELAY = 4;
    private static final int ANIM_UP_STEP = 5;
    private static final int LAUNCH_DELAY = 6;

    /** {@code R_T_Table}, six pointers to the {@code AWS} records it throws. */
    private static final int WAVE_TABLE = 0x04c376;
    /** {@code R_F_Anim_Up}, eight frames of the body. */
    private static final int ANIM_UP = 0x04c38e;

    /** {@code Obj_Final_1}, {@code Obj_Final_Bas_1} and {@code _2}. */
    private final int body = 0x232;
    private final int base1 = 0x20e;
    private final int base2 = 0x220;
    /** {@code Obj_Big_Explo_1} to {@code _3}. */
    private final int explo1 = 0x6d0;
    private final int explo2 = 0x706;
    private final int explo3 = 0x73e;

    private final int[] waveAddresses = new int[6];
    private final int[] waves = {-1, -1, -1, -1, -1, -1};
    private final int[] animUp;

    ForestFinalMonster(AgonyBinary bin) {
        for (int i = 0; i < waveAddresses.length; i++) {
            waveAddresses[i] = bin.u32(WAVE_TABLE + i * 4);
        }
        animUp = SeaRoutines.words(bin, ANIM_UP, 8);
    }

    int[] waveAddresses() {
        return waveAddresses.clone();
    }

    void bind(Map<Integer, Integer> indexByAddress) {
        for (int i = 0; i < waves.length; i++) {
            waves[i] = indexByAddress.getOrDefault(waveAddresses[i], -1);
        }
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien top = bank.aliens[0];
        Alien bottom = bank.aliens[1];

        if (vars[MODE] == 0) {
            bank.count = 2;
            bank.first = 0;
            top.x = 256 + 350;
            top.y = 256 + 130;
            bottom.x = 256 + 350;
            bottom.y = 256 + 130;
            top.objOff = body;
            bottom.objOff = base1;
            top.energy = 150;
            bottom.energy = 20000;
            ForestRoutines.clearStatus(top);
            ForestRoutines.clearStatus(bottom);
            top.fireRateStat = 20;
            bottom.fireRateStat = 0xff;
            state.routModPalCounter = 0;
            vars[X_MODE] = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 2;
        bank.first = 0;
        if (vars[MODE] == 2) {
            return explode(state, top, bottom, vars);
        }

        // The sway: X_Mode is 0 going left and -1 going right.
        if (vars[X_MODE] != 0) {
            top.x += 2;
            bottom.x += 2;
            if (top.x >= 256 + 260) {
                vars[X_MODE]++;
            }
        } else {
            top.x -= 2;
            bottom.x -= 2;
            if (top.x <= 256 + 160) {
                vars[X_MODE]--;
            }
        }

        vars[ANIM_DELAY]++;
        bottom.objOff = (vars[ANIM_DELAY] & 8) != 0 ? base1 : base2;
        vars[ANIM_UP_STEP] = (vars[ANIM_UP_STEP] + 1) & 7;
        top.objOff = animUp[vars[ANIM_UP_STEP]];

        if (top.exploState() != 0) {
            vars[EXPLO_STEP] = 0;
            vars[MODE]++;
            return explode(state, top, bottom, vars);
        }

        vars[LAUNCH_DELAY]++;
        if (vars[LAUNCH_DELAY] != 18) {
            return true;
        }
        vars[LAUNCH_DELAY] = 0;
        vars[STEP]++;
        if (vars[STEP] == 6) {
            vars[STEP] = 0;
        }
        int wave = waves[vars[STEP]];
        if (wave >= 0) {
            state.pendingWaves.add(new int[]{wave, top.x - 20, top.y - 20});
        }
        return true;
    }

    /** The end of the level, step by step as the original counts it. */
    private boolean explode(GameState state, Alien top, Alien bottom, int[] vars) {
        vars[EXPLO_STEP]++;
        switch (vars[EXPLO_STEP]) {
            case 1 -> {
                // Only in the overlay: this is what ends the level.
                state.quitDelay = 100;
                burst(top, explo1);
                burst(bottom, explo1);
            }
            case 21 -> {
                burst(top, explo1);
                state.shortPhase = true;
                bang(state);
            }
            case 35 -> {
                burst(top, explo2);
                burst(bottom, explo2);
                bang(state);
            }
            case 52 -> {
                burst(top, explo3);
                burst(bottom, explo3);
                bang(state);
                state.cleanUp = true;
            }
            default -> {
            }
        }
        return true;
    }

    /** {@code clr.l AWO_Alien_Status} then {@code move.b #1}: explode again. */
    private static void burst(Alien alien, int shape) {
        alien.objOff = shape;
        ForestRoutines.clearStatus(alien);
        alien.status = 1;
    }

    /** {@code Sound0_Req} 1 at volume 63, then {@code Sound_Start}. */
    private static void bang(GameState state) {
        state.requestSound(1, 63);
    }
}
