package com.artandmagic.agony.data.original;

import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.Sprite;
import com.artandmagic.agony.data.AttackWave;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.LevelData;
import com.artandmagic.agony.data.ObjectDef;
import com.artandmagic.agony.data.SpriteFrame;
import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.data.StartList;
import com.artandmagic.agony.data.TrackTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a located level overlay into the tables the engine runs on.
 *
 * <p>Everything here is a straight transcription of a format the sources
 * describe: a front scroll character is three bitplanes of 32 by 32, an object
 * is a list of blitter parts and collision rectangles, an attack wave is a
 * 32 byte {@code AWS} record, and the level script is the stream of long words
 * {@code Start_list} holds.
 */
public final class OriginalLevel {

    /** Raster line the playfield starts on, DIWSTRT $2d90 plus the status bar. */
    private static final int FIRST_LINE = 0x3f;

    /** Bytes per {@code Sprites_Struct} entry. */
    private static final int SST_ENTRY = 26;
    /** Bytes per front scroll character: three planes of 32 by 32. */
    private static final int FRONT_CHAR = 384;
    /** Bytes of an {@code ATTACK_WAVE_START} record. */
    private static final int AWS_LEN = 32;
    /** Bytes of one track slot, {@code TRACK STRUCT}. */
    private static final int TRACK_LEN = 144;

    private final AgonyBinary bin;
    private final LevelData level = new LevelData();
    private final GameAssets assets;
    private final RoutineLibrary routines;

    /** Movement tables, one per attack wave, indexed as they are built. */
    private final List<TrackTable> tracks = new ArrayList<>();
    private final Map<Integer, Integer> waveIndex = new LinkedHashMap<>();
    private final List<AttackWave> waves = new ArrayList<>();

    private OriginalLevel(AgonyBinary bin, GameAssets fallback, RoutineLibrary routines) {
        this.bin = bin;
        this.assets = fallback;
        this.routines = routines;
    }

    /**
     * Converts a decrunched overlay.
     *
     * @param binary   the located overlay
     * @param fallback generated artwork, used for anything not converted yet
     */
    public static Result convert(AgonyBinary binary, GameAssets fallback) {
        return convert(binary, fallback, RoutineLibrary.NONE);
    }

    public static Result convert(AgonyBinary binary, GameAssets fallback,
                                 RoutineLibrary routines) {
        OriginalLevel c = new OriginalLevel(binary, fallback, routines);
        c.frontScroll();
        c.backScroll();
        c.objects();
        c.script();
        c.statusPanel();
        c.playerSprites();
        c.sounds();
        c.tables();
        c.copperGradients();
        c.spellSprites();
        c.level.name = switch (binary.level) {
            case 1 -> "SEA";
            case 2 -> "FOREST";
            case 3 -> "MARSHES";
            case 4 -> "MOUNTAINS";
            case 5 -> "HIGHLANDS";
            case 6 -> "FIRE";
            default -> "AGONY";
        };
        c.level.rain = binary.level == 1;
        return new Result(c.level, c.assets);
    }

    /** What the conversion produced. */
    public record Result(LevelData level, GameAssets assets) {
    }

    // ----------------------------------------------------------- front scroll

    private void frontScroll() {
        int charset = bin.address("Front_Charset");
        int count = (bin.address("Sprites_Bitmap") - charset) / FRONT_CHAR;
        Sprite[] chars = new Sprite[Math.max(1, count)];
        for (int i = 0; i < count; i++) {
            chars[i] = planar(charset + i * FRONT_CHAR, 32, 32, 3);
        }
        level.frontChars = chars;

        // Both bytes of every cell are kept: the character laid down flat and
        // the one CHAR COPY cuts over it.
        int columns = bin.blocks().getOrDefault("Front_Map_Columns", 0);
        int map = bin.address("Front_Map");
        int cell = LevelData.FRONT_CELL;
        byte[] cells = new byte[columns * LevelData.FRONT_ROWS * cell];
        for (int c = 0; c < columns; c++) {
            for (int r = 0; r < LevelData.FRONT_ROWS; r++) {
                int at = (c * LevelData.FRONT_ROWS + r) * cell;
                cells[at] = (byte) bin.u8(map + c * 12 + r * 2);
                cells[at + 1] = (byte) bin.u8(map + c * 12 + r * 2 + 1);
            }
        }
        level.frontMap = cells;

        int steps = bin.blocks().getOrDefault("Front_Pal_Steps", 0);
        int pal = bin.has("Front_Pal") ? bin.address("Front_Pal") : 0;
        int[][] palettes = new int[Math.max(1, steps)][];
        for (int s = 0; s < steps; s++) {
            int[] colours = new int[LevelData.FRONT_ROWS * 7];
            for (int i = 0; i < colours.length; i++) {
                colours[i] = bin.u16(pal + s * 84 + i * 2);
            }
            palettes[s] = colours;
        }
        if (steps == 0) {
            palettes[0] = new int[LevelData.FRONT_ROWS * 7];
        }
        level.frontPalettes = palettes;
    }

    private Sprite planar(int address, int width, int height, int planes) {
        return planar(address, width, height, planes, 0, 0);
    }

    /**
     * Reads a planar image and returns it as one colour index per pixel.  The
     * colour base is added to every pixel the image actually covers, which is
     * how a three colour object reaches the upper half of the palette.
     *
     * <p>{@code pad} is the number of bytes every plane but the first carries
     * past the end of its rows.  It is zero for everything the blitter reads
     * with a modulo of its own, and two for the second plane of a three colour
     * object: {@code N_Blit0} clears {@code Bltbmod}, so that plane has to hold
     * the whole blit width, one word wider than the image, while the first gets
     * the {@code Bltamod} of -2 that steps it back onto its own rows.
     */
    private Sprite planar(int address, int width, int height, int planes,
                          int colourBase, int pad) {
        Sprite s = new Sprite(width, height);
        int bytesPerRow = width / 8;
        int[] planeAt = new int[planes];
        int[] stride = new int[planes];
        int at = 0;
        for (int p = 0; p < planes; p++) {
            stride[p] = bytesPerRow + (p > 0 ? pad : 0);
            planeAt[p] = at;
            at += stride[p] * height;
        }
        for (int p = 0; p < planes; p++) {
            for (int y = 0; y < height; y++) {
                for (int b = 0; b < bytesPerRow; b++) {
                    int value = bin.u8(address + planeAt[p] + y * stride[p] + b);
                    for (int bit = 0; bit < 8; bit++) {
                        if ((value & (0x80 >> bit)) != 0) {
                            int x = b * 8 + bit;
                            s.pixels[y * width + x] |= (byte) (1 << p);
                        }
                    }
                }
            }
        }
        if (colourBase != 0) {
            for (int i = 0; i < s.pixels.length; i++) {
                if (s.pixels[i] != 0) {
                    s.pixels[i] += (byte) colourBase;
                }
            }
        }
        return s;
    }

    // ------------------------------------------------------------ back scroll

    /**
     * The back playfield: sixteen patterns of five columns, each carrying its
     * own three colours per band.
     *
     * <p>{@code Back_Pattern} opens with sixteen word offsets, one per pattern;
     * a pattern is 48 bytes of colour, six bands of a background plus three
     * shades, followed by pairs of (frame, delay) ended by a negative frame.  A
     * frame is 30 character numbers in {@code Back_Frame}, five columns of six,
     * which is exactly the half screen the original redraws on each of its two
     * phases.  {@code Back_Char_Info} then says, for every character, how its
     * one or two stored bitplanes feed the two planes of the playfield.
     */
    private void backScroll() {
        if (!bin.has("Back_Charset") || !bin.has("Back_Char_Info") || !bin.has("Back_Pattern")) {
            return;
        }
        int info = bin.address("Back_Char_Info");
        int offsets = info + bin.u16(info);
        int charset = bin.address("Back_Charset");
        int frames = bin.address("Back_Frame");
        int patterns = bin.address("Back_Pattern");

        int charCount = Math.min(256, (offsets - info - 2));
        Sprite[] chars = new Sprite[charCount];
        for (int i = 0; i < charCount; i++) {
            chars[i] = backChar(charset + bin.u16(offsets + i * 2), bin.u8(info + 2 + i));
        }
        level.backChars = chars;

        sky();

        List<LevelData.BackPattern> list = new ArrayList<>();
        int[] sequence = new int[17];
        for (int i = 0; i < 16; i++) {
            int block = patterns + bin.u16(patterns + i * 2);
            LevelData.BackPattern pattern = new LevelData.BackPattern();

            for (int band = 0; band < 6; band++) {
                pattern.background[band] = bin.u16(block + band * 8);
                for (int k = 0; k < 3; k++) {
                    pattern.colors[band][k] = bin.u16(block + band * 8 + 2 + k * 2);
                }
                // Playfield 2 shows the sky only where the back scroll is empty.
                pattern.sky[band] = new int[]{
                    pattern.background[band], pattern.colors[band][0]
                };
            }

            // (frame, delay) pairs, ended by a negative frame number.
            List<Integer> seq = new ArrayList<>();
            for (int k = 0; k < 64; k++) {
                int frame = bin.u8(block + 48 + k * 2);
                if ((frame & 0x80) != 0) {
                    break;
                }
                seq.add(frame);
                seq.add(Math.max(1, bin.u8(block + 49 + k * 2)));
            }
            pattern.sequence = seq.stream().mapToInt(Integer::intValue).toArray();
            list.add(pattern);
            sequence[i] = i;
        }
        sequence[16] = -1;
        level.backPatterns = list.toArray(new LevelData.BackPattern[0]);
        level.backSequence = sequence;

        // Every frame of Back.frm, thirty character numbers each.
        int frameCount = (bin.address("Back_Pattern") - frames) / 60;
        int[] cells = new int[frameCount * 30];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = bin.u16(frames + i * 2);
        }
        level.backFrames = cells;

        // Back_Read.bin: five configurations of two phases of thirty
        // (layer, offset) pairs.
        int read = bin.address("Read_Table");
        int[] table = new int[5 * 2 * 30 * 2];
        for (int i = 0; i < table.length; i++) {
            table[i] = bin.u16(read + i * 2);
        }
        level.backReadTable = table;

        // Write_Table: ten groups of six, one per column, giving (column, band).
        int[] write = new int[60 * 2];
        for (int i = 0; i < 60; i++) {
            write[i * 2] = i / 6;
            write[i * 2 + 1] = i % 6;
        }
        level.backWriteTable = write;
    }

    /**
     * The sky plane, the TRANSFER 1/2 OF SKY block of {@code Ag_Back_Scroll.s}.
     *
     * <p>{@code Sky_Anim_Table} holds four words per animation step, and the
     * four blits that read them run on one after the other without the
     * destination being reset, so they lay four bands of forty lines down the
     * sky bitplane.  Each blit is nineteen words wide with an A modulo of -2,
     * which steps the source on by thirty six bytes a line while it reads
     * thirty eight of them: every line starts two bytes back into the one
     * above, and that is what slants the band.
     *
     * <p>Only level five moves {@code Sky_Count} on; everywhere else the count
     * is put back to zero the moment it reaches one, so the sky keeps the first
     * step and never animates.
     */
    private void sky() {
        if (!bin.has("Sky_Dat") || !bin.has("Sky_Anim_Table")) {
            return;
        }
        int anim = bin.address("Sky_Anim_Table");
        int data = bin.address("Sky_Dat");
        byte[] plane = new byte[Hw.BACK_W * Hw.SCREEN_H];

        // Nineteen words reach the screen, the width of the data fetch.
        int fetched = 19 * 16;
        for (int band = 0; band < 4; band++) {
            int source = data + bin.u16(anim + band * 2);
            int top = band * 40;
            for (int row = 0; row < 40; row++) {
                int y = top + row;
                if (y >= Hw.SCREEN_H) {
                    break;
                }
                int line = source + row * 36;
                for (int x = 0; x < fetched && x < Hw.BACK_W; x++) {
                    if ((bin.u8(line + (x >> 3)) & (0x80 >> (x & 7))) != 0) {
                        plane[y * Hw.BACK_W + x] = 1;
                    }
                }
            }
        }
        level.skyBitmap = plane;
    }

    /**
     * One back scroll character.  The mode says how the stored planes reach the
     * two planes of playfield 2: 0 keeps both, 1 repeats the first in both, 2
     * and 3 leave one of them empty.
     */
    private Sprite backChar(int address, int mode) {
        Sprite plane0 = planar(address, 32, 32, 1);
        Sprite out = new Sprite(32, 32);
        Sprite plane1 = mode == 0 ? planar(address + 128, 32, 32, 1) : null;
        for (int i = 0; i < out.pixels.length; i++) {
            int a = plane0.pixels[i] & 1;
            int v = switch (mode) {
                case 0 -> a | ((plane1.pixels[i] & 1) << 1);
                case 1 -> a | (a << 1);
                case 2 -> a << 1;
                default -> a;
            };
            out.pixels[i] = (byte) v;
        }
        return out;
    }

    // ---------------------------------------------------------------- objects

    private void objects() {
        int sst = bin.address("Sprites_Struct");
        int bitmap = bin.address("Sprites_Bitmap");
        // Sprites_Struct is followed by AWO_Struct, the mask buffer and the four
        // blit routine tables, and X44 closes that run.
        int count = bin.has("X44")
                ? (bin.address("X44") - sst - (32 * 196 + 512 + 4 * 48)) / SST_ENTRY
                : 0;

        Sprite[] sprites = new Sprite[Math.max(1, count)];
        for (int i = 0; i < count; i++) {
            int e = sst + i * SST_ENTRY;
            // The blit class says everything about the stored image: its size,
            // how many bitplanes it keeps and which half of the front palette
            // it is drawn in.  N_Blit0 to N_Blit3 are 32x32, 64x32, 32x64 and
            // 64x64 in three colours, N_Blit4 to N_Blit7 the same in the other
            // three, and N_Blit8 to N_Blit11 the same in seven.
            int blitClass = bin.u16(e + 4);
            int form = blitClass & 3;
            int w = (form == 1 || form == 3) ? 64 : 32;
            int h = (form == 2 || form == 3) ? 64 : 32;
            int planes = blitClass >= 8 ? 3 : 2;
            int colourBase = (blitClass >= 4 && blitClass < 8) ? 4 : 0;

            // A three colour object keeps its second plane one word wider than
            // the image; a seven colour one keeps all three plain.
            int pad = planes == 2 ? 2 : 0;

            int data = bitmap + bin.u32(e + 18);
            sprites[i] = blitClass <= 11
                    ? planar(data, w, h, planes, colourBase, pad)
                    : new Sprite(1, 1);
        }
        level.objectSprites = sprites;
        level.firstExploSprite = 0;
        // Anything the attack waves do not name is read when it is asked for.
        level.objectDecoder = this::objectAt;
    }

    /**
     * Reads one object record: a word offset to the collision rectangles, the
     * muzzle the alien fires from, then the blitter parts.
     */
    private ObjectDef objectAt(int objOff) {
        int base = bin.address("Objects_Struct") + objOff;
        int rectOff = bin.u16(base);

        List<ObjectDef.Part> parts = new ArrayList<>();
        int p = base + 4;
        for (int i = 0; i < 64; i++) {
            if (bin.u8(p + 1) == 0x80) {
                break;
            }
            parts.add(new ObjectDef.Part(bin.s8(p), bin.s8(p + 1), bin.u8(p + 2)));
            p += 3;
        }

        List<ObjectDef.Rect> rects = new ArrayList<>();
        int r = base + rectOff;
        for (int i = 0; i < 32; i++) {
            if (bin.u8(r) == 0x80) {
                break;
            }
            rects.add(new ObjectDef.Rect(bin.s8(r), bin.s8(r + 1), bin.s8(r + 2), bin.s8(r + 3)));
            r += 4;
        }

        return new ObjectDef(bin.s8(base + 2), bin.s8(base + 3),
                rects.toArray(new ObjectDef.Rect[0]), parts.toArray(new ObjectDef.Part[0]));
    }

    /** Reads an animation: object offsets until the original's negative word. */
    private int[] animationAt(int animOff) {
        int base = bin.address("Anim_Base") + animOff;
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            int v = bin.u16(base + i * 2);
            if ((v & 0x8000) != 0) {
                break;
            }
            out.add(v);
        }
        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    // ----------------------------------------------------------- level script

    private void script() {
        Map<Integer, ObjectDef> objects = new HashMap<>();
        Map<Integer, int[]> animations = new HashMap<>();
        StartList list = new StartList();

        int p = bin.address("Start_list");
        int trigger = bin.u16(p);
        p += 2;

        for (int i = 0; i < 512; i++) {
            // SL_End writes $7fff as the trigger of the entry that never fires.
            if (trigger == 0x7fff) {
                break;
            }
            int pointer = bin.u32(p);
            p += 4;
            if (pointer == 0) {
                break;
            }
            boolean routine = (pointer & 0x8000_0000L) != 0;
            boolean absolute = (pointer & 0x4000_0000) != 0;
            int aws = pointer & 0x3fff_ffff;

            int startX = 0;
            int startY = 0;
            if (absolute) {
                startX = bin.u16(p);
                startY = bin.u16(p + 2);
                p += 4;
            }

            if (!routine && (aws < bin.address("Start_list") || aws >= bin.relStart)) {
                break;
            }
            if (routine) {
                // Start_C: the parameters follow, ended by Par_End of -1.
                List<Integer> values = new ArrayList<>();
                while (bin.u16(p) != 0xffff && values.size() < 16) {
                    values.add(bin.u16(p));
                    p += 2;
                }
                p += 2;
                var implementation = routines.forAddress(aws);
                if (implementation != null) {
                    list.routine(trigger, implementation,
                            values.stream().mapToInt(Integer::intValue).toArray());
                }
            } else if (aws >= bin.address("Start_list") && aws < bin.relStart) {
                Integer index = waveIndex.get(aws);
                if (index == null) {
                    index = waves.size();
                    waveIndex.put(aws, index);
                    waves.add(readWave(aws, absolute, startX, startY, objects, animations));
                }
                list.wave(trigger, index);
            }

            trigger = bin.u16(p);
            p += 2;
        }

        // The waves a routine launches by itself are converted the same way and
        // appended, so the engine cannot tell them from the script's own.
        Map<Integer, Integer> routineWaves = new LinkedHashMap<>();
        for (int address : routines.waveAddresses()) {
            Integer index = waveIndex.get(address);
            if (index == null) {
                index = waves.size();
                waveIndex.put(address, index);
                waves.add(readWave(address, true, 0, 0, objects, animations));
            }
            routineWaves.put(address, index);
        }
        routines.bindWaves(routineWaves);
        level.routineEndsLevel = routines.endsLevel();

        level.objects = objects;
        level.animations = animations;
        level.waves = waves.toArray(new AttackWave[0]);
        level.tracks = tracks.toArray(new TrackTable[0]);
        level.startList = list;
    }

    private AttackWave readWave(int address, boolean absolute, int startX, int startY,
                                Map<Integer, ObjectDef> objects, Map<Integer, int[]> animations) {
        AttackWave w = new AttackWave();
        int xOff = bin.u16(address);
        int yOff = bin.u16(address + 2);
        int objOff = bin.u16(address + 4);
        w.alienRate = bin.u8(address + 6);
        w.alienNum = Math.max(1, Math.min(16, bin.u8(address + 7)));
        w.alienEnergy = bin.u16(address + 8);
        w.alienBadFireRate = bin.u8(address + 10);
        w.alienFireRate = bin.u8(address + 11);
        for (int i = 0; i < 6; i++) {
            // tst.b / bpl: a band whose flag is not negative is recoloured.
            w.palModLine[i] = bin.s8(address + 12 + i) >= 0;
        }
        for (int i = 0; i < 7; i++) {
            w.palModCol[i] = bin.s16(address + 18 + i * 2);
        }
        w.absolute = absolute;
        w.startX = startX;
        w.startY = startY;

        w.animation = objOff;
        animations.computeIfAbsent(objOff, this::animationAt);
        for (int obj : animations.getOrDefault(objOff, new int[0])) {
            objects.computeIfAbsent(obj, this::objectAt);
        }

        w.track = tracks.size();
        tracks.add(readTrack(xOff, yOff, absolute));
        return w;
    }

    /**
     * Decodes a movement table: signed four bit deltas, two per byte.
     *
     * <p>The two kinds differ in more than their name.  A relative track opens
     * with the word the first alien starts at and the follower reads its deltas
     * one nibble further in, at {@code pos + 1}, subtracting two from every dx
     * so that the formation drifts with the scroll.  An absolute track has no
     * start word, takes its position from the start list entry and reads its
     * deltas at {@code pos - 1}.
     */
    private TrackTable readTrack(int xOff, int yOff, boolean absolute) {
        String label = absolute ? "Absolute_Tracks" : "Relative_Tracks";
        if (!bin.has(label)) {
            label = "Relative_Tracks";
        }
        int base = bin.has(label) ? bin.address(label) : 0;
        int xs = base + xOff;
        int ys = base + yOff;
        int shift = absolute ? -1 : 1;

        int max = 1024;
        byte[] dx = new byte[max];
        byte[] dy = new byte[max];
        int length = 1;
        for (int pos = 1; pos < max; pos++) {
            int nibble = pos + shift;
            int vx = nibbleAt(xs, nibble);
            int vy = nibbleAt(ys, nibble);
            dx[pos] = (byte) vx;
            dy[pos] = (byte) vy;
            length = pos + 1;
            if (vx == TrackTable.END) {
                break;
            }
        }
        byte[] tx = java.util.Arrays.copyOf(dx, length);
        byte[] ty = java.util.Arrays.copyOf(dy, length);

        int sx = absolute ? 0 : bin.u16(xs);
        int sy = absolute ? 0 : bin.u16(ys);
        return new TrackTable(sx, sy, tx, ty, !absolute);
    }

    private int nibbleAt(int address, int index) {
        int b = bin.u8(address + (index >> 1));
        int v = (index & 1) == 0 ? (b >> 4) : (b & 0xf);
        return (v & 8) != 0 ? v - 16 : v;
    }

    // ------------------------------------------------------------ status panel

    private void statusPanel() {
        if (!bin.has("Status_Digit") || !bin.has("Text_Dat")) {
            return;
        }
        int digit = bin.address("Status_Digit");
        int glyphs = (bin.address("Text_Dat") - digit) / 16;
        byte[][] font = new byte[Math.max(64, glyphs)][];
        for (int i = 0; i < font.length; i++) {
            byte[] rows = new byte[16];
            if (i < glyphs) {
                for (int y = 0; y < 16; y++) {
                    rows[y] = (byte) bin.u8(digit + i * 16 + y);
                }
            }
            font[i] = rows;
        }
        assets.font = font;

        // Text.bin: a word offset per sentence, then font indices ended by a
        // negative byte.  The port keeps them as indices, so the panel can draw
        // them without knowing which glyph is which letter.
        int text = bin.address("Text_Dat");
        int[][] sentences = new int[16][];
        for (int i = 1; i < sentences.length; i++) {
            int off = bin.u16(text + (i - 1) * 2);
            if (off == 0 || off > 0x4000) {
                continue;
            }
            int[] buffer = new int[64];
            int n = 0;
            while (n < buffer.length) {
                int c = bin.u8(text + off + n);
                if ((c & 0x80) != 0) {
                    break;
                }
                buffer[n++] = c;
            }
            sentences[i] = java.util.Arrays.copyOf(buffer, n);
        }
        assets.textIndices = sentences;
    }

    /**
     * The four sound effects.  {@code Sound_Dat} opens with one eight byte entry
     * per effect, a long offset then the length in bytes, which is exactly what
     * {@code Ag_Sounds.s} feeds to AUD0LC and AUD0LEN.
     */
    private void sounds() {
        if (!bin.has("Sound_Dat")) {
            return;
        }
        int table = bin.address("Sound_Dat");
        for (int i = 0; i < 4; i++) {
            int offset = bin.u32(table + i * 8);
            int length = bin.u16(table + i * 8 + 4);
            if (offset <= 0 || length <= 0 || length > 200_000) {
                continue;
            }
            assets.soundSamples[i] = bin.slice(table + offset, length);
        }
    }

    // ----------------------------------------------------------- player sprites

    /**
     * The sorcerer occupies four sprite channels, attached in pairs, and each of
     * his sixteen frames names four blocks of sprite data.  A block holds the
     * upper axe, then the body, then the lower axe, each behind its two control
     * words.
     */
    private void playerSprites() {
        if (!bin.has("Sorcerer_Dat")) {
            return;
        }
        assets.sorcerer = sorcererFrames(bin.address("Sorcerer_Dat"));
        if (bin.has("Sorcerer2_Dat")) {
            assets.sorcerer2 = ghostFrames(bin.address("Sorcerer2_Dat"));
        }
        if (bin.has("Sorcerer_Pal")) {
            assets.sorcererPalette = palette(bin.address("Sorcerer_Pal"));
        }
        if (bin.has("Icones_Pal")) {
            assets.iconesPalette = palette(bin.address("Icones_Pal"));
        }
        assets.axe = axeFrame(bin.address("Sorcerer_Dat"));
        weaponSprites();
    }

    /**
     * The shots, the bonuses, the death pieces and the spell menu.
     *
     * <p>All of them are hardware sprite blocks: two control words followed by
     * two bitplane words per line.  A lone sprite has three colours, a pair
     * written at the same position is attached and has fifteen.
     */
    private void weaponSprites() {
        // Fw_Fire_Spr holds the four weapon levels back to back, at the offsets
        // the source names: 0, 25*4, 264 and 508.
        if (bin.has("Fw_Fire_Spr")) {
            int base = bin.address("Fw_Fire_Spr");
            int[] at = {0, 100, 264, 508};
            int[] height = {24, 40, 60, 100};
            for (int i = 0; i < 4; i++) {
                assets.forwardFire[i] = single(base + at[i] + 4, height[i]);
            }
        }

        // Four bonus shapes of 800 bytes, each two animation frames of an
        // attached pair 200 bytes apart.
        if (bin.has("Bonus_Spr")) {
            int base = bin.address("Bonus_Spr");
            for (int i = 0; i < 4; i++) {
                assets.bonus[i] = pair(base + i * 800 + 4, base + i * 800 + 204, 48);
            }
        }

        // Twelve slots share one shape, eight lines tall.
        if (bin.has("Alien_Fire_Spr")) {
            assets.alienFire = single(bin.address("Alien_Fire_Spr") + 4, 8);
        }

        // Eight pieces of 33 long words each, one per sprite channel.
        if (bin.has("Die_Spr")) {
            int base = bin.address("Die_Spr");
            for (int i = 0; i < 8; i++) {
                assets.diePieces[i] = single(base + i * 132 + 4, 32);
            }
        }

        // The menu: four sprite blocks of 768 bytes, attached in pairs, holding
        // the eight icons one below the other.
        if (bin.has("Icones_Spr")) {
            int base = bin.address("Icones_Spr");
            for (int i = 0; i < 8; i++) {
                int line = i * 24;
                assets.icons[i] = wideIcon(base, line, 23);
            }
        }
        if (bin.has("Arow_Spr")) {
            int base = bin.address("Arow_Spr");
            assets.arrow = pair(base + 4, base + 72, 16);
            assets.lock = pair(base + 140, base + 208, 15);
        }
    }

    /**
     * The tables the original keeps as data rather than as code: the arc the
     * axes swing along, the eight rotation steps of the rotative fire ball, the
     * wave of the forward fire ball, the fall of the hour glass, the paths the
     * eight death pieces take, the bonus shape table and the rain offsets.
     */
    private void tables() {
        if (bin.has("Axe_Traj")) {
            assets.axeTraj = bytes(bin.address("Axe_Traj"), 96, false);
        }
        if (bin.has("Rot_Table")) {
            assets.rotTable = bytes(bin.address("Rot_Table"), 8 * 16, true);
        }
        if (bin.has("Sin_Table") && bin.has("FBall_3spr0")) {
            int size = bin.address("FBall_3spr0") - bin.address("Sin_Table");
            assets.sinTable = bytes(bin.address("Sin_Table"), size, false);
        }
        if (bin.has("Time_Y_Table")) {
            // The spell runs Time_Y from 1 to $f0 and reads the table at each step.
            assets.timeYTable = bytes(bin.address("Time_Y_Table"), 0x100, false);
        }
        if (bin.has("Bonus_Image")) {
            int[] image = new int[12];
            for (int i = 0; i < image.length; i++) {
                image[i] = bin.u16(bin.address("Bonus_Image") + i * 2);
            }
            assets.bonusImage = image;
        }
        if (bin.has("Rain_X_Table") && bin.has("Rain_Spr0")) {
            int size = bin.address("Rain_Spr0") - bin.address("Rain_X_Table");
            int[] table = new int[size / 2];
            for (int i = 0; i < table.length; i++) {
                table[i] = bin.u16(bin.address("Rain_X_Table") + i * 2);
            }
            assets.rainX = table;
        }
        if (bin.has("Die_Table")) {
            int base = bin.address("Die_Table");
            int[][] paths = new int[8][];
            for (int piece = 0; piece < 8; piece++) {
                int at = base + bin.u16(base + piece * 2);
                List<Integer> path = new ArrayList<>();
                for (int step = 0; step < 512; step++) {
                    int dx = bin.u8(at + step * 2);
                    if (dx == 0x80) {
                        break;
                    }
                    path.add(bin.s8(at + step * 2));
                    path.add(bin.s8(at + step * 2 + 1));
                }
                paths[piece] = path.stream().mapToInt(Integer::intValue).toArray();
            }
            assets.dieTraj = paths;
        }
    }

    /**
     * Walks the copper list and pulls out the two gradients the game paints on.
     *
     * <p>Colour 0, the background, is rewritten on every scanline; the copper
     * sets it just before the display window opens and clears it again near the
     * right edge, so the value that counts is the first one on each line.
     * Colour 9, the sky where nothing covers it, changes every ten lines.  The
     * playfield starts at raster line $3f.
     */
    private void copperGradients() {
        if (!bin.has("Main_Cl")) {
            return;
        }
        int[] background = new int[Hw.SCREEN_H];
        int[] sky = new int[Hw.SCREEN_H];
        boolean[] seen = new boolean[Hw.SCREEN_H];

        int at = bin.address("Main_Cl");
        int line = 0;
        int currentSky = 0;
        int currentBackground = 0;

        for (int step = 0; step < 20000; step++) {
            int w0 = bin.u16(at);
            int w1 = bin.u16(at + 2);
            at += 4;
            if (w0 == 0xffff && w1 == 0xfffe) {
                break;
            }
            if ((w0 & 1) != 0) {
                line = w0 >> 8;
                continue;
            }
            int register = w0 & 0x1fe;
            int y = line - FIRST_LINE;
            if (register == 0x180) {
                if (y >= 0 && y < Hw.SCREEN_H && !seen[y]) {
                    seen[y] = true;
                    background[y] = w1;
                    currentBackground = w1;
                }
            } else if (register == 0x192) {
                currentSky = w1;
                if (y >= 0 && y < Hw.SCREEN_H) {
                    sky[y] = w1;
                }
            }
        }

        // Carry each value forward until the copper changes it again.
        int held = background[0];
        for (int y = 0; y < Hw.SCREEN_H; y++) {
            if (seen[y]) {
                held = background[y];
            } else {
                background[y] = held;
            }
        }
        held = 0;
        for (int y = 0; y < Hw.SCREEN_H; y++) {
            if (sky[y] != 0) {
                held = sky[y];
            } else {
                sky[y] = held;
            }
        }

        assets.backgroundGradient = background;
        assets.skyGradient = sky;
    }

    private int[] bytes(int address, int count, boolean signed) {
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = signed ? bin.s8(address + i) : bin.u8(address + i);
        }
        return out;
    }

    /**
     * The spell graphics, all of them hardware sprite blocks whose sizes the
     * code names: three parts of 13, 5 and 13 lines for the back fire ball, four
     * balls of 15 lines per channel for the rotative one, sixteen hour glasses
     * of 31 lines in two halves, six smart bomb frames of 156 lines in two
     * columns, four mega blast sprites of 150 lines and two forward balls of 15.
     */
    private void spellSprites() {
        if (bin.has("Back_FB_Spr0")) {
            int base = bin.address("Back_FB_Spr0");
            assets.backFireBall[0] = single(base + 4, 13);
            assets.backFireBall[1] = single(base + 56 + 4, 5);
            assets.backFireBall[2] = single(base + 80 + 4, 13);
        }
        if (bin.has("FBall_3spr0")) {
            int base = bin.address("FBall_3spr0");
            for (int i = 0; i < 4; i++) {
                assets.rotFireBall0[i] = single(base + i * 64 + 4, 15);
                assets.rotFireBall1[i] = single(base + 260 + i * 64 + 4, 15);
            }
            assets.fwFireBall[0] = single(base + 4, 15);
            assets.fwFireBall[1] = single(base + 64 + 4, 15);
        }
        if (bin.has("Time_Spr")) {
            int base = bin.address("Time_Spr");
            for (int i = 0; i < 16; i++) {
                assets.stopTimeLeft[i] = single(base + i * 256 + 4, 31);
                assets.stopTimeRight[i] = single(base + i * 256 + 128 + 4, 31);
            }
        }
        if (bin.has("Smart_B_Spr")) {
            int base = bin.address("Smart_B_Spr");
            for (int i = 0; i < 6; i++) {
                assets.smartBombLeft[i] = single(base + i * 628 + 4, 156);
                assets.smartBombRight[i] = single(base + 3768 + i * 628 + 4, 156);
            }
        }
        if (bin.has("Mega_B_Spr")) {
            int base = bin.address("Mega_B_Spr");
            for (int i = 0; i < 4; i++) {
                assets.megaBlast[i] = single(base + i * 604 + 4, 150);
            }
        }
    }

    /** A lone sprite: two bitplanes, three colours, sixteen pixels wide. */
    private SpriteFrame single(int data, int height) {
        byte[] px = new byte[16 * height];
        for (int y = 0; y < height; y++) {
            int a = bin.u16(data + y * 4);
            int b = bin.u16(data + y * 4 + 2);
            for (int x = 0; x < 16; x++) {
                int mask = 0x8000 >> x;
                int v = ((a & mask) != 0 ? 1 : 0) | ((b & mask) != 0 ? 2 : 0);
                px[y * 16 + x] = (byte) v;
            }
        }
        return new SpriteFrame(0, 16, height, px);
    }

    /** An attached pair: sixteen pixels wide, fifteen colours. */
    private SpriteFrame pair(int even, int odd, int height) {
        byte[] px = new byte[16 * height];
        attached(px, 16, height, even, odd, 0);
        return new SpriteFrame(0, 16, height, px);
    }

    /**
     * One icon of the spell menu, cut out of the four 768 byte blocks: the
     * first pair carries its left half, the second its right half.
     */
    private SpriteFrame wideIcon(int base, int line, int height) {
        byte[] px = new byte[32 * height];
        int skip = line * 4;
        attached(px, 32, height, base + 4 + skip, base + 768 + 4 + skip, 0);
        attached(px, 32, height, base + 1536 + 4 + skip, base + 2304 + 4 + skip, 16);
        return new SpriteFrame(0, 32, height, px);
    }

    private int[] palette(int address) {
        int[] out = new int[16];
        for (int i = 0; i < 16; i++) {
            out[i] = bin.u16(address + i * 2);
        }
        return out;
    }

    private SpriteFrame[] sorcererFrames(int base) {
        SpriteFrame[] frames = new SpriteFrame[16];
        for (int f = 0; f < 16; f++) {
            int head = base + f * 12;
            int y1 = bin.s16(head);
            int h = bin.u16(head + 2);
            if (h <= 0 || h > 128) {
                frames[f] = assets.sorcerer != null && assets.sorcerer[f] != null
                        ? assets.sorcerer[f] : new SpriteFrame(0, 1, 1, new byte[1]);
                continue;
            }
            int[] block = new int[4];
            for (int i = 0; i < 4; i++) {
                block[i] = base + bin.u16(head + 4 + i * 2);
            }
            // The body sits behind the upper axe: two control words plus seven
            // lines of axe, then two more control words.
            int body = 4 + 7 * 4 + 4;
            byte[] px = new byte[32 * h];
            attached(px, 32, h, block[0] + body, block[1] + body, 0);
            attached(px, 32, h, block[2] + body, block[3] + body, 16);
            frames[f] = new SpriteFrame(y1, 32, h, px);
        }
        return frames;
    }

    /**
     * The double the seeker and the shield use.  {@code Sorcerer2_Shape}
     * advances by eight, so a frame is four words: the first visible line, the
     * height and two block offsets.  Those two blocks are lone sprites written
     * sixteen pixels apart, not an attached pair, so each is two bitplanes.
     */
    private SpriteFrame[] ghostFrames(int base) {
        SpriteFrame[] frames = new SpriteFrame[16];
        for (int f = 0; f < 16; f++) {
            int head = base + f * 8;
            int y1 = bin.s16(head);
            int h = bin.u16(head + 2);
            if (h <= 0 || h > 128) {
                frames[f] = new SpriteFrame(0, 1, 1, new byte[1]);
                continue;
            }
            int left = base + bin.u16(head + 4) + 4;
            int right = base + bin.u16(head + 6) + 4;
            byte[] px = new byte[32 * h];
            for (int y = 0; y < h; y++) {
                plot(px, 32, y, 0, bin.u16(left + y * 4), bin.u16(left + y * 4 + 2));
                plot(px, 32, y, 16, bin.u16(right + y * 4), bin.u16(right + y * 4 + 2));
            }
            frames[f] = new SpriteFrame(y1, 32, h, px);
        }
        return frames;
    }

    private void plot(byte[] px, int width, int y, int x0, int planeA, int planeB) {
        for (int x = 0; x < 16; x++) {
            int mask = 0x8000 >> x;
            int v = ((planeA & mask) != 0 ? 1 : 0) | ((planeB & mask) != 0 ? 2 : 0);
            if (v != 0) {
                px[y * width + x0 + x] = (byte) v;
            }
        }
    }

    private SpriteFrame axeFrame(int base) {
        int[] block = new int[4];
        for (int i = 0; i < 4; i++) {
            block[i] = base + bin.u16(base + 4 + i * 2);
        }
        byte[] px = new byte[32 * 7];
        attached(px, 32, 7, block[0] + 4, block[1] + 4, 0);
        attached(px, 32, 7, block[2] + 4, block[3] + 4, 16);
        return new SpriteFrame(0, 32, 7, px);
    }

    /**
     * Combines two attached sprites into four bit pixels: the even sprite gives
     * bits 0 and 1, the odd one bits 2 and 3.
     */
    private void attached(byte[] px, int width, int height, int even, int odd, int x0) {
        for (int y = 0; y < height; y++) {
            int a0 = bin.u16(even + y * 4);
            int b0 = bin.u16(even + y * 4 + 2);
            int a1 = bin.u16(odd + y * 4);
            int b1 = bin.u16(odd + y * 4 + 2);
            for (int x = 0; x < 16; x++) {
                int mask = 0x8000 >> x;
                int v = ((a0 & mask) != 0 ? 1 : 0)
                        | ((b0 & mask) != 0 ? 2 : 0)
                        | ((a1 & mask) != 0 ? 4 : 0)
                        | ((b1 & mask) != 0 ? 8 : 0);
                if (v != 0 && x0 + x < width) {
                    px[y * width + x0 + x] = (byte) v;
                }
            }
        }
    }
}
