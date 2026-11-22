package com.artandmagic.agony.game;

import com.artandmagic.agony.audio.SoundFx;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.Palette;
import com.artandmagic.agony.core.Screen;
import com.artandmagic.agony.core.SpriteEngine;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.LevelData;
import com.artandmagic.agony.input.Joystick;

/**
 * The main module, {@code Agony_Parent_.s}.
 *
 * <p>The original splits the game over two rates.  The main loop runs at 25 Hz:
 * it scrolls both playfields, redraws the aliens, runs the level script, the
 * collisions and the level routines, updates the status line and moves the
 * alien shots.  The level 3 interrupt runs at 50 Hz and handles everything made
 * of hardware sprites: the sorcerer, his axes, his shot, the spell menu, the
 * spells, the death sequence, the bonuses and the rain.
 *
 * <p>{@link #videoFrame()} is that interrupt and calls {@link #gameFrame()}
 * every other time, exactly as {@code Gen_25hz_Phase} does.
 */
public final class AgonyGame {

    private final LevelData level;
    private final GameAssets assets;
    private final Joystick joystick;

    public final GameState state = new GameState();
    public final Palette palette = new Palette();
    /** The compositor outlives a level, so the window keeps the same frame. */
    public final Screen screen;
    public final SpriteEngine sprites = new SpriteEngine();

    private final FrontScroll frontScroll;
    private final BackScroll backScroll;
    private final TrackManager tracks;
    private final ObjectRenderer objects;
    private final Collisions collisions;
    private final Player player;
    private final SpellMenu menu;
    private final Spells spells;
    private final AlienFire alienFire;
    private final DeathSequence death;
    private final BonusManager bonus;
    private final StatusBar status;
    private final Rain rain;
    private final SoundFx sound;

    public AgonyGame(LevelData level, GameAssets assets, Joystick joystick, SoundFx sound) {
        this(level, assets, joystick, sound, new Screen());
    }

    public AgonyGame(LevelData level, GameAssets assets, Joystick joystick, SoundFx sound,
                     Screen screen) {
        this.level = level;
        this.assets = assets;
        this.joystick = joystick;
        this.sound = sound;
        this.screen = screen;

        frontScroll = new FrontScroll(level, state);
        backScroll = new BackScroll(level, state);
        tracks = new TrackManager(level, state);
        objects = new ObjectRenderer(level, state, tracks);
        collisions = new Collisions(state, tracks, objects, sound);
        player = new Player(state, assets, joystick, sound);
        menu = new SpellMenu(state, assets, joystick);
        spells = new Spells(state, assets, tracks);
        alienFire = new AlienFire(state, assets, tracks, objects);
        death = new DeathSequence(state, assets);
        bonus = new BonusManager(state, assets, sound);
        status = new StatusBar(state, assets);
        rain = new Rain(state, assets);

        reset();
    }

    /** Global initialisation, the block that follows {@code Restart}. */
    public void reset() {
        palette.setSpritePalette(assets.sorcererPalette);
        palette.setBackgroundGradient(assets.backgroundGradient);
        palette.setSkyGradient(assets.skyGradient);
        frontScroll.reset();
        backScroll.reset();
        frontScroll.prime(palette);
        backScroll.prime(palette);
        status.clear();

        state.rainOn = level.rain;
        state.textNum = 13;
        state.textDelay = -1;
        state.beginToStart = 1;
        state.stop = true;
    }

    /** One 50 Hz video frame: the level 3 interrupt plus the main loop. */
    public void videoFrame() {
        keyboard();

        sprites.clear();
        player.update(sprites);
        menu.update(sprites, palette);
        spells.tickTimer();
        spells.update(sprites, palette);
        death.update(sprites, palette);
        bonus.update(sprites, palette);
        alienFire.draw(sprites);
        rain.update(sprites, palette);

        // The fine scroll of the front playfield advances once per video frame.
        if (!state.stop2 && !state.vdoSInc) {
            state.vdoSInc = true;
            state.frontShift--;
        }

        state.gen25hzPhase++;
        if (state.gen25hzPhase >= 2) {
            state.gen25hzPhase = 0;
            gameFrame();
        }

        compose();
    }

    /** One 25 Hz game frame, the body of {@code Main_Loop}. */
    private void gameFrame() {
        pauseControl();

        frontScroll.update(palette);
        backScroll.update(palette);

        // The original measures the raster position here to find out whether it
        // had time to spare; with no raster to race, every frame is a short one.
        state.shortPhase = true;

        frontScroll.restore();
        objects.render(frontScroll.work, frontScroll.bufferOrigin());

        tracks.update(frontScroll, palette);
        if (!state.pause2) {
            // Pause2 leaves Ag_Playability_.s at once, so neither the collision
            // test nor the routine manager is reached.  The routine manager has
            // a second door of its own: spell two closes it.
            collisions.update();
            if (state.curentSpell != 2) {
                tracks.updateRoutines();
            }
            if (state.soundRequest >= 0) {
                sound.request(state.soundRequest, state.soundVolume);
                state.soundRequest = -1;
            }
        }

        status.update();
        sound.update();
        sound.poll();
        alienFire.update();

        frontScroll.control();

        beginToStart();
        clearScenery();
        endOfMap();
        cleanUp();
        quitDelay();
    }

    /**
     * {@code Pause2} and {@code Stop2} only follow {@code Pause} and
     * {@code Stop} when the fine scroll sits on a character boundary, so the
     * scrolling never freezes halfway through a column.
     */
    private void pauseControl() {
        int shift = state.frontShift;
        if (shift == 0 || shift == 15 || shift == 31) {
            return;
        }
        if (state.stop || (!state.prgPause && state.curentSpell != 2)) {
            state.stop2 = state.stop;
        }
        state.pause2 = state.pause;
    }

    private void beginToStart() {
        if (state.beginToStart == 0) {
            return;
        }
        if (joystick.fire()) {
            state.stop = false;
            state.textDelay = 2;
            state.beginToStart = 0;
            state.oldTextNum = -1;
            return;
        }
        state.btsDelay++;
        if (state.btsDelay < 10) {
            return;
        }
        state.btsDelay = 0;
        if (state.beginToStart == 1) {
            state.beginToStart = 2;
            status.bitmap.clear();
        } else {
            state.beginToStart = 1;
            state.textNum = 13;
            state.textDelay = -1;
            state.oldTextNum = -1;
        }
    }

    /**
     * Leaves the level once the scenery has run out.
     *
     * <p>{@code Prg_Pause} is raised by the front scroll when the map ends and
     * by the back scroll when the pattern list does, both straight from the
     * sources.  What the original does next is run the end of level routine of
     * {@code Ag_Game_*.s}, which sets {@code Quit_Delay} itself; that routine is
     * 68000 code and is not in the source release, so the port sets the same
     * delay once the scenery is over and nothing is left on screen.
     */
    private void endOfMap() {
        if (level.routineEndsLevel) {
            // The script still has its final monster to bring in over the
            // stopped scenery, and that routine ends the level itself.
            return;
        }
        if (!state.prgPause || state.quitDelay != 0 || state.cleanUp || state.die) {
            return;
        }
        for (AlienBank bank : tracks.banks) {
            if (!bank.isFree() && bank.count > 0) {
                return;
            }
        }
        state.quitDelay = 50;
    }

    /** The wipe the end of level routine asks for. */
    private void clearScenery() {
        if (!state.clearScenery) {
            return;
        }
        state.clearScenery = false;
        frontScroll.scenery.clear();
        frontScroll.work.clear();
        for (Track t : tracks.tracks) {
            t.release();
        }
    }

    private void cleanUp() {
        if (!state.cleanUp) {
            return;
        }
        tracks.cleanUp();
    }

    private void quitDelay() {
        if (state.quitDelay == 0) {
            screen.setFade(256);
            return;
        }
        // The original fades over the delay it counts down before leaving.
        screen.setFade(Math.min(256, state.quitDelay * 256 / 40));
        state.quitDelay--;
        if (state.quitDelay == 1) {
            exit();
        }
    }

    /**
     * End of level, the {@code Exit} block of {@code Agony_Parent_.s}.
     *
     * <p>The original fades the screen out, waits fifty frames and then loads
     * the next file: the following level when a life is left, the game over
     * screen otherwise.  The port raises a flag instead and lets the driver
     * bring the next level in.
     */
    private void exit() {
        state.quitDelay = 0;
        state.cleanUp = false;
        state.stop = false;
        state.prgPause = false;
        state.gameOver = state.life == 0;
        state.levelComplete = true;
    }

    /** True once the level is over and the next one should be loaded. */
    public boolean levelComplete() {
        return state.levelComplete;
    }

    /** True when that ending was a game over rather than a level cleared. */
    public boolean gameOver() {
        return state.gameOver;
    }

    /** Score and lives to carry into the next level. */
    public int score() {
        return state.score;
    }

    public int lives() {
        return state.life;
    }

    /** Carries the player state over from the level just finished. */
    public void inherit(int score, int lives, int weapon, boolean axeUp, boolean axeDown,
                        boolean[] spells) {
        state.score = score;
        state.life = lives;
        state.fwFireWeapon = weapon;
        state.axeUpOn = axeUp;
        state.axeDownOn = axeDown;
        System.arraycopy(spells, 0, state.spellAvailable, 0, spells.length);
        state.oldScore = -1;
        state.oldLife = -1;
    }

    /** Port only: the sorcerer ignores every hit while this is on. */
    public void setInvincible(boolean on) {
        state.invincible = on;
        System.out.println("Invincible: " + (on ? "on" : "off"));
    }

    public boolean invincible() {
        return state.invincible;
    }

    public int weapon() {
        return state.fwFireWeapon;
    }

    public boolean axeUp() {
        return state.axeUpOn;
    }

    public boolean axeDown() {
        return state.axeDownOn;
    }

    public boolean[] spells() {
        return state.spellAvailable;
    }

    // --------------------------------------------------------------- keyboard

    /** The keys handled by {@code Keyboard_Int}. */
    private void keyboard() {
        if (joystick.takePause()) {
            togglePause();
        }
        if (joystick.takeMenu()) {
            openMenu();
        }
        if (joystick.takeMode()) {
            state.menuMode = !state.menuMode;
        }
        if (joystick.takeAbort() && state.quitDelay == 0) {
            state.quitDelay = 20;
            state.life = 0;
            state.cleanUp = true;
        }

        // F1 to F4, the debug keys of the original.
        if (joystick.takeCheat(0)) {
            state.axeDownOn = !state.axeDownOn;
            if (state.axeDownOn) {
                state.axeDownX = state.sorcererX;
                state.axeUpX = state.sorcererX;
            }
        }
        if (joystick.takeCheat(1)) {
            state.axeUpOn = !state.axeUpOn;
            if (state.axeUpOn) {
                state.axeUpX = state.sorcererX;
                state.axeDownX = state.sorcererX;
                java.util.Arrays.fill(state.spellAvailable, true);
            }
        }
        if (joystick.takeCheat(2)) {
            state.fwFireWeapon = (state.fwFireWeapon + 1) & 3;
            state.point = 0x20000;
        }
        if (joystick.takeCheat(4)) {
            setInvincible(!state.invincible);
        }
        if (joystick.takeCheat(3)) {
            state.fwFireWeapon = 3;
            state.point = 0x80000;
            state.axeDownOn = true;
            state.axeUpOn = true;
        }
    }

    private void togglePause() {
        if (state.beginToStart != 0 || state.die) {
            return;
        }
        if (state.pause) {
            state.pause = false;
            state.stop = false;
            state.textDelay = 2;
            if (state.iconesMode) {
                state.iconesOff = true;
                state.curentSpell = state.safeCurSpell;
                state.oldTextNum = -1;
            }
        } else {
            state.pause = true;
            state.stop = true;
            state.textNum = 10;
            state.textDelay = -1;
        }
    }

    private void openMenu() {
        if (state.die || state.beginToStart != 0 || state.quitDelay != 0) {
            return;
        }
        if (state.iconesMode) {
            togglePause();
            return;
        }
        if (state.curentSpell == 2) {
            return;
        }
        player.openMenu();
    }

    // ---------------------------------------------------------------- display

    private void compose() {
        screen.compose(frontScroll.work, frontScroll.scrollOffset(),
                backScroll.back, backScroll.scrollOffset(),
                backScroll.sky, state.skyScroll, palette);
        screen.renderSprites(sprites, palette);
        screen.present(status.bitmap);
    }

    /** The alien banks and tracks, for tools that want to inspect the level. */
    public TrackManager tracks() {
        return tracks;
    }

    /** The composed frame, 640 by 422 ARGB pixels. */
    public int[] frame() {
        return screen.frame();
    }

    /** Height of the playfield, for callers that want to letterbox. */
    public static int height() {
        return Hw.OUT_H;
    }
}
