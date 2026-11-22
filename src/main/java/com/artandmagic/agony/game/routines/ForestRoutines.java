package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.GameState;

import java.util.HashMap;
import java.util.Map;

/**
 * The level routines of the forest, level 2, {@code Game/Ag_Game_LFORET.s}.
 *
 * <p>Eight of them are called by the script: the ghost with its vial, the star
 * shot, the flying monster with its guided missile, the spider on its thread,
 * the fast one that darts across, the kamikaze, the ground plant that throws a
 * fan of three shots, and the final monster.  The {@code Demo_Page} calls of
 * the source are assembled only in the demo build ({@code ASM_Demo_Mode=2}) and
 * are not in the script of this overlay.
 *
 * <p>The addresses are read off the {@code Start_C} entries of the script; the
 * source and the binary agree on the trigger and the parameters of every call,
 * which ties each name to its address:
 *
 * <pre>
 *   $04b76c  R_Spectre          $04bee4  R_Araignee
 *   $04b964  R_Tir_Etoile       $04bfa2  R_Rapide
 *   $04bb60  R_Volant_Missile   $04c048  R_Kamikaze
 *   $04c160  R_Sol_Etoile       $04c39e  R_Final
 * </pre>
 *
 * <p>The routines of this level differ from their namesakes of the sea in small
 * ways — none of them installs a palette of its own, and they close with
 * {@link #dropPalette} rather than by giving one back — so each has a class of
 * its own here.
 */
public final class ForestRoutines implements RoutineLibrary {

    private final Map<Integer, Routine> byAddress = new HashMap<>();
    private final ForestFinalMonster boss;

    public ForestRoutines(AgonyBinary bin) {
        boss = new ForestFinalMonster(bin);
        byAddress.put(0x04b76c, new ForestGhost(bin));
        byAddress.put(0x04b964, new ForestStarFire(bin));
        byAddress.put(0x04bb60, new ForestMissileFlyer());
        byAddress.put(0x04bee4, new ForestSpider());
        byAddress.put(0x04bfa2, new ForestDarter(bin));
        byAddress.put(0x04c048, new ForestKamikaze());
        byAddress.put(0x04c160, new ForestGroundStar(bin));
        byAddress.put(0x04c39e, boss);
    }

    @Override
    public Routine forAddress(int address) {
        return byAddress.get(address);
    }

    @Override
    public int[] waveAddresses() {
        return boss.waveAddresses();
    }

    @Override
    public void bindWaves(Map<Integer, Integer> indexByAddress) {
        boss.bind(indexByAddress);
    }

    /** {@code R_Final} sets {@code Quit_Delay}: the boss ends the forest. */
    @Override
    public boolean endsLevel() {
        return true;
    }

    /**
     * The close of every routine of this level:
     * <pre>
     *   tst   Rout_Mod_Pal_Counter(D)
     *   bne   .no_close_col
     *   move.l #-1,Rout_Pal_Ptr(D)
     *   st    Refresh_Pal+1(D)
     * </pre>
     * The routine palette is dropped only when no routine is counted as
     * holding it; the counter itself is left alone.
     */
    static void dropPalette(GameState state) {
        if (state.routModPalCounter == 0) {
            state.routPalette = null;
            state.refreshPal = GameState.PAL_ASKED;
        }
    }

    /**
     * {@code clr.l AWO_Alien_Status}: the status byte, both fire rate bytes and
     * the spare byte behind them go to zero in one move.
     */
    static void clearStatus(Alien alien) {
        alien.status = 0;
        alien.fireRateStat = 0;
        alien.fireRateDyn = 0;
    }

    /** A parameter word of the {@code Start_C} call. */
    static int param(int[] params, int index, int fallback) {
        return params != null && params.length > index ? params[index] : fallback;
    }
}
