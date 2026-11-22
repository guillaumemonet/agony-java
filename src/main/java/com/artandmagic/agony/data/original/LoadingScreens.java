package com.artandmagic.agony.data.original;

import com.artandmagic.agony.data.Picture;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The loading screens that come between the levels, read from their programs.
 *
 * <p>No source of those programs is in the release; what follows comes from
 * reading the six of them on the disks ({@code Agony.08}, {@code 0A},
 * {@code 0C}, {@code 10}, {@code 12} and {@code 14}), which share one piece of
 * code and differ only in their data.  Each one is assembled at a fixed address
 * and does three things worth knowing about:
 *
 * <ul>
 *   <li>it points the copper at a list of its own, whose {@code BPLxPT} moves it
 *       fills in at run time from {@code move.l #planes,d0}, adding
 *       {@code addi.l #stride,d0} from one plane to the next;</li>
 *   <li>it fades in from a table of thirty two colours it reaches with
 *       {@code lea palette,a0}, multiplying every component by a step from 0
 *       to 16 and shifting it down by four;</li>
 *   <li>it loads the next level with the same {@code LOAD} sequence as the rest
 *       of the game, {@code move #disk,d6 / move.l #code,d7 / move #file,d0},
 *       which names the file the picture announces.</li>
 * </ul>
 *
 * <p>The copper list carries the rest: {@code DIWSTRT}, {@code DIWSTOP},
 * {@code DDFSTRT}, {@code DDFSTOP} and {@code BPLCON0}, which make the picture a
 * 352 by 290 lores overscan screen of six planes, extra half brite.  The planes
 * sit in the program as they are, one after the other, the palette right
 * behind them.  Nothing here is keyed on a file name or an address: everything
 * is read from the instructions, so a different release of the disks with the
 * code moved about still decodes.
 */
public final class LoadingScreens {

    private LoadingScreens() {
    }

    /** One loading screen, and the file it loads once it is done. */
    public record Screen(String file, String next, Picture picture) {
    }

    /**
     * Scans a folder of disk images.
     *
     * @return the loading screens, keyed by the file they load next
     */
    public static Map<String, Screen> scan(Path folder) {
        return scan(Disks.read(folder));
    }

    /** Finds the loading screens among the files of the disks. */
    public static Map<String, Screen> scan(Disks disks) {
        Map<String, Screen> out = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> file : disks.files().entrySet()) {
            // The loading programs are a hundred kilobytes or so unpacked;
            // the title, the ending and the levels are well over that.
            int size = file.getValue().length;
            if (size < 50_000 || size > 150_000) {
                continue;
            }
            try {
                Screen s = decode(file.getKey(), file.getValue());
                if (s != null) {
                    out.putIfAbsent(s.next(), s);
                }
            } catch (RuntimeException e) {
                // Not a loading screen.
            }
        }
        return out;
    }

    /** Decodes one program, or returns null when it is not a loading screen. */
    public static Screen decode(String file, byte[] p) {
        Code.Origin origin = Code.origin(p);
        if (origin == null) {
            return null;
        }
        int org = origin.org();
        Code.Screen shape = Code.screen(p, origin.copper());
        if (shape == null) {
            return null;
        }
        int rowBytes = shape.width() / 8;

        // move.l #planes,d0 followed by the bsr that writes the pointers, and
        // the addi.l that steps from one plane to the next.
        int planesAt = Code.operandBefore(p, 0x203c, 0x6100, org);
        int stride = Code.operand(p, 0x0680, org, false);
        if (planesAt < 0 || stride != rowBytes * shape.height()
                || planesAt + shape.planes() * stride > p.length) {
            return null;
        }

        // lea palette,a0 / moveq #0,d0 / bsr: the fade in.
        int fade = Code.find(p, 0, 0x41f9, -1, -1, 0x7000, 0x6100);
        if (fade < 0) {
            return null;
        }
        int paletteAt = Code.u32(p, fade + 2) - org;
        if (paletteAt < 0 || paletteAt + 64 > p.length) {
            return null;
        }
        int[] palette = new int[32];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = Code.u16(p, paletteAt + i * 2) & 0x0fff;
        }

        // LOAD: move #disk,d6 / move.l #code,d7 / move #file,d0.  The title
        // program loads a file too, so the screen is only a loading screen
        // when that file is the one thing it does after the picture.
        int load = Code.find(p, 0, 0x3c3c, -1, 0x2e3c, -1, -1, 0x303c);
        if (load < 0) {
            return null;
        }
        String next = String.format("Agony.%02X", Code.u16(p, load + 12));

        byte[] pixels = Picture.chunky(p, planesAt, stride, rowBytes,
                shape.width(), shape.height(), shape.planes());
        boolean ehb = shape.planes() == 6;
        return new Screen(file, next, new Picture(shape.width(), shape.height(), pixels, palette, ehb));
    }
}
