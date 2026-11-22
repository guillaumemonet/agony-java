package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Transporteur}, from the TRANSPORTEUR block of
 * {@code AG_GAME_LMARAIS.S}.
 *
 * <p>It cannot be shot: its energy is -1.  It crawls in from the right one
 * pixel a frame, animating every other frame, and the moment it reaches the x
 * the script names it unloads its two attack waves, {@code R_T_Transporteur1}
 * and {@code R_T_Transporteur2}, both starting four pixels behind it.  Then it
 * shakes on the spot for six seconds, blows up, and closes a second later.
 * Unlike the sea's it has no palette.
 *
 * <p>The original fills the energy and fire rate of 32 aliens after grabbing a
 * bank, which runs over into the bank that follows; the engine's
 * {@code startWave} fills the sixteen of the bank, as it does for the sea.
 */
final class MarshTransporter implements Routine {

    private static final int MODE = 0;
    private static final int ANIM_STEP = 1;
    private static final int ANIM_DELAY = 2;
    private static final int TIME = 3;

    private static final int P_LAUNCH_X = 0;

    /** {@code R_Transporteur_Shape}, four words. */
    static final int SHAPES = 0x04f830;
    /** The two {@code AWS} records that sit between its shapes and its code. */
    static final int WAVE_1 = 0x04f838;
    static final int WAVE_2 = 0x04f858;

    private final int[] shapes;
    private int wave1 = -1;
    private int wave2 = -1;

    MarshTransporter(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 4);
    }

    void bind(int wave1, int wave2) {
        this.wave1 = wave1;
        this.wave2 = wave2;
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        int mode = vars[MODE];

        if (mode == 0) {
            bank.count = 1;
            bank.first = 0;
            alien.x = 256 + 320;
            alien.y = 256 + 144;
            alien.objOff = shapes[0];
            alien.energy = 0xffff;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[TIME] = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;

        if (mode == 1) {
            vars[ANIM_DELAY] ^= 1;
            if (vars[ANIM_DELAY] != 0) {
                animate(alien, vars);
            }
            alien.x -= 1;
            int launchX = MarshRoutines.param(params, P_LAUNCH_X, 256 + 150);
            if (alien.x != launchX) {
                return true;
            }
            vars[MODE]++;
            // st Refresh_Pal+1, then a track and a bank for each wave.
            state.refreshPal = GameState.PAL_ASKED;
            int x = launchX - 4;
            int y = 256 + 138;
            if (wave1 >= 0) {
                state.pendingWaves.add(new int[]{wave1, x, y});
            }
            if (wave2 >= 0) {
                state.pendingWaves.add(new int[]{wave2, x, y});
            }
            return true;
        }

        // Mode 2: it shakes on the spot, then goes up.
        animate(alien, vars);
        vars[TIME]++;
        if (vars[TIME] == 25 * 6) {
            alien.status = 1;
        }
        if (vars[TIME] == 25 * 7) {
            MarshRoutines.closePalette(state);
            return false;
        }
        return true;
    }

    private void animate(Alien alien, int[] vars) {
        int step = vars[ANIM_STEP];
        vars[ANIM_STEP] = step + 2;
        alien.objOff = shapes[(step / 2) % shapes.length];
        if (step == 3 * 2) {
            vars[ANIM_STEP] = 0;
        }
    }
}
