package com.artandmagic.agony.audio;

import com.artandmagic.agony.data.original.LevelMusic;

/**
 * The level music driver, Jeroen Tel's Amiga audio driver of {@code
 * Muzack/Forest.s}, played on Paula channels 1 to 3; channel 0 stays with the
 * sound effects.
 *
 * <p>A voice reads a sequence of steps.  A sequence byte picks a step, a
 * transposition ({@code $80}-{@code $fe}), an instrument offset ({@code $7x}),
 * a repeat count ({@code $6x}), or jumps back ({@code $ff,xx}).  A step plays
 * notes ({@code $00}-{@code $5f}) with a length ({@code $81}-{@code $bf}), an
 * instrument ({@code $c0}-{@code $ef}), an arpeggio ({@code $6x}), a volume
 * ({@code $fc}), a glide ({@code $fd}), a tie to the last note ({@code $fb}) and
 * rests ({@code $f1}-{@code $fa}), up to its end ({@code $ff}) or the end of the
 * voice ({@code $fe}).  An instrument setting gives a sample, a volume, a
 * vibrato, an arpeggio switch, a detune and an attack or a release ramp.
 *
 * <p>This is the 68000 code transcribed routine by routine: {@code AudioIn},
 * {@code AudioPl}, {@code StepHnd}, {@code SequHnd}, {@code SfxInit}, {@code
 * EffHand} with its fade, glide, vibrato, arpeggio, attack and release, and
 * {@code SetFade}.  Byte and word arithmetic wrap as they do on the 68000.
 * {@link #play()} is {@code MUSIC_PLAY}, which the vertical blank of {@code
 * Agony_Parent_.s} calls fifty times a second.  The audio filter the
 * instrument settings switch is not reproduced.
 */
public final class TelPlayer {

    /** What {@code DataMem} keeps for one voice. */
    private static final class Voice {
        int sequInd;
        int stepInd;
        int arpIndx;
        /** The period in the high word, a fraction in the low one. */
        int frqWord;
        int fadeWrd;
        int fadeCnt;
        int realSmp;
        int sequNum;
        int stepNum;
        int realSnd;
        int spdCoun;
        int spdTest;
        int vibDlay;
        int gldDlay;
        int mainVol;
        int voicVol;
        int realVol;
        int rlDelay;
        int rlCount;
        int vibDep;
        int vibSpd;
        int vibDir;
        int gldSpd;
        int destNot;
        int arpNumb;
        int sfxByte;
        int repStep;
        int sfxTran;
        int realLen;
        int lenCopy;
        int realNot;
        int tranByt;
        int voicSts;
        int voicTst;
        int attTest;

        /** The {@code AUDxLC} and {@code AUDxLEN} last written, in bytes. */
        int lc;
        int len;
        /** Whether the channel's DMA is on. */
        boolean dma;
    }

    private final Paula paula;
    private final Voice[] voices = new Voice[4];
    private volatile LevelMusic tune;
    /** The step every voice starts on: one {@code $ff} byte, {@code StpExit}. */
    private static final int STP_EXIT = -1;

    public TelPlayer(Paula paula) {
        this.paula = paula;
        for (int v = 0; v < voices.length; v++) {
            voices[v] = new Voice();
        }
    }

    /** {@code MUSIC_INIT}: voices 1 to 3 start sequences 1 to 3 of a tune. */
    public synchronized void start(LevelMusic music) {
        tune = music;
        for (int v = 1; v <= 3; v++) {
            paula.channel(v).setVolume(0);
            paula.channel(v).stop();
            voices[v] = new Voice();
            audioIn(v, v);
        }
    }

    /** Silences the music and forgets the tune. */
    public synchronized void stop() {
        tune = null;
        for (int v = 1; v <= 3; v++) {
            paula.channel(v).setVolume(0);
            paula.channel(v).stop();
        }
    }

    /** True while a tune is loaded. */
    public boolean active() {
        return tune != null;
    }

    /**
     * {@code SetFade}: each effect frame takes {@code speed} from a count of
     * {@code $1000}, the volume following; the voice stops at zero.  The end of
     * a level fades every voice at 90.
     */
    public synchronized void fade(int speed) {
        for (int v = 1; v <= 3; v++) {
            voices[v].fadeWrd = speed & 0xffff;
        }
    }

    /** {@code MUSIC_PLAY}, once per vertical blank. */
    public synchronized void play() {
        if (tune == null) {
            return;
        }
        for (int v = 1; v <= 3; v++) {
            audioPl(v);
        }
    }

    // ------------------------------------------------------------------
    // Reading the tune

    private int u8(int at) {
        return at == STP_EXIT ? 0xff : tune.data[at] & 0xff;
    }

    private int s8(int at) {
        return (byte) u8(at);
    }

    private int u16(int at) {
        return ((tune.data[at] & 0xff) << 8) | (tune.data[at + 1] & 0xff);
    }

    private int pointer(int at) {
        int a = (u16(at) << 16 | u16(at + 2)) - tune.origin;
        return Math.max(0, Math.min(tune.data.length - 1, a));
    }

    /** {@code FreqTab} entry for a note, read where the driver would read it. */
    private int freq(int note) {
        return u16(tune.freqTab + 2 * note);
    }

    /** A byte of the instrument setting the voice plays. */
    private int sfx(Voice d, int k) {
        return u8(tune.sfx + d.realSnd + k);
    }

    // ------------------------------------------------------------------
    // The driver

    private void audioIn(int voice, int sequence) {
        Voice d = voices[voice];
        d.sequNum = sequence * 8;
        d.sequInd = pointer(tune.sequTab + d.sequNum);
        d.mainVol = 0x40;
        d.fadeWrd = 0;
        d.fadeCnt = 0x1000;
        d.stepNum = 0;
        d.stepInd = STP_EXIT;
        d.tranByt = 0;
        d.sfxTran = 0;
        d.repStep = 0;
        d.voicSts = 0;
        d.voicTst = 1;
        d.lenCopy = 1;
        d.spdTest = 0;
        d.spdCoun = 0;
    }

    private void audioPl(int voice) {
        Voice d = voices[voice];
        if (d.voicTst == 0) {
            return;
        }
        Paula.Channel ch = paula.channel(voice);
        int period = (((d.frqWord >>> 16) + (byte) -sfx(d, 4)) & 0xffff);
        ch.setPeriod(period);
        ch.setVolume((d.mainVol * d.voicVol) >> 6);

        if ((d.voicSts & 2) != 0) {
            d.voicSts--;
            if (!d.dma) {
                d.dma = true;
                ch.play(tune.data, d.lc, d.len);
                // Until the next frame rewrites them, the registers still
                // hold the whole sample, which is what a DMA restart replays.
                ch.loop(d.lc, d.len);
            }
        } else if ((d.voicSts & 1) != 0) {
            d.voicSts--;
            LevelMusic.Sample s = sample(d.realSmp);
            if (s != null) {
                d.lc = s.repeatStart();
                d.len = s.start() + s.length() - s.repeatStart();
                ch.loop(d.lc, d.len);
            }
        }

        d.spdCoun = (d.spdCoun - 1) & 0xffff;
        if (d.spdCoun == 0xffff) {
            d.spdTest = ~d.spdTest & 0xffff;
            int at = (d.spdTest & 2) + d.sequNum;
            d.spdCoun = u16(tune.sequTab + at + 4);
            d.lenCopy = (d.lenCopy - 1) & 0xff;
            if (d.lenCopy == 0) {
                stepHnd(voice, d);
                return;
            }
        }
        if ((byte) d.realNot >= 0) {
            effHand(d);
        }
    }

    private LevelMusic.Sample sample(int n) {
        return n >= 0 && n < tune.samples.size() ? tune.samples.get(n) : null;
    }

    private void dmaOff(int voice, Voice d) {
        d.dma = false;
        paula.channel(voice).stop();
    }

    private void stepHnd(int voice, Voice d) {
        d.gldDlay = -1;
        int a1 = d.stepInd;
        int d0 = u8(a1);
        a1 = next(a1);
        if (d0 != 0xfb) {
            dmaOff(voice, d);
        } else {
            d0 = u8(a1);
            a1 = next(a1);
        }
        if (d0 == 0xff) {
            sequHnd(d);
            a1 = d.stepInd;
            d0 = u8(a1);
            a1 = next(a1);
        }
        if (d0 == 0xfe) {
            d.voicTst = 0;
            return;
        }
        boolean glide = d0 == 0xfd;
        boolean noNewNote = false;
        if (!glide) {
            d.realVol = 0;
            if (d0 == 0xfc) {
                d.realVol = s8(a1);
                a1++;
                d0 = u8(a1++);
                if (d0 == 0xfd) {
                    glide = true;
                } else if (d0 >= 0xfb) {
                    noNewNote = true;
                }
            }
        }
        if (!glide && !noNewNote) {
            if (d0 >= 0xf0) {
                // A rest.
                d.realLen = d0 & 0x0f;
                d.realNot = 0xff;
                d.stepInd = a1;
                d.lenCopy = d.realLen;
                return;
            }
            if (d0 >= 0xc0) {
                d.realSnd = (byte) ((d0 & 0x3f) + d.sfxTran) * 8;
                d0 = u8(a1++);
                if (d0 == 0xfd) {
                    glide = true;
                } else if (d0 >= 0xc0) {
                    noNewNote = true;
                }
            }
            if (!glide && !noNewNote && d0 >= 0x80) {
                d.realLen = d0 & 0x3f;
                d0 = u8(a1++);
                if (d0 == 0xfd) {
                    glide = true;
                } else if (d0 >= 0x80) {
                    noNewNote = true;
                }
            }
            if (!glide && !noNewNote && d0 >= 0x60) {
                d.arpNumb = (d0 & 0x1f) << 2;
                d0 = u8(a1++);
                if (d0 >= 0x60) {
                    noNewNote = true;
                }
            }
            if (!glide && !noNewNote) {
                d.realNot = (d0 + d.tranByt) & 0xff;
            }
        }
        if (glide) {
            int x = u8(a1);
            d.gldSpd = ((x >> 4) + 1) & 0xff;
            d.gldDlay = (byte) (d.realLen - (x & 0x0f) + 1);
            a1++;
            d.realNot = (u8(a1++) + d.tranByt) & 0xff;
            d.destNot = (u8(a1++) + d.tranByt) & 0xff;
        }
        if (noNewNote) {
            a1--;
        }
        d.stepInd = a1;
        sfxInit(d);
        d.lenCopy = d.realLen;
    }

    private int next(int at) {
        // StpExit holds one byte; what follows it never gets read.
        return at == STP_EXIT ? STP_EXIT : at + 1;
    }

    private void sfxInit(Voice d) {
        d.realSmp = sfx(d, 7);
        LevelMusic.Sample s = sample(d.realSmp);
        if (s != null) {
            d.lc = s.start();
            d.len = s.length();
        }
        int vol = d.realVol;
        if (vol == 0) {
            vol = (byte) sfx(d, 0);
            d.realVol = vol;
        }
        d.voicVol = vol;

        d.frqWord = freq((byte) d.realNot) << 16;
        d.voicSts = 2;

        d.attTest = 0;
        d.rlDelay = -1;
        int r = sfx(d, 6);
        if ((r & 0x80) != 0) {
            d.attTest = r;
            d.voicVol = 0;
            d.rlCount = 0x200;
        } else if (r != 0) {
            d.rlDelay = r + 1;
            d.rlCount = 0x200;
        }
    }

    private void sequHnd(Voice d) {
        d.repStep = (d.repStep - 1) & 0xff;
        if (d.repStep != 0xff) {
            d.stepInd = pointer(tune.stepTab + d.stepNum);
            return;
        }
        int a1 = d.sequInd;
        int d0 = u8(a1++);
        if (d0 == 0xff) {
            a1 = pointer(tune.sequTab + d.sequNum) + u8(a1);
            d0 = u8(a1++);
        }
        if (d0 >= 0x80) {
            d.tranByt = d0 & 0x7f;
            d0 = u8(a1++);
        }
        if (d0 >= 0x70) {
            d.sfxTran = d0 & 0x0f;
            d0 = u8(a1++);
        }
        d.repStep = 0;
        if (d0 >= 0x60) {
            d.repStep = d0 & 0x0f;
            d0 = u8(a1++);
        }
        d.stepNum = (byte) d0 * 4;
        d.sequInd = a1;
        d.stepInd = pointer(tune.stepTab + d.stepNum);
    }

    private void effHand(Voice d) {
        if ((d.voicSts & 1) != 0) {
            int vib = sfx(d, 1);
            if (vib != 0) {
                d.vibDlay = (byte) (d.realLen - sfx(d, 2) + 1);
                d.vibDep = vib >> 4;
                d.vibSpd = halfUp(vib & 0x0f);
                d.vibDir = 0;
            } else {
                d.vibDlay = -1;
            }
            d.sfxByte = sfx(d, 3);
            if ((d.sfxByte & 0x40) != 0) {
                d.arpIndx = pointer(tune.arpTab + (byte) d.arpNumb);
            }
        }
        int d6 = d.lenCopy;

        // Fade.
        if (d.fadeWrd != 0) {
            d.fadeCnt -= d.fadeWrd;
            if (d.fadeCnt < 0) {
                d.fadeCnt &= 0xffff;
                d.voicTst = 0;
                d.mainVol = 0;
            } else {
                d.mainVol = (d.fadeCnt * 0x40) >> 12;
            }
        }

        boolean skipVibrato = false;
        if (d6 <= d.gldDlay) {
            glide(d);
            skipVibrato = true;
        }
        if (!skipVibrato && d6 < d.vibDlay) {
            vibrato(d);
        }
        arpeggio(d);

        // Attack, then release.
        if ((d.attTest & 0x80) != 0) {
            int ramp = ramp(d);
            if (ramp >= 0) {
                d.voicVol = d.realVol * (0x200 - ramp) / 512;
                return;
            }
            d.attTest = 0;
            d.voicVol = d.realVol;
        }
        if (d6 < d.rlDelay) {
            int ramp = ramp(d);
            if (ramp >= 0) {
                d.voicVol = d.realVol * ramp / 512;
            } else {
                d.rlDelay = -1;
                d.voicVol = 0;
            }
        }
    }

    /** {@code lsr #1} then {@code addx}: half, rounded up. */
    private static int halfUp(int x) {
        return (x >> 1) + (x & 1);
    }

    private void glide(Voice d) {
        int from = freq((byte) d.realNot);
        int dest = freq((byte) d.destNot);
        int diff = (short) ((from - dest) & 0xffff);
        long delta = (long) diff * (byte) d.gldSpd << 10;
        long frq = (d.frqWord & 0xffffffffL) - delta;
        d.frqWord = (int) frq;
        int period = (short) (d.frqWord >>> 16);
        boolean reached = delta >= 0 ? period <= (short) dest : period >= (short) dest;
        if (reached) {
            d.frqWord = dest << 16;
            d.realNot = d.destNot;
            d.gldDlay = -1;
            d.vibSpd = halfUp(d.vibDep & 0x0f);
            d.vibDir = 0;
        }
    }

    private void vibrato(Voice d) {
        d.vibSpd = (byte) (d.vibSpd - 1);
        if (d.vibSpd < 0) {
            d.vibDir = ~d.vibDir & 0xff;
            d.vibSpd = sfx(d, 1) & 0x0f;
        }
        int note = (byte) d.realNot;
        int step = ((freq(note) - freq(note + 1)) << 1) & 0xffff;
        int d3 = (step << 16) >> (d.vibDep & 63);
        if ((byte) d.vibDir < 0) {
            d3 = -d3;
        }
        d.frqWord += d3;
    }

    private void arpeggio(Voice d) {
        if ((d.sfxByte & 0x40) == 0) {
            return;
        }
        int a1 = d.arpIndx;
        int d0 = u8(a1++);
        if (d0 == 0xff) {
            a1 = pointer(tune.arpTab + (byte) d.arpNumb) + u8(a1);
            d0 = u8(a1++);
        }
        if (d0 == 0xfe) {
            d.sfxByte &= 0xbf;
            return;
        }
        d.frqWord = freq((byte) (d0 + d.realNot)) << 16;
        d.arpIndx = a1;
    }

    /**
     * {@code RampVol}: takes the instrument's ramp speed from the count; the
     * count left, or -1 once it has run out.
     */
    private int ramp(Voice d) {
        d.rlCount -= sfx(d, 5) + 1;
        if (d.rlCount < 0) {
            d.rlCount &= 0xffff;
            return -1;
        }
        return d.rlCount;
    }
}
