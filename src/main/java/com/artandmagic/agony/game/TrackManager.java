package com.artandmagic.agony.game;

import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.Palette;
import com.artandmagic.agony.data.AttackWave;
import com.artandmagic.agony.data.LevelData;
import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.StartList;
import com.artandmagic.agony.data.TrackTable;

/**
 * The level script and the track follower, the first half of
 * {@code Ag_Playability_.s}.
 *
 * <p>{@code Level_X} advances by two on every game frame; whenever it reaches
 * the trigger of the next start list entry, the entry is fired: either an
 * attack wave, which grabs one of the sixteen track slots and one of the thirty
 * two alien banks, or a level routine, which grabs a routine slot and a bank of
 * its own.
 *
 * <p>The follower then advances every alien of every track by one step of its
 * movement table and copies the result into the banks the renderer walks.
 */
public final class TrackManager {

    public static final int TRACKS = 16;
    public static final int BANKS = 32;
    public static final int ROUTINES = 32;

    private final LevelData level;
    private final GameState state;

    public final AlienBank[] banks = new AlienBank[BANKS];
    public final Track[] tracks = new Track[TRACKS];

    /** Active level routines, {@code Rout_Struct}. */
    private final Routine[] routines = new Routine[ROUTINES];
    private final AlienBank[] routineBanks = new AlienBank[ROUTINES];
    /**
     * {@code Rout_Variables} is sixteen bytes, which a routine may split into
     * words or bytes as it likes; sixteen ints hold either shape.
     */
    private final int[][] routineVars = new int[ROUTINES][16];
    private final int[][] routineParams = new int[ROUTINES][];

    /** Working copy of the front palette, {@code Front_Pal_Buffer}. */
    private final int[] paletteBuffer = new int[Hw.BANDS * 7];

    public TrackManager(LevelData level, GameState state) {
        this.level = level;
        this.state = state;
        for (int i = 0; i < BANKS; i++) {
            banks[i] = new AlienBank();
        }
        for (int i = 0; i < TRACKS; i++) {
            tracks[i] = new Track(i);
        }
        state.slWaiting = level.startList.firstTrigger();
    }

    /** One 25 Hz frame of the level script and of the track follower. */
    public void update(FrontScroll front, Palette palette) {
        if (state.pause2) {
            return;
        }
        boolean paletteAllowed = state.curentSpell != 2 && state.beginToStart == 0;

        if (paletteAllowed) {
            if (!state.cleanUp) {
                state.levelX += 2;
                if (state.shortPhase) {
                    fireStartList();
                }
            }
        }

        // The two branch targets of Ag_Playability_.s are not the same door:
        // Clean_Up jumps over the start list only and the track follower still
        // runs, while spell two and Begin_To_Start jump past the follower and
        // the palette both, straight to the collision test.
        if (paletteAllowed) {
            followTracks();
        }

        if (paletteAllowed && state.shortPhase && state.refreshPal != 0) {
            updatePalette(front, palette);
        }
    }

    // ------------------------------------------------------------ start list

    private void fireStartList() {
        StartList list = level.startList;
        while (state.startListIndex < list.entries().size()) {
            StartList.Entry entry = list.entries().get(state.startListIndex);
            if (state.levelX < entry.trigger) {
                break;
            }
            state.startListIndex++;
            if (entry.routine != null) {
                startRoutine(entry.routine, entry.params);
            } else if (entry.wave >= 0 && entry.wave < level.waves.length) {
                startWave(level.waves[entry.wave]);
            }
            state.slWaiting = state.startListIndex < list.entries().size()
                    ? list.entries().get(state.startListIndex).trigger
                    : Integer.MAX_VALUE;
        }
    }

    /**
     * True while the script still holds a level routine it has not started.
     *
     * <p>On the fire level the scenery runs out a little before the script
     * reaches Mentor, so the screen is briefly empty with the end of level
     * routine still to come; the original simply waits for it.
     */
    public boolean routinePending() {
        var entries = level.startList.entries();
        for (int i = state.startListIndex; i < entries.size(); i++) {
            if (entries.get(i).routine != null) {
                return true;
            }
        }
        return false;
    }

    private void startWave(AttackWave wave) {
        startWave(wave, wave.startX, wave.startY);
    }

    private void startWave(AttackWave wave, int startX, int startY) {
        Track track = freeTrack();
        AlienBank bank = freeBank();
        if (track == null || bank == null) {
            return;
        }
        state.refreshPal = GameState.PAL_ASKED;
        bank.allocate();
        track.start(wave, bank, startX, startY);

        // Energy and fire rate of the sixteen aliens, as the original does when
        // the wave is installed.
        int counter = 0;
        for (int i = 0; i < AlienBank.SIZE; i++) {
            Alien a = bank.aliens[i];
            a.energy = wave.alienEnergy;
            a.status = 0;
            counter++;
            if (wave.alienFireRate != 0 && counter == wave.alienFireRate) {
                a.fireRateStat = wave.alienBadFireRate;
                counter = 0;
            } else {
                a.fireRateStat = 0xff;
            }
            a.fireRateDyn = 0;
        }
    }

    private void startRoutine(Routine routine, int[] params) {
        for (int i = 0; i < ROUTINES; i++) {
            if (routines[i] == null) {
                AlienBank bank = freeBank();
                if (bank == null) {
                    return;
                }
                bank.allocate();
                routines[i] = routine;
                routineBanks[i] = bank;
                routineParams[i] = params;
                java.util.Arrays.fill(routineVars[i], 0);
                return;
            }
        }
    }

    private Track freeTrack() {
        for (Track t : tracks) {
            if (t.free) {
                return t;
            }
        }
        return null;
    }

    private AlienBank freeBank() {
        for (AlienBank b : banks) {
            if (b.isFree()) {
                return b;
            }
        }
        return null;
    }

    // --------------------------------------------------------- track follower

    private void followTracks() {
        for (Track track : tracks) {
            if (track.free || track.wave == null) {
                continue;
            }
            follow(track);
        }
    }

    private void follow(Track track) {
        AttackWave wave = track.wave;
        TrackTable table = trackTable(wave);

        if (track.alienNum == 0) {
            spawn(track, wave);
        } else {
            int last = 0;
            for (int i = 0; i < track.alienNum; i++) {
                int slot = track.firstAlien + i;
                track.pos[slot]++;
                track.anim[slot]++;
                last = track.pos[slot];
            }
            if (last == wave.alienRate) {
                spawn(track, wave);
            }
        }

        AlienBank bank = track.bank;
        bank.count = track.alienNum;
        bank.first = track.firstAlien;

        int i = 0;
        while (i < track.alienNum) {
            int slot = track.firstAlien + i;
            Alien alien = bank.aliens[slot % AlienBank.SIZE];
            int p = track.pos[slot];

            if (p == 0) {
                if (wave.absolute) {
                    alien.x = track.startX;
                    alien.y = track.startY;
                } else if (table.worldAnchored) {
                    // The entry point belongs to the map, not to the screen.
                    alien.x = table.startX - state.levelX + 320 - 32;
                    alien.y = table.startY;
                } else {
                    alien.x = Hw.BIAS + table.startX;
                    alien.y = Hw.BIAS + table.startY;
                }
            } else {
                int dx = table.dx(p);
                int dy = table.dy(p);
                if (dx == TrackTable.END) {
                    // The leading alien reached the end of its path.
                    track.firstAlien++;
                    track.alienNum--;
                    bank.first = track.firstAlien;
                    bank.count = track.alienNum;
                    if (track.alienNum == 0) {
                        // Removing a track is the single place the original
                        // writes a one: the modulation of the wave that has just
                        // finished has to come back off the working buffer.
                        track.release();
                        state.refreshPal = GameState.PAL_FORCED;
                        return;
                    }
                    continue;
                }
                if (!wave.absolute) {
                    dx -= 2;
                }
                alien.x += dx;
                alien.y += dy;
            }

            alien.objOff = animationFrame(wave, track.anim[slot]);
            i++;
        }
    }

    private void spawn(Track track, AttackWave wave) {
        if (track.alienNum >= wave.alienNum || track.firstAlien != 0) {
            return;
        }
        int slot = track.alienNum;
        track.pos[slot] = 0;
        track.anim[slot] = 0;
        track.alienNum++;
    }

    private TrackTable trackTable(AttackWave wave) {
        if (level.tracks.length == 0) {
            return new TrackTable(320, 96, new byte[]{TrackTable.END}, new byte[]{0});
        }
        return level.tracks[Math.floorMod(wave.track, level.tracks.length)];
    }

    /**
     * Resolves the animation cursor to an object number; the original walks a
     * word list and restarts it when it finds a negative entry.
     */
    private int animationFrame(AttackWave wave, int cursor) {
        int[] anim = level.animations.get(wave.animation);
        if (anim == null || anim.length == 0) {
            return 0;
        }
        return anim[cursor % anim.length];
    }

    // ------------------------------------------------------------- palette

    /**
     * Rebuilds the front palette: the map colours, then the recolouring of every
     * active attack wave, then the override a routine may have installed.
     */
    private void updatePalette(FrontScroll front, Palette palette) {
        // PALETTE CTRL keeps the palette in a buffer of its own and only lays
        // the base colours back over it when it is told to: a forced refresh
        // always copies, an asked one gives way while two or more routines are
        // holding the palette, so the one underneath is not wiped out.
        if (state.refreshPal == GameState.PAL_FORCED || state.routModPalCounter < 2) {
            int[] map = front.currentPalette();
            System.arraycopy(map, 0, paletteBuffer, 0,
                    Math.min(map.length, paletteBuffer.length));
        }
        state.refreshPal = 0;

        for (Track track : tracks) {
            if (track.free || track.wave == null) {
                continue;
            }
            applyModulation(track.wave.palModLine, track.wave.palModCol);
        }
        if (state.routPalette != null) {
            applyRoutinePalette(state.routPalette);
        }

        for (int band = 0; band < Hw.BANDS; band++) {
            palette.setFrontBand(band, paletteBuffer, band * 7);
        }
    }

    private void applyModulation(boolean[] lines, int[] colors) {
        for (int band = 0; band < Hw.BANDS; band++) {
            if (!lines[band]) {
                continue;
            }
            for (int c = 0; c < 7; c++) {
                if (colors[c] >= 0) {
                    paletteBuffer[band * 7 + c] = colors[c];
                }
            }
        }
    }

    /**
     * A routine palette is a flag byte per band followed by seven colours,
     * exactly the layout {@code Rout_Pal_Ptr} points at.
     */
    private void applyRoutinePalette(int[] pal) {
        for (int band = 0; band < Hw.BANDS && band < pal.length; band++) {
            // As for a wave, a band is recoloured when its flag is not negative.
            if (pal[band] < 0) {
                continue;
            }
            for (int c = 0; c < 7; c++) {
                int index = Hw.BANDS + c;
                if (index < pal.length && pal[index] >= 0) {
                    paletteBuffer[band * 7 + c] = pal[index];
                }
            }
        }
    }

    // ------------------------------------------------------- routine manager

    /** Calls every active level routine once, {@code Routine_Loop}. */
    public void updateRoutines() {
        if (state.pause2 || state.curentSpell == 2) {
            return;
        }
        for (int i = 0; i < ROUTINES; i++) {
            if (routines[i] == null) {
                continue;
            }
            boolean alive = routines[i].update(state, routineBanks[i], routineVars[i], routineParams[i]);
            // A routine that started another took its slot while it ran, before
            // its own slot could be given back.
            for (GameState.PendingRoutine request : state.pendingRoutines) {
                startRoutine(request.routine(), request.params());
            }
            state.pendingRoutines.clear();
            if (!alive) {
                routineBanks[i].release();
                routines[i] = null;
                routineBanks[i] = null;
            }
        }

        for (int[] request : state.pendingWaves) {
            if (request[0] >= 0 && request[0] < level.waves.length) {
                startWave(level.waves[request[0]], request[1], request[2]);
            }
        }
        state.pendingWaves.clear();
    }

    /**
     * Marks every alien as exploding and drops every routine: the clean up the
     * main loop runs when the player dies or aborts.
     */
    public void cleanUp() {
        state.afOff = true;
        for (Track t : tracks) {
            t.release();
        }
        for (int i = 0; i < ROUTINES; i++) {
            routines[i] = null;
            if (routineBanks[i] != null) {
                routineBanks[i].release();
                routineBanks[i] = null;
            }
        }
        for (AlienBank bank : banks) {
            if (bank.isFree() || bank.count == 0) {
                continue;
            }
            for (int i = 0; i < bank.count; i++) {
                Alien a = bank.aliens[(bank.first + i) % AlienBank.SIZE];
                if (a.exploState() == 0) {
                    a.status = 1;
                }
            }
        }
    }
}
