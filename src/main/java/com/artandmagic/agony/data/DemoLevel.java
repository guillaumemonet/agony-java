package com.artandmagic.agony.data;

import com.artandmagic.agony.core.Sprite;

import java.util.Random;

/**
 * A playable stand in for level 1, {@code Game/Ag_Game_LMER.s} and the
 * {@code LMER} scenery.
 *
 * <p>The real level is a pair of binaries (the map and the character set) plus a
 * hand written start list; neither is part of the source release.  What follows
 * builds the same tables from scratch: a cave of 32 by 32 characters over a two
 * layer sea, six colour bands per palette step, three kinds of alien with their
 * movement tables, and a script that releases them as the level scrolls past.
 */
public final class DemoLevel {

    /** Length of the level, in character columns. */
    private static final int COLUMNS = 300;

    private DemoLevel() {
    }

    public static LevelData create() {
        LevelData level = new LevelData();
        level.name = "SEA";
        level.rain = true;

        Random random = new Random(0x41474f4eL);

        level.frontChars = frontCharacters();
        level.frontMap = frontMap(random);
        level.frontPalettes = frontPalettes();

        level.backChars = backCharacters();
        level.backPatterns = backPatterns(random);
        level.backSequence = new int[]{0, 1, 2, 1, -1};

        level.skyChars = skyCharacters();
        level.skyMap = skyMap(random);
        level.skySpeed = new int[]{2, 3, 5, 8, 11, 14};

        level.objectSprites = objectSprites();
        level.firstExploSprite = 1;
        level.objects = objects();
        level.animations = new java.util.HashMap<>();
        level.animations.put(0, new int[]{9});
        level.animations.put(1, new int[]{10});
        level.animations.put(2, new int[]{11});

        level.tracks = tracks();
        level.waves = waves();
        level.startList = startList();
        return level;
    }

    // ------------------------------------------------------------ front scroll

    /**
     * Character 0 is empty, 1 to 3 are the cave ceiling, 4 to 6 the floor and
     * 7 and 8 are pillars that block the middle of the screen.
     */
    private static Sprite[] frontCharacters() {
        Sprite[] chars = new Sprite[9];
        chars[0] = new Sprite(32, 32);

        for (int i = 0; i < 3; i++) {
            chars[1 + i] = rock(i, true);
            chars[4 + i] = rock(i, false);
        }
        chars[7] = pillar(false);
        chars[8] = pillar(true);
        return chars;
    }

    /** A block of cave wall, hanging from the top or growing from the bottom. */
    private static Sprite rock(int variant, boolean ceiling) {
        Sprite s = new Sprite(32, 32);
        int[] profile = new int[32];
        Random r = new Random(variant * 31L + (ceiling ? 7 : 13));
        int depth = 14 + variant * 5;
        for (int x = 0; x < 32; x++) {
            depth += r.nextInt(5) - 2;
            depth = Math.max(6, Math.min(30, depth));
            profile[x] = depth;
        }
        for (int x = 0; x < 32; x++) {
            for (int d = 0; d < profile[x]; d++) {
                int y = ceiling ? d : 31 - d;
                // The edge facing the cave catches the light; the rock darkens
                // as it goes back into the wall.
                int depthFromSurface = profile[x] - 1 - d;
                int shade = depthFromSurface < 1 ? 6 : depthFromSurface < 3 ? 5
                        : depthFromSurface < 6 ? 4 : depthFromSurface < 12 ? 3 : 2;
                s.set(x, y, shade);
            }
        }
        return s;
    }

    private static Sprite pillar(boolean thick) {
        Sprite s = new Sprite(32, 32);
        int half = thick ? 12 : 7;
        for (int y = 0; y < 32; y++) {
            for (int x = 16 - half; x <= 15 + half; x++) {
                int edge = Math.min(x - (16 - half), 15 + half - x);
                s.set(x, y, edge < 2 ? 6 : edge < 4 ? 4 : 3);
            }
        }
        return s;
    }

    /** Six characters per column: a ceiling, four open rows and a floor. */
    private static byte[] frontMap(Random random) {
        int cell = LevelData.FRONT_CELL;
        byte[] map = new byte[COLUMNS * LevelData.FRONT_ROWS * cell];
        for (int c = 0; c < COLUMNS; c++) {
            int base = c * LevelData.FRONT_ROWS * cell;
            map[base] = (byte) (1 + random.nextInt(3));
            map[base + (LevelData.FRONT_ROWS - 1) * cell] = (byte) (4 + random.nextInt(3));

            // Every so often a pillar narrows the passage.
            if (c > 20 && random.nextInt(24) == 0) {
                int row = 1 + random.nextInt(4);
                map[base + row * cell] = (byte) (random.nextBoolean() ? 7 : 8);
            }
        }
        return map;
    }

    /**
     * Four palette steps, each holding the seven colours of the six bands; the
     * level walks through them as it scrolls, which is how the original changes
     * the mood of a level without changing its characters.
     */
    private static int[][] frontPalettes() {
        int[][] steps = new int[4][];
        int[][] tints = {
            {0x0112, 0x0334, 0x0557, 0x0779, 0x099b, 0x0bbd, 0x0dff},
            {0x0211, 0x0432, 0x0654, 0x0876, 0x0a98, 0x0cba, 0x0fdc},
            {0x0121, 0x0342, 0x0564, 0x0786, 0x09a8, 0x0bca, 0x0dfc},
            {0x0122, 0x0344, 0x0566, 0x0788, 0x09aa, 0x0bcc, 0x0dee}
        };
        for (int s = 0; s < steps.length; s++) {
            int[] pal = new int[6 * 7];
            for (int band = 0; band < 6; band++) {
                for (int c = 0; c < 7; c++) {
                    // Bands further down the screen are darker, as in a cave.
                    pal[band * 7 + c] = darken(tints[s][c], band);
                }
            }
            steps[s] = pal;
        }
        return steps;
    }

    private static int darken(int rgb12, int amount) {
        int r = Math.max(0, ((rgb12 >> 8) & 0xf) - amount / 2);
        int g = Math.max(0, ((rgb12 >> 4) & 0xf) - amount / 2);
        int b = Math.max(0, (rgb12 & 0xf) - amount / 3);
        return (r << 8) | (g << 4) | b;
    }

    // ------------------------------------------------------------- back scroll

    private static Sprite[] backCharacters() {
        Sprite[] chars = new Sprite[5];
        chars[0] = new Sprite(32, 32);
        for (int i = 1; i < chars.length; i++) {
            Sprite s = new Sprite(32, 32);
            int crest = 6 + i * 5;
            for (int x = 0; x < 32; x++) {
                int top = crest + (int) Math.round(Math.sin((x + i * 9) / 5.0) * 4);
                for (int y = top; y < 32; y++) {
                    s.set(x, y, y < top + 3 ? 3 : y < top + 10 ? 2 : 1);
                }
            }
            chars[i] = s;
        }
        return chars;
    }

    private static LevelData.BackPattern[] backPatterns(Random random) {
        int[][] palettes = {
            {0x0013, 0x0025, 0x0148},
            {0x0024, 0x0036, 0x0259},
            {0x0112, 0x0224, 0x0347}
        };
        int[] backgrounds = {0x0002, 0x0013, 0x0101};

        LevelData.BackPattern[] patterns = new LevelData.BackPattern[3];
        for (int p = 0; p < patterns.length; p++) {
            LevelData.BackPattern pattern = new LevelData.BackPattern();
            pattern.map = new byte[64 * LevelData.FRONT_ROWS];
            for (int c = 0; c < 64; c++) {
                int base = c * LevelData.FRONT_ROWS;
                // Only the lower half of the back playfield carries the sea.
                pattern.map[base + 3] = (byte) (1 + random.nextInt(2));
                pattern.map[base + 4] = (byte) (2 + random.nextInt(2));
                pattern.map[base + 5] = (byte) (3 + random.nextInt(2));
            }
            for (int band = 0; band < 6; band++) {
                pattern.background[band] = backgrounds[p];
                pattern.colors[band] = new int[]{
                    darken(palettes[p][0], band),
                    darken(palettes[p][1], band),
                    darken(palettes[p][2], band)
                };
                pattern.sky[band] = new int[]{backgrounds[p], darken(0x0468, band)};
            }
            patterns[p] = pattern;
        }
        return patterns;
    }

    private static Sprite[] skyCharacters() {
        Sprite[] chars = new Sprite[4];
        chars[0] = new Sprite(32, 32);
        for (int i = 1; i < chars.length; i++) {
            Sprite s = new Sprite(32, 32);
            Random r = new Random(i * 977L);
            int cx = r.nextInt(32);
            int cy = 8 + r.nextInt(16);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    double d = Math.hypot((x - cx) / 14.0, (y - cy) / 6.0);
                    if (d < 1.0) {
                        s.set(x, y, 1);
                    }
                }
            }
            chars[i] = s;
        }
        return chars;
    }

    private static byte[] skyMap(Random random) {
        byte[] map = new byte[128 * LevelData.FRONT_ROWS];
        for (int c = 0; c < 128; c++) {
            int base = c * LevelData.FRONT_ROWS;
            for (int row = 0; row < 3; row++) {
                map[base + row] = (byte) (random.nextInt(6) == 0 ? 1 + random.nextInt(3) : 0);
            }
        }
        return map;
    }

    // ----------------------------------------------------------------- enemies

    /**
     * Sprite 0 is unused, 1 to 8 are the explosion frames the object renderer
     * substitutes for a dying alien, and 9 to 11 are the three alien shapes.
     */
    private static Sprite[] objectSprites() {
        Sprite[] sprites = new Sprite[12];
        sprites[0] = new Sprite(1, 1);
        Sprite[] explosion = PlaceholderAssets.create().explosion;
        System.arraycopy(explosion, 0, sprites, 1, 8);

        sprites[9] = jellyfish();
        sprites[10] = crab();
        sprites[11] = eye();
        return sprites;
    }

    private static Sprite jellyfish() {
        Sprite s = new Sprite(32, 32);
        for (int y = 0; y < 16; y++) {
            int half = (int) Math.round(Math.sqrt(1 - Math.pow((y - 15) / 15.0, 2)) * 15);
            for (int x = 16 - half; x <= 15 + half; x++) {
                s.set(x, y, y < 4 ? 7 : y < 9 ? 6 : 5);
            }
        }
        for (int t = 0; t < 5; t++) {
            int x = 4 + t * 6;
            for (int y = 16; y < 32; y++) {
                s.set(x + (int) Math.round(Math.sin(y / 3.0 + t) * 2), y, 4);
            }
        }
        return s;
    }

    private static Sprite crab() {
        Sprite s = new Sprite(32, 24);
        for (int y = 6; y < 18; y++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, 1 - Math.pow((y - 12) / 6.0, 2))) * 14);
            for (int x = 16 - half; x <= 15 + half; x++) {
                s.set(x, y, y < 10 ? 6 : 5);
            }
        }
        for (int i = 0; i < 4; i++) {
            int y = 8 + i * 3;
            s.set(1 + i, y, 4);
            s.set(2 + i, y, 4);
            s.set(30 - i, y, 4);
            s.set(29 - i, y, 4);
        }
        s.set(11, 10, 7);
        s.set(20, 10, 7);
        return s;
    }

    private static Sprite eye() {
        Sprite s = new Sprite(24, 24);
        for (int y = 0; y < 24; y++) {
            for (int x = 0; x < 24; x++) {
                double d = Math.hypot(x - 12, y - 12) / 12.0;
                if (d > 1.0) {
                    continue;
                }
                s.set(x, y, d > 0.8 ? 3 : d > 0.45 ? 7 : d > 0.2 ? 2 : 1);
            }
        }
        return s;
    }

    private static java.util.Map<Integer, ObjectDef> objects() {
        ObjectDef[] defs = new ObjectDef[12];
        for (int i = 0; i < 9; i++) {
            // Explosions carry no collision box and never fire.
            defs[i] = new ObjectDef(0, 0, new ObjectDef.Rect[0],
                    new ObjectDef.Part[]{new ObjectDef.Part(0, 0, i)});
        }
        defs[9] = new ObjectDef(0, 14,
                new ObjectDef.Rect[]{new ObjectDef.Rect(2, 0, 30, 16)},
                new ObjectDef.Part[]{new ObjectDef.Part(0, 0, 9)});
        defs[10] = new ObjectDef(0, 12,
                new ObjectDef.Rect[]{new ObjectDef.Rect(2, 6, 30, 18)},
                new ObjectDef.Part[]{new ObjectDef.Part(0, 0, 10)});
        defs[11] = new ObjectDef(0, 12,
                new ObjectDef.Rect[]{new ObjectDef.Rect(3, 3, 21, 21)},
                new ObjectDef.Part[]{new ObjectDef.Part(0, 0, 11)});

        java.util.Map<Integer, ObjectDef> map = new java.util.HashMap<>();
        for (int i = 0; i < defs.length; i++) {
            map.put(i, defs[i]);
        }
        return map;
    }

    /**
     * Three movement tables.  A dx of 0 means the alien drifts with the scroll,
     * since the follower subtracts two from every step of a relative track.
     */
    private static TrackTable[] tracks() {
        TrackTable[] tables = new TrackTable[3];
        tables[0] = wave(20, 96, 0, 3, 24);
        tables[1] = wave(20, 40, 0, 2, 40);
        tables[2] = dive(20, 20);
        return tables;
    }

    /** A sine path across the screen. */
    private static TrackTable wave(int startX, int startY, int dx, int amplitude, int period) {
        int steps = 210;
        byte[] xs = new byte[steps + 1];
        byte[] ys = new byte[steps + 1];
        double previous = 0;
        for (int i = 0; i < steps; i++) {
            xs[i] = (byte) dx;
            double value = Math.sin(i * Math.PI * 2 / period) * amplitude;
            ys[i] = (byte) clampNibble((int) Math.round(value - previous));
            previous += ys[i];
        }
        xs[steps] = TrackTable.END;
        return new TrackTable(startX + 300, startY, xs, ys);
    }

    /** A path that swoops down, levels out and climbs again. */
    private static TrackTable dive(int startX, int startY) {
        int steps = 210;
        byte[] xs = new byte[steps + 1];
        byte[] ys = new byte[steps + 1];
        for (int i = 0; i < steps; i++) {
            xs[i] = (byte) (i < 60 ? -1 : 0);
            ys[i] = (byte) (i < 50 ? 2 : i < 120 ? 0 : -1);
        }
        xs[steps] = TrackTable.END;
        return new TrackTable(startX + 300, startY, xs, ys);
    }

    private static int clampNibble(int v) {
        return Math.max(-7, Math.min(7, v));
    }

    private static AttackWave[] waves() {
        AttackWave[] waves = new AttackWave[4];

        AttackWave jellies = new AttackWave();
        jellies.track = 0;
        jellies.animation = 0;
        jellies.alienNum = 5;
        jellies.alienRate = 14;
        jellies.alienEnergy = 1;
        jellies.alienFireRate = 3;
        jellies.alienBadFireRate = 50;
        waves[0] = jellies;

        AttackWave crabs = new AttackWave();
        crabs.track = 1;
        crabs.animation = 1;
        crabs.alienNum = 6;
        crabs.alienRate = 10;
        crabs.alienEnergy = 2;
        crabs.alienFireRate = 2;
        crabs.alienBadFireRate = 40;
        // This wave tints the two middle bands, as the original waves do.
        crabs.palModLine[2] = true;
        crabs.palModLine[3] = true;
        crabs.palModCol[5] = 0x0f80;
        crabs.palModCol[6] = 0x0fc0;
        waves[1] = crabs;

        AttackWave eyes = new AttackWave();
        eyes.track = 2;
        eyes.animation = 2;
        eyes.alienNum = 4;
        eyes.alienRate = 18;
        eyes.alienEnergy = 3;
        eyes.alienFireRate = 1;
        eyes.alienBadFireRate = 35;
        waves[2] = eyes;

        AttackWave squadron = new AttackWave();
        squadron.track = 0;
        squadron.animation = 2;
        squadron.alienNum = 8;
        squadron.alienRate = 8;
        squadron.alienEnergy = 2;
        squadron.alienFireRate = 4;
        squadron.alienBadFireRate = 60;
        waves[3] = squadron;

        return waves;
    }

    /** The level script: a wave roughly every four seconds. */
    private static StartList startList() {
        StartList list = new StartList();
        int x = 200;
        int[] order = {0, 1, 0, 2, 1, 3, 2, 0, 3, 1, 2, 3};
        for (int wave : order) {
            list.wave(x, wave);
            x += 220;
        }
        return list;
    }
}
