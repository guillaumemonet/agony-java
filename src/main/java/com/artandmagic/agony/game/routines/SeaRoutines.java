package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.audio.SoundFx;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.GameState;

import java.util.HashMap;
import java.util.Map;

/**
 * The level routines of the first level, {@code Game/Ag_Game_LMER.s}.
 *
 * <p>Ten of them are called by the script, and the source names every one:
 * the ghost with its vial, the bomb dropper, the star shot, the transporter,
 * the ground plant that spits, the flying monster with its guided missile, the
 * flying monster that grows when it is hit, the one that bounces off the floor,
 * the spider on its thread, the fast one that darts left and right, and the
 * final monster.
 *
 * <p>The addresses below are where each of those routines sits in this build,
 * read off the {@code Start_C} entries of the script; the source and the binary
 * agree on the trigger and the parameters of every call, which is what ties a
 * name to an address.  The tables a routine works from — its palette, its shape
 * list, its curves — stay in the level data and are read from there, so only
 * the logic lives here.
 */
public final class SeaRoutines implements RoutineLibrary {

    private final Map<Integer, Routine> byAddress = new HashMap<>();
    private final Transporter transporter;
    private final FinalMonster boss;

    public SeaRoutines(AgonyBinary bin) {
        this(bin, null);
    }

    public SeaRoutines(AgonyBinary bin, SoundFx sound) {
        transporter = new Transporter(bin);
        boss = new FinalMonster(bin, sound);
        byAddress.put(0x04efc8, transporter);
        byAddress.put(0x04fa3a, boss);
        byAddress.put(0x04f19a, new GroundPlant(bin));
        byAddress.put(0x04f81c, new Spider(bin));
        byAddress.put(0x04f8ee, new Darter(bin));
        byAddress.put(0x04f696, new Swelling(bin));
        byAddress.put(0x04ed70, new StarFire(bin));
        byAddress.put(0x04ec00, new Bomber(bin));
        byAddress.put(0x04e9f0, new Ghost(bin));
        byAddress.put(0x04f2f2, new MissileFlyer(bin));
    }

    @Override
    public Routine forAddress(int address) {
        return byAddress.get(address);
    }

    @Override
    public int[] waveAddresses() {
        return new int[]{Transporter.WAVE_1, Transporter.WAVE_2,
            FinalMonster.WAVE_1, FinalMonster.WAVE_2, FinalMonster.WAVE_3};
    }

    @Override
    public void bindWaves(Map<Integer, Integer> indexByAddress) {
        transporter.bind(indexByAddress.getOrDefault(Transporter.WAVE_1, -1),
                indexByAddress.getOrDefault(Transporter.WAVE_2, -1));
        boss.bind(indexByAddress.getOrDefault(FinalMonster.WAVE_1, -1),
                indexByAddress.getOrDefault(FinalMonster.WAVE_2, -1),
                indexByAddress.getOrDefault(FinalMonster.WAVE_3, -1));
    }

    /** Releases the palette a routine installed, once the last user is gone. */
    static void releasePalette(GameState state) {
        state.routModPalCounter--;
        if (state.routModPalCounter == 0) {
            state.routPalette = null;
            state.refreshPal = GameState.PAL_ASKED;
        }
    }

    /** Installs the palette a routine recolours the bands with. */
    static void takePalette(GameState state, int[] palette) {
        state.routPalette = palette;
        state.refreshPal = GameState.PAL_ASKED;
        state.routModPalCounter++;
    }

    /** Reads a routine palette: six band flags then seven colours. */
    static int[] palette(AgonyBinary bin, int address) {
        int[] out = new int[13];
        for (int i = 0; i < 6; i++) {
            out[i] = bin.s8(address + i);
        }
        for (int i = 0; i < 7; i++) {
            out[6 + i] = bin.s16(address + 6 + i * 2);
        }
        return out;
    }

    /** Reads a list of words. */
    static int[] words(AgonyBinary bin, int address, int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = bin.u16(address + i * 2);
        }
        return out;
    }

    /** Reads a list of unsigned bytes. */
    static int[] bytes(AgonyBinary bin, int address, int count) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = bin.u8(address + i);
        }
        return out;
    }
}
