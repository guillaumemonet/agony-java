package com.artandmagic.agony.data.original;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader for an Amiga disk image holding an ordinary AmigaDOS file system.
 *
 * <p>The three Agony disks are OFS volumes whose files, {@code Agony.00} to
 * {@code Agony.14}, are the overlays the game loads as it moves from level to
 * level.  Only the handful of structures needed to walk the root directory and
 * pull a file out are implemented here.
 */
public final class Adf {

    private static final int BLOCK = 512;
    private static final int ROOT = 880;

    private final byte[] image;
    private final boolean ffs;

    public Adf(byte[] image) {
        this.image = image;
        this.ffs = (image[3] & 1) != 0;
    }

    public static Adf read(Path path) throws IOException {
        return new Adf(Files.readAllBytes(path));
    }

    /** One directory entry. */
    public record Entry(String name, int block, int size, boolean directory) {
    }

    /** Volume name, as shown by the Workbench. */
    public String volume() {
        return name(ROOT);
    }

    /** Lists the root directory; Agony keeps everything there. */
    public List<Entry> list() {
        List<Entry> out = new ArrayList<>();
        int tableSize = word32(ROOT * BLOCK + 12);
        for (int i = 0; i < tableSize; i++) {
            int chain = word32(ROOT * BLOCK + 24 + i * 4);
            while (chain > 0 && chain < image.length / BLOCK) {
                int base = chain * BLOCK;
                int secondary = word32(base + 508);
                out.add(new Entry(name(chain), chain, word32(base + 324), secondary == 2));
                chain = word32(base + 496);
            }
        }
        return out;
    }

    /** Reads a file by name, or returns null when the volume does not hold it. */
    public byte[] file(String name) {
        for (Entry e : list()) {
            if (!e.directory() && e.name().equalsIgnoreCase(name)) {
                return read(e.block());
            }
        }
        return null;
    }

    /** Reads the contents of a file header block. */
    public byte[] read(int headerBlock) {
        int size = word32(headerBlock * BLOCK + 324);
        byte[] out = new byte[size];
        int written = 0;
        int header = headerBlock;

        while (header != 0 && written < size) {
            int base = header * BLOCK;
            int count = word32(base + 8);
            for (int i = 0; i < count && written < size; i++) {
                int data = word32(base + 24 + (71 - i) * 4);
                if (data == 0) {
                    continue;
                }
                int from = data * BLOCK;
                int length;
                if (ffs) {
                    length = Math.min(BLOCK, size - written);
                } else {
                    length = Math.min(word32(from + 12), size - written);
                    from += 24;
                }
                if (from + length > image.length) {
                    length = Math.max(0, image.length - from);
                }
                System.arraycopy(image, from, out, written, length);
                written += length;
            }
            header = word32(base + 504);
        }
        return out;
    }

    private String name(int block) {
        int base = block * BLOCK;
        int len = image[base + 432] & 0xff;
        return new String(image, base + 433, Math.min(len, 30),
                java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private int word32(int offset) {
        return ((image[offset] & 0xff) << 24) | ((image[offset + 1] & 0xff) << 16)
                | ((image[offset + 2] & 0xff) << 8) | (image[offset + 3] & 0xff);
    }
}
