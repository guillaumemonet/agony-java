package com.artandmagic.agony.tools;

import com.artandmagic.agony.audio.Paula;
import com.artandmagic.agony.audio.SoundFx;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.Pacer;
import com.artandmagic.agony.data.HiScores;
import com.artandmagic.agony.data.PlaceholderAssets;
import com.artandmagic.agony.data.original.Disks;
import com.artandmagic.agony.data.original.IntroData;
import com.artandmagic.agony.data.original.LoadingScreens;
import com.artandmagic.agony.data.original.OriginalAssets;
import com.artandmagic.agony.data.original.OriginalLevel;
import com.artandmagic.agony.data.original.TitleData;
import com.artandmagic.agony.game.AgonyGame;
import com.artandmagic.agony.input.Joystick;
import com.artandmagic.agony.screens.EndingScreen;
import com.artandmagic.agony.screens.IntroScreen;
import com.artandmagic.agony.screens.LoadingScreen;
import com.artandmagic.agony.screens.TitleScreen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Renders the screenshots of the README from the disk images, without a
 * window: {@code Screenshots <folder> [--adf=<disks>]}.
 *
 * <p>The presentation runs in real time, because its script waits for its
 * sounds to end, so the whole run takes about half a minute.
 */
public final class Screenshots {

    private Screenshots() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 && !args[0].startsWith("--") ? args[0] : "docs/screenshots");
        Path adf = Path.of("..", "adf");
        for (String a : args) {
            if (a.startsWith("--adf=")) {
                adf = Path.of(a.substring(6));
            }
        }
        Files.createDirectories(out);
        Disks disks = Disks.read(adf);
        Joystick joystick = new Joystick();

        // The presentation: the owl in its sweep of light, Art & Magic, the logo.
        IntroData intro = IntroData.scan(disks);
        if (intro != null) {
            Paula paula = new Paula();
            paula.start();
            IntroScreen screen = new IntroScreen(intro, paula);
            Pacer pacer = new Pacer();
            Map<Integer, String> shots = Map.of(540, "intro-psygnosis", 900, "intro-art-and-magic",
                    1400, "intro-agony");
            for (int i = 0; screen.update(joystick); i++) {
                if (shots.containsKey(i)) {
                    save(screen.frame(), screen.width(), screen.height(), out, shots.get(i));
                }
                pacer.waitFrame();
            }
            screen.finish();
            paula.close();
        }

        // The title: the first credits page and the hiscore table.
        TitleData.Found title = TitleData.scan(disks);
        if (title != null) {
            TitleScreen screen = new TitleScreen(title.data(), new HiScores(title.hiscores(), null),
                    0, joystick);
            Map<Integer, String> shots = Map.of(17 + 150 + 300, "title-credits",
                    17 + 150 + 12 * 600 + 300, "title-hiscores");
            for (int i = 0; i <= 17 + 150 + 12 * 600 + 300; i++) {
                screen.update(joystick);
                if (shots.containsKey(i)) {
                    save(screen.frame(), screen.width(), screen.height(), out, shots.get(i));
                }
            }
        }

        // A loading screen, the forest's.
        OriginalAssets.Found forest = null;
        for (OriginalAssets.Found f : OriginalAssets.scan(disks)) {
            if (f.level() == 2) {
                forest = f;
            }
        }
        Map<String, LoadingScreens.Screen> loading = LoadingScreens.scan(disks);
        if (forest != null && loading.containsKey(forest.file())) {
            LoadingScreen screen = new LoadingScreen(loading.get(forest.file()).picture());
            for (int i = 0; i < 40; i++) {
                screen.update(joystick);
            }
            save(screen.frame(), screen.width(), screen.height(), out, "loading-forest");
        }

        // Each level: a moment of play, then its final monster.
        int[] play = {0, 1900, 3000, 3500, 1300, 4100, 6600};
        for (OriginalAssets.Found f : OriginalAssets.scan(disks)) {
            OriginalLevel.Result r = OriginalAssets.convert(f, PlaceholderAssets.create());
            AgonyGame game = new AgonyGame(r.level(), r.assets(), joystick,
                    new SoundFx(new Paula(), r.assets().soundPriority));
            game.setInvincible(true);
            int at = f.level() < play.length ? play[f.level()] : 2000;
            int boss = 9100;
            for (int i = 0; i <= boss && !game.levelComplete(); i++) {
                joystick.setFire(i % 16 < 4);
                joystick.setUp(i % 300 < 40);
                joystick.setDown(i % 300 >= 150 && i % 300 < 190);
                game.videoFrame();
                if (i == at) {
                    save(game.frame(), Hw.OUT_W, Hw.OUT_H, out, "level" + f.level());
                }
                if (i == boss) {
                    save(game.frame(), Hw.OUT_W, Hw.OUT_H, out, "level" + f.level() + "-boss");
                }
            }
            joystick.setFire(false);
            joystick.setUp(false);
            joystick.setDown(false);
        }

        // The end of the story.
        TitleData.Found ending = TitleData.scanEnding(disks);
        if (ending != null) {
            EndingScreen screen = new EndingScreen(ending.data());
            for (int i = 0; i < 17 + 150 + 120; i++) {
                screen.update(joystick);
            }
            save(screen.frame(), screen.width(), screen.height(), out, "ending");
        }
        System.out.println("screenshots written to " + out.toAbsolutePath());
    }

    /** Saves a frame at half its size: one pixel per lores pixel. */
    private static void save(int[] frame, int w, int h, Path dir, String name) throws IOException {
        // Each output pixel averages a 2 by 2 block, which keeps the thin lines
        // of the hires interlace pictures of the presentation.
        BufferedImage half = new BufferedImage(w / 2, h / 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h / 2; y++) {
            for (int x = 0; x < w / 2; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                for (int k = 0; k < 4; k++) {
                    int c = frame[(y * 2 + (k >> 1)) * w + x * 2 + (k & 1)];
                    r += (c >> 16) & 0xff;
                    g += (c >> 8) & 0xff;
                    b += c & 0xff;
                }
                half.setRGB(x, y, ((r / 4) << 16) | ((g / 4) << 8) | (b / 4));
            }
        }
        ImageIO.write(half, "png", new File(dir.toFile(), name + ".png"));
    }
}
