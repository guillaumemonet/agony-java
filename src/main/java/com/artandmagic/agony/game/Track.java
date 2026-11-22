package com.artandmagic.agony.game;

import com.artandmagic.agony.data.AttackWave;

/**
 * One of the sixteen {@code TRACK STRUCT} slots of {@code Track_Table}.
 *
 * <p>A track ties an attack wave to the bank of aliens it drives.  Aliens are
 * released one after the other, every {@code alienRate} steps, and leave the
 * track when their movement table returns the terminator; when the last one has
 * left, the slot is released for the next wave of the level script.
 */
public final class Track {

    /** Maximum number of aliens a track can queue over its life. */
    public static final int QUEUE = 32;

    /** Slot number, {@code TS_Num}. */
    public final int num;
    /** True while the slot can be handed to a new attack wave. */
    public boolean free = true;

    /** The bank holding this wave, {@code TS_Awo_Ptr}. */
    public AlienBank bank;
    /** The wave being played, {@code TS_Aws_Ptr}. */
    public AttackWave wave;

    /** How many aliens are currently on the track, {@code TS_Alien_Num}. */
    public int alienNum;
    /** Queue index of the oldest alien, {@code TS_First_Alien}. */
    public int firstAlien;
    /** Start position of an absolute track. */
    public int startX;
    public int startY;

    /** Position of each queued alien along the track. */
    public final int[] pos = new int[QUEUE];
    /** Animation cursor of each queued alien. */
    public final int[] anim = new int[QUEUE];

    public Track(int num) {
        this.num = num;
    }

    /** Hands the slot to a new attack wave. */
    public void start(AttackWave wave, AlienBank bank, int startX, int startY) {
        this.wave = wave;
        this.bank = bank;
        this.startX = startX;
        this.startY = startY;
        this.alienNum = 0;
        this.firstAlien = 0;
        this.free = false;
        java.util.Arrays.fill(pos, 0);
        java.util.Arrays.fill(anim, 0);
    }

    /**
     * Releases the slot once the wave is over, and with it the bank.
     *
     * <p>{@code Ag_Playability_.s} writes a long of $ffff0000 over the header
     * of the bank when it drops the last alien: a count of -1, which is what
     * marks the bank free again, and a first alien of zero.  Leaving a count of
     * zero there instead hands the level thirty two waves and no more.
     */
    public void release() {
        free = true;
        wave = null;
        if (bank != null) {
            bank.release();
            bank = null;
        }
    }
}
