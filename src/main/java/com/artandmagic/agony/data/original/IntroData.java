package com.artandmagic.agony.data.original;

import java.util.ArrayList;
import java.util.List;

/**
 * The presentation that opens the game ({@code Agony.01}, {@code intro.bin}),
 * read from its program.
 *
 * <p>No source of it is in the release.  It is a short piece of straight line
 * code: a handful of logos in hires interlace, each faded in and out, with
 * sampled sound played straight through the audio registers.  This class walks
 * that code from its entry to the {@code jmp} that leaves for the title and
 * turns each recognised instruction pattern into a {@link Step}; the timing of
 * the subroutines it calls (the fades, the waits) and the volume envelopes its
 * vertical blank handler runs are reproduced by
 * {@link com.artandmagic.agony.screens.IntroScreen}.
 *
 * <ul>
 *   <li>{@code move.l #diw,$8e(a6)} places the picture on the screen;</li>
 *   <li>{@code move.l #planes,d0} ... {@code addi.l #stride,d0} fills the four
 *       bitplane pointers of the two copper lists, one per interlaced field,
 *       the second field 80 bytes, one hires line, further on;</li>
 *   <li>{@code move.l #list,$80(a6)} shows it; a list holding {@code COLOR01}
 *       moves behind {@code WAIT}s is the sweep of light over the Psygnosis
 *       owl, whose waits are moved up a line a frame from a table
 *       ({@code lea table,a3 / subq.w #1,(a3)});</li>
 *   <li>{@code lea palette,a0 / bsr} fades in, {@code move.w #n,d0 / bsr}
 *       waits n frames, a bare {@code bsr} fades out to grey;</li>
 *   <li>{@code move.l #sample,$a0(a6)} and the length, period, volume and
 *       {@code DMACON} writes that follow play a sound; clearing an audio
 *       interrupt and polling {@code INTREQR} for it waits for the sound to
 *       end before its channel is turned off.</li>
 * </ul>
 */
public final class IntroData {

    /** What a step does. */
    public enum Kind {
        /** Shows a picture strip: a = planes, b = stride, c = DIW start, d = DIW stop. */
        SHOW,
        /** Fades the palette at a in, a step every five frames. */
        FADE_IN,
        /** Sets the palette at a at once. */
        SET_PALETTE,
        /** Waits a frames. */
        WAIT,
        /** Fades to grey $666, a step every three frames. */
        FADE_OUT,
        /** Fades the grey to black, $111 every three frames. */
        FADE_BLACK,
        /** Starts channel a: data b, length c bytes, period d, volume e; f = 1 when it loops. */
        PLAY,
        /** Waits for the sound on channel a to end, then stops the channel. */
        WAIT_END,
        /** Stops channel a. */
        STOP,
        /** Starts the volume ramp up of channels 2 and 3. */
        RAMP_UP,
        /** Starts the volume ramp down of channels 2 and 3 (and up of 0 and 1). */
        RAMP_DOWN,
        /** Starts fading channels 0 and 1 out. */
        FADE_SOUND,
        /** Starts the sweep: a = table of start lines, b = how many, colours in {@link Step#colours}. */
        SWEEP,
    }

    /** One step of the script. */
    public record Step(Kind kind, int a, int b, int c, int d, int e, int f, int[] colours) {
        static Step of(Kind kind, int... v) {
            int[] x = java.util.Arrays.copyOf(v, 6);
            return new Step(kind, x[0], x[1], x[2], x[3], x[4], x[5], null);
        }
    }

    /** The program, so pictures, palettes and samples can be read from it. */
    public final byte[] program;
    /** The address it is assembled at. */
    public final int org;
    public final List<Step> steps;
    private IntroData(byte[] program, int org, List<Step> steps) {
        this.program = program;
        this.org = org;
        this.steps = steps;
    }

    /** A word of the program at an absolute address. */
    public int word(int address) {
        return Code.u16(program, address - org);
    }

    /** Finds the presentation among the files of the disks. */
    public static IntroData scan(Disks disks) {
        // The booter loads it first: file number 1 of the original disk.
        byte[] p = disks.file("Agony.01");
        if (p == null) {
            return null;
        }
        try {
            return decode(p);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Reads the script, or returns null when the program is not the presentation. */
    public static IntroData decode(byte[] p) {
        // The program starts with the usual wait for line 16 and is assembled
        // where the booter drops it; the level 3 vector it installs tells where.
        Code.Origin origin = Code.origin(p);
        int org = origin != null ? origin.org() : originFromVector(p);
        if (org < 0) {
            return null;
        }
        int end = Code.find(p, 0, 0x4ef9);
        if (end < 0) {
            return null;
        }

        // The subroutines, recognised by how they start.
        List<Step> steps = new ArrayList<>();
        int diw = -1;
        int planes = -1;
        int stride = -1;
        int value = 0;
        int[] location = new int[4];
        int[] length = new int[4];
        int[] period = new int[4];
        int[] volume = new int[4];
        Integer rampUp = null;
        int pendingWait = -1;

        for (int i = 0; i + 2 <= end; i += 2) {
            int op = Code.u16(p, i);
            // move.l #diw,$8e(a6)
            if (op == 0x2d7c && Code.u16(p, i + 6) == 0x008e) {
                diw = Code.u32(p, i + 2);
                i += 6;
                continue;
            }
            // move.l #list,$80(a6): the lists are switched, nothing new to show.
            if (op == 0x2d7c && Code.u16(p, i + 6) == 0x0080) {
                i += 6;
                continue;
            }
            // move.l #sample,$a0+16n(a6)
            if (op == 0x2d7c && audioRegister(Code.u16(p, i + 6)) == 0) {
                location[channel(Code.u16(p, i + 6))] = Code.u32(p, i + 2);
                i += 6;
                continue;
            }
            // move.w #v,$a4/$a6/$a8+16n(a6)
            if (op == 0x3d7c && audioRegister(Code.u16(p, i + 4)) > 0) {
                audio(Code.u16(p, i + 4), Code.u16(p, i + 2), length, period, volume);
                i += 4;
                continue;
            }
            // move.w d0,$a4/$a6/$a8+16n(a6)
            if (op == 0x3d40 && audioRegister(Code.u16(p, i + 2)) > 0) {
                audio(Code.u16(p, i + 2), value, length, period, volume);
                i += 2;
                continue;
            }
            // clr.w $a8+16n(a6)
            if (op == 0x426e && audioRegister(Code.u16(p, i + 2)) == 8) {
                volume[channel(Code.u16(p, i + 2))] = 0;
                i += 2;
                continue;
            }
            // move.w #dmacon,$96(a6): audio channels on or off.
            if (op == 0x3d7c && Code.u16(p, i + 4) == 0x0096) {
                int v = Code.u16(p, i + 2);
                for (int ch = 0; ch < 4; ch++) {
                    if ((v & (1 << ch)) == 0) {
                        continue;
                    }
                    if ((v & 0x8000) != 0) {
                        boolean oneShot = waitsForEnd(p, i + 6, end, ch);
                        steps.add(Step.of(Kind.PLAY, ch, location[ch], length[ch] * 2,
                                period[ch], volume[ch], oneShot ? 0 : 1));
                    } else if (!endedBefore(steps, ch)) {
                        steps.add(Step.of(Kind.STOP, ch));
                    }
                }
                i += 4;
                continue;
            }
            // move.w #$0n00,$9c(a6) / btst #n,INTREQR: wait for a sound to end.
            if (op == 0x3d7c && Code.u16(p, i + 4) == 0x009c) {
                int v = Code.u16(p, i + 2);
                for (int ch = 0; ch < 4; ch++) {
                    if (v == (0x80 << ch)) {
                        steps.add(Step.of(Kind.WAIT_END, ch));
                    }
                }
                i += 4;
                continue;
            }
            // move.l #planes,d0 ... addi.l #stride,d0
            if (op == 0x203c) {
                value = Code.u32(p, i + 2);
                int add = Code.find(p, i + 6, 0x0680);
                if (add >= 0 && add - i < 0x20) {
                    // The second field of a picture is one hires line, 80
                    // bytes, further on; anything else is a new picture, on
                    // screen from the next frame since the lists are live.
                    int base = value;
                    if (base != planes && base != planes + 0x50 && diw != -1) {
                        planes = base;
                        stride = Code.u32(p, add + 2);
                        steps.add(Step.of(Kind.SHOW, planes, stride, diw >>> 16, diw & 0xffff));
                    }
                }
                i += 4;
                continue;
            }
            // move.w #n,d0 / moveq #n,d0 / bsr: a wait, or the final fade.
            if (op == 0x303c) {
                value = Code.u16(p, i + 2);
                pendingWait = value;
                i += 2;
                continue;
            }
            if ((op & 0xff00) == 0x7000) {
                value = (byte) op;
                continue;
            }
            // lea palette,a0 / bsr (fade in) or lea palette,a0 / lea $180(a6),a1 (set).
            if (op == 0x41f9) {
                int pal = Code.u32(p, i + 2);
                int next = Code.u16(p, i + 6);
                if (next == 0x6100) {
                    steps.add(Step.of(Kind.FADE_IN, pal));
                    i += 8;
                    pendingWait = -1;
                    continue;
                }
                if (next == 0x43ee && Code.u16(p, i + 8) == 0x0180) {
                    steps.add(Step.of(Kind.SET_PALETTE, pal));
                    i += 8;
                    continue;
                }
                i += 4;
                continue;
            }
            // lea table,a3 / subq.w #1,(a3): the sweep of light.
            if (op == 0x47f9 && Code.u16(p, i + 6) == 0x5353) {
                int table = Code.u32(p, i + 2);
                int count = 0;
                while (count < 256 && Code.u16(p, table - org + count * 2) != 0) {
                    count++;
                }
                steps.add(new Step(Kind.SWEEP, table, count, 0, 0, 0, 0,
                        sweepColours(p, org, count)));
                i += 6;
                continue;
            }
            // move.w #1,flag / st.b flag: the envelopes of the handler.
            if (op == 0x33fc && Code.u16(p, i + 2) == 1) {
                int flag = Code.u32(p, i + 4);
                if (rampUp == null) {
                    rampUp = flag;
                }
                steps.add(Step.of(flag == rampUp ? Kind.RAMP_UP : Kind.RAMP_DOWN));
                i += 6;
                continue;
            }
            if (op == 0x50f9) {
                steps.add(Step.of(Kind.FADE_SOUND));
                i += 4;
                continue;
            }
            // subi.w #$111,d0: the walk from grey to black.
            if (op == 0x0440 && Code.u16(p, i + 2) == 0x0111) {
                steps.add(Step.of(Kind.FADE_BLACK));
                i += 2;
                continue;
            }
            // bsr: a wait when a count was just loaded, a fade out otherwise.
            if (op == 0x6100) {
                int target = i + 2 + (short) Code.u16(p, i + 2);
                if (isWait(p, target)) {
                    if (pendingWait >= 0) {
                        steps.add(Step.of(Kind.WAIT, pendingWait));
                    }
                } else if (isFadeOut(p, target)) {
                    steps.add(Step.of(Kind.FADE_OUT));
                }
                pendingWait = -1;
                i += 2;
            }
        }
        return steps.isEmpty() ? null : new IntroData(p, org, steps);
    }

    /** True when a channel's next audio interrupt is waited for before it plays again. */
    private static boolean waitsForEnd(byte[] p, int from, int end, int ch) {
        for (int i = from; i + 6 <= end; i += 2) {
            if (Code.u16(p, i) == 0x3d7c && Code.u16(p, i + 4) == 0x009c
                    && Code.u16(p, i + 2) == (0x80 << ch)) {
                return true;
            }
            if (Code.u16(p, i) == 0x3d7c && Code.u16(p, i + 4) == 0x0096
                    && (Code.u16(p, i + 2) & 0x8000) != 0 && (Code.u16(p, i + 2) & (1 << ch)) != 0) {
                return false;
            }
        }
        return false;
    }

    /** True when the channel was just waited for, which already stops it. */
    private static boolean endedBefore(List<Step> steps, int ch) {
        for (int k = steps.size() - 1; k >= 0; k--) {
            Step s = steps.get(k);
            if (s.kind() == Kind.WAIT_END && s.a() == ch) {
                return true;
            }
            if (s.kind() == Kind.PLAY && s.a() == ch) {
                return false;
            }
        }
        return false;
    }

    /** The routine that counts frames: {@code clr.w counter / cmp.w counter,d0}. */
    private static boolean isWait(byte[] p, int at) {
        return at >= 0 && at + 8 <= p.length && Code.u16(p, at) == 0x4279
                && Code.u16(p, at + 6) == 0xb079;
    }

    /** The fade to grey: {@code moveq #16,d0 / bsr} and the step towards 6. */
    private static boolean isFadeOut(byte[] p, int at) {
        return at >= 0 && at + 4 <= p.length && Code.u16(p, at) == 0x7010
                && Code.u16(p, at + 2) == 0x6100;
    }

    /**
     * The colours of the sweep: the {@code COLOR01} moves that follow the
     * waits of the copper list it runs in.
     */
    private static int[] sweepColours(byte[] p, int org, int count) {
        // The list is the one whose waits the sweep moves: lea list,a0 just
        // before the table names its first wait.
        for (int i = 0; i + 8 <= p.length; i += 2) {
            if (Code.u16(p, i) == 0x0182 && Code.u16(p, i + 4) == 0xff01
                    && Code.u16(p, i + 6) == 0xfffe) {
                int[] out = new int[count];
                int at = i + 8;
                for (int k = 0; k < count && at + 4 <= p.length; k++) {
                    out[k] = Code.u16(p, at + 2) & 0x0fff;
                    at += 8;
                }
                return out;
            }
        }
        return new int[count];
    }

    /** 0 for AUDxLC, 4 for AUDxLEN, 6 for AUDxPER, 8 for AUDxVOL, -1 otherwise. */
    private static int audioRegister(int reg) {
        if (reg < 0xa0 || reg > 0xd8) {
            return -1;
        }
        int r = reg & 0x0f;
        return (r == 0 || r == 4 || r == 6 || r == 8) ? r : -1;
    }

    private static int channel(int reg) {
        return (reg - 0xa0) >> 4;
    }

    private static void audio(int reg, int v, int[] length, int[] period, int[] volume) {
        int ch = channel(reg);
        switch (audioRegister(reg)) {
            case 4 -> length[ch] = v;
            case 6 -> period[ch] = v;
            case 8 -> volume[ch] = v;
            default -> {
            }
        }
    }

    private static int originFromVector(byte[] p) {
        for (int v = 0; v + 10 <= p.length; v += 2) {
            if (Code.u16(p, v) == 0x23fc && Code.u32(p, v + 6) == 0x6c) {
                int vector = Code.u32(p, v + 2);
                for (int h = 0; h + 10 <= p.length; h += 2) {
                    if (Code.u32(p, h) == 0x48e7fffe && Code.u16(p, h + 4) == 0x4df9
                            && Code.u32(p, h + 6) == 0x00dff000) {
                        return vector - h;
                    }
                }
            }
        }
        return -1;
    }

}
