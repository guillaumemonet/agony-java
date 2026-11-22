package com.artandmagic.agony.screens;

import com.artandmagic.agony.data.original.TitleData;
import com.artandmagic.agony.input.Joystick;

/**
 * The end of the game, the program the last level loads once Mentor is
 * beaten ({@code Agony.03}).
 *
 * <p>It is built like the title, picture and text pages, and its main loop
 * reads:
 *
 * <pre>
 *   fade the picture in over 17 frames, the bare picture 150
 *   loop: pages 0 to 9, each 250 frames (the fifth, "the end", 500)
 *         followed by the bare picture 100 (after the last one, 500)
 * </pre>
 *
 * <p>The first six pages tell the end of the story, the last four greet the
 * people who helped.  Fire leaves for the title program.
 */
public final class EndingScreen {

    private static final int FADE = 16;
    /** Pages the sequence shows. */
    private static final int PAGES = 10;

    private final TitleData data;
    private final PagePainter painter;
    private final int[] frame;

    private int wait = FADE + 1 + 150;
    private int fadeStep;
    /** 0..19: page n/2 when even, the bare picture after it when odd. */
    private int step;
    private boolean released;

    public EndingScreen(TitleData data) {
        this.data = data;
        this.painter = new PagePainter(data);
        this.frame = new int[data.picture.width * 2 * data.picture.height * 2];
    }

    /**
     * One video frame.
     *
     * @return false once the player has pressed fire
     */
    public boolean update(Joystick joystick) {
        if (fadeStep < FADE) {
            fadeStep++;
        }
        released |= !joystick.fire();
        if (released && joystick.fire()) {
            return false;
        }
        if (--wait <= 0) {
            int page = step / 2;
            if ((step & 1) == 0) {
                painter.page(page);
                wait = page == 5 ? 500 : 250;
            } else {
                painter.clear();
                wait = page == PAGES - 1 ? 500 : 100;
            }
            step = (step + 1) % (PAGES * 2);
        }
        data.picture.render(painter.work, frame, fadeStep);
        return true;
    }

    public int[] frame() {
        return frame;
    }

    public int width() {
        return data.picture.width * 2;
    }

    public int height() {
        return data.picture.height * 2;
    }
}
