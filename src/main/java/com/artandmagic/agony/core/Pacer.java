package com.artandmagic.agony.core;

/**
 * Holds the main loop to the 50 Hz of a PAL vertical blank.
 *
 * <p>Sleeping the whole wait misses the mark by whole milliseconds on a coarse
 * system timer, which a 50 Hz scroll shows at once, so the last stretch is spun
 * out instead.
 */
public final class Pacer {

    /** Nanoseconds between two video frames, at 50 Hz. */
    public static final long FRAME_NS = 1_000_000_000L / 50;
    /** How much of the wait is spun out rather than slept away. */
    private static final long SPIN_NS = 2_000_000L;

    private long next = System.nanoTime();

    /** Starts counting again from now, after a pause in the loop. */
    public void reset() {
        next = System.nanoTime();
    }

    /**
     * Waits for the next frame.
     *
     * @return false when the thread was interrupted
     */
    public boolean waitFrame() {
        next += FRAME_NS;
        long wait = next - System.nanoTime();
        if (wait > 0) {
            try {
                long coarse = wait - SPIN_NS;
                if (coarse > 0) {
                    Thread.sleep(coarse / 1_000_000L, (int) (coarse % 1_000_000L));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            while (System.nanoTime() < next) {
                Thread.onSpinWait();
            }
        } else if (wait < -FRAME_NS * 5) {
            // Badly behind, for instance after the window was dragged.
            next = System.nanoTime();
        }
        return true;
    }
}
