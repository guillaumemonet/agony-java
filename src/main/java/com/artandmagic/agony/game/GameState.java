package com.artandmagic.agony.game;

import com.artandmagic.agony.core.Hw;

/**
 * Every game variable of the original, gathered in one place.
 *
 * <p>These are the labels of the VARIABLE block at the end of
 * {@code Agony_Parent_.s} plus the handful of absolute variables that live in
 * the level files ({@code Life}, {@code Score}, {@code Fw_Fire_Weapon},
 * {@code Spell_Advailable}).  Names are kept close to the originals so the
 * assembly can still be read side by side with this port.
 */
public final class GameState {

    // ---------------------------------------------------------------- timing
    /** Counts video frames inside one 25 Hz game frame. */
    public int gen25hzPhase;
    /** True on the frame the original detects as a short one, used to rate limit. */
    public boolean shortPhase;

    // ---------------------------------------------------------------- scroll
    /** Fine scroll of the back playfield, 0..31, decremented once per frame. */
    public int backShift = 31;
    /** Fine scroll of the front playfield, 32 down to 0 over sixteen frames. */
    public int frontShift = 33;
    public int frontPhase;
    public int backPhase;
    public int backConfig;
    /** Long scroll position of the front playfield, in pixels. */
    public int frontScroll;
    /** Long scroll position of the back playfield, in pixels. */
    public int backScroll;
    /** Parallax offsets of the six sky bands. */
    public final int[] skyScroll = new int[Hw.BANDS];
    /** Set when the video frame must not advance the fine scroll twice. */
    public boolean vdoSInc;
    /** Value of frontShift the object module works from, {@code Front_Shift_Phase}. */
    public int frontShiftPhase;

    // ------------------------------------------------------------ level flow
    /** Advances by two on every game frame and drives the start list. */
    public int levelX = -32;
    /** Value of levelX at which the next start list entry fires. */
    public int slWaiting;
    /** Index of the next start list entry. */
    public int startListIndex;
    /** Set when the front map ran out: the level stops scrolling. */
    public boolean prgPause;
    /** Frames left before the level is left; 1 triggers the exit. */
    public int quitDelay;
    /** Set when everything must be wound down (death or abort). */
    public boolean cleanUp;
    /** Set when the level is over and the next one should be loaded. */
    public boolean levelComplete;
    /** Set when the last life is gone and the game goes back to the start. */
    public boolean gameOver;
    /** 0 playing, 1 title text shown, 2 title text hidden. */
    public int beginToStart = 1;
    public int btsDelay;

    // -------------------------------------------------------------- the hero
    /** Sorcerer position, biased by 256. */
    public int sorcererX = 64 + Hw.BIAS;
    public int sorcererY = 50 + Hw.BIAS;
    public int sorcererShape;
    public int sorcererDelay;
    /** True while the sorcerer is vulnerable and visible. */
    public boolean sorcererOn = true;
    /** The ghost used by the seeker and the shield spells. */
    public int sorcerer2X;
    public int sorcerer2Y;
    public int sorcerer2Shape;

    /** The two orbiting axes. */
    public int axeUpX = 64 + Hw.BIAS;
    public int axeUpY = 4 + Hw.BIAS;
    public int axeDownX = 64 + Hw.BIAS;
    public int axeDownY = 160 + Hw.BIAS;
    public int axeDelay;
    public boolean axeMove;
    public boolean axeUpOn;
    public boolean axeDownOn;

    // ------------------------------------------------------------ the weapon
    /** Value of fwFireStep that marks no shot in flight. */
    public static final int FW_F_OFF = 20 * 12;
    /** Distance travelled by the forward shot; FW_F_OFF means idle. */
    public int fwFireStep = FW_F_OFF;
    public int fwFirePhase;
    /** Set while the forward shot is suppressed (mega blast, death). */
    public boolean fwFireOff;
    /** Weapon level, 0..3, raised by the fire ball bonus. */
    public int fwFireWeapon;

    // --------------------------------------------------------------- scoring
    /** Binary coded decimal score, as manipulated by abcd. */
    public int score;
    public int oldScore = -1;
    /** Points to add on the next status update, binary coded decimal. */
    public int point;
    /** Score accumulated towards the next extra life. */
    public int extraLife;
    /** Lives, one bit per life. */
    public int life = 7;
    public int oldLife = -1;

    // ---------------------------------------------------------------- spells
    /** Spell currently running, -1 when none. */
    public int curentSpell = -1;
    public int safeCurSpell;
    /** Remaining spell time, binary coded decimal. */
    public int spellTime;
    public int oldSpellTime = -1;
    public int spellTimeDelay;
    /** Which of the eight spells the player owns. */
    public final boolean[] spellAvailable = new boolean[8];
    /** Round robin index used when picking the next spell bonus. */
    public int spellNextBonus;

    /** True while the spell menu is open. */
    public boolean iconesMode;
    public boolean iconesOff;
    public boolean iconesFireUp;
    public boolean selectionOn;
    public int arowY;
    public boolean arowUp;
    public boolean arowDown;
    /** Frames the fire button has been held, used to open the menu. */
    public int fireCount;
    /** When false the menu can only be opened with the menu key. */
    public boolean menuMode = true;

    /** Spell working variables. */
    public int rotStep;
    public int backFbX0;
    public int backFbX1 = -100;
    public int fwFbX0;
    public int fwFbX1 = 128;
    public int smartBX;
    public int smartBStep;
    public int smartBStepH;
    public int smartBStepHC;
    public int megaBX;
    public int timeStep;
    public int timeY;

    // ----------------------------------------------------------------- death
    public boolean die;
    /**
     * Not in the original: the sorcerer ignores every hit, a port only aid to
     * reach the end of a level.  Toggled with F5 or {@code --invincible}.
     */
    public boolean invincible;
    public int dieMode;
    public int dieX;
    public int dieY;
    /** Animation cursor of each of the eight pieces the sorcerer bursts into. */
    public final int[] dieStep = new int[8];

    // ----------------------------------------------------------------- bonus
    /** 0 waiting, 1 armed, 2 falling, 3 rolling on the ground. */
    public int bonusMode;
    public int bonusAnim;
    public int bonusDelay;
    public int bonusX;
    public int bonusY;
    public int bonusNum;

    // ------------------------------------------------------------ pause/stop
    /** Pause freezes the whole game, stop only freezes the scrolling. */
    public boolean pause;
    public boolean pause2;
    public boolean stop = true;
    public boolean stop2;

    // ---------------------------------------------------------- status panel
    public int textNum = 13;
    public int oldTextNum = -1;
    public int textDelay = -1;
    public boolean refreshStatus;
    public int statusDelay = 18;

    // --------------------------------------------------------------- effects
    public boolean rainOn;
    public int rainXOff0;
    public int rainXOff1;
    public boolean flashPhase;
    /** Set when the front palette must be rebuilt from the map and the waves. */
    /**
     * {@code Refresh_Pal}, which is a word and not a flag.
     *
     * <p>PALETTE CTRL of {@code Ag_Playability_.s} reads it three ways: zero
     * means there is nothing to do, exactly one forces the base palette to be
     * copied back over the working buffer, and anything else only asks for a
     * refresh, which the copy is allowed to skip while two or more routines are
     * holding the palette.  The single place that writes one is the removal of
     * a track, where the modulation of the wave that just ended has to be taken
     * back off; everywhere else sets the byte with {@code st}, which is 255.
     */
    public int refreshPal;
    /** {@code move #1,_Refresh_Pal}: copy the base palette whatever happens. */
    public static final int PAL_FORCED = 1;
    /** {@code st Refresh_Pal+1}: a refresh the copy may skip. */
    public static final int PAL_ASKED = 0xff;
    /** Palette override installed by a routine, or null. */
    public int[] routPalette;
    public int routModPalCounter;
    /** Set to blank every alien shot on the screen. */
    public boolean afOff;

    /**
     * Attack waves a routine asked for, each {@code {wave, startX, startY}}.
     * The original grabs a free track slot inside the routine; draining the
     * list right after the routines run amounts to the same thing, because the
     * follower has already passed for this frame.
     */
    public final java.util.List<int[]> pendingWaves = new java.util.ArrayList<>();

    /**
     * Level routines a routine started itself, as the final monster of the
     * highlands does with its slaves: it walks {@code Rout_Struct} for a free
     * slot and {@code AWO_Struct} for a free bank, and fills both in by hand.
     * The routine manager installs each request as soon as the routine that
     * made it returns, so a slot further down the table still runs in the same
     * frame, exactly as in the original.
     */
    public final java.util.List<PendingRoutine> pendingRoutines = new java.util.ArrayList<>();

    /** A routine to start, and the parameter words it reads. */
    public record PendingRoutine(com.artandmagic.agony.data.Routine routine, int[] params) {
    }

    /**
     * {@code Sound0_Req} and {@code Sound0_Vol_Req}: the sound a level routine
     * asks for before it calls {@code Sound_Start}, -1 when none.  The main
     * loop hands it to the sound driver once the routines have run.
     */
    public int soundRequest = -1;
    public int soundVolume;

    /** Asks for a sound the way the level routines do. */
    public void requestSound(int number, int volume) {
        soundRequest = number;
        soundVolume = volume;
    }
    /**
     * Raised by the end of level routine: it wipes the three front screens and
     * frees every track, so nothing is left but its own explosion.
     */
    public boolean clearScenery;

    // ------------------------------------------------------------ collisions
    /**
     * Good_Col_List: the eight rectangles the player owns.  Slot 0 is the
     * sorcerer himself, 1 the forward shot, 2 and 3 the axes and 4..7 the
     * current spell.  A x1 of -1 disables the slot.
     */
    public final int[] goodCol = new int[8 * 4];

    public GameState() {
        clearCollisions(0, 8);
    }

    /** Disables count collision rectangles starting at first. */
    public void clearCollisions(int first, int count) {
        for (int i = 0; i < count; i++) {
            int b = (first + i) * 4;
            goodCol[b] = -1;
            goodCol[b + 1] = -1;
            goodCol[b + 2] = -1;
            goodCol[b + 3] = -1;
        }
    }

    /** Fills one collision rectangle. */
    public void setCollision(int slot, int x1, int y1, int x2, int y2) {
        int i = slot * 4;
        goodCol[i] = x1;
        goodCol[i + 1] = y1;
        goodCol[i + 2] = x2;
        goodCol[i + 3] = y2;
    }

    /** Adds a binary coded decimal amount to the points waiting to be scored. */
    public void addPoints(int bcd) {
        point = Bcd.add(point, bcd);
    }
}
