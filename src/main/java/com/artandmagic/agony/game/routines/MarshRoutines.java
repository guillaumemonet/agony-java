package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.GameState;

import java.util.HashMap;
import java.util.Map;

/**
 * The level routines of the marshes, level 3, {@code Game/AG_GAME_LMARAIS.S}.
 *
 * <p>Eleven routines are called by the script: the ghost with its vial, the
 * star shot, the flier with its guided missile, the darter, the flying
 * kamikaze, the transporter, the ground plant that spits, the ground plant that
 * fires a fan of three shots, the jumper, the ground kamikaze and the final
 * monster with its tongue.
 *
 * <p>The addresses below are where each routine sits in this build, read off
 * the {@code Start_C} entries of the script; the source and the binary agree on
 * the trigger and the parameters of every call, and the routines are assembled
 * in the order of the source, which ties every name to its address.
 *
 * <p>None of the routines of this level installs a palette of its own: the
 * shared close sequence only gives back {@code Rout_Pal_Ptr} when no routine
 * counts itself in {@code Rout_Mod_Pal_Counter}, and the {@code Dummy_Pal} the
 * script hands {@code R_Rapide} is never read.
 */
public final class MarshRoutines implements RoutineLibrary {

    private final Map<Integer, Routine> byAddress = new HashMap<>();
    private final MarshTransporter transporter;

    public MarshRoutines(AgonyBinary bin) {
        transporter = new MarshTransporter(bin);
        byAddress.put(0x04ef2a, new MarshGhost(bin));
        byAddress.put(0x04f122, new MarshStarFire(bin));
        byAddress.put(0x04f30a, new MarshMissileFlyer(bin));
        byAddress.put(0x04f6a2, new MarshDarter(bin));
        byAddress.put(0x04f740, new MarshKamikaze(bin));
        byAddress.put(0x04f878, transporter);
        byAddress.put(0x04fa22, new MarshGroundPlant(bin));
        byAddress.put(0x04fb7a, new MarshGroundStar(bin));
        byAddress.put(0x04fcd0, new MarshJumper(bin));
        byAddress.put(0x04fd7c, new MarshGroundKamikaze(bin));
        byAddress.put(0x04fe74, new MarshFinalMonster(bin));
    }

    @Override
    public Routine forAddress(int address) {
        return byAddress.get(address);
    }

    @Override
    public int[] waveAddresses() {
        return new int[]{MarshTransporter.WAVE_1, MarshTransporter.WAVE_2};
    }

    @Override
    public void bindWaves(Map<Integer, Integer> indexByAddress) {
        transporter.bind(indexByAddress.getOrDefault(MarshTransporter.WAVE_1, -1),
                indexByAddress.getOrDefault(MarshTransporter.WAVE_2, -1));
    }

    /**
     * The tail of every close of this level: {@code tst Rout_Mod_Pal_Counter},
     * and only when nobody holds the palette, {@code move.l #-1,Rout_Pal_Ptr}
     * and {@code st Refresh_Pal+1}.
     */
    static void closePalette(GameState state) {
        if (state.routModPalCounter == 0) {
            state.routPalette = null;
            state.refreshPal = GameState.PAL_ASKED;
        }
    }

    /** A parameter word of the script, or a fallback when the call has fewer. */
    static int param(int[] params, int index, int fallback) {
        return params != null && params.length > index ? params[index] : fallback;
    }
}
