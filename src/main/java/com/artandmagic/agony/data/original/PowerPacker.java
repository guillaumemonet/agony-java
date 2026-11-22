package com.artandmagic.agony.data.original;

/**
 * PowerPacker PP20 decruncher.
 *
 * <p>Every Agony overlay on the disks is a PP20 file.  The format reads its bit
 * stream backwards, one longword at a time from the end, and produces the
 * output backwards as well:
 * <pre>
 *   0..3     "PP20"
 *   4..7     four offset widths, chosen by a two bit index
 *   8..n-5   the bit stream
 *   n-4..n-2 decompressed size, 24 bits
 *   n-1      bits to discard in the first longword read
 * </pre>
 */
public final class PowerPacker {

    private final byte[] data;
    private int position;
    private int word;
    private int left;

    private PowerPacker(byte[] data, int end) {
        this.data = data;
        this.position = end;
    }

    /** True when the buffer carries the PP20 signature. */
    public static boolean isPacked(byte[] data) {
        return data != null && data.length > 12
                && data[0] == 'P' && data[1] == 'P' && data[2] == '2' && data[3] == '0';
    }

    /** Decrunches a PP20 buffer, or returns it unchanged when it is not packed. */
    public static byte[] unpack(byte[] data) {
        if (!isPacked(data)) {
            return data;
        }
        int[] widths = {
            data[4] & 0xff, data[5] & 0xff, data[6] & 0xff, data[7] & 0xff
        };
        int size = ((data[data.length - 4] & 0xff) << 16)
                | ((data[data.length - 3] & 0xff) << 8)
                | (data[data.length - 2] & 0xff);
        int skip = data[data.length - 1] & 0xff;

        byte[] out = new byte[size];
        int pos = size;
        PowerPacker bits = new PowerPacker(data, data.length - 4);
        bits.get(skip);

        while (pos > 0) {
            if (bits.get(1) == 0) {
                // A run of literal bytes, its length coded two bits at a time.
                int count = 1;
                int c;
                do {
                    c = bits.get(2);
                    count += c;
                } while (c == 3);
                for (int i = 0; i < count && pos > 0; i++) {
                    out[--pos] = (byte) bits.get(8);
                }
                if (pos == 0) {
                    break;
                }
            }

            int index = bits.get(2);
            int offset;
            int length;
            if (index == 3) {
                offset = bits.get(1) != 0 ? bits.get(widths[3]) : bits.get(7);
                length = 5;
                int c;
                do {
                    c = bits.get(3);
                    length += c;
                } while (c == 7);
            } else {
                offset = bits.get(widths[index]);
                length = index + 2;
            }

            for (int i = 0; i < length && pos > 0; i++) {
                pos--;
                int from = pos + offset + 1;
                out[pos] = from < size ? out[from] : 0;
            }
        }
        return out;
    }

    private int get(int count) {
        int result = 0;
        for (int i = 0; i < count; i++) {
            if (left == 0) {
                position -= 4;
                word = ((data[position] & 0xff) << 24) | ((data[position + 1] & 0xff) << 16)
                        | ((data[position + 2] & 0xff) << 8) | (data[position + 3] & 0xff);
                left = 32;
            }
            result = (result << 1) | (word & 1);
            word >>>= 1;
            left--;
        }
        return result;
    }
}
