package com.artandmagic.agony.data.original;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The files of the three Agony disks, unpacked, under one set of names
 * whatever release the disk images come from.
 *
 * <p>Two releases are known, holding the same programs:
 *
 * <ul>
 *   <li>the floppy release as cracked by Crystal, whose files are named
 *       {@code Agony.00} to {@code Agony.14} after the file numbers of the
 *       original trackloader, every program packed with PowerPacker;</li>
 *   <li>the hard disk release of the JOTD loader, whose files have names of
 *       their own ({@code intro.bin}, {@code code1.bin}, {@code end1.bin}...)
 *       and the packer of {@link JotdPacker}.</li>
 * </ul>
 *
 * <p>The programs load one another with the {@code LOAD} macro of
 * {@code DISK/load.s}, which hands over a disk number, the checksum code of
 * {@code load_label.s} and the file number.  The JOTD loader keeps a table
 * from disk number and code to file name; the programs themselves pair every
 * code with its file number.  Putting the two together gives every hard disk
 * file its {@code Agony.xx} name, so the rest of the port only ever sees the
 * names of the floppy release.
 */
public final class Disks {

    private final Map<String, byte[]> files;

    private Disks(Map<String, byte[]> files) {
        this.files = files;
    }

    /** The files, unpacked, by name, in the order of the disks. */
    public Map<String, byte[]> files() {
        return Collections.unmodifiableMap(files);
    }

    /** One file by name, ignoring case, or null. */
    public byte[] file(String name) {
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return files.isEmpty();
    }

    private static final Map<Path, Disks> CACHE = new HashMap<>();

    /** Reads every disk image of a folder; the result is kept for later calls. */
    public static synchronized Disks read(Path folder) {
        Path key = folder == null ? Path.of("") : folder.toAbsolutePath().normalize();
        Disks cached = CACHE.get(key);
        if (cached == null) {
            cached = load(folder);
            CACHE.put(key, cached);
        }
        return cached;
    }

    private static Disks load(Path folder) {
        Map<String, byte[]> raw = new LinkedHashMap<>();
        if (folder != null && Files.isDirectory(folder)) {
            List<Path> images = new ArrayList<>();
            try (Stream<Path> list = Files.list(folder)) {
                list.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".adf"))
                        .sorted().forEach(images::add);
            } catch (IOException e) {
                return new Disks(raw);
            }
            for (Path image : images) {
                try {
                    Adf adf = Adf.read(image);
                    for (Adf.Entry entry : adf.list()) {
                        if (!entry.directory()) {
                            raw.putIfAbsent(entry.name(), adf.read(entry.block()));
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    // Not an AmigaDOS volume this reader understands.
                }
            }
        }

        Map<String, Long> table = loaderTable(raw);
        Map<String, byte[]> unpacked = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : raw.entrySet()) {
            byte[] data = e.getValue();
            try {
                if (PowerPacker.isPacked(data)) {
                    data = PowerPacker.unpack(data);
                } else if (table.containsKey(e.getKey().toLowerCase())
                        && JotdPacker.isPacked(data)) {
                    data = JotdPacker.unpack(data);
                }
            } catch (RuntimeException ex) {
                continue;
            }
            unpacked.put(e.getKey(), data);
        }
        if (table.isEmpty()) {
            return new Disks(unpacked);
        }

        // Give the hard disk files the names of the floppy release.
        Map<Long, Integer> numberOf = new HashMap<>();
        for (byte[] program : unpacked.values()) {
            for (int at = Code.find(program, 0, 0x3c3c, -1, 0x2e3c, -1, -1, 0x303c); at >= 0;
                 at = Code.find(program, at + 2, 0x3c3c, -1, 0x2e3c, -1, -1, 0x303c)) {
                long key = ((long) Code.u16(program, at + 2) << 32)
                        | (Code.u32(program, at + 6) & 0xffffffffL);
                numberOf.putIfAbsent(key, Code.u16(program, at + 12));
            }
        }
        Map<String, byte[]> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : unpacked.entrySet()) {
            Long key = table.get(e.getKey().toLowerCase());
            Integer number = key != null ? numberOf.get(key) : null;
            String name = number != null ? String.format("Agony.%02X", number) : e.getKey();
            renamed.putIfAbsent(name, e.getValue());
        }
        return new Disks(renamed);
    }

    /**
     * The file table of the JOTD loader: records of five longs, the offset and
     * length the file had on the original disk, the disk number, the checksum
     * code and a pointer to the file name, ended by a zero long.  Returns file
     * name to disk number and code, or an empty map for the floppy release.
     */
    private static Map<String, Long> loaderTable(Map<String, byte[]> raw) {
        Map<String, Long> out = new HashMap<>();
        for (byte[] exe : raw.values()) {
            if (exe.length < 64 || Code.u32(exe, 0) != 0x3f3) {
                continue;
            }
            int code = hunkCode(exe);
            if (code < 0) {
                continue;
            }
            // The names follow one another as C strings.  "intro.bin" also
            // appears in an error message, so every occurrence is tried.
            for (int names = indexOf(exe, "intro.bin".getBytes(), 0); names >= 0;
                 names = indexOf(exe, "intro.bin".getBytes(), names + 1)) {
                int nameOffset = names - code;
                // The record whose last long points at the first name.
                for (int t = code; t + 20 <= exe.length; t += 2) {
                    long ptr = Code.u32(exe, t + 16) & 0xffffffffL;
                    if (ptr != nameOffset || Code.u32(exe, t + 8) > 3) {
                        continue;
                    }
                    for (int r = t; r + 20 <= exe.length && Code.u32(exe, r) != 0; r += 20) {
                        int disk = Code.u32(exe, r + 8);
                        long check = Code.u32(exe, r + 12) & 0xffffffffL;
                        int at = code + Code.u32(exe, r + 16);
                        if (at < 0 || at >= exe.length) {
                            break;
                        }
                        int end = at;
                        while (end < exe.length && exe[end] != 0) {
                            end++;
                        }
                        String name = new String(exe, at, end - at).toLowerCase();
                        out.put(name, ((long) disk << 32) | check);
                    }
                    return out;
                }
            }
        }
        return out;
    }

    /** Offset of the first code hunk's contents in an AmigaDOS executable. */
    private static int hunkCode(byte[] exe) {
        int p = 4;
        while (p + 4 <= exe.length && Code.u32(exe, p) != 0) {
            p += 4 + 4 * Code.u32(exe, p);
        }
        p += 4;
        if (p + 12 > exe.length) {
            return -1;
        }
        int first = Code.u32(exe, p + 4);
        int last = Code.u32(exe, p + 8);
        p += 12 + 4 * (last - first + 1);
        if (p + 8 > exe.length || (Code.u32(exe, p) & 0x3fffffff) != 0x3e9) {
            return -1;
        }
        return p + 8;
    }

    private static int indexOf(byte[] data, byte[] what, int from) {
        outer:
        for (int i = from; i + what.length <= data.length; i++) {
            for (int k = 0; k < what.length; k++) {
                if (Character.toLowerCase(data[i + k]) != what[k]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
