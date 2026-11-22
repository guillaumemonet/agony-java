package com.artandmagic.agony.screens;

import com.artandmagic.agony.audio.Paula;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.data.original.IntroData;
import com.artandmagic.agony.data.original.IntroData.Kind;
import com.artandmagic.agony.data.original.IntroData.Step;
import com.artandmagic.agony.input.Joystick;

/**
 * The presentation that opens the game, played from the script
 * {@link IntroData} reads out of its program.
 *
 * <p>The pictures are hires interlace, four planes, 80 bytes a line: each
 * interlaced line becomes one line of the doubled frame the port draws, and a
 * hires pixel one pixel of it, so they sit on the same grid as the overscan
 * loading screens.  A picture lands where its {@code DIWSTRT} puts it, the
 * frame starting at the {@code $2071} corner those screens use; everything
 * around it is colour 0, the border.
 *
 * <p>What the subroutines and the vertical blank handler of the program do is
 * reproduced here:
 *
 * <ul>
 *   <li>the fade in sets every colour to $666 and steps each component one
 *       nearer its target every five frames, seventeen times; the fade out
 *       steps towards $666 every three frames; the last fade walks the grey
 *       down to black by $111 every three frames;</li>
 *   <li>the sweep lowers every wait of its copper list by a line a frame and
 *       ends when the first one goes below zero; a line shows the colour of
 *       the last wait it is past, in colour 1;</li>
 *   <li>the handler raises the period of channels 0 and 1 every ten frames,
 *       opens their volume up to 15 a step every three frames, and runs the
 *       ramps the script asks for: channels 2 and 3 up a step every five
 *       frames to 63, or down a step every other frame while channels 0 and 1
 *       come up to 63, and channels 0 and 1 down a step a frame.</li>
 * </ul>
 */
public final class IntroScreen {

    /** The frame: the overscan area of the loading screens, doubled. */
    private static final int W = 352 * 2;
    private static final int H = 290 * 2;
    /** The corner of that area, as DIWSTRT holds it. */
    private static final int DIW_H = 0x71;
    private static final int DIW_V = 0x20;
    private static final int GREY = 0x666;

    private final IntroData data;
    private final Paula paula;
    private final int[] frame = new int[W * H];

    private final int[] colours = new int[16];
    private Step scene;
    private int step;
    /** Frames left in the current step, or the step's own counter. */
    private int timer;
    private int fadeCount;
    private boolean started;
    private boolean released;
    private boolean done;

    /** The sweep: the waits, their colours, and whether its list is shown. */
    private int[] sweepLines;
    private int[] sweepColours;
    private boolean sweepShown;
    private int blackLevel;

    // The vertical blank handler.
    private int period01 = -1;
    private int volume01;
    private int volume23;
    private int periodCounter;
    private int volumeCounter;
    private int rampCounter;
    /** 0 opening, 1 done, 2 fading out: channels 0 and 1. */
    private int mode01;
    /** 0 idle, 1 running, -1 over: the two ramps of channels 2 and 3. */
    private int rampUp;
    private int rampDown;

    public IntroScreen(IntroData data, Paula paula) {
        this.data = data;
        this.paula = paula;
    }

    /**
     * One video frame.
     *
     * @return false once the presentation is over, or fire was pressed
     */
    public boolean update(Joystick joystick) {
        released |= !joystick.fire();
        if (released && joystick.fire()) {
            finish();
        }
        if (done) {
            return false;
        }
        handler();
        run();
        draw();
        return !done;
    }

    /** Stops the sound when the presentation ends or is cut short. */
    public void finish() {
        for (int ch = 0; ch < 4; ch++) {
            paula.channel(ch).setVolume(0);
            paula.channel(ch).stop();
        }
        done = true;
    }

    // --------------------------------------------------------------- script

    /** Runs the script until a step has to wait for later frames. */
    private void run() {
        while (step < data.steps.size()) {
            Step s = data.steps.get(step);
            if (!started) {
                begin(s);
                started = true;
            }
            if (!tick(s)) {
                return;
            }
            step++;
            started = false;
        }
        finish();
    }

    private void begin(Step s) {
        switch (s.kind()) {
            case SHOW -> scene = s;
            case FADE_IN -> {
                java.util.Arrays.fill(colours, GREY);
                fadeCount = 17;
                timer = 5;
            }
            case FADE_OUT -> {
                fadeCount = 17;
                timer = 3;
            }
            case FADE_BLACK -> {
                blackLevel = GREY;
                timer = 3;
            }
            case SET_PALETTE -> {
                for (int i = 0; i < 16; i++) {
                    colours[i] = data.word(s.a() + i * 2) & 0x0fff;
                }
            }
            case WAIT -> timer = s.a();
            case PLAY -> play(s);
            case STOP -> paula.channel(s.a()).stop();
            case RAMP_UP -> rampUp = 1;
            case RAMP_DOWN -> rampDown = 1;
            case FADE_SOUND -> mode01 = 2;
            case SWEEP -> {
                sweepLines = new int[s.b()];
                for (int i = 0; i < sweepLines.length; i++) {
                    sweepLines[i] = (short) data.word(s.a() + i * 2);
                }
                sweepColours = s.colours();
                sweepShown = true;
            }
            default -> {
            }
        }
    }

    /** One frame of a step; true once it is over. */
    private boolean tick(Step s) {
        switch (s.kind()) {
            case FADE_IN -> {
                if (--timer > 0) {
                    return false;
                }
                timer = 5;
                stepColours(s.a());
                if (--fadeCount > 0) {
                    return false;
                }
                // The lists switch away from the sweep once the owl is lit.
                sweepShown = false;
                return true;
            }
            case FADE_OUT -> {
                if (--timer > 0) {
                    return false;
                }
                timer = 3;
                stepColours(-1);
                return --fadeCount <= 0;
            }
            case FADE_BLACK -> {
                if (--timer > 0) {
                    return false;
                }
                timer = 3;
                java.util.Arrays.fill(colours, blackLevel);
                blackLevel -= 0x111;
                return blackLevel < 0;
            }
            case WAIT -> {
                return --timer <= 0;
            }
            case WAIT_END -> {
                return !paula.channel(s.a()).playing();
            }
            case SWEEP -> {
                for (int i = 0; i < sweepLines.length; i++) {
                    if (--sweepLines[i] < 0) {
                        return true;
                    }
                }
                return false;
            }
            default -> {
                return true;
            }
        }
    }

    /** One step of a fade: towards a palette, or towards grey when -1. */
    private void stepColours(int palette) {
        for (int i = 0; i < 16; i++) {
            int target = palette < 0 ? GREY : data.word(palette + i * 2) & 0x0fff;
            int c = colours[i];
            int out = 0;
            for (int shift = 0; shift <= 8; shift += 4) {
                int v = (c >> shift) & 15;
                int t = (target >> shift) & 15;
                v += Integer.signum(t - v);
                out |= v << shift;
            }
            colours[i] = out;
        }
    }

    private void play(Step s) {
        Paula.Channel ch = paula.channel(s.a());
        int start = s.b() - data.org;
        ch.setPeriod(s.d());
        ch.setVolume(s.e());
        ch.play(data.program, start, s.c());
        if (s.f() != 0) {
            ch.loop(start, s.c());
        } else {
            ch.loop(0, 0);
        }
        if (s.a() < 2 && period01 < 0) {
            period01 = s.d();
            volume01 = s.e();
        }
        if (s.a() >= 2) {
            volume23 = s.e();
        }
    }

    // ------------------------------------------------------- the vbl handler

    private void handler() {
        if (period01 > 0 && ++periodCounter == 10) {
            periodCounter = 0;
            period01--;
            paula.channel(0).setPeriod(period01);
            paula.channel(1).setPeriod(period01);
        }
        if (mode01 != 1 && period01 > 0) {
            if (++volumeCounter == 3) {
                volumeCounter = 0;
                if (volume01 < 15) {
                    volume01++;
                    volume01();
                }
            }
            if (mode01 == 2) {
                if (volume01 == 0) {
                    mode01 = 1;
                } else {
                    volume01--;
                    volume01();
                }
            }
        }
        if (rampUp == 1 && ++rampCounter == 5) {
            rampCounter = 0;
            if (volume23 == 63) {
                rampUp = -1;
            } else {
                volume23++;
                volume23();
            }
        }
        if (rampDown == 1 && ++rampCounter == 2) {
            rampCounter = 0;
            if (volume23 == 0) {
                rampDown = -1;
                paula.channel(2).stop();
                paula.channel(3).stop();
            } else {
                volume23--;
                volume23();
                if (volume01 != 63) {
                    volume01++;
                    volume01();
                }
            }
        }
    }

    private void volume01() {
        paula.channel(0).setVolume(volume01);
        paula.channel(1).setVolume(volume01);
    }

    private void volume23() {
        paula.channel(2).setVolume(volume23);
        paula.channel(3).setVolume(volume23);
    }

    // ---------------------------------------------------------------- drawing

    private void draw() {
        int border = Hw.rgb12(colours[0]);
        java.util.Arrays.fill(frame, border);
        if (scene == null) {
            return;
        }
        int[] rgb = new int[16];
        for (int i = 0; i < 16; i++) {
            rgb[i] = Hw.rgb12(colours[i]);
        }
        int planes = scene.a() - data.org;
        int stride = scene.b();
        int vStart = scene.c() >> 8;
        int vStop = (scene.d() >> 8) | (((scene.d() & 0x8000) == 0) ? 0x100 : 0);
        int hStart = scene.c() & 0xff;
        int rows = Math.min((vStop - vStart) * 2, stride / 80);
        int x0 = (hStart - DIW_H) * 2;
        int y0 = (vStart - DIW_V) * 2;
        byte[] p = data.program;

        for (int row = 0; row < rows; row++) {
            int y = y0 + row;
            if (y < 0 || y >= H) {
                continue;
            }
            int colour1 = rgb[1];
            if (sweepShown) {
                colour1 = Hw.rgb12(sweepColour(vStart + row / 2));
            }
            int line = planes + row * 80;
            for (int x = 0; x < 640; x++) {
                int px = x0 + x;
                if (px < 0 || px >= W) {
                    continue;
                }
                int at = line + (x >> 3);
                int bit = 7 - (x & 7);
                int v = ((p[at] >> bit) & 1)
                        | (((p[at + stride] >> bit) & 1) << 1)
                        | (((p[at + 2 * stride] >> bit) & 1) << 2)
                        | (((p[at + 3 * stride] >> bit) & 1) << 3);
                frame[y * W + px] = v == 1 ? colour1 : rgb[v];
            }
        }
    }

    /** Colour 1 on a raster line while the sweep's list is shown. */
    private int sweepColour(int line) {
        int c = GREY;
        for (int i = 0; i < sweepLines.length && i < sweepColours.length; i++) {
            int v = Math.max(0, Math.min(255, sweepLines[i]));
            if (v > line) {
                break;
            }
            c = sweepColours[i];
        }
        return c;
    }

    public int[] frame() {
        return frame;
    }

    public int width() {
        return W;
    }

    public int height() {
        return H;
    }
}
