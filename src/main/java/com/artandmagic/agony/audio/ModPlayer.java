package com.artandmagic.agony.audio;

/**
 * The ProTracker replayer, a port of {@code Ag_Pt_Player.s}.
 *
 * <p>That file is the well known Protracker V1.1B VBlank replayer by Lars Hamre,
 * which Agony calls once per vertical blank through {@code MUSIC_PLAY} after a
 * single {@code MUSIC_INIT}.  The one addition Art &amp; Magic made is the
 * master volume used to fade the song in and out, kept here as
 * {@link #setMasterVolume}.
 *
 * <p>The module itself lives in the level executable ({@code Muzack/Sea&amp;Fire.s}
 * and friends); this class reads a standard 31 instrument module, which is the
 * same data in its usual container.
 */
public final class ModPlayer {

    private static final int CHANNELS = 4;

    /** Base periods of the three octaves, finetune 0. */
    private static final int[] PERIODS = {
        856, 808, 762, 720, 678, 640, 604, 570, 538, 508, 480, 453,
        428, 404, 381, 360, 339, 320, 302, 285, 269, 254, 240, 226,
        214, 202, 190, 180, 170, 160, 151, 143, 135, 127, 120, 113
    };

    private static final int[] SINE = {
        0, 24, 49, 74, 97, 120, 141, 161, 180, 197, 212, 224,
        235, 244, 250, 253, 255, 253, 250, 244, 235, 224, 212, 197,
        180, 161, 141, 120, 97, 74, 49, 24
    };

    private static final class Sample {
        byte[] data = new byte[0];
        int length;
        int finetune;
        int volume;
        int loopStart;
        int loopLength;
    }

    private static final class Voice {
        int sampleNumber;
        int period;
        int wantedPeriod;
        int finetune;
        int volume;
        int command;
        int commandParam;
        int portaSpeed;
        int vibratoPos;
        int vibratoCommand;
        int tremoloPos;
        int tremoloCommand;
        int patternLoopRow;
        int patternLoopCount;
        int funkOffset;
    }

    private final Paula paula;
    private final Sample[] samples = new Sample[32];
    private final Voice[] voices = new Voice[CHANNELS];

    private byte[][] patterns = new byte[0][];
    private int[] order = new int[0];
    private int songLength;

    private int speed = 6;
    private int counter;
    private int songPosition;
    private int patternRow;
    private int patternBreakRow = -1;
    private int positionJump = -1;

    private boolean playing;
    private int masterVolume = 64;

    public ModPlayer(Paula paula) {
        this.paula = paula;
        for (int i = 0; i < samples.length; i++) {
            samples[i] = new Sample();
        }
        for (int i = 0; i < CHANNELS; i++) {
            voices[i] = new Voice();
        }
    }

    /** Master volume, 0 to 64, used for fades. */
    public void setMasterVolume(int volume) {
        masterVolume = Math.max(0, Math.min(64, volume));
    }

    /** Loads a 31 instrument module; returns false if the data is not one. */
    public boolean load(byte[] mod) {
        if (mod == null || mod.length < 1084) {
            return false;
        }
        String magic = new String(mod, 1080, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        if (!magic.equals("M.K.") && !magic.equals("M!K!") && !magic.equals("4CHN")
                && !magic.equals("FLT4")) {
            return false;
        }

        int offset = 20;
        int totalSampleBytes = 0;
        for (int i = 1; i <= 31; i++) {
            Sample s = samples[i];
            offset += 22;
            s.length = word(mod, offset) * 2;
            s.finetune = mod[offset + 2] & 0x0f;
            if (s.finetune > 7) {
                s.finetune -= 16;
            }
            s.volume = mod[offset + 3] & 0xff;
            s.loopStart = word(mod, offset + 4) * 2;
            s.loopLength = word(mod, offset + 6) * 2;
            offset += 8;
            totalSampleBytes += s.length;
        }

        songLength = mod[950] & 0xff;
        order = new int[128];
        int patternCount = 0;
        for (int i = 0; i < 128; i++) {
            order[i] = mod[952 + i] & 0xff;
            patternCount = Math.max(patternCount, order[i] + 1);
        }

        int pos = 1084;
        patterns = new byte[patternCount][];
        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new byte[1024];
            if (pos + 1024 <= mod.length) {
                System.arraycopy(mod, pos, patterns[i], 0, 1024);
            }
            pos += 1024;
        }

        for (int i = 1; i <= 31; i++) {
            Sample s = samples[i];
            s.data = new byte[Math.max(0, s.length)];
            if (pos + s.length <= mod.length) {
                System.arraycopy(mod, pos, s.data, 0, s.length);
            }
            pos += s.length;
            // ProTracker mutes the two leading words of an instrument.
            if (s.data.length >= 2) {
                s.data[0] = 0;
                s.data[1] = 0;
            }
        }
        return totalSampleBytes >= 0;
    }

    /** {@code mt_init}: rewinds the song and clears the voices. */
    public void init() {
        speed = 6;
        counter = 0;
        songPosition = 0;
        patternRow = 0;
        patternBreakRow = -1;
        positionJump = -1;
        for (Voice v : voices) {
            v.sampleNumber = 0;
            v.period = 0;
            v.volume = 0;
            v.vibratoPos = 0;
            v.tremoloPos = 0;
            v.patternLoopRow = 0;
            v.patternLoopCount = 0;
        }
        playing = patterns.length > 0;
    }

    /** {@code mt_end}: silences every voice. */
    public void stop() {
        playing = false;
        for (int c = 0; c < CHANNELS; c++) {
            paula.channel(c).setVolume(0);
            paula.channel(c).stop();
        }
    }

    /** {@code mt_music}: one vertical blank. */
    public void play() {
        if (!playing) {
            return;
        }
        counter++;
        if (counter >= speed) {
            counter = 0;
            newRow();
        } else {
            for (int c = 0; c < CHANNELS; c++) {
                effect(c, voices[c]);
            }
        }
    }

    private void newRow() {
        byte[] pattern = pattern(songPosition);
        for (int c = 0; c < CHANNELS; c++) {
            note(c, voices[c], pattern, patternRow * 16 + c * 4);
        }
        advanceRow();
    }

    private byte[] pattern(int position) {
        if (order.length == 0 || patterns.length == 0) {
            return new byte[1024];
        }
        int index = order[position % Math.max(1, songLength)];
        return patterns[Math.floorMod(index, patterns.length)];
    }

    private void advanceRow() {
        patternRow++;
        if (patternBreakRow >= 0) {
            patternRow = patternBreakRow;
            patternBreakRow = -1;
            songPosition++;
        } else if (patternRow >= 64) {
            patternRow = 0;
            songPosition++;
        }
        if (positionJump >= 0) {
            songPosition = positionJump;
            positionJump = -1;
        }
        if (songLength > 0 && songPosition >= songLength) {
            songPosition = 0;
        }
    }

    private void note(int channel, Voice v, byte[] pattern, int offset) {
        int b0 = pattern[offset] & 0xff;
        int b1 = pattern[offset + 1] & 0xff;
        int b2 = pattern[offset + 2] & 0xff;
        int b3 = pattern[offset + 3] & 0xff;

        int period = ((b0 & 0x0f) << 8) | b1;
        int instrument = (b0 & 0xf0) | (b2 >> 4);
        v.command = b2 & 0x0f;
        v.commandParam = b3;

        if (instrument > 0 && instrument < samples.length) {
            v.sampleNumber = instrument;
            Sample s = samples[instrument];
            v.finetune = s.finetune;
            v.volume = s.volume;
        }

        if (period > 0) {
            int tuned = finetuned(period, v.finetune);
            if (v.command == 0x3 || v.command == 0x5) {
                v.wantedPeriod = tuned;
            } else {
                v.period = tuned;
                v.wantedPeriod = tuned;
                if ((v.vibratoCommand & 0x04) == 0) {
                    v.vibratoPos = 0;
                }
                if ((v.tremoloCommand & 0x04) == 0) {
                    v.tremoloPos = 0;
                }
                trigger(channel, v);
            }
        }

        // Effects that act on the row itself rather than on the ticks.
        switch (v.command) {
            case 0x9 -> sampleOffset(channel, v);
            case 0xB -> positionJump = v.commandParam;
            case 0xC -> v.volume = Math.min(64, v.commandParam);
            case 0xD -> patternBreakRow = (v.commandParam >> 4) * 10 + (v.commandParam & 0x0f);
            case 0xE -> extended(channel, v, true);
            case 0xF -> {
                if (v.commandParam > 0) {
                    // The VBlank replayer treats the whole range as a tick count.
                    speed = Math.min(v.commandParam, 31);
                }
            }
            default -> {
            }
        }
        apply(channel, v);
    }

    private void trigger(int channel, Voice v) {
        Sample s = samples[v.sampleNumber];
        Paula.Channel ch = paula.channel(channel);
        ch.setPeriod(v.period);
        ch.play(s.data, 0, Math.max(2, s.length));
        ch.loop(s.loopStart, s.loopLength);
    }

    private void sampleOffset(int channel, Voice v) {
        Sample s = samples[v.sampleNumber];
        int start = v.commandParam * 256;
        if (start >= s.length) {
            return;
        }
        Paula.Channel ch = paula.channel(channel);
        ch.play(s.data, start, s.length - start);
        ch.loop(s.loopStart, s.loopLength);
    }

    private void effect(int channel, Voice v) {
        switch (v.command) {
            case 0x0 -> arpeggio(v);
            case 0x1 -> v.period = Math.max(113, v.period - v.commandParam);
            case 0x2 -> v.period = Math.min(856, v.period + v.commandParam);
            case 0x3 -> tonePortamento(v);
            case 0x4 -> vibrato(v);
            case 0x5 -> {
                tonePortamento(v);
                volumeSlide(v);
            }
            case 0x6 -> {
                vibrato(v);
                volumeSlide(v);
            }
            case 0x7 -> tremolo(v);
            case 0xA -> volumeSlide(v);
            case 0xE -> extended(channel, v, false);
            default -> {
            }
        }
        apply(channel, v);
    }

    private void arpeggio(Voice v) {
        if (v.commandParam == 0) {
            return;
        }
        int step = counter % 3;
        int add = switch (step) {
            case 1 -> v.commandParam >> 4;
            case 2 -> v.commandParam & 0x0f;
            default -> 0;
        };
        if (add == 0) {
            return;
        }
        int index = periodIndex(v.wantedPeriod, v.finetune) + add;
        v.period = finetuned(PERIODS[Math.min(index, PERIODS.length - 1)], v.finetune);
    }

    private void tonePortamento(Voice v) {
        if (v.command == 0x3 && v.commandParam != 0) {
            v.portaSpeed = v.commandParam;
        }
        if (v.wantedPeriod == 0 || v.period == 0) {
            return;
        }
        if (v.period < v.wantedPeriod) {
            v.period = Math.min(v.wantedPeriod, v.period + v.portaSpeed);
        } else if (v.period > v.wantedPeriod) {
            v.period = Math.max(v.wantedPeriod, v.period - v.portaSpeed);
        }
    }

    private void vibrato(Voice v) {
        if (v.command == 0x4 && v.commandParam != 0) {
            v.vibratoCommand = v.commandParam;
        }
        int depth = v.vibratoCommand & 0x0f;
        int speedParam = (v.vibratoCommand >> 4) & 0x0f;
        int delta = SINE[(v.vibratoPos >> 2) & 31] * depth / 128;
        v.period = ((v.vibratoPos & 0x80) != 0 ? v.wantedPeriod - delta : v.wantedPeriod + delta);
        v.vibratoPos = (v.vibratoPos + speedParam * 4) & 0xff;
    }

    private void tremolo(Voice v) {
        if (v.command == 0x7 && v.commandParam != 0) {
            v.tremoloCommand = v.commandParam;
        }
        int depth = v.tremoloCommand & 0x0f;
        int speedParam = (v.tremoloCommand >> 4) & 0x0f;
        int delta = SINE[(v.tremoloPos >> 2) & 31] * depth / 64;
        int volume = (v.tremoloPos & 0x80) != 0 ? v.volume - delta : v.volume + delta;
        v.volume = Math.max(0, Math.min(64, volume));
        v.tremoloPos = (v.tremoloPos + speedParam * 4) & 0xff;
    }

    private void volumeSlide(Voice v) {
        int up = v.commandParam >> 4;
        int down = v.commandParam & 0x0f;
        if (up > 0) {
            v.volume = Math.min(64, v.volume + up);
        } else {
            v.volume = Math.max(0, v.volume - down);
        }
    }

    private void extended(int channel, Voice v, boolean onRow) {
        int sub = v.commandParam >> 4;
        int param = v.commandParam & 0x0f;
        switch (sub) {
            case 0x1 -> {
                if (onRow) {
                    v.period = Math.max(113, v.period - param);
                }
            }
            case 0x2 -> {
                if (onRow) {
                    v.period = Math.min(856, v.period + param);
                }
            }
            case 0x5 -> {
                if (onRow) {
                    v.finetune = param > 7 ? param - 16 : param;
                }
            }
            case 0x6 -> {
                if (!onRow) {
                    return;
                }
                if (param == 0) {
                    v.patternLoopRow = patternRow;
                } else if (v.patternLoopCount == 0) {
                    v.patternLoopCount = param;
                    patternBreakRow = v.patternLoopRow;
                } else if (--v.patternLoopCount > 0) {
                    patternBreakRow = v.patternLoopRow;
                }
            }
            case 0x9 -> {
                if (!onRow && param > 0 && counter % param == 0) {
                    trigger(channel, v);
                }
            }
            case 0xA -> {
                if (onRow) {
                    v.volume = Math.min(64, v.volume + param);
                }
            }
            case 0xB -> {
                if (onRow) {
                    v.volume = Math.max(0, v.volume - param);
                }
            }
            case 0xC -> {
                if (!onRow && counter == param) {
                    v.volume = 0;
                }
            }
            case 0xD -> {
                if (!onRow && counter == param) {
                    trigger(channel, v);
                }
            }
            default -> {
            }
        }
    }

    private void apply(int channel, Voice v) {
        Paula.Channel ch = paula.channel(channel);
        if (v.period > 0) {
            ch.setPeriod(v.period);
        }
        ch.setVolume(v.volume * masterVolume / 64);
    }

    private static int finetuned(int period, int finetune) {
        if (finetune == 0) {
            return period;
        }
        return (int) Math.round(period * Math.pow(2, -finetune / 96.0));
    }

    private static int periodIndex(int period, int finetune) {
        int best = 0;
        int bestDelta = Integer.MAX_VALUE;
        for (int i = 0; i < PERIODS.length; i++) {
            int delta = Math.abs(finetuned(PERIODS[i], finetune) - period);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }
        return best;
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }
}
