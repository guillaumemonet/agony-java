package com.artandmagic.agony.game;

/**
 * One {@code ATTACK_WAVE_OBJ} record, twelve bytes in the original
 * ({@code AWO_} fields of {@code Agony_Parent_.s}).
 */
public final class Alien {

    /** Position, biased by 256 like every game coordinate. */
    public int x;
    public int y;
    /** Index of the {@link com.artandmagic.agony.data.ObjectDef} to draw. */
    public int objOff;
    /** Remaining hit points; 255 marks an alien that cannot be shot. */
    public int energy;
    /**
     * Bits 0..3 hold the explosion state (0 alive, 1..14 exploding, 15 gone),
     * bits 4..7 the white flash played when the alien is hit.
     */
    public int status;
    /** Number of frames between two shots, 255 for an alien that barely fires. */
    public int fireRateStat = 0xff;
    /** Frames counted since the last shot. */
    public int fireRateDyn;

    public void reset() {
        x = 0;
        y = 0;
        objOff = 0;
        energy = 0;
        status = 0;
        fireRateStat = 0xff;
        fireRateDyn = 0;
    }

    /** Explosion state, the low nibble of {@code AWO_Alien_Status}. */
    public int exploState() {
        return status & 0xf;
    }

    public boolean alive() {
        return (status & 0xf) == 0;
    }

    /** True once the explosion has run its course and the alien is off. */
    public boolean gone() {
        return (status & 0xf) == 0xf;
    }
}
