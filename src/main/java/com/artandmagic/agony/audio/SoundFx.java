package com.artandmagic.agony.audio;

/**
 * The sound effect channel, {@code Sound_Start} and {@code Ag_Sounds.s}.
 *
 * <p>Agony plays its effects on a single channel, stolen from the music.  A new
 * effect only interrupts the one playing if its priority, read from
 * {@code Pri_Table}, is at least as high; the level 4 interrupt clears the
 * priority when the sample has been played once, which frees the channel again.
 *
 * <p>Effect numbers follow the original: 0 bonus taken, 1 alien destroyed,
 * 2 the sorcerer fires, 3 an alien is hit.
 */
public final class SoundFx {

    private final Paula paula;
    private final int[] priority;

    /** Sample data of the four effects, or null when the asset is missing. */
    private final byte[][] samples = new byte[8][];
    /** Replay period of each effect. */
    private final int[] periods = new int[8];

    /** Effect requested this frame, -1 when none. */
    private int request = -1;
    private int requestVolume;
    /** Priority of the effect currently playing. */
    private int currentPriority = -1;

    public SoundFx(Paula paula, int[] priority) {
        this.paula = paula;
        this.priority = priority;
        java.util.Arrays.fill(periods, 277);
    }

    /** Installs the sample of one effect. */
    public void setSample(int number, byte[] data, int period) {
        if (number >= 0 && number < samples.length) {
            samples[number] = data;
            periods[number] = period;
        }
    }

    /**
     * Asks for an effect to be played, the {@code Sound_Start} subroutine.
     * The request is only kept if it outranks whatever is playing.
     */
    public void request(int number, int volume) {
        if (number < 0 || number >= samples.length) {
            return;
        }
        int pri = number < priority.length ? priority[number] : 0;
        if (pri < currentPriority) {
            return;
        }
        currentPriority = pri;
        request = number;
        requestVolume = volume;
    }

    /** One 25 Hz frame: starts the requested effect, as {@code Ag_Sounds.s} does. */
    public void update() {
        if (request < 0) {
            return;
        }
        byte[] data = samples[request];
        int period = periods[request];
        int volume = requestVolume;
        request = -1;

        Paula.Channel ch = paula.channel(0);
        if (data == null) {
            currentPriority = -1;
            ch.stop();
            return;
        }
        ch.setPeriod(period);
        ch.setVolume(volume);
        ch.loop(0, 0);
        ch.play(data, 0, data.length);
    }

    /**
     * Releases the channel when the effect has finished; the original does this
     * from the audio interrupt after one pass through the sample.
     */
    public void poll() {
        if (currentPriority >= 0 && !paula.channel(0).playing()) {
            currentPriority = -1;
        }
    }
}
