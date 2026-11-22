package com.artandmagic.agony.data.original;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The music a level plays, found in its overlay.
 *
 * <p>The levels do not play ProTracker modules.  {@code Agony_Parent_.s}
 * includes one {@code Muzack/*.s} file per level, the audio driver Jeroen Tel
 * wrote for The Sonic Circle with that level's score and instruments: the sea
 * and the fire share one tune, the marshes and the highlands another, the
 * forest and the mountains have their own.  Only {@code Muzack/Forest.s} is in
 * the source release, which is how the format is known; the four tunes are in
 * the six level overlays on the disks, and the driver code is the same in all
 * of them, so it is ported once ({@code audio/TelPlayer}) and its data is read
 * from each overlay here.
 *
 * <p>The driver reaches its tables PC relative, so they are found by the
 * {@code lea table(pc),an} instructions of its code, starting from the note
 * period table ({@code FreqTab}), which is the same everywhere.  The tables
 * that follow it are {@code SequTab}, {@code StepTab}, {@code ArpTab} and
 * {@code SFX}, in that order.  {@code SequTab}, {@code StepTab} and
 * {@code ArpTab} hold absolute addresses; the first arpeggio sits right after
 * the 32 entries of {@code ArpTab}, which gives the address the overlay is
 * assembled at.  The instruments are IFF 8SVX samples from {@code SmpStrt},
 * the list ending with {@code "EXIT"}.
 */
public final class LevelMusic {

    /** One instrument: its sound, how much plays once, and the part that loops. */
    public record Sample(int start, int length, int repeatStart) {
    }

    /** The overlay the tune is read from; every offset below is into it. */
    public final byte[] data;
    /** {@code FreqTab}: 96 note periods, a word each. */
    public final int freqTab;
    /** {@code SequTab}: a sequence address and two speeds per entry, 8 bytes. */
    public final int sequTab;
    /** {@code StepTab}: the step addresses, a long each. */
    public final int stepTab;
    /** {@code ArpTab}: 32 arpeggio addresses. */
    public final int arpTab;
    /** {@code SFX}: 8 bytes per instrument setting. */
    public final int sfx;
    /** What to take from an absolute address to get an offset into the overlay. */
    public final int origin;
    /** The samples, in the order the instrument settings number them. */
    public final List<Sample> samples;

    private LevelMusic(byte[] data, int freqTab, int sequTab, int stepTab, int arpTab, int sfx,
                       int origin, List<Sample> samples) {
        this.data = data;
        this.freqTab = freqTab;
        this.sequTab = sequTab;
        this.stepTab = stepTab;
        this.arpTab = arpTab;
        this.sfx = sfx;
        this.origin = origin;
        this.samples = samples;
    }

    /** The first periods of {@code FreqTab}. */
    private static final int[] FREQ_START = {0x7df5, 0x76e7, 0x7036, 0x69ea};

    /** How far before {@code FreqTab} the driver code may start. */
    private static final int CODE_SPAN = 0x1000;

    /**
     * Reads the tune of a level overlay.
     *
     * @return the tune, or null when the overlay carries no such driver
     */
    public static LevelMusic find(byte[] p) {
        int freq = Code.find(p, 0, FREQ_START);
        if (freq < 0) {
            return null;
        }
        // The tables the driver code names PC relative, from FreqTab on.
        TreeSet<Integer> targets = new TreeSet<>();
        for (int i = Math.max(0, freq - CODE_SPAN) & ~1; i + 4 <= freq; i += 2) {
            if ((Code.u16(p, i) & 0xf1ff) == 0x41fa) {
                int target = i + 2 + (short) Code.u16(p, i + 2);
                if (target >= freq && target < freq + CODE_SPAN) {
                    targets.add(target);
                }
            }
        }
        if (targets.size() < 5 || targets.first() != freq) {
            return null;
        }
        Integer[] t = targets.toArray(new Integer[0]);
        int seq = t[1];
        int step = t[2];
        int arp = t[3];
        int sfx = t[4];
        if (seq - freq < 192 || seq - freq > 195 || (step - seq) % 8 != 0
                || (arp - step) % 4 != 0 || sfx < arp + 128) {
            return null;
        }
        int origin = Code.u32(p, arp) - (arp + 128);
        for (int a = seq; a < step; a += 8) {
            if (!inside(p, Code.u32(p, a) - origin)) {
                return null;
            }
        }
        for (int a = step; a < sfx && a < arp + 128; a += 4) {
            if (!inside(p, Code.u32(p, a) - origin)) {
                return null;
            }
        }
        List<Sample> samples = samples(p, sfx);
        if (samples.isEmpty()) {
            return null;
        }
        return new LevelMusic(p, freq, seq, step, arp, sfx, origin, samples);
    }

    private static boolean inside(byte[] p, int at) {
        return at >= 0 && at < p.length;
    }

    /**
     * Reads the samples as {@code MUSIC_INIT} does: for each {@code FORM} the
     * one shot and repeat lengths of its {@code VHDR}, then its {@code BODY}.  A
     * sample with no repeat part is given a two byte loop at its end, which on
     * the Amiga is silence.
     */
    private static List<Sample> samples(byte[] p, int from) {
        List<Sample> out = new ArrayList<>();
        int at = indexOf(p, from, "FORM");
        while (at >= 0 && at + 12 <= p.length && is(p, at, "FORM") && is(p, at + 8, "8SVX")) {
            int end = at + 8 + Code.u32(p, at + 4);
            int oneShot = -1;
            int repeat = 0;
            int body = -1;
            int bodyLength = 0;
            for (int c = at + 12; c + 8 <= Math.min(end, p.length); ) {
                int size = Code.u32(p, c + 4);
                if (is(p, c, "VHDR")) {
                    oneShot = Code.u32(p, c + 8);
                    repeat = Code.u32(p, c + 12);
                } else if (is(p, c, "BODY")) {
                    body = c + 8;
                    bodyLength = size;
                }
                c += 8 + size + (size & 1);
            }
            if (oneShot < 0 || body < 0 || body + bodyLength > p.length) {
                break;
            }
            if (repeat == 0) {
                oneShot -= 2;
                repeat = 2;
            }
            out.add(new Sample(body, oneShot + repeat, body + oneShot));
            at = end + ((end - at) & 1);
            if (at + 4 <= p.length && is(p, at, "EXIT")) {
                break;
            }
        }
        return out;
    }

    private static int indexOf(byte[] p, int from, String tag) {
        for (int i = Math.max(0, from); i + 4 <= p.length; i++) {
            if (is(p, i, tag)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean is(byte[] p, int at, String tag) {
        for (int k = 0; k < 4; k++) {
            if (p[at + k] != tag.charAt(k)) {
                return false;
            }
        }
        return true;
    }
}
