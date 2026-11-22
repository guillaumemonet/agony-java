package com.artandmagic.agony.game;

import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.SpriteEngine;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.ObjectDef;
import com.artandmagic.agony.data.SpriteFrame;

/**
 * The alien shots, {@code Ag_Alien_Fire.s}.
 *
 * <p>There are thirty two of them at most, all sharing sprite channel 5.  When
 * an alien fires, the engine works out how many horizontal steps the shot must
 * travel for one vertical step, {@code dx / (3 * dy)}, and stores that as the
 * slope; the shot then moves three pixels left per game frame and one pixel up
 * or down every time the slope counter runs out.  A shot is never fired at an
 * alien that is behind the sorcerer or closer than 64 pixels.
 */
public final class AlienFire {

    /** Number of shots the game can have in flight. */
    public static final int SLOTS = 32;

    /** One {@code AF_} record. */
    private static final class Shot {
        int x = -1;
        int y;
        /** Steps between two vertical moves, negative to move up. */
        int slope;
        /** Steps since the last vertical move. */
        int counter;
        /** Where the sprite is drawn, interpolated between game frames. */
        int spriteX;
        /** Size of the group of shots this one shares a sprite slot with. */
        int closerNum;
        /** Position inside that group. */
        int closerOffset;
        /** Whether the slot is showing this shot on the current frame. */
        boolean visible;

        boolean live() {
            return x >= 0;
        }

        void kill() {
            x = -1;
        }
    }

    private final Shot[] shots = new Shot[SLOTS];
    /**
     * The eight counters of {@code AF_LFO}; counter <i>i</i> wraps at
     * <i>i</i> + 3, which is what shares one sprite slot between the shots that
     * sit too close together for the hardware to show at once.
     */
    private final int[] lfo = new int[8];
    private final GameState state;
    private final GameAssets assets;
    private final TrackManager tracks;
    private final ObjectRenderer objects;

    public AlienFire(GameState state, GameAssets assets, TrackManager tracks, ObjectRenderer objects) {
        this.state = state;
        this.assets = assets;
        this.tracks = tracks;
        this.objects = objects;
        for (int i = 0; i < SLOTS; i++) {
            shots[i] = new Shot();
        }
    }

    /** One 25 Hz game frame. */
    public void update() {
        boolean frozen = state.curentSpell == 2 || state.curentSpell == 5
                || state.pause || state.pause2 || state.die || state.iconesMode;

        if (!frozen) {
            if (state.shortPhase) {
                launch();
            }
            move();
            multiplex();
            if (state.shortPhase) {
                testCollision();
            }
        }

        if (state.afOff) {
            state.afOff = false;
            for (Shot s : shots) {
                s.kill();
            }
        }
    }

    /** Lets every alien whose counter has run out fire one shot. */
    private void launch() {
        for (AlienBank bank : tracks.banks) {
            if (bank.isFree() || bank.count <= 0) {
                continue;
            }
            for (int i = 0; i < bank.count; i++) {
                Alien alien = bank.aliens[(bank.first + i) % AlienBank.SIZE];
                if (!alien.alive()) {
                    continue;
                }
                alien.fireRateDyn = (alien.fireRateDyn + 1) & 0xff;
                if (alien.fireRateDyn != alien.fireRateStat) {
                    continue;
                }
                alien.fireRateDyn = 0;
                fire(alien);
            }
        }
    }

    private void fire(Alien alien) {
        Shot shot = null;
        for (Shot s : shots) {
            if (!s.live()) {
                shot = s;
                break;
            }
        }
        if (shot == null) {
            return;
        }

        ObjectDef def = objects.object(alien.objOff);
        int x = alien.x + (def != null ? def.fireX : 0);
        int y = alien.y + (def != null ? def.fireY : 0);

        int dx = x - state.sorcererX;
        if (dx <= 64) {
            // Behind the sorcerer, or right on top of him: no shot.
            return;
        }

        int dy = y - state.sorcererY - 45;
        boolean up = dy > 0;
        dy = Math.abs(dy);

        int slope = 0;
        if (dy != 0) {
            int steps = dx / (3 * dy);
            if (steps == 0) {
                steps = 1;
            }
            if (steps <= 255) {
                slope = up ? -steps : steps;
            }
        }

        shot.x = x;
        shot.y = y;
        shot.spriteX = x;
        shot.slope = slope;
        shot.counter = 0;
    }

    private void move() {
        for (Shot s : shots) {
            if (!s.live()) {
                continue;
            }
            s.x -= 3;
            s.counter++;

            int steps = Math.abs(s.slope);
            if (steps != 0 && s.counter >= steps) {
                s.counter = 0;
                s.y += s.slope < 0 ? -1 : 1;
            }

            if (s.x <= 250 || s.x >= Hw.BIAS + 272
                    || s.y <= Hw.BIAS || s.y >= Hw.BIAS + 192) {
                s.kill();
            } else {
                s.spriteX = s.x;
            }
        }
    }

    /** A shot is eight by seven pixels and only ever tests the sorcerer. */
    private void testCollision() {
        if (!state.sorcererOn || state.die) {
            return;
        }
        int x1 = state.goodCol[0];
        if (x1 < 0) {
            return;
        }
        int y1 = state.goodCol[1];
        int x2 = state.goodCol[2];
        int y2 = state.goodCol[3];

        for (Shot s : shots) {
            if (!s.live()) {
                continue;
            }
            if (s.x > x2 || s.y > y2 || s.x + 8 < x1 || s.y + 7 < y1) {
                continue;
            }
            if (state.invincible) {
                return;
            }
            state.die = true;
            state.dieMode = 0;
            return;
        }
    }

    /**
     * Works out which shots the single sprite channel can show.
     *
     * <p>The shots are sorted by height and cut into groups: a group holds the
     * shots that sit within ten lines of its first one, which is closer than a
     * sprite channel can place two objects.  A group of one is always drawn; a
     * larger group shows one member at a time, chosen by the counter of
     * {@code AF_LFO} that matches its size, so the shots flicker in turn.
     */
    private void multiplex() {
        java.util.List<Shot> order = new java.util.ArrayList<>(SLOTS);
        for (Shot s : shots) {
            s.visible = false;
            s.closerNum = 0;
            s.closerOffset = 0;
            if (s.live()) {
                order.add(s);
            }
        }
        order.sort(java.util.Comparator.comparingInt(s -> s.y));

        int i = 0;
        while (i < order.size()) {
            Shot first = order.get(i);
            int base = first.y;
            int members = 1;
            int j = i + 1;
            while (j < order.size() && order.get(j).y - base <= 10) {
                order.get(j).closerOffset = members;
                members++;
                j++;
            }
            for (int k = i; k < j; k++) {
                order.get(k).closerNum = members;
            }
            i = j;
        }

        for (int k = 0; k < lfo.length; k++) {
            lfo[k]++;
            if (lfo[k] == k + 2) {
                lfo[k] = 0;
            }
        }

        for (Shot s : order) {
            if (s.closerNum == 1) {
                s.visible = true;
            } else {
                int counter = lfo[Math.min(lfo.length - 1, s.closerNum - 2)];
                s.visible = counter + s.closerOffset == s.closerNum - 1;
            }
        }
    }

    /**
     * One 50 Hz video frame: rebuilds sprite channel 5 and slides the shots one
     * more pixel, the ALIEN FIRE X DEC block of {@code Ag_Sprites.s}.
     */
    public void draw(SpriteEngine sprites) {
        boolean frozen = state.curentSpell == 2 || state.curentSpell == 5
                || state.pause || state.die || state.iconesMode;

        SpriteFrame frame = assets.alienFire;
        for (Shot s : shots) {
            if (!s.live()) {
                continue;
            }
            if (!frozen) {
                s.spriteX--;
            }
            if (frame != null && s.visible) {
                sprites.add(5, frame.toSprite(s.spriteX - Hw.BIAS, s.y - Hw.BIAS, 16 + 2 * 4, false));
            }
        }
    }
}
