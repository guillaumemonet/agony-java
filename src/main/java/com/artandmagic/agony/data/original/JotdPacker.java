package com.artandmagic.agony.data.original;

/**
 * The packer of the hard disk release, the one installed by the "Agony HD
 * Loader" of Jean-François Fabre (JOTD, 1997).  Its {@code .bin} files are the
 * same programs as the {@code Agony.xx} files of the floppy release, packed
 * another way; the decruncher sits in the loader, {@code AGONY} on the first
 * disk, and is reproduced here.
 *
 * <p>A file is the unpacked length as a long, one fill byte, then commands of
 * one byte each, until the output is full:
 *
 * <pre>
 *   1 l xxxxx [x]  copy from the output already written.  l clear: length
 *                  xxxxx + 3; l set: a 13 bit length from this byte and the
 *                  next, + 35.  Bit 5 says how far back: clear, one byte d
 *                  and the copy starts d + 1 + length - 1 back; set, a word d
 *                  and it starts d + $101 + length - 1 back.
 *   0 1 1 r xxxx x a 12 bit count from this byte and the next: r set, that
 *                  many plus 18 fill bytes; r clear, one byte follows, written
 *                  that many plus 18 times
 *   0 1 0 xxxxx x  a 13 bit count from this byte and the next: that many
 *                  plus 33 literal bytes follow
 *   0 0 1 r xxxx   r set: xxxx + 2 fill bytes; r clear: one byte follows,
 *                  written xxxx + 2 times
 *   0 0 0 xxxxx    xxxxx + 1 literal bytes follow
 * </pre>
 */
public final class JotdPacker {

    private JotdPacker() {
    }

    /**
     * Whether a buffer looks like one of these files: a plausible length and
     * a first command that writes literals.
     */
    public static boolean isPacked(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        long size = u32(data, 0);
        return size > data.length && size < 4 * 1024 * 1024;
    }

    /** Decrunches a file. */
    public static byte[] unpack(byte[] in) {
        int size = (int) u32(in, 0);
        byte[] out = new byte[size];
        int fill = in[4];
        int src = 5;
        int dst = 0;
        while (dst < size) {
            int c = in[src++] & 0xff;
            if ((c & 0x80) != 0) {
                int length;
                if ((c & 0x40) != 0) {
                    length = ((((c << 8) | (in[src++] & 0xff)) & 0x1fff) + 0x22);
                } else {
                    length = (c & 0x1f) + 2;
                }
                int from;
                if ((c & 0x20) != 0) {
                    int d = ((in[src] & 0xff) << 8) | (in[src + 1] & 0xff);
                    src += 2;
                    from = dst - 0x101 - d - length;
                } else {
                    int d = in[src++] & 0xff;
                    from = dst - 1 - d - length;
                }
                for (int i = 0; i <= length && dst < size; i++) {
                    out[dst++] = out[from + i];
                }
            } else if ((c & 0x40) != 0) {
                int v = ((c << 8) | (in[src++] & 0xff)) & 0x1fff;
                if ((c & 0x20) != 0) {
                    int count;
                    byte b;
                    if ((v & 0x1000) != 0) {
                        count = (v & 0x0fff) + 0x12;
                        b = (byte) fill;
                    } else {
                        count = v + 0x12;
                        b = in[src++];
                    }
                    for (int i = 0; i < count && dst < size; i++) {
                        out[dst++] = b;
                    }
                } else {
                    int count = v + 0x21;
                    for (int i = 0; i < count && dst < size; i++) {
                        out[dst++] = in[src++];
                    }
                }
            } else if ((c & 0x20) != 0) {
                int count = (c & 0x0f) + 2;
                byte b = (c & 0x10) != 0 ? (byte) fill : in[src++];
                for (int i = 0; i < count && dst < size; i++) {
                    out[dst++] = b;
                }
            } else {
                int count = (c & 0x1f) + 1;
                for (int i = 0; i < count && dst < size; i++) {
                    out[dst++] = in[src++];
                }
            }
        }
        return out;
    }

    private static long u32(byte[] p, int at) {
        return ((long) (p[at] & 0xff) << 24) | ((p[at + 1] & 0xff) << 16)
                | ((p[at + 2] & 0xff) << 8) | (p[at + 3] & 0xff);
    }
}
