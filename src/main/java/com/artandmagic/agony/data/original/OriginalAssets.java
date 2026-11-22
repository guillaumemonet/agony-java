package com.artandmagic.agony.data.original;

import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.game.routines.Routines;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Finds the real game data on a set of Agony disk images and converts it.
 *
 * <p>The three disks hold the overlays {@code Agony.00} to {@code Agony.14}, all
 * PowerPacked.  Six of them are level programs, recognisable because they carry
 * the whole engine; which level each one is follows from the space
 * {@code Sprites_Mask} reserves, a different figure in every level.
 */
public final class OriginalAssets {

    private OriginalAssets() {
    }

    /** One level found on the disks. */
    public record Found(int level, String file, AgonyBinary binary) {
    }

    /** Scans a folder of .adf images and returns the level overlays it holds. */
    public static List<Found> scan(Path folder) {
        return scan(Disks.read(folder));
    }

    /** Returns the level overlays among the files of the disks. */
    public static List<Found> scan(Disks disks) {
        TreeMap<Integer, Found> byLevel = new TreeMap<>();
        for (Map.Entry<String, byte[]> file : disks.files().entrySet()) {
            // A level overlay is a good 300 kilobytes once unpacked.
            if (file.getValue().length < 300_000) {
                continue;
            }
            try {
                AgonyBinary binary = new AgonyBinary(file.getValue());
                if (binary.level > 0 && !byLevel.containsKey(binary.level)) {
                    byLevel.put(binary.level, new Found(binary.level, file.getKey(), binary));
                }
            } catch (RuntimeException e) {
                // Not a level overlay: the intro, the title or the ending.
            }
        }
        return new ArrayList<>(byLevel.values());
    }

    /**
     * Converts one of the levels found by {@link #scan}.
     *
     * @param found    the level to convert
     * @param fallback generated artwork, used for anything not converted
     */
    public static OriginalLevel.Result convert(Found found, GameAssets fallback) {
        RoutineLibrary routines = Routines.forLevel(found.level(), found.binary());
        return OriginalLevel.convert(found.binary(), fallback, routines);
    }
}
