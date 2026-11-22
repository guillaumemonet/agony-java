package com.artandmagic.agony.tools;

import com.artandmagic.agony.audio.Paula;
import com.artandmagic.agony.audio.SoundFx;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.data.PlaceholderAssets;
import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.RoutineLibrary;
import com.artandmagic.agony.data.StartList;
import com.artandmagic.agony.data.original.Disks;
import com.artandmagic.agony.data.original.OriginalAssets;
import com.artandmagic.agony.data.original.OriginalLevel;
import com.artandmagic.agony.game.AgonyGame;
import com.artandmagic.agony.game.routines.Routines;
import com.artandmagic.agony.input.Joystick;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A development tool for the level routines, run without a window.
 *
 * <pre>
 *   LevelProbe report &lt;level&gt;            the Start_C calls of the script, resolved or not
 *   LevelProbe run &lt;level&gt; &lt;frames&gt; [dir] [--kill]
 *                                         plays the level, invincible, firing;
 *                                         prints which routines started and whether
 *                                         the level ended; saves a frame every 250
 *                                         frames into dir when given; --kill
 *                                         blows up every alien every 400 frames
 * </pre>
 *
 * The disks are read from {@code --adf=<folder>}, {@code ../adf} or {@code adf}.
 */
public final class LevelProbe {

    private LevelProbe() {
    }

    public static void main(String[] args) throws IOException {
        Path adf = Path.of("..", "adf");
        java.util.List<String> rest = new java.util.ArrayList<>();
        boolean kill = false;
        for (String a : args) {
            if (a.equals("--kill")) {
                kill = true;
            } else if (a.startsWith("--adf=")) {
                adf = Path.of(a.substring(6));
            } else {
                rest.add(a);
            }
        }
        if (!java.nio.file.Files.isDirectory(adf)) {
            adf = Path.of("adf");
        }
        if (rest.size() < 2) {
            System.out.println("usage: LevelProbe report <level> | run <level> <frames> [dir]");
            return;
        }
        int level = Integer.parseInt(rest.get(1));
        OriginalAssets.Found found = null;
        for (OriginalAssets.Found f : OriginalAssets.scan(Disks.read(adf))) {
            if (f.level() == level) {
                found = f;
            }
        }
        if (found == null) {
            System.out.println("level " + level + " not on the disks in " + adf);
            return;
        }

        // Wrap the library: name every routine, count its starts, and stand a
        // placeholder in for the addresses it does not know.
        RoutineLibrary real = Routines.forLevel(level, found.binary());
        Map<Routine, Integer> addressOf = new IdentityHashMap<>();
        Map<Integer, int[]> starts = new TreeMap<>();
        Map<Integer, String> names = new HashMap<>();
        RoutineLibrary spy = new RoutineLibrary() {
            @Override
            public Routine forAddress(int address) {
                Routine r = real.forAddress(address);
                names.put(address, r == null ? "MISSING" : r.getClass().getSimpleName());
                Routine counted = (state, bank, vars, params) -> {
                    starts.computeIfAbsent(address, k -> new int[1]);
                    return r != null && r.update(state, bank, vars, params);
                };
                addressOf.put(counted, address);
                return counted;
            }

            @Override
            public int[] waveAddresses() {
                return real.waveAddresses();
            }

            @Override
            public void bindWaves(Map<Integer, Integer> indexByAddress) {
                real.bindWaves(indexByAddress);
            }

            @Override
            public boolean endsLevel() {
                return real.endsLevel();
            }
        };
        OriginalLevel.Result result = OriginalLevel.convert(found.binary(),
                PlaceholderAssets.create(), spy);

        if (rest.get(0).equals("report")) {
            int missing = 0;
            int total = 0;
            for (StartList.Entry e : result.level().startList.entries()) {
                if (e.routine == null) {
                    continue;
                }
                int address = addressOf.get(e.routine);
                String name = names.get(address);
                total++;
                if (name.equals("MISSING")) {
                    missing++;
                }
                StringBuilder params = new StringBuilder();
                for (int p : e.params) {
                    params.append(String.format(" $%04x", p));
                }
                System.out.printf("trigger %5d  $%06x  %-18s%s%n", e.trigger, address, name, params);
            }
            System.out.println(level + ": " + total + " Start_C, " + missing + " missing");
            return;
        }

        int frames = Integer.parseInt(rest.get(2));
        String dir = rest.size() > 3 ? rest.get(3) : null;
        Joystick joystick = new Joystick();
        AgonyGame game = new AgonyGame(result.level(), result.assets(), joystick,
                new SoundFx(new Paula(), result.assets().soundPriority));
        game.setInvincible(true);
        int i;
        for (i = 0; i < frames && !game.levelComplete(); i++) {
            joystick.setFire(i % 16 < 4);
            joystick.setUp(i % 300 < 40);
            joystick.setDown(i % 300 >= 150 && i % 300 < 190);
            game.videoFrame();
            if (kill && i % 400 == 399) {
                // Blow up everything on screen, the boss included, to check
                // what the routines do when their aliens die.
                for (com.artandmagic.agony.game.AlienBank bank : game.tracks().banks) {
                    if (bank.isFree()) {
                        continue;
                    }
                    for (int k = 0; k < bank.count; k++) {
                        com.artandmagic.agony.game.Alien a =
                                bank.aliens[(bank.first + k) % com.artandmagic.agony.game.AlienBank.SIZE];
                        if (a.exploState() == 0) {
                            a.status = 1;
                        }
                    }
                }
            }
            if (dir != null && i % 250 == 0) {
                save(game, dir + "/level" + level + "_" + i + ".png");
            }
        }
        if (dir != null) {
            save(game, dir + "/level" + level + "_last.png");
        }
        System.out.println("level " + level + ": " + i + " frames, levelX " + game.state.levelX
                + (game.levelComplete() ? ", level complete" : ", not complete")
                + (game.state.prgPause ? ", scenery over" : ""));
        for (Map.Entry<Integer, int[]> e : starts.entrySet()) {
            System.out.printf("  ran $%06x %s%n", e.getKey(), names.get(e.getKey()));
        }
    }

    private static void save(AgonyGame game, String file) throws IOException {
        BufferedImage image = new BufferedImage(Hw.OUT_W, Hw.OUT_H, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Hw.OUT_W, Hw.OUT_H, game.frame(), 0, Hw.OUT_W);
        ImageIO.write(image, "png", new File(file));
    }
}
