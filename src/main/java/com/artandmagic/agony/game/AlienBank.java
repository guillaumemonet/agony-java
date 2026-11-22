package com.artandmagic.agony.game;

/**
 * A bank of sixteen aliens, one entry of {@code AWO_Struct}.
 *
 * <p>The original reserves 32 of these.  A count of -1 marks a free bank, a
 * count of 0 a bank that is allocated but currently holds nothing.  Aliens live
 * in a queue: {@link #first} is the index of the oldest one still on the track,
 * which is how an attack wave drops its leading aliens as they reach the end of
 * their path.
 */
public final class AlienBank {

    public static final int SIZE = 16;
    /** Marks a free bank. */
    public static final int FREE = -1;

    public int count = FREE;
    public int first;
    public final Alien[] aliens = new Alien[SIZE];

    public AlienBank() {
        for (int i = 0; i < SIZE; i++) {
            aliens[i] = new Alien();
        }
    }

    public boolean isFree() {
        return count == FREE;
    }

    /** Allocates the bank and empties it. */
    public void allocate() {
        count = 0;
        first = 0;
        for (Alien a : aliens) {
            a.reset();
        }
    }

    public void release() {
        count = FREE;
        first = 0;
    }

    /** Returns the n-th live alien of the queue. */
    public Alien alien(int n) {
        return aliens[(first + n) % SIZE];
    }
}
