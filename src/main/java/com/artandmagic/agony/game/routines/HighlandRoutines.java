package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.GameState;

import java.util.HashMap;
import java.util.Map;

/**
 * The level routines of the highlands, level 5, {@code Game/Ag_Game_LPLATEAUX.s}.
 *
 * <p>Twelve of them are called by the script: the plant that spits, the flier
 * with its guided missile, the fast one that crosses straight over, the ground
 * creature that charges along the floor, the one that leaps at the sorcerer,
 * the flying kamikaze, the ghost with its vial, the monster that bursts into a
 * star of shots, the ground creature that throws three, the dragon with its
 * fiery tongue, the flier that speeds up when it is hit, and the final monster,
 * which sends copies of itself at the sorcerer through a thirteenth routine,
 * {@code R_Final_2}, that the script never names.
 *
 * <p>The addresses are those of the routines in this build, read off the
 * {@code Start_C} entries of the script, which match the source call for call
 * in trigger and parameters.  Most routines here are the level one routines
 * with the recolouring taken out: only the dragon and the accelerating flier
 * still claim the palette, but every one of them keeps the close of the
 * original, which drops the routine palette when nobody holds it.
 */
public final class HighlandRoutines implements RoutineLibrary {

    private final Map<Integer, Routine> byAddress = new HashMap<>();

    public HighlandRoutines(AgonyBinary bin) {
        byAddress.put(0x04f886, new HighlandGroundPlant(bin));
        byAddress.put(0x04f9b6, new HighlandMissileFlyer());
        byAddress.put(0x04fd50, new HighlandDarter(bin));
        byAddress.put(0x04fdfa, new HighlandGroundCharger(bin));
        byAddress.put(0x04feaa, new HighlandGroundJumper());
        byAddress.put(0x050024, new HighlandKamikaze());
        byAddress.put(0x05011e, new HighlandGhost(bin));
        byAddress.put(0x050316, new HighlandStarFire(bin));
        byAddress.put(0x050526, new HighlandGroundStar(bin));
        byAddress.put(0x0506ca, new HighlandDragon(bin));
        byAddress.put(0x05086a, new HighlandAccelerator(bin));
        byAddress.put(0x050942, new HighlandFinalMonster(bin));
    }

    @Override
    public Routine forAddress(int address) {
        return byAddress.get(address);
    }

    /**
     * The close every routine of this level shares: the bank is given back by
     * the routine loop, and the routine palette is dropped when no routine
     * holds it any more ({@code tst Rout_Mod_Pal_Counter}, then
     * {@code move.l #-1,Rout_Pal_Ptr} and {@code st Refresh_Pal+1}).
     */
    static void dropPalette(GameState state) {
        if (state.routModPalCounter == 0) {
            state.routPalette = null;
            state.refreshPal = GameState.PAL_ASKED;
        }
    }

    /** A parameter word, or a fallback when the script passed fewer. */
    static int param(int[] params, int index, int fallback) {
        return params != null && params.length > index ? params[index] : fallback;
    }
}
