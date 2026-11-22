package com.artandmagic.agony;

import com.artandmagic.agony.audio.ModPlayer;
import com.artandmagic.agony.audio.Paula;
import com.artandmagic.agony.audio.SoundFx;
import com.artandmagic.agony.audio.TelPlayer;
import com.artandmagic.agony.core.Display;
import com.artandmagic.agony.core.DownloadWindow;
import com.artandmagic.agony.core.Pacer;
import com.artandmagic.agony.core.Screen;
import com.artandmagic.agony.data.AssetLoader;
import com.artandmagic.agony.data.DemoLevel;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.HiScores;
import com.artandmagic.agony.data.LevelData;
import com.artandmagic.agony.data.PlaceholderAssets;
import com.artandmagic.agony.data.original.DiskDownloader;
import com.artandmagic.agony.data.original.LevelMusic;
import com.artandmagic.agony.data.original.IntroData;
import com.artandmagic.agony.data.original.Disks;
import com.artandmagic.agony.data.original.LoadingScreens;
import com.artandmagic.agony.data.original.OriginalAssets;
import com.artandmagic.agony.data.original.OriginalLevel;
import com.artandmagic.agony.data.original.OriginalMusic;
import com.artandmagic.agony.data.original.TitleData;
import com.artandmagic.agony.game.AgonyGame;
import com.artandmagic.agony.input.Joystick;
import com.artandmagic.agony.screens.EndingScreen;
import com.artandmagic.agony.screens.IntroScreen;
import com.artandmagic.agony.screens.LoadingScreen;
import com.artandmagic.agony.screens.TitleScreen;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Entry point and level chain.
 *
 * <p>Agony, by Art &amp; Magic, published by Psygnosis in 1992; graphics by
 * Franck Sauer and Marc Albinet, code by Yves Grolet.  This is a Java port of
 * the original 68000 sources, driven at the same two rates as the Amiga
 * version: a 50 Hz video frame, and a 25 Hz game frame every second one.
 *
 * <p>{@code Agony_Parent_.s} ends a level by fading the screen out, waiting
 * fifty frames and loading the next file, or the game over screen when the last
 * life is gone.  The disks name that order plainly: every level overlay is
 * preceded by a loading overlay carrying its tune, from {@code loading_sea} to
 * {@code loading_fire}, in the same order as {@code ASM_Level} 1 to 6.  The
 * loop below follows it, carrying the score, the lives, the weapon, the axes
 * and the spells from one level to the next.
 */
public final class Main {

    /** How many levels the disks hold. */
    private static final int LEVELS = 6;

    private Main() {
    }

    /**
     * Where to look for the Agony disk images: the folder given with
     * {@code --adf=}, an {@code adf} folder here or beside the project, and
     * otherwise the port's own folder in the user's home, which the disks are
     * downloaded into the first time the game starts without them.
     */
    private static Path adfFolder(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--adf=")) {
                return Path.of(arg.substring(6));
            }
        }
        for (Path candidate : new Path[]{Path.of("adf"), Path.of("..", "adf")}) {
            if (DiskDownloader.hasDisks(candidate)) {
                return candidate;
            }
        }
        Path home = DiskDownloader.defaultFolder();
        if (!DiskDownloader.hasAllDisks(home) && !DownloadWindow.download(home)) {
            // Without the disks there is no game to play: the failure has been
            // shown, so leave rather than open on a half read set of disks.
            System.exit(1);
        }
        return home;
    }

    /** Everything one level needs. */
    private record Stage(LevelData level, GameAssets assets, String tune, LevelMusic song) {
    }

    public static void main(String[] args) {
        Path dataFolder = Path.of(args.length > 0 && !args[0].startsWith("--") ? args[0] : "data");
        AssetLoader loader = new AssetLoader(dataFolder);
        Path adf = adfFolder(args);

        int first = 1;
        boolean invincible = false;
        // Asking for a level is a development shortcut: it skips the title.
        boolean skipTitle = false;
        for (String arg : args) {
            if (arg.startsWith("--level=")) {
                first = Integer.parseInt(arg.substring(8));
                skipTitle = true;
            }
            if (arg.equals("--invincible")) {
                invincible = true;
            }
            if (arg.equals("--no-title")) {
                skipTitle = true;
            }
        }

        List<OriginalAssets.Found> levels = OriginalAssets.scan(adf);
        Map<String, LoadingScreens.Screen> loading = LoadingScreens.scan(adf);
        TitleData.Found title = TitleData.scan(adf);
        TitleData.Found ending = TitleData.scanEnding(Disks.read(adf));
        IntroData intro = skipTitle ? null : IntroData.scan(Disks.read(adf));
        HiScores hiscores = new HiScores(title != null ? title.hiscores() : null,
                HiScores.defaultFile());
        Map<String, OriginalMusic.Module> modules = OriginalMusic.scan(adf);

        Paula paula = new Paula();
        GameAssets probe = PlaceholderAssets.create();
        SoundFx sound = new SoundFx(paula, probe.soundPriority);
        ModPlayer music = new ModPlayer(paula);
        // The programs between levels play modules; the levels play the
        // music driver compiled into their overlay.
        TelPlayer levelMusic = new TelPlayer(paula);
        paula.setVerticalBlank(() -> {
            music.play();
            levelMusic.play();
        });
        paula.start();

        System.out.println("Agony (Art & Magic, 1992) - Java port");
        if (levels.isEmpty()) {
            System.out.println("Original data: none found, using stand in artwork"
                    + " (put the Agony .adf files in ./adf)");
        } else {
            System.out.println("Original data: " + levels.size() + " levels, "
                    + loading.size() + " loading screens, "
                    + (title != null ? "the title screen" : "no title screen") + " and "
                    + modules.size() + " modules on the disks");
        }
        System.out.println("Arrows to fly, Ctrl to fire, Space "
                + "for the spell menu, P pauses, Escape aborts the level, F5 toggles invincibility.");

        Joystick joystick = new Joystick();
        Screen screen = new Screen();
        Display display = new Display(screen.frame(), joystick, "Agony");
        Pacer pacer = new Pacer();

        if (intro != null) {
            // The booter loads the presentation first; fire cuts it short.
            music.stop();
            IntroScreen presentation = new IntroScreen(intro, paula);
            pacer.reset();
            while (presentation.update(joystick)) {
                display.present(presentation.frame(), presentation.width(), presentation.height());
                if (!pacer.waitFrame()) {
                    presentation.finish();
                    paula.close();
                    return;
                }
            }
            presentation.finish();
        }

        int lastScore = 0;
        session:
        while (true) {
            if (title != null && !skipTitle) {
                playTune(music, modules, title.file());
                if (!showTitle(title, hiscores, lastScore, display, joystick, pacer)) {
                    break;
                }
            }
            skipTitle = false;

            int current = first;
            Stage stage = load(current, levels, modules, loader, sound, music);
            if (!showLoading(current, levels, loading, display, joystick, pacer)) {
                break;
            }
            begin(stage, music, levelMusic);
            AgonyGame game = new AgonyGame(stage.level(), stage.assets(), joystick, sound, screen);
            if (invincible) {
                game.setInvincible(true);
            }
            announce(current, stage);

            pacer.reset();
            while (true) {
                game.videoFrame();
                display.present();

                if (game.levelComplete()) {
                    if (!fadeOut(levelMusic, display, pacer)) {
                        break session;
                    }
                    if (game.gameOver()) {
                        // Every level loads the title program when the last
                        // life is gone; it asks for initials if the score
                        // gets into the table.
                        lastScore = game.score();
                        invincible = game.invincible();
                        if (title == null) {
                            skipTitle = true;
                        }
                        continue session;
                    }
                    int score = game.score();
                    int lives = game.lives();
                    int weapon = game.weapon();
                    boolean axeUp = game.axeUp();
                    boolean axeDown = game.axeDown();
                    boolean[] spells = game.spells().clone();
                    boolean keepInvincible = game.invincible();

                    if (current == LEVELS && ending != null) {
                        // The last level loads the end of the game, which goes
                        // on to the title program, score and all.
                        playTune(music, modules, ending.file());
                        if (!showEnding(ending, display, joystick, pacer)) {
                            break session;
                        }
                        lastScore = score;
                        continue session;
                    }
                    current = current % LEVELS + 1;
                    stage = load(current, levels, modules, loader, sound, music);
                    if (!showLoading(current, levels, loading, display, joystick, pacer)) {
                        break session;
                    }
                    begin(stage, music, levelMusic);
                    game = new AgonyGame(stage.level(), stage.assets(), joystick, sound, screen);
                    game.inherit(score, lives, weapon, axeUp, axeDown, spells);
                    if (keepInvincible) {
                        game.setInvincible(true);
                    }
                    announce(current, stage);
                    pacer.reset();
                }

                if (!pacer.waitFrame()) {
                    break session;
                }
            }
        }
        paula.close();
    }

    /**
     * Runs the title screen until fire has been pressed and the story page has
     * stayed for as long as the first level takes to load.
     *
     * @return false when the loop was interrupted
     */
    private static boolean showTitle(TitleData.Found title, HiScores hiscores, int score,
                                     Display display, Joystick joystick, Pacer pacer) {
        TitleScreen screen = new TitleScreen(title.data(), hiscores, score, joystick);
        pacer.reset();
        while (screen.update(joystick)) {
            display.present(screen.frame(), screen.width(), screen.height());
            if (!pacer.waitFrame()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Runs the end of the game until fire is pressed.
     *
     * @return false when the loop was interrupted
     */
    private static boolean showEnding(TitleData.Found ending, Display display,
                                      Joystick joystick, Pacer pacer) {
        EndingScreen screen = new EndingScreen(ending.data());
        pacer.reset();
        while (screen.update(joystick)) {
            display.present(screen.frame(), screen.width(), screen.height());
            if (!pacer.waitFrame()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Starts the music of a level once its loading screen is gone: the driver
     * of its overlay takes over from the loading tune.
     */
    private static void begin(Stage stage, ModPlayer music, TelPlayer levelMusic) {
        if (stage.song() != null) {
            music.stop();
            levelMusic.start(stage.song());
        }
    }

    /**
     * The end of a level, game over or not: {@code Exit} fades the four voices
     * at a speed of 90 and waits fifty frames before it leaves.
     *
     * @return false when the loop was interrupted
     */
    private static boolean fadeOut(TelPlayer levelMusic, Display display, Pacer pacer) {
        if (!levelMusic.active()) {
            return true;
        }
        levelMusic.fade(90);
        for (int i = 0; i < 50; i++) {
            display.present();
            if (!pacer.waitFrame()) {
                return false;
            }
        }
        levelMusic.stop();
        return true;
    }

    /** Plays the module a program carries, if the disks gave one. */
    private static void playTune(ModPlayer music, Map<String, OriginalMusic.Module> modules,
                                 String file) {
        for (OriginalMusic.Module m : modules.values()) {
            if (file.equalsIgnoreCase(m.file()) && music.load(m.data())) {
                music.init();
                return;
            }
        }
        music.stop();
    }

    /**
     * Shows the loading screen of a level, the picture the program that loads
     * it puts up while the disk turns.
     *
     * @return false when the loop was interrupted
     */
    private static boolean showLoading(int number, List<OriginalAssets.Found> levels,
                                       Map<String, LoadingScreens.Screen> loading,
                                       Display display, Joystick joystick, Pacer pacer) {
        String file = null;
        for (OriginalAssets.Found found : levels) {
            if (found.level() == number) {
                file = found.file();
            }
        }
        LoadingScreens.Screen entry = file != null ? loading.get(file) : null;
        if (entry == null) {
            return true;
        }
        LoadingScreen screen = new LoadingScreen(entry.picture());
        pacer.reset();
        while (screen.update(joystick)) {
            display.present(screen.frame(), screen.width(), screen.height());
            if (!pacer.waitFrame()) {
                return false;
            }
        }
        return true;
    }

    /** Builds one level, with its own artwork and its own tune. */
    private static Stage load(int number, List<OriginalAssets.Found> levels,
                              Map<String, OriginalMusic.Module> modules, AssetLoader loader,
                              SoundFx sound, ModPlayer music) {
        GameAssets assets = PlaceholderAssets.create();
        LevelData level = DemoLevel.create();

        for (OriginalAssets.Found found : levels) {
            if (found.level() == number) {
                OriginalLevel.Result result = OriginalAssets.convert(found, assets);
                level = result.level();
                assets = result.assets();
            }
        }

        if (AssetLoader.installSounds(sound, assets) == 0) {
            loader.loadSounds(sound);
        }

        // The loading screen plays its module; the level, its own driver.
        LevelMusic song = null;
        for (OriginalAssets.Found found : levels) {
            if (found.level() == number) {
                song = LevelMusic.find(found.binary().data());
            }
        }
        String tune = null;
        OriginalMusic.Module module = OriginalMusic.forLoading(modules, number);
        if (module != null && music.load(module.data())) {
            music.init();
            tune = module.name();
        } else if (!loader.loadMusic(music)) {
            music.stop();
        }
        if (song != null) {
            tune = (tune != null ? tune + " while loading, then " : "")
                    + "the level driver (" + song.samples.size() + " instruments)";
        }
        return new Stage(level, assets, tune, song);
    }

    private static void announce(int number, Stage stage) {
        System.out.println("Level " + number + " " + stage.level().name
                + (stage.tune() != null ? "  music: " + stage.tune() : ""));
    }
}
