package com.artandmagic.agony.data.original;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Finds the Protracker modules that sit inside the Agony overlays.
 *
 * <p>{@code Ag_Pt_Player.s} is the Protracker V1.1B replayer and {@code mt_init}
 * walks {@code Pt_Module} exactly as it would walk a module file: the order
 * table at 952, the patterns at 1084 and the sample data after them.  The only
 * thing the in memory copy lacks is the four byte tag a file carries at 1080,
 * so putting {@code M.K.} back is enough to hand the block to an ordinary
 * replayer.
 *
 * <p>The disks hold the intro music, the end of game music and one loading tune
 * per level.  Loose module files may sit beside the project as well, and those
 * carry what the disks of this release do not: the in game music, credited to
 * Matthew Simmonds and Martin Iveson.  A loose module wins over a disk one.
 */
public final class OriginalMusic {

    private OriginalMusic() {
    }

    /** One module found on the disks. */
    public record Module(String name, String file, byte[] data) {
    }

    /**
     * Scans a folder of disk images for modules.
     *
     * @return the modules found, keyed by their name, in the order met
     */
    public static Map<String, Module> scan(Path folder) {
        return scan(Disks.read(folder));
    }

    /** Finds the modules among the files of the disks. */
    public static Map<String, Module> scan(Disks disks) {
        Map<String, Module> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> file : disks.files().entrySet()) {
            if (file.getValue().length < 10_000) {
                continue;
            }
            for (Module m : find(file.getValue(), file.getKey())) {
                out.putIfAbsent(m.name().toLowerCase(), m);
            }
        }
        return out;
    }

    /** Finds every module inside one decrunched overlay. */
    public static List<Module> find(byte[] data, String file) {
        List<Module> out = new ArrayList<>();
        for (int s = 0; s + 1084 < data.length; s++) {
            if (!printableName(data, s)) {
                continue;
            }
            int songLength = data[s + 950] & 0xff;
            if (songLength < 1 || songLength > 128) {
                continue;
            }

            int patterns = 0;
            boolean orderOk = true;
            for (int i = 0; i < 128; i++) {
                int v = data[s + 952 + i] & 0xff;
                if (v >= 64) {
                    orderOk = false;
                    break;
                }
                patterns = Math.max(patterns, v + 1);
            }
            if (!orderOk) {
                continue;
            }

            int sampleBytes = 0;
            int used = 0;
            boolean headersOk = true;
            for (int k = 0; k < 31; k++) {
                int e = s + 20 + k * 30;
                int length = word(data, e + 22) * 2;
                if ((data[e + 24] & 0xff) > 15 || (data[e + 25] & 0xff) > 64) {
                    headersOk = false;
                    break;
                }
                sampleBytes += length;
                if (length > 2) {
                    used++;
                }
            }
            if (!headersOk || used < 3 || sampleBytes < 8000) {
                continue;
            }

            int total = 1084 + patterns * 1024 + sampleBytes;
            if (s + total > data.length) {
                continue;
            }

            byte[] module = new byte[total];
            System.arraycopy(data, s, module, 0, total);
            // Put the tag back so an ordinary replayer accepts the block.
            module[1080] = 'M';
            module[1081] = '.';
            module[1082] = 'K';
            module[1083] = '.';

            out.add(new Module(name(data, s), file, module));
            s += total - 1;
        }
        return out;
    }

    /** The six levels, in the order {@code ASM_Level} numbers them. */
    private static final String[] LEVEL_NAMES = {
        "", "sea", "forest", "marshes", "mountains", "highlands", "fire"
    };

    /**
     * The music for a level: what plays during the level if it is there, the
     * tune of its loading screen otherwise, and the intro as a last resort.
     */
    public static Module forLevel(Map<String, Module> modules, int level) {
        if (level >= 1 && level < LEVEL_NAMES.length) {
            String name = LEVEL_NAMES[level];
            Module m = modules.get("level_" + name);
            if (m == null) {
                m = modules.get("loading_" + name);
            }
            if (m == null && level == 4) {
                // The disk names this one differently from the rest.
                m = modules.get("mod.loading_mountain");
            }
            if (m != null) {
                return m;
            }
        }
        return modules.get("mod.agony intro");
    }

    /**
     * Adds the modules of a folder of loose files, keyed by their file name
     * with any {@code mod.} prefix removed.
     */
    public static void addFolder(Map<String, Module> modules, Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile).sorted().forEach(file -> {
                byte[] data;
                try {
                    data = Files.readAllBytes(file);
                } catch (IOException e) {
                    return;
                }
                if (data.length < 1084 || !tagged(data)) {
                    return;
                }
                String key = file.getFileName().toString().toLowerCase();
                if (key.startsWith("mod.")) {
                    key = key.substring(4);
                }
                modules.put(key, new Module(name(data, 0), file.getFileName().toString(), data));
            });
        } catch (IOException e) {
            // Nothing to add.
        }
    }

    private static boolean tagged(byte[] data) {
        String tag = new String(data, 1080, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        return tag.equals("M.K.") || tag.equals("M!K!") || tag.equals("4CHN") || tag.equals("FLT4");
    }

    private static boolean printableName(byte[] data, int s) {
        int letters = 0;
        for (int i = 0; i < 20; i++) {
            int c = data[s + i] & 0xff;
            if (c == 0) {
                continue;
            }
            if (c < 32 || c > 126) {
                return false;
            }
            letters++;
        }
        return letters >= 4;
    }

    private static String name(byte[] data, int s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            int c = data[s + i] & 0xff;
            if (c == 0) {
                break;
            }
            sb.append((char) c);
        }
        return sb.toString().trim();
    }

    private static int word(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }
}
