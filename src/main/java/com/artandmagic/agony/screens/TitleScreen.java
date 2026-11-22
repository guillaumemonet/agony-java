package com.artandmagic.agony.screens;

import com.artandmagic.agony.data.HiScores;
import com.artandmagic.agony.data.original.TitleData;
import com.artandmagic.agony.input.Joystick;

import java.awt.event.KeyEvent;

/**
 * The title screen, the program every game starts from and every game over
 * comes back to.
 *
 * <p>Its main loop, read from the program, runs on the vertical blank counter:
 *
 * <pre>
 *   fade the picture in over 17 frames
 *   if the score just played gets in: ask for initials, show the table 500
 *   show the bare picture 150
 *   loop: pages 0 to 11, each 400 frames then the bare picture 200,
 *         the hiscore table 500, the bare picture 400
 * </pre>
 *
 * <p>Fire, tested in the vertical blank handler, leaves at any moment through
 * the end of the program: page 12, the one that tells how Alestes turned into
 * an owl, stays on the screen while the first level loads.
 */
public final class TitleScreen {

    private static final int FADE = 16;
    /** Page shown while the game loads. */
    private static final int STORY_PAGE = 12;
    /** Page asking for initials. */
    private static final int ENTRY_PAGE = 13;
    /** Character codes: digits start at 26, 36 is the dot. */
    private static final int DIGIT_0 = 26;
    private static final int DOT = 36;
    /** How long the story stays while the level loads, in frames. */
    private static final int LOADING = 175;

    private final TitleData data;
    private final HiScores scores;
    private final PagePainter painter;
    private final int[] frame;

    private enum Show { PICTURE, PAGE, TABLE, ENTRY, STORY }

    private Show show = Show.PICTURE;
    /** The fade in, then the bare picture for 150 frames. */
    private int wait = FADE + 1 + 150;
    private int fadeStep;
    /** Position in the looping part of the sequence. */
    private int cycle;
    /** True when the bare picture of the opening is still to come. */
    private boolean opening;
    private boolean released;
    private boolean done;

    private final byte[] initials = {DOT, DOT, DOT};
    private int cursor;
    private int playerScore;

    /**
     * @param score the score of the game just over, packed decimal, 0 for none
     */
    public TitleScreen(TitleData data, HiScores scores, int score, Joystick joystick) {
        this.data = data;
        this.scores = scores;
        this.painter = new PagePainter(data);
        this.frame = new int[data.picture.width * 2 * data.picture.height * 2];
        this.playerScore = score;
        joystick.clearKeys();
        if (scores.qualifies(score)) {
            show = Show.ENTRY;
            drawEntry();
        }
    }

    /**
     * One video frame.
     *
     * @return false once the player has pressed fire and the story has been
     *         shown for as long as the load takes
     */
    public boolean update(Joystick joystick) {
        if (fadeStep < FADE) {
            fadeStep++;
        }
        released |= !joystick.fire();

        switch (show) {
            case ENTRY -> entry(joystick);
            case STORY -> {
                if (--wait <= 0 || (released && joystick.fire() && wait < LOADING - 25)) {
                    done = true;
                }
            }
            default -> {
                if (released && joystick.fire()) {
                    show = Show.STORY;
                    painter.page(STORY_PAGE);
                    wait = LOADING;
                    released = false;
                } else if (--wait <= 0) {
                    next();
                }
            }
        }
        data.picture.render(painter.work, frame, fadeStep);
        return !done;
    }

    /** Moves the sequence on once a screen has stayed its time. */
    private void next() {
        if (opening) {
            opening = false;
            bare(150);
            return;
        }
        // 0..23: page n/2 then the bare picture; 24: table; 25: picture.
        int step = cycle;
        cycle = (cycle + 1) % 26;
        if (step < 24) {
            if ((step & 1) == 0) {
                painter.page(step / 2);
                show = Show.PAGE;
                wait = 400;
            } else {
                bare(200);
            }
        } else if (step == 24) {
            drawTable();
            show = Show.TABLE;
            wait = 500;
        } else {
            bare(400);
        }
    }

    private void bare(int frames) {
        painter.clear();
        show = Show.PICTURE;
        wait = frames;
    }

    // ------------------------------------------------------------- name entry

    private void entry(Joystick joystick) {
        for (int key = joystick.takeKey(); key >= 0; key = joystick.takeKey()) {
            if (key == KeyEvent.VK_BACK_SPACE) {
                // The original steps back only over the last position once it
                // has been filled, and blanks it with a dot.
                if (initials[2] == DOT && cursor > 0) {
                    cursor--;
                }
                initials[cursor] = DOT;
            } else if (key == KeyEvent.VK_ENTER) {
                scores.insert(initials.clone(), playerScore);
                playerScore = 0;
                drawTable();
                show = Show.TABLE;
                wait = 500;
                // After the table the opening carries on as without a score.
                opening = true;
                released = false;
                return;
            } else {
                int code = code(key);
                if (code < 0) {
                    continue;
                }
                initials[cursor] = (byte) code;
                if (cursor < 2) {
                    cursor++;
                }
            }
            drawEntry();
        }
    }

    /** Character code of a key, or -1. */
    private static int code(int key) {
        if (key >= KeyEvent.VK_A && key <= KeyEvent.VK_Z) {
            return key - KeyEvent.VK_A;
        }
        if (key >= KeyEvent.VK_0 && key <= KeyEvent.VK_9) {
            return DIGIT_0 + key - KeyEvent.VK_0;
        }
        if (key == KeyEvent.VK_SPACE) {
            return PagePainter.SPACE;
        }
        if (key == KeyEvent.VK_PERIOD) {
            return DOT;
        }
        return -1;
    }

    private void drawEntry() {
        painter.page(ENTRY_PAGE);
        // Centred on a 316 pixel line at y 210, as the program works it out.
        int x = (316 - painter.width(initials)) / 2;
        painter.text(x, 210, initials);
    }

    /** Six rows 40 lines apart from y 16: initials at x 30, score at x 150. */
    private void drawTable() {
        painter.clear();
        for (int row = 0; row < HiScores.ROWS; row++) {
            int y = 16 + row * 40;
            painter.text(30, y, scores.name(row));
            int score = scores.score(row);
            byte[] digits = new byte[6];
            for (int i = 0; i < 6; i++) {
                digits[i] = (byte) (DIGIT_0 + ((score >> (20 - i * 4)) & 15));
            }
            painter.text(150, y, digits);
        }
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
