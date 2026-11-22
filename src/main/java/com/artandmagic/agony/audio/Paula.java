package com.artandmagic.agony.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * The four sound channels of Paula.
 *
 * <p>A channel plays signed eight bit samples at a rate given by its period:
 * the chip fetches one word every {@code period} colour clocks, so the output
 * frequency is {@code 3546895 / period} on a PAL machine.  Once the sample is
 * exhausted the channel restarts at the loop point the last DMA write gave it,
 * which is exactly how ProTracker sustains an instrument.
 *
 * <p>Agony gives channel 0 to the sound effects, which steal it from the music
 * whenever a higher priority effect is requested ({@code Sound_Start} and the
 * level 4 interrupt of {@code Agony_Parent_.s}).
 */
public final class Paula implements AutoCloseable {

    /** Colour clock of a PAL machine, in Hertz. */
    public static final double PAL_CLOCK = 3546895.0;

    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNELS = 4;
    private static final int BUFFER_FRAMES = 512;

    /** One hardware channel. */
    public static final class Channel {
        byte[] data = new byte[0];
        int start;
        int length;
        int loopStart;
        int loopLength;
        int period = 428;
        int volume;
        double position;
        boolean playing;

        /** Starts a sample, the equivalent of writing AUDxLC/AUDxLEN then DMACON. */
        public synchronized void play(byte[] data, int start, int length) {
            this.data = data != null ? data : new byte[0];
            this.start = start;
            this.length = Math.max(0, length);
            this.position = 0;
            this.playing = this.length > 0;
        }

        /** Sets the loop the channel falls back to, written while the sample plays. */
        public synchronized void loop(int loopStart, int loopLength) {
            this.loopStart = loopStart;
            this.loopLength = loopLength;
        }

        public synchronized void setPeriod(int period) {
            this.period = Math.max(50, period);
        }

        public synchronized void setVolume(int volume) {
            this.volume = Math.max(0, Math.min(64, volume));
        }

        public synchronized void stop() {
            playing = false;
        }

        public boolean playing() {
            return playing;
        }
    }

    private final Channel[] channels = new Channel[CHANNELS];
    private SourceDataLine line;
    /** Set once the vertical blank has thrown, so it is reported one time. */
    private boolean tickFailed;
    private Thread thread;
    private volatile boolean running;
    /** Called once per video frame, 50 times a second, by the mixer thread. */
    private volatile Runnable vblank;

    public Paula() {
        for (int i = 0; i < CHANNELS; i++) {
            channels[i] = new Channel();
        }
    }

    public Channel channel(int n) {
        return channels[n];
    }

    /** Registers the replayer tick, called at 50 Hz in step with the audio. */
    public void setVerticalBlank(Runnable vblank) {
        this.vblank = vblank;
    }

    /** Opens the audio line and starts mixing; failures leave the game silent. */
    public void start() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 2, true, false);
        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, BUFFER_FRAMES * 8);
            line.start();
            System.out.println("Audio: " + line.getLineInfo()
                    + " at " + SAMPLE_RATE + " Hz");
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.out.println("Audio: no output line (" + e.getMessage()
                    + "), the game runs silent");
            line = null;
            return;
        }
        running = true;
        thread = new Thread(this::mix, "paula");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() {
        running = false;
        if (thread != null) {
            try {
                thread.join(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (line != null) {
            line.stop();
            line.close();
        }
    }

    private void mix() {
        byte[] out = new byte[BUFFER_FRAMES * 4];
        int[] left = new int[BUFFER_FRAMES];
        int[] right = new int[BUFFER_FRAMES];
        double framesPerTick = SAMPLE_RATE / 50.0;
        double tickCounter = 0;

        while (running) {
            java.util.Arrays.fill(left, 0);
            java.util.Arrays.fill(right, 0);

            tickCounter += BUFFER_FRAMES;
            while (tickCounter >= framesPerTick) {
                tickCounter -= framesPerTick;
                Runnable tick = vblank;
                if (tick != null) {
                    try {
                        tick.run();
                    } catch (RuntimeException e) {
                        // A broken module must not take the mixer down, but a
                        // player that dies on the first tick must not do it in
                        // silence either.
                        if (!tickFailed) {
                            tickFailed = true;
                            System.out.println("Audio: the vertical blank threw "
                                    + e + ", the music stops here");
                            e.printStackTrace(System.out);
                        }
                    }
                }
            }

            for (int c = 0; c < CHANNELS; c++) {
                render(channels[c], (c == 0 || c == 3) ? left : right, BUFFER_FRAMES);
            }

            for (int i = 0; i < BUFFER_FRAMES; i++) {
                int l = clamp(left[i]);
                int r = clamp(right[i]);
                out[i * 4] = (byte) l;
                out[i * 4 + 1] = (byte) (l >> 8);
                out[i * 4 + 2] = (byte) r;
                out[i * 4 + 3] = (byte) (r >> 8);
            }
            line.write(out, 0, out.length);
        }
    }

    private void render(Channel ch, int[] target, int frames) {
        byte[] data;
        int start;
        int length;
        int loopStart;
        int loopLength;
        int volume;
        double step;
        double position;
        boolean playing;

        synchronized (ch) {
            if (!ch.playing || ch.volume == 0 || ch.length <= 0) {
                return;
            }
            data = ch.data;
            start = ch.start;
            length = ch.length;
            loopStart = ch.loopStart;
            loopLength = ch.loopLength;
            volume = ch.volume;
            position = ch.position;
            playing = true;
            step = (PAL_CLOCK / ch.period) / SAMPLE_RATE;
        }

        for (int i = 0; i < frames && playing; i++) {
            int index = start + (int) position;
            if (index >= start + length || index >= data.length) {
                if (loopLength > 2) {
                    position -= length;
                    start = loopStart;
                    length = loopLength;
                    if (position < 0) {
                        position = 0;
                    }
                    index = start + (int) position;
                    if (index >= data.length) {
                        playing = false;
                        break;
                    }
                } else {
                    playing = false;
                    break;
                }
            }
            target[i] += data[index] * volume * 4;
            position += step;
        }

        synchronized (ch) {
            ch.position = position;
            ch.start = start;
            ch.length = length;
            ch.playing = playing;
        }
    }

    private static int clamp(int v) {
        if (v > 32767) {
            return 32767;
        }
        if (v < -32768) {
            return -32768;
        }
        return v;
    }
}
