package com.artandmagic.agony.data;

/**
 * An {@code ATTACK_WAVE_START} record ({@code AWS_} fields of
 * {@code Agony_Parent_.s}): everything the track follower needs to spawn and
 * drive one formation of up to sixteen aliens.
 */
public final class AttackWave {

    /** Index of the movement table the aliens follow. */
    public int track;
    /** Index of the animation sequence the aliens play. */
    public int animation;
    /** Distance, in track steps, between two consecutive aliens. */
    public int alienRate = 8;
    /** How many aliens the wave releases (1..16). */
    public int alienNum = 4;
    /** Hit points of each alien; 255 means indestructible. */
    public int alienEnergy = 1;
    /** Fire rate given to the aliens that are allowed to shoot. */
    public int alienBadFireRate = 40;
    /** One alien out of this many is allowed to shoot; 0 disables shooting. */
    public int alienFireRate = 2;
    /** True for an absolute track, which starts at {@link #startX}/{@link #startY}. */
    public boolean absolute;
    /** Start position of an absolute track, already biased by 256. */
    public int startX;
    public int startY;

    /** Which of the six colour bands the wave recolours. */
    public final boolean[] palModLine = new boolean[6];
    /** Replacement colours 1..7 for those bands; -1 keeps the map colour. */
    public final int[] palModCol = {-1, -1, -1, -1, -1, -1, -1};
}
