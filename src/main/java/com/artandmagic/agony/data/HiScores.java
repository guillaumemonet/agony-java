package com.artandmagic.agony.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The hiscore table of the title program.
 *
 * <p>Six records of eight bytes, best first: three character codes of the
 * title font, a spare byte, and the score as a packed decimal long, the same
 * value the game keeps in {@code Score}.  The original loads the table from
 * {@code Agony.00} on the second disk and writes it back there when a new score
 * gets in; the port starts from the table on the disk and keeps its own copy in
 * the user's home, so the disk images are never written to.
 */
public final class HiScores {

    public static final int ROWS = 6;
    public static final int RECORD = 8;

    private final byte[] table = new byte[ROWS * RECORD];
    private final Path file;

    /**
     * @param defaults the table as the disk holds it, or null
     * @param file     where the port keeps its copy, or null for none
     */
    public HiScores(byte[] defaults, Path file) {
        this.file = file;
        if (defaults != null) {
            System.arraycopy(defaults, 0, table, 0, Math.min(defaults.length, table.length));
        }
        if (file != null && Files.isRegularFile(file)) {
            try {
                byte[] saved = Files.readAllBytes(file);
                if (saved.length == table.length) {
                    System.arraycopy(saved, 0, table, 0, table.length);
                }
            } catch (IOException e) {
                // Keep the table from the disk.
            }
        }
    }

    /** The port's own copy, in the user's home. */
    public static Path defaultFile() {
        return Path.of(System.getProperty("user.home"), ".agony-java", "hiscores.bin");
    }

    /** Character codes of a row's initials. */
    public byte[] name(int row) {
        return new byte[]{table[row * RECORD], table[row * RECORD + 1], table[row * RECORD + 2]};
    }

    /** Packed decimal score of a row. */
    public int score(int row) {
        int at = row * RECORD + 4;
        return ((table[at] & 0xff) << 24) | ((table[at + 1] & 0xff) << 16)
                | ((table[at + 2] & 0xff) << 8) | (table[at + 3] & 0xff);
    }

    /**
     * Whether a score gets into the table.  The original compares it with the
     * last row and asks for initials unless that row is higher.
     */
    public boolean qualifies(int score) {
        return Integer.compareUnsigned(score(ROWS - 1), score) <= 0 && score != 0;
    }

    /**
     * Puts a score in, below every row that beats it, and saves the table.
     *
     * @param name three character codes
     */
    public void insert(byte[] name, int score) {
        int row = 0;
        while (row < ROWS && Integer.compareUnsigned(score(row), score) > 0) {
            row++;
        }
        if (row >= ROWS) {
            return;
        }
        System.arraycopy(table, row * RECORD, table, (row + 1) * RECORD,
                (ROWS - 1 - row) * RECORD);
        int at = row * RECORD;
        table[at] = name[0];
        table[at + 1] = name[1];
        table[at + 2] = name[2];
        table[at + 3] = 0;
        table[at + 4] = (byte) (score >>> 24);
        table[at + 5] = (byte) (score >>> 16);
        table[at + 6] = (byte) (score >>> 8);
        table[at + 7] = (byte) score;
        save();
    }

    private void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, table);
        } catch (IOException e) {
            // The original shows its "remove write protect" page here; the
            // port just carries on with the table in memory.
            System.out.println("Hiscores not saved: " + e.getMessage());
        }
    }
}
