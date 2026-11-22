package com.artandmagic.agony.game;

import com.artandmagic.agony.audio.SoundFx;
import com.artandmagic.agony.data.ObjectDef;

/**
 * The alien against player collision test, {@code AWO COLISION TEST} of
 * {@code Ag_Playability_.s}.
 *
 * <p>Every living alien is described by a handful of rectangles; each of them is
 * tested against the eight rectangles the player owns ({@code Good_Col_List}).
 * Slot 0 is the sorcerer, so a hit there kills him; slot 1 is the forward shot,
 * which is consumed on impact; slots 2 and 3 are the axes and 4 to 7 belong to
 * the spell in progress.
 */
public final class Collisions {

    /** Slot of the sorcerer in the player rectangle list. */
    public static final int SLOT_SORCERER = 0;
    /** Slot of the forward shot. */
    public static final int SLOT_FORWARD_FIRE = 1;

    private final GameState state;
    private final TrackManager tracks;
    private final ObjectRenderer objects;
    private final SoundFx sound;

    public Collisions(GameState state, TrackManager tracks, ObjectRenderer objects, SoundFx sound) {
        this.state = state;
        this.tracks = tracks;
        this.objects = objects;
        this.sound = sound;
    }

    /** One 25 Hz frame. */
    public void update() {
        if (state.pause2 || state.pause) {
            return;
        }

        for (AlienBank bank : tracks.banks) {
            if (bank.isFree() || bank.count <= 0) {
                continue;
            }
            for (int i = 0; i < bank.count; i++) {
                test(bank.aliens[(bank.first + i) % AlienBank.SIZE]);
            }
        }
    }

    private void test(Alien alien) {
        if (!alien.alive()) {
            return;
        }
        ObjectDef def = objects.object(alien.objOff);
        if (def == null) {
            return;
        }

        for (ObjectDef.Rect rect : def.rects) {
            int x1 = alien.x + rect.x1();
            int y1 = alien.y + rect.y1();
            int x2 = alien.x + rect.x2();
            int y2 = alien.y + rect.y2();

            for (int slot = 0; slot < 8; slot++) {
                int b = slot * 4;
                int gx1 = state.goodCol[b];
                if (gx1 < 0) {
                    continue;
                }
                if (x2 < gx1 || y2 < state.goodCol[b + 1]
                        || state.goodCol[b + 2] < x1 || state.goodCol[b + 3] < y1) {
                    continue;
                }
                hit(alien, slot);
                return;
            }
        }
    }

    private void hit(Alien alien, int slot) {
        state.point = 0x13;
        sound.request(3, 63);
        // The original writes the whole status byte rather than setting the
        // bit, so a hit restarts the flash count instead of adding to it.
        alien.status = 0x10;

        // An alien set up with 255 points of energy is meant to survive; the
        // original still counts down, it just takes 255 hits to get there.
        alien.energy--;
        if (alien.energy == 0) {
            sound.request(1, 63);
            alien.status = 0x01;
            state.point = 0x116;
        }

        if (slot == SLOT_SORCERER && state.sorcererOn && !state.die) {
            if (state.invincible) {
                return;
            }
            state.die = true;
            state.dieMode = 0;
            return;
        }
        if (slot == SLOT_FORWARD_FIRE) {
            state.fwFireStep = GameState.FW_F_OFF;
            state.clearCollisions(SLOT_FORWARD_FIRE, 1);
        }
    }
}
