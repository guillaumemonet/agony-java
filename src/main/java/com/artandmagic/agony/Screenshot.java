package com.artandmagic.agony;

import com.artandmagic.agony.audio.Paula;
import com.artandmagic.agony.audio.SoundFx;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.data.DemoLevel;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.LevelData;
import com.artandmagic.agony.data.PlaceholderAssets;
import com.artandmagic.agony.data.original.OriginalAssets;
import com.artandmagic.agony.data.original.OriginalLevel;
import com.artandmagic.agony.game.AgonyGame;
import com.artandmagic.agony.input.Joystick;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Runs the game without a window and saves a frame as a PNG.
 *
 * <p>Handy to check the engine after a change, and the only way to look at the
 * output on a machine with no display.  Give it a frame count and a file name:
 * {@code java com.artandmagic.agony.Screenshot 500 shot.png}.
 */
public final class Screenshot {

    private Screenshot() {
    }

    public static void main(String[] args) throws IOException {
        int frames = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        String target = args.length > 1 ? args[1] : "agony.png";

        GameAssets assets = PlaceholderAssets.create();
        LevelData level = DemoLevel.create();

        int wanted = 1;
        for (int i = 2; i < args.length; i++) {
            if (args[i].matches("[1-6]")) {
                wanted = Integer.parseInt(args[i]);
            }
        }
        java.nio.file.Path adf = java.nio.file.Path.of("adf");
        if (!java.nio.file.Files.isDirectory(adf)) {
            adf = java.nio.file.Path.of("..", "adf");
        }
        var levels = OriginalAssets.scan(adf);
        for (var f : levels) {
            if (f.level() == wanted) {
                OriginalLevel.Result r = OriginalAssets.convert(f, assets);
                level = r.level();
                assets = r.assets();
                System.out.println("using original data: " + f.file() + " level " + f.level());
            }
        }

        Joystick joystick = new Joystick();
        SoundFx sound = new SoundFx(new Paula(), assets.soundPriority);
        AgonyGame game = new AgonyGame(level, assets, joystick, sound);

        // "menu" holds fire down long enough to open the spell menu, which is
        // the other screen worth looking at.
        boolean menu = args.length > 2 && args[2].equals("menu");

        joystick.setFire(true);
        for (int i = 0; i < frames; i++) {
            joystick.setFire(menu ? i > 20 : i % 40 < 6);
            joystick.setDown(!menu && i % 200 < 60);
            joystick.setUp(!menu && i % 200 >= 120 && i % 200 < 180);
            game.videoFrame();
        }

        BufferedImage image = new BufferedImage(Hw.OUT_W, Hw.OUT_H, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Hw.OUT_W, Hw.OUT_H, game.frame(), 0, Hw.OUT_W);
        ImageIO.write(image, "png", new File(target));

        System.out.println("frame " + frames + " written to " + target);
        System.out.println("levelX=" + game.state.levelX
                + " score=" + Integer.toHexString(game.state.score)
                + " life=" + Integer.toBinaryString(game.state.life)
                + " aliens=" + aliveCount(game));
    }

    private static int aliveCount(AgonyGame game) {
        int total = 0;
        for (var bank : game.tracks().banks) {
            if (!bank.isFree() && bank.count > 0) {
                total += bank.count;
            }
        }
        return total;
    }
}
