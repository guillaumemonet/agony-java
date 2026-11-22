package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.audio.SoundFx;
import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Final}, the boss of the first level, from the MONSTRE FINAL block of
 * {@code Ag_Game_LMER.s}.
 *
 * <p>It sits in the middle of the screen with a hundred and thirty points of
 * energy and throws a wave of balls every seventy frames, cycling through three
 * of them, each entering at its own spot.  Being hit turns its palette white
 * for a couple of frames, which is what the flash and normal palettes are for.
 *
 * <p>When it finally gives, the routine runs the end of the level: the screen is
 * wiped, every track is freed, and four explosions go off in turn with the
 * destruction sound.  The last one sets {@code Quit_Delay}, which is what
 * actually ends the level and sends the game to the next one.
 */
final class FinalMonster implements Routine {

    private static final int MODE = 0;
    private static final int TIME = 1;
    private static final int STEP = 2;
    private static final int EXPLO_STEP = 3;

    /** The three {@code AWS} records it throws, in front of its palettes. */
    static final int WAVE_1 = 0x04f99e;
    static final int WAVE_2 = 0x04f9be;
    static final int WAVE_3 = 0x04f9de;

    private final int[] flash;
    private final int[] normal;
    private final int[] explo;

    private final int body = 0x5c2;
    private final int burst1 = 0x5d8;
    private final int burst2 = 0x60a;
    private final int burst3 = 0x640;

    private final SoundFx sound;
    private int wave1 = -1;
    private int wave2 = -1;
    private int wave3 = -1;

    FinalMonster(AgonyBinary bin, SoundFx sound) {
        this.sound = sound;
        flash = SeaRoutines.palette(bin, 0x04f9fe);
        normal = SeaRoutines.palette(bin, 0x04fa12);
        explo = SeaRoutines.palette(bin, 0x04fa26);
    }

    void bind(int wave1, int wave2, int wave3) {
        this.wave1 = wave1;
        this.wave2 = wave2;
        this.wave3 = wave3;
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien boss = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            boss.x = 256 + 160;
            boss.y = 256 + 90;
            boss.objOff = body;
            boss.energy = 130;
            boss.status = 0;
            boss.fireRateStat = 30;
            boss.fireRateDyn = 0;
            state.routModPalCounter = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;

        if (vars[MODE] == 1) {
            // The white flash the first frames of a hit give it.
            int hit = boss.status & 0xf0;
            if (hit != 0) {
                state.routPalette = hit <= 0x20 ? flash : normal;
                state.refreshPal = GameState.PAL_ASKED;
            }

            if (boss.exploState() == 0) {
                return throwWave(state, vars);
            }
            vars[MODE]++;
        }

        return explode(state, bank, vars);
    }

    /** Every seventy frames it lets one of its three waves go. */
    private boolean throwWave(GameState state, int[] vars) {
        vars[TIME]++;
        if (vars[TIME] != 70) {
            return true;
        }
        vars[TIME] = 0;

        int step = vars[STEP];
        if (step == 0) {
            vars[STEP] = 1;
            launch(state, wave1, 256 + 260, 256 + 108);
        } else if (step == 1) {
            vars[STEP] = 2;
            launch(state, wave2, 256 + 268, 256 + 108);
        } else {
            vars[STEP] = 0;
            launch(state, wave3, 256 + 268, 256 + 100);
        }
        return true;
    }

    private void launch(GameState state, int wave, int x, int y) {
        if (wave >= 0) {
            state.pendingWaves.add(new int[]{wave, x, y});
        }
    }

    /** The end of the level, step by step as the original counts it. */
    private boolean explode(GameState state, AlienBank bank, int[] vars) {
        Alien boss = bank.aliens[0];
        vars[EXPLO_STEP]++;
        int step = vars[EXPLO_STEP];

        switch (step) {
            case 1 -> burst(boss, 256 + 220, 256 + 100, burst1);
            case 17 -> {
                state.routPalette = flash;
                state.refreshPal = GameState.PAL_ASKED;
                bang();
            }
            case 19 -> state.clearScenery = true;
            case 21 -> {
                burst(boss, 256 + 250, 256 + 80, burst1);
                state.routPalette = explo;
                state.refreshPal = GameState.PAL_ASKED;
                bang();
            }
            case 35 -> {
                burst(boss, 256 + 200, 256 + 110, burst2);
                bang();
            }
            case 52 -> {
                burst(boss, 256 + 250, 256 + 120, burst3);
                bang();
                // This is what ends the level and brings the next one in.
                state.quitDelay = 25;
                state.cleanUp = true;
            }
            default -> {
            }
        }
        return true;
    }

    private void burst(Alien alien, int x, int y, int shape) {
        alien.x = x;
        alien.y = y;
        alien.objOff = shape;
        alien.status = 1;
        alien.fireRateStat = 0;
        alien.fireRateDyn = 0;
    }

    private void bang() {
        if (sound != null) {
            sound.request(1, 63);
        }
    }
}
