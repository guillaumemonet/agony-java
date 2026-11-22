package com.artandmagic.agony.data;

import com.artandmagic.agony.core.Sprite;

import java.util.HashMap;
import java.util.Map;

/**
 * One level: its scenery, its enemies and its script.
 *
 * <p>The original assembles a separate executable per level out of
 * {@code Front.Map}, {@code Front.Pal}, {@code Front.Bin}, {@code Back.*},
 * {@code Sky.bin}, {@code Objects.*} and {@code Game/Ag_Game_L<name>.s}; the
 * fields below hold the same tables in a form the Java engine can walk.
 */
public final class LevelData {

    /** Level name, shown while the level loads. */
    public String name = "SEA";

    // ------------------------------------------------------------ front scroll
    /** 32 by 32 characters of the front playfield, colours 0..7. */
    public Sprite[] frontChars = new Sprite[0];
    /**
     * The map, column major: six cells per column, top to bottom.
     *
     * <p>A cell is two bytes, which is the step {@code Ag_Front_Scroll_.s}
     * takes through {@code Front.Map}.  CHAR INIT lays the first character down
     * flat, then builds {@code Front_Mask} from the three planes of the second
     * one, and CHAR COPY cuts that second character over the first through the
     * mask.  Every cell of the scenery is those two characters on top of each
     * other, so reading only the first leaves the map half drawn.
     */
    public byte[] frontMap = new byte[0];
    /** Height of the map, in characters. */
    public static final int FRONT_ROWS = 6;
    /** Bytes per map cell: the character, then the one cut over it. */
    public static final int FRONT_CELL = 2;
    /**
     * Front palettes, one entry every ten character columns, each holding the
     * seven colours of the six bands ({@code Front.Pal}, read six words at a
     * time by the palette control of {@code Ag_Playability_.s}).
     */
    public int[][] frontPalettes = new int[0][];

    // ------------------------------------------------------------- back scroll
    /** 32 by 32 characters of the back playfield, colours 0..3. */
    public Sprite[] backChars = new Sprite[0];
    /**
     * Back patterns: each entry is the list of characters of one screen wide
     * band of scenery, plus the three colours of each of the six bands
     * ({@code Back.pat} and {@code Back.inf}).
     */
    public BackPattern[] backPatterns = new BackPattern[0];
    /** Order in which the patterns are played, ended by -1 ({@code Pat_Ptr}). */
    public int[] backSequence = {0, -1};

    /**
     * The sky as one ready made plane, 320 by 192, one bit per pixel.  The
     * original blits four tiles of 36 bytes by 40 lines with a source modulo of
     * -2, which slants them into a seamless band; the result is kept here.
     */
    public byte[] skyBitmap;

    /** The one plane sky, 32 pixels per character, colours 0..1. */
    public Sprite[] skyChars = new Sprite[0];
    /** Sky map, column major, one character per column and per band. */
    public byte[] skyMap = new byte[0];
    /** Parallax speed of each sky band, in sixteenths of a pixel per frame. */
    public int[] skySpeed = {1, 2, 3, 4, 5, 6};

    // ----------------------------------------------------------------- enemies
    /** Blitter objects the aliens are drawn with ({@code Objects.bin}). */
    public Sprite[] objectSprites = new Sprite[0];
    /**
     * Alien descriptions ({@code Objects.obj}), keyed by the byte offset the
     * animation tables refer to them by, which is what {@code AWO_Alien_Obj_Off}
     * holds.
     */
    public Map<Integer, ObjectDef> objects = new HashMap<>();
    /**
     * Reads an object record straight out of the level, for an offset the map
     * has never been asked for.
     *
     * <p>The original never keeps such a map: {@code Ag_Object_.s} adds
     * {@code AWO_Alien_Obj_Off} to {@code Objects_Struct} and reads the record
     * where it lands, so any offset a routine cares to write is valid.  The
     * conversion can only walk the attack waves, which leaves out every shape a
     * level routine installs by hand: the final monster and its explosions, the
     * bomb carrier, the vial, the guided missile.  Those are read on demand
     * through here, which is what the original does on every frame anyway.
     */
    public java.util.function.IntFunction<ObjectDef> objectDecoder;
    /**
     * Index of the first explosion sprite in {@link #objectSprites}; the
     * original calls it {@code First_Explo_Spr} and sets it per level.
     */
    public int firstExploSprite = 1;
    /**
     * Animation sequences, keyed by their byte offset into {@code Anim_Base};
     * each is the list of object offsets to play, the original ending it with a
     * negative word.
     */
    public Map<Integer, int[]> animations = new HashMap<>();
    /** Movement tables ({@code Relative_Tracks} and {@code Absolute_Tracks}). */
    public TrackTable[] tracks = new TrackTable[0];
    /** Attack waves ({@code ATTACK_WAVE_START} records). */
    public AttackWave[] waves = new AttackWave[0];
    /** The level script ({@code Start_list}). */
    public StartList startList = new StartList();

    /**
     * True when a level routine ends the level ({@link RoutineLibrary#endsLevel}),
     * so the end of the scenery does not.
     */
    public boolean routineEndsLevel;

    /** True on the first level, the only one with rain. */
    public boolean rain;

    /**
     * Character numbers of every back scroll frame, 30 per frame
     * ({@code Back.frm}); a frame is the half screen the original redraws on
     * each of its two phases.
     */
    public int[] backFrames;
    /**
     * {@code Table/Back_Read.bin}: for each of the five configurations, each of
     * the two phases and each of the thirty positions, which of the three
     * pattern layers to take the character from and where in its frame.  Laid
     * out flat as (layer, offset) pairs.
     */
    public int[] backReadTable;
    /**
     * {@code Write_Table}: the destination of those thirty positions, as
     * (column, band) pairs.
     */
    public int[] backWriteTable;

    /** One entry of the back scroll pattern table. */
    public static final class BackPattern {
        /** Character numbers, column major, six per column. */
        public byte[] map = new byte[0];
        /** Animation of the pattern: (frame, delay) pairs. */
        public int[] sequence = new int[0];
        /** Background colour of each of the six bands. */
        public int[] background = new int[6];
        /** The three back scroll colours of each of the six bands. */
        public int[][] colors = new int[6][3];
        /** The two sky colours of each of the six bands. */
        public int[][] sky = new int[6][2];
        /** Frames each column of the pattern is held for. */
        public int delay = 1;
    }
}
