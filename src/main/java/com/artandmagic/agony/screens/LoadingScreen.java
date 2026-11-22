package com.artandmagic.agony.screens;

import com.artandmagic.agony.data.Picture;
import com.artandmagic.agony.input.Joystick;

/**
 * The picture shown while the next level loads.
 *
 * <p>The loading programs fade the picture in over seventeen vertical blanks,
 * the colour registers going from step 0 to step 16, load the level while the
 * tune plays, and fade back out the same way before jumping into it.  The disk
 * access is what sets how long the picture stays; with nothing to load the port
 * holds it for a few seconds, and fire cuts that short once the fade in is
 * over.
 */
public final class LoadingScreen {

    /** Steps of the fade, 0 black and 16 the full picture. */
    private static final int STEPS = 16;
    /** How long the picture stays once faded in, in video frames. */
    private static final int HOLD = 200;

    private final Picture picture;
    private final int[] frame;
    private int tick;
    private int fadeOutStart = -1;
    private boolean released;

    public LoadingScreen(Picture picture) {
        this.picture = picture;
        this.frame = new int[picture.width * 2 * picture.height * 2];
    }

    /**
     * One video frame.
     *
     * @return false once the screen is over
     */
    public boolean update(Joystick joystick) {
        int step;
        if (tick <= STEPS) {
            step = tick;
            // A fire press held over from the level must be let go first.
            released |= !joystick.fire();
        } else if (fadeOutStart < 0) {
            step = STEPS;
            released |= !joystick.fire();
            if (tick >= STEPS + HOLD || (released && joystick.fire())) {
                fadeOutStart = tick;
            }
        } else {
            step = STEPS - (tick - fadeOutStart);
            if (step < 0) {
                return false;
            }
        }
        picture.render(frame, step);
        tick++;
        return true;
    }

    public int[] frame() {
        return frame;
    }

    public int width() {
        return picture.width * 2;
    }

    public int height() {
        return picture.height * 2;
    }
}
