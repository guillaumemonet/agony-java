package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.GameState;

import java.util.HashMap;
import java.util.Map;

/**
 * The level routines of the fire, level 6, {@code Game/Ag_Game_LFEUX.s}.
 *
 * <p>Ten of them are called by the script: the flier with its guided missile,
 * the fast one that darts across, the ground jumper that leaps at the
 * sorcerer, the kamikaze, the star shot, the ground star shot, the dragon with
 * its tongue of fire, the flier that speeds up when it is hit, the column of
 * flames, and Mentor, the final monster.  An eleventh, {@code R_Final_2}, is
 * never named by the script: Mentor starts it himself, once for every burst of
 * fire balls he throws.
 *
 * <p>The addresses below are where each routine sits in this build, read off
 * the {@code Start_C} entries of the script; the source and the binary agree on
 * the trigger and the parameters of every call, and the routines follow each
 * other in the overlay in the order of the source.  The tables a routine works
 * from — its shape lists, the boss palette, the column heights — stay in the
 * level data and are read from there; the object offsets the code loads as
 * immediates are written here, each named after the {@code Obj_} label it
 * stands for.
 *
 * <p>None of these routines takes a share of the palette: no
 * {@code Rout_Mod_Pal_Counter} is ever counted up in this level.  Every one of
 * them still runs the common close, which puts the map palette back when
 * nobody holds a share, and Mentor installs his own without counting it.
 */
public final class FireRoutines implements RoutineLibrary {

    private final Map<Integer, Routine> byAddress = new HashMap<>();

    public FireRoutines(AgonyBinary bin) {
        FireFinalBall ball = new FireFinalBall(bin);
        byAddress.put(0x05069e, new FireMissileFlyer());
        byAddress.put(0x050a38, new FireDarter(bin));
        byAddress.put(0x050ad6, new FireLeaper());
        byAddress.put(0x050c50, new FireKamikaze());
        byAddress.put(0x050d52, new FireStarFire(bin));
        byAddress.put(0x050f62, new FireGroundStar(bin));
        byAddress.put(0x0510f2, new FireDragon(bin));
        byAddress.put(0x05126a, new FireAccelerator(bin));
        byAddress.put(0x051342, new FireColumn(bin));
        byAddress.put(0x051458, new FireFinalMonster(bin, ball));
        byAddress.put(FireFinalBall.ADDRESS, ball);
    }

    @Override
    public Routine forAddress(int address) {
        return byAddress.get(address);
    }

    /**
     * The tail every routine of the level closes with: the bank is given back
     * (the routine loop does that when the routine returns false), and the
     * routine palette is dropped, but only when no routine holds a share of
     * it — {@code tst Rout_Mod_Pal_Counter}, {@code move.l #-1,Rout_Pal_Ptr},
     * {@code st Refresh_Pal+1}.
     */
    static boolean close(GameState state) {
        if (state.routModPalCounter == 0) {
            state.routPalette = null;
            state.refreshPal = GameState.PAL_ASKED;
        }
        return false;
    }
}
