package com.artandmagic.agony.data;

/**
 * A movement table for one attack wave.
 *
 * <p>{@code Relative_Tracks} and {@code Absolute_Tracks} are streams of signed
 * four bit deltas, two per byte, read in step with the position of the alien
 * along the track.  A dx of -8 marks the end of the track and removes the
 * alien; on a relative track the engine also subtracts 2 from every dx, so a
 * delta of 2 means "hold the screen position" while 0 means "drift with the
 * scroll".
 *
 * <p>A relative track carries its start position in front of the delta stream
 * and the follower turns it into a screen position with
 * {@code startX - Level_X + 288}, which anchors the entry point to the scenery
 * rather than to the screen; {@link #worldAnchored} says which of the two rules
 * to apply.
 */
public final class TrackTable {

    /** Value of a dx that terminates a track. */
    public static final int END = -8;

    /** Where the first alien of the track appears, in screen coordinates. */
    public final int startX;
    public final int startY;
    /** Signed x deltas, indexed by the position of the alien along the track. */
    public final byte[] dx;
    /** Signed y deltas. */
    public final byte[] dy;

    /**
     * True when {@link #startX} is a position on the map, to be converted with
     * {@code startX - Level_X + 288} as the original does.
     */
    public final boolean worldAnchored;

    public TrackTable(int startX, int startY, byte[] dx, byte[] dy) {
        this(startX, startY, dx, dy, false);
    }

    public TrackTable(int startX, int startY, byte[] dx, byte[] dy, boolean worldAnchored) {
        this.startX = startX;
        this.startY = startY;
        this.dx = dx;
        this.dy = dy;
        this.worldAnchored = worldAnchored;
    }

    public int dx(int step) {
        return step >= 0 && step < dx.length ? dx[step] : END;
    }

    public int dy(int step) {
        return step >= 0 && step < dy.length ? dy[step] : 0;
    }

    public int length() {
        return dx.length;
    }
}
