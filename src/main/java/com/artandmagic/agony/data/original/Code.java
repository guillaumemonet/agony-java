package com.artandmagic.agony.data.original;

/**
 * Reading data addresses out of the 68000 code of the programs that have no
 * source in the release: the intro, the title, the loading screens.
 *
 * <p>Those programs are assembled at a fixed address and name their data with
 * absolute operands, so a picture or a table is found by looking for the
 * instruction that uses it, never by a hard coded offset.  This class holds the
 * few searches they share.
 */
final class Code {

    private Code() {
    }

    /** Where a program is assembled, and the offset of its main copper list. */
    record Origin(int org, int copper) {
    }

    /** The display a copper list sets up. */
    record Screen(int width, int height, int planes) {
    }

    /**
     * Finds the address a program is assembled at.
     *
     * <p>Two things pin it down.  The program hands its copper list to
     * {@code COP1LC} with {@code move.l #list,$80(a6)}, and the list starts with
     * the {@code BPLxPT} moves; and it installs its vertical blank handler
     * with {@code move.l #handler,$6c}, a handler that opens with {@code movem.l
     * d0-d7/a0-a6,-(sp) / lea $dff000,a6}.  Only the right origin makes both
     * land where they should.
     */
    static Origin origin(byte[] p) {
        for (int v = 0; v + 10 <= p.length; v += 2) {
            if (u16(p, v) != 0x23fc || u32(p, v + 6) != 0x6c) {
                continue;
            }
            int vector = u32(p, v + 2);
            for (int c = 0; c + 8 <= p.length; c += 2) {
                if (u16(p, c) != 0x2d7c || u16(p, c + 6) != 0x0080) {
                    continue;
                }
                int list = u32(p, c + 2);
                for (int at = find(p, 0, 0x00e0, -1, 0x00e2, -1, 0x00e4); at >= 0;
                     at = find(p, at + 2, 0x00e0, -1, 0x00e2, -1, 0x00e4)) {
                    int org = list - at;
                    int handler = vector - org;
                    if (org > 0 && handler >= 0 && handler + 10 <= p.length
                            && u32(p, handler) == 0x48e7fffe && u16(p, handler + 4) == 0x4df9
                            && u32(p, handler + 6) == 0x00dff000) {
                        return new Origin(org, at);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Reads the screen a copper list opens: {@code DIWSTRT}, {@code DIWSTOP},
     * {@code DDFSTRT}, {@code DDFSTOP} and {@code BPLCON0}.  Only plain lores
     * screens are accepted, the kind these programs use.
     */
    static Screen screen(byte[] p, int copper) {
        int diwStrt = -1;
        int diwStop = -1;
        int ddfStrt = -1;
        int ddfStop = -1;
        int bplcon0 = -1;
        for (int i = copper; i + 4 <= p.length; i += 4) {
            int reg = u16(p, i);
            int val = u16(p, i + 2);
            if (reg == 0xffff) {
                break;
            }
            switch (reg) {
                case 0x8e -> diwStrt = diwStrt < 0 ? val : diwStrt;
                case 0x90 -> diwStop = diwStop < 0 ? val : diwStop;
                case 0x92 -> ddfStrt = ddfStrt < 0 ? val : ddfStrt;
                case 0x94 -> ddfStop = ddfStop < 0 ? val : ddfStop;
                case 0x100 -> bplcon0 = bplcon0 < 0 ? val : bplcon0;
                default -> {
                }
            }
        }
        if (diwStrt < 0 || diwStop < 0 || ddfStrt < 0 || ddfStop < 0 || bplcon0 < 0) {
            return null;
        }
        int planes = (bplcon0 >> 12) & 7;
        boolean hires = (bplcon0 & 0x8000) != 0;
        boolean ham = (bplcon0 & 0x0800) != 0;
        boolean dualPlayfield = (bplcon0 & 0x0400) != 0;
        if (planes == 0 || hires || ham || dualPlayfield) {
            return null;
        }
        // Lores fetch: a word every eight colour clocks from DDFSTRT to
        // DDFSTOP.  DIWSTOP has no ninth vertical bit: the hardware takes it as
        // the complement of bit 7.
        int width = ((ddfStop - ddfStrt) / 8 + 1) * 16;
        int vStart = diwStrt >> 8;
        int vStop = (diwStop >> 8) | (((diwStop & 0x8000) == 0) ? 0x100 : 0);
        return new Screen(width, vStop - vStart, planes);
    }

    /**
     * The 32 bit operand of the first {@code opcode #imm} directly followed by
     * the word {@code next}, made relative to the origin; -1 when absent or
     * outside the program.
     */
    static int operandBefore(byte[] p, int opcode, int next, int org) {
        for (int i = 0; i + 8 <= p.length; i += 2) {
            if (u16(p, i) == opcode && u16(p, i + 6) == next) {
                int at = u32(p, i + 2) - org;
                return at >= 0 && at < p.length ? at : -1;
            }
        }
        return -1;
    }

    /** The 32 bit operand of the first {@code opcode #imm}, or -1. */
    static int operand(byte[] p, int opcode, int org, boolean relative) {
        for (int i = 0; i + 6 <= p.length; i += 2) {
            if (u16(p, i) == opcode) {
                return relative ? u32(p, i + 2) - org : u32(p, i + 2);
            }
        }
        return -1;
    }

    /** Finds a sequence of words from an even offset, -1 standing for any. */
    static int find(byte[] p, int from, int... words) {
        outer:
        for (int i = Math.max(0, from) & ~1; i + words.length * 2 <= p.length; i += 2) {
            for (int k = 0; k < words.length; k++) {
                if (words[k] >= 0 && u16(p, i + k * 2) != words[k]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    static int u16(byte[] p, int at) {
        return ((p[at] & 0xff) << 8) | (p[at + 1] & 0xff);
    }

    static int u32(byte[] p, int at) {
        return (u16(p, at) << 16) | u16(p, at + 2);
    }
}
