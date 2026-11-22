package com.artandmagic.agony.data;

import com.artandmagic.agony.core.Sprite;

/**
 * Everything that does not belong to a particular level: the sorcerer, his
 * weapons, the spell graphics, the bonuses and the status bar font.
 *
 * <p>In the original these sit in the absolute data block of
 * {@code Agony_Parent_.s} ({@code Main_Char/Sorcerer.bin}, {@code FwFire.bin},
 * {@code Bonusses.bin}, {@code Alien_Fire.bin}, {@code Die.bin},
 * {@code Icones.bin}, {@code Arow.bin}, {@code Status/Digit.bin} and
 * {@code Status/Text.bin}).
 */
public final class GameAssets {

    /** Sixteen walking frames of the sorcerer, 32 pixels wide, 16 colours. */
    public SpriteFrame[] sorcerer = new SpriteFrame[16];
    /** The translucent double used by the seeker and the shield. */
    public SpriteFrame[] sorcerer2 = new SpriteFrame[16];
    /** The four levels of the forward shot, indexed by {@code Fw_Fire_Weapon}. */
    public SpriteFrame[] forwardFire = new SpriteFrame[4];
    /** The spinning axe. */
    public SpriteFrame axe;
    /** One alien shot. */
    public SpriteFrame alienFire;
    /** The eight pieces the sorcerer bursts into when he dies. */
    public SpriteFrame[] diePieces = new SpriteFrame[8];
    /** The four bonus shapes: sword, money bag, fire ball, spell scroll. */
    public SpriteFrame[] bonus = new SpriteFrame[4];
    /** The eight spell icons of the menu, drawn on the left of the screen. */
    public SpriteFrame[] icons = new SpriteFrame[8];
    /** The menu cursor. */
    public SpriteFrame arrow;
    /** The cross drawn over a spell the player does not own. */
    public SpriteFrame lock;

    /**
     * Vertical travel of the axes for each of the 96 horizontal offsets they can
     * sit at ({@code Main_Char/Axe_Traj.bin}).
     */
    public int[] axeTraj;
    /**
     * Eight rotation steps of eight (dx, dy) pairs, four for each of the two
     * sprite channels the rotative fire ball uses ({@code Main_Char/Rot.bin}).
     */
    public int[] rotTable;
    /** Vertical wave of the forward fire ball ({@code Main_Char/Sin_Table.bin}). */
    public int[] sinTable;
    /** Height of the hour glass for each step of the stop time spell. */
    public int[] timeYTable;
    /** Trajectory of the eight death pieces, (dx, dy) pairs per piece. */
    public int[][] dieTraj;
    /** Which of the four drawn shapes each bonus number uses ({@code Bonus_Image}). */
    public int[] bonusImage = {0, 1, 2, 3, 3, 3, 3, 3, 3, 3, 3, 0};
    /** Colour 0 for each of the 192 scanlines, as the copper writes it. */
    public int[] backgroundGradient;
    /** Colour 9, the bare sky, for each scanline. */
    public int[] skyGradient;
    /** Horizontal offsets of the rain drops ({@code Table/Rain_X.bin}, doubled). */
    public int[] rainX;
    /** The 24 drop template of {@code Rain_Spr0}, 15 lines of two bitplanes. */
    public int[] rainDrop;

    /** Spell graphics, one entry per spell number, may be null. */
    /** The three parts of the back fire ball: 13, 5 and 13 lines. */
    public SpriteFrame[] backFireBall = new SpriteFrame[3];
    /** Four balls of the rotative fire ball, per sprite channel. */
    public SpriteFrame[] rotFireBall0 = new SpriteFrame[4];
    public SpriteFrame[] rotFireBall1 = new SpriteFrame[4];
    /** Sixteen shapes of the hour glass, each two sprites side by side. */
    public SpriteFrame[] stopTimeLeft = new SpriteFrame[16];
    public SpriteFrame[] stopTimeRight = new SpriteFrame[16];
    /** Six frames of the smart bomb, left and right column. */
    public SpriteFrame[] smartBombLeft = new SpriteFrame[6];
    public SpriteFrame[] smartBombRight = new SpriteFrame[6];
    /** The four sprites the mega blast is made of. */
    public SpriteFrame[] megaBlast = new SpriteFrame[4];
    /** Two balls per channel for the forward fire ball. */
    public SpriteFrame[] fwFireBall = new SpriteFrame[2];

    /** Sorcerer palette, colours 16..31 ({@code Sorcerer.pal}). */
    public int[] sorcererPalette = new int[16];
    /** Spell menu palette, swapped in while the menu is open ({@code Icones.pal}). */
    public int[] iconesPalette = new int[16];

    /**
     * The 8 by 16 status bar font.  The original indexes it with the character
     * code shifted left by four, and the digits happen to sit at the start.
     */
    public byte[][] font = new byte[64][];

    /** The fourteen status bar sentences of {@code Status/Text.bin}. */
    public String[] texts = new String[16];
    /**
     * The same sentences as font indices, which is how {@code Status/Text.bin}
     * stores them; when this is set it is used in preference to {@link #texts},
     * because the original font is not laid out alphabetically.
     */
    public int[][] textIndices;

    /** Priorities of the four sound effects ({@code Pri_Table}). */
    public int[] soundPriority = {4, 3, 1, 2};
    /**
     * The four effect samples of {@code Sounds/Sound.bin}, signed eight bit:
     * bonus taken, alien destroyed, the sorcerer fires, alien hit.
     */
    public byte[][] soundSamples = new byte[4][];
    /** Replay period the original gives the effect channel. */
    public int soundPeriod = 277;

    /** Explosion sprites, drawn in place of a dying alien. */
    public Sprite[] explosion = new Sprite[8];

    /**
     * Frame of the explosion animation for each of the fifteen explosion
     * states, {@code Explo_Spr_List} in {@code Agony_Parent_.s}.
     */
    public static final int[] EXPLO_SPR_LIST = {
        0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 7
    };

    /** Spell durations in binary coded decimal ({@code Time_Table}). */
    public static final int[] SPELL_TIME = {0x15, 0x06, 0x05, 0x10, 0x08, 0x06, 0x12, 0x08};

    /** Order in which spells are handed out as bonuses ({@code Spell_Pri}). */
    public static final int[] SPELL_PRI = {4, 1, 0, 7, 2, 3, 6, 5};
}
