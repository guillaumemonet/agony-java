package com.artandmagic.agony.game;

import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.Palette;
import com.artandmagic.agony.core.SpriteEngine;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.SpriteFrame;

/**
 * The eight spells, the SPELL ROUTINES block of {@code Ag_Sprites.s}.
 *
 * <p>A spell lasts as long as its timer, counted down in binary coded decimal
 * once every fifty video frames, and owns the last four player collision
 * rectangles.  Each one also rewrites colour registers 29 to 31, which is how
 * the same three sprite colours give every spell its own look.
 *
 * <p>Positions are transcribed from the assembly, so they carry its two
 * conversions: a sprite x is a hardware start position, {@code $90} at the left
 * edge of the display window, and a sprite y is counted from
 * {@link #SPR_Y_OFF}.  Game coordinates are biased by 256 throughout.
 */
public final class Spells {

    /** First player collision rectangle a spell may use. */
    private static final int SLOT = 4;
    /** Vertical offset of the sprite DMA on a PAL machine. */
    private static final int SPR_Y_OFF = 0x40;
    /** Hardware x of the left edge of the display window. */
    private static final int DIW_X = 0x90;

    private final GameState state;
    private final GameAssets assets;
    private final TrackManager tracks;

    public Spells(GameState state, GameAssets assets, TrackManager tracks) {
        this.state = state;
        this.assets = assets;
        this.tracks = tracks;
    }

    /** Counts the spell timer down; one 50 Hz video frame. */
    public void tickTimer() {
        if (state.pause2 || state.spellTime == 0) {
            return;
        }
        state.spellTimeDelay--;
        if (state.spellTimeDelay != 0) {
            return;
        }
        state.spellTimeDelay = 50;
        state.spellTime = Bcd.sub(state.spellTime, 1);
        if (state.spellTime == 0) {
            state.curentSpell = -1;
        }
    }

    /** One 50 Hz video frame of the spell in progress. */
    public void update(SpriteEngine sprites, Palette palette) {
        if (state.iconesMode || state.pause2) {
            return;
        }

        state.sorcererOn = true;
        state.fwFireOff = false;
        state.clearCollisions(SLOT, 4);

        switch (state.curentSpell) {
            case 0 -> backFireBall(sprites, palette);
            case 1 -> rotativeFireBall(sprites, palette);
            case 2 -> stopTime(sprites, palette);
            case 3 -> seeker(sprites, palette);
            case 4 -> smartBomb(sprites, palette);
            case 5 -> megaBlast(sprites, palette);
            case 6 -> shield(sprites, palette);
            case 7 -> forwardFireBall(sprites, palette);
            default -> {
            }
        }
    }

    private void colors(Palette palette, int c29, int c30, int c31) {
        palette.setAll(29, c29);
        palette.setAll(30, c30);
        palette.setAll(31, c31);
    }

    /** Screen y of a sprite whose hardware position is {@code vy}. */
    private static int screenY(int vy) {
        return vy - SPR_Y_OFF;
    }

    /** Screen x of a sprite whose hardware start is {@code hx}. */
    private static int screenX(int hx) {
        return hx - DIW_X;
    }

    private void add(SpriteEngine sprites, int channel, SpriteFrame frame, int x, int y) {
        if (frame != null) {
            sprites.add(channel, frame.toSprite(x, y, 16 + (channel / 2) * 4, false));
        }
    }

    // --------------------------------------------------- (0) back fire ball

    /**
     * Two balls thrown backwards, each drawn in three parts that spread apart
     * as the ball travels; when one reaches -100 the other is restarted.
     */
    private void backFireBall(SpriteEngine sprites, Palette palette) {
        palette.setAll(29, 0x058f);
        palette.setAll(30, 0x08cf);
        palette.setHighByte(31, 0xff);

        state.backFbX0 -= 10;
        if (state.backFbX0 == -100) {
            state.backFbX1 = 0;
        }
        state.backFbX1 -= 10;
        if (state.backFbX1 == -100) {
            state.backFbX0 = 0;
        }

        backBall(sprites, 6, state.backFbX0, SLOT,
                assets.backFireBall[0], assets.backFireBall[1], assets.backFireBall[2]);
        backBall(sprites, 7, state.backFbX1, SLOT + 1,
                assets.backFireBall[0], assets.backFireBall[1], assets.backFireBall[2]);
    }

    private void backBall(SpriteEngine sprites, int channel, int travel, int slot,
                          SpriteFrame top, SpriteFrame middle, SpriteFrame bottom) {
        int centre = state.sorcererY - (256 - SPR_Y_OFF - 46);
        int vyTop = Math.max(SPR_Y_OFF - 16, centre + travel - 14);
        int vyMiddle = centre;
        int vyBottom = centre - travel;

        int x = state.sorcererX + travel;
        state.setCollision(slot,
                x, vyTop + (256 - SPR_Y_OFF - 46),
                x + 32, vyBottom + (256 - SPR_Y_OFF - 46 + 14));

        int sx = screenX(x - (256 - DIW_X - 10));
        add(sprites, channel, top, sx, screenY(vyTop));
        add(sprites, channel, middle, sx, screenY(vyMiddle));
        add(sprites, channel, bottom, sx, screenY(vyBottom));
    }

    // ----------------------------------------------- (1) rotative fire ball

    /**
     * Two rings of four balls each, their offsets read from {@code Rot_Table}:
     * eight steps of eight (dx, dy) pairs, the first four for one channel and
     * the last four for the other.
     */
    private void rotativeFireBall(SpriteEngine sprites, Palette palette) {
        colors(palette, 0x0ff9, 0x0f85, 0x0d40);

        int step = state.rotStep;
        state.rotStep++;
        if (step == 8) {
            state.rotStep = 0;
        }

        int[] table = assets.rotTable;
        if (table != null) {
            int at = (step % 8) * 16;
            int hx = state.sorcererX - (256 - 12 - DIW_X);
            int vy = state.sorcererY - (256 - 37 - SPR_Y_OFF);

            for (int i = 0; i < 4; i++) {
                ball(sprites, 6, assets.rotFireBall0[i],
                        hx + table[at + i * 2], vy + table[at + i * 2 + 1]);
            }
            for (int i = 0; i < 4; i++) {
                ball(sprites, 7, assets.rotFireBall1[i],
                        hx + table[at + 8 + i * 2], vy + table[at + 8 + i * 2 + 1]);
            }
        }

        // Four rectangles, one along each side of the sorcerer.
        int x = state.sorcererX;
        int y = state.sorcererY;
        state.setCollision(SLOT, x - 40, y - 60, x + 80, y - 40);
        state.setCollision(SLOT + 1, x + 100, y - 40, x + 120, y + 80);
        state.setCollision(SLOT + 2, x - 40, y + 100, x + 80, y + 120);
        state.setCollision(SLOT + 3, x - 60, y - 40, x - 40, y + 80);
    }

    /** A ball whose hardware y has climbed past the top of the sprite is dropped. */
    private void ball(SpriteEngine sprites, int channel, SpriteFrame frame, int hx, int vy) {
        if (vy <= SPR_Y_OFF - 16) {
            return;
        }
        add(sprites, channel, frame, screenX(hx), screenY(vy));
    }

    // ------------------------------------------------------- (2) stop time

    /**
     * The hour glass falls down the right of the screen along
     * {@code Time_Y_Table} and the level freezes until it reaches $f0.
     */
    private void stopTime(SpriteEngine sprites, Palette palette) {
        colors(palette, 0x0000, 0x0222, 0x0333);

        state.stop = true;
        state.refreshStatus = true;
        state.afOff = true;

        state.timeY++;
        int step = state.timeY;
        if (step >= 0xf0) {
            if (!state.prgPause) {
                state.stop2 = false;
                state.stop = false;
            }
            state.timeY = 0;
            state.curentSpell = -1;
        }

        int[] table = assets.timeYTable;
        int vy = (table != null ? table[step & 0xff] : 0) + SPR_Y_OFF - 50;

        state.timeStep = (state.timeStep + 256) & (15 * 256);
        int shape = state.timeStep / 256;

        // Two lone sprites side by side, sixteen pixels apart.
        int hx = (DIW_X + 240);
        add(sprites, 6, assets.stopTimeLeft[shape], screenX(hx), screenY(vy));
        add(sprites, 7, assets.stopTimeRight[shape], screenX(hx) + 16, screenY(vy));
    }

    // ---------------------------------------------------------- (3) seeker

    /** A double that flies to the alien closest to the sorcerer on the x axis. */
    private void seeker(SpriteEngine sprites, Palette palette) {
        colors(palette, 0x0ff8, 0x0fb0, 0x0f60);

        int targetX = 256 + 180;
        int targetY = 256 + 80;
        int best = 32767;

        for (AlienBank bank : tracks.banks) {
            if (bank.isFree() || bank.count <= 0) {
                continue;
            }
            for (int i = 0; i < bank.count; i++) {
                Alien a = bank.aliens[(bank.first + i) % AlienBank.SIZE];
                if (a.status != 0) {
                    continue;
                }
                int d = Math.abs(a.x - state.sorcererX);
                if (best <= d) {
                    continue;
                }
                best = d;
                targetX = a.x;
                targetY = a.y;
            }
        }

        final int speed = 2;
        int x = state.sorcerer2X;
        int y = state.sorcerer2Y;

        if (targetX <= 256 + 280 && targetX >= 256 && targetY >= 256 && targetY <= 256 + 190) {
            x += x > targetX ? -speed : speed;
            y += y > targetY ? -speed : speed;
            y = Math.min(y, 256 + 192 - 60);
            state.sorcerer2X = x;
            state.sorcerer2Y = y;
        }
        // Within one step of the target the double snaps onto it, for this frame
        // only: the stored position keeps running.
        if (Math.abs(targetX - x) <= speed) {
            x = targetX;
        }
        if (Math.abs(targetY - y) <= speed) {
            y = targetY;
        }
        x -= 16;
        y -= 40;

        state.setCollision(SLOT, x, y + 37, x + 64, y + 69);
        ghost(sprites, x, y);
    }

    /** Draws the double, two lone sprites wide, at a biased position. */
    private void ghost(SpriteEngine sprites, int x, int y) {
        SpriteFrame frame = assets.sorcerer2[state.sorcerer2Shape % assets.sorcerer2.length];
        if (frame == null) {
            return;
        }
        sprites.add(6, frame.toSprite(x - 256, y - 256, 16 + 3 * 4, false));
    }

    // ------------------------------------------------------ (4) smart bomb

    /** Two columns of fire that sweep out sideways and come back. */
    private void smartBomb(SpriteEngine sprites, Palette palette) {
        colors(palette, 0x0ff8, 0x0fb0, 0x0f60);

        state.smartBStep += 157 * 4;
        if (state.smartBStep == 157 * 4 * 6) {
            state.smartBStep = 0;
        }
        int shape = state.smartBStep / (157 * 4);

        state.smartBStepHC++;
        if (state.smartBStepHC == 5) {
            state.smartBStepH++;
            state.smartBStepHC = 0;
        }
        int spread = state.smartBX + state.smartBStepH;
        if (spread > 128) {
            spread = 0;
            state.smartBStepH = 0;
        }
        state.smartBX = spread;

        int half = (state.sorcererX - (256 - DIW_X)) >> 1;
        int left = half - spread - 16 / 2;
        int right = Math.min(half + spread, (DIW_X + 300) / 2) + 32 / 2;

        int y = state.sorcererY;
        if (y <= 256 + 32) {
            y = 256 + 32;
        }
        if (y >= 256 + 192 - 156 + 32) {
            y = 256 + 192 - 156 + 32;
        }
        int vy = y - (256 - SPR_Y_OFF + 30);

        add(sprites, 6, assets.smartBombLeft[shape], screenX(left * 2), screenY(vy));
        add(sprites, 7, assets.smartBombRight[shape], screenX(right * 2), screenY(vy));

        int top = y - 30;
        state.setCollision(SLOT, state.sorcererX - spread, top,
                state.sorcererX - spread + 50, top + 156);
        state.setCollision(SLOT + 1, state.sorcererX + spread, top,
                state.sorcererX + spread + 50, top + 156);
    }

    // ------------------------------------------------------ (5) mega blast

    /** A wall of fire four sprites wide, which also suppresses the normal shot. */
    private void megaBlast(SpriteEngine sprites, Palette palette) {
        colors(palette, 0x0fff, 0x0ffc, 0x0ff8);

        state.fwFireOff = true;
        state.afOff = true;

        state.megaBX = (state.megaBX + 10) & 127;
        int travel = state.megaBX;

        int half = ((state.sorcererX - (256 - DIW_X - 32)) >> 1) + travel;
        half = Math.min(half, (DIW_X + 300) / 2);

        int y = state.sorcererY;
        int clamped = y;
        if (clamped <= 256 + 32) {
            clamped = 256 + 32;
        }
        if (clamped >= 256 + 192 - 150 + 32) {
            clamped = 256 + 192 - 150 + 32;
        }
        int vy = clamped - (256 - SPR_Y_OFF + 30);

        int[] channel = {6, 7, 4, 5};
        for (int i = 0; i < 4; i++) {
            add(sprites, channel[i], assets.megaBlast[i], screenX(half * 2) + i * 16, screenY(vy));
        }

        int x = state.sorcererX + travel * 2;
        state.setCollision(SLOT, x, y, x + 64, y + 150);
    }

    // ---------------------------------------------------------- (6) shield

    /** The double covers the sorcerer, who cannot be hit while it lasts. */
    private void shield(SpriteEngine sprites, Palette palette) {
        colors(palette, 0x0fff, 0x07df, 0x008f);
        state.sorcererOn = false;
        ghost(sprites, state.sorcererX, state.sorcererY - 10);
    }

    // ----------------------------------------------- (7) forward fire ball

    /**
     * Two balls weaving ahead of the sorcerer, their vertical travel read from
     * {@code Sin_Table} one step for every eight pixels of advance.
     */
    private void forwardFireBall(SpriteEngine sprites, Palette palette) {
        colors(palette, 0x0ff9, 0x0fb7, 0x0f85);

        state.fwFbX0 += 8;
        if (state.fwFbX0 == 128) {
            state.fwFbX1 = 0;
        }
        state.fwFbX1 += 8;
        if (state.fwFbX1 == 128) {
            state.fwFbX0 = 0;
        }

        forwardBall(sprites, 6, state.fwFbX0, SLOT);
        forwardBall(sprites, 7, state.fwFbX1, SLOT + 2);
    }

    private void forwardBall(SpriteEngine sprites, int channel, int travel, int slot) {
        int[] sine = assets.sinTable;
        int wave = sine != null ? sine[(travel >> 3) % sine.length] : 0;

        int centre = state.sorcererY - (256 - SPR_Y_OFF - 46);
        int vyTop = Math.max(SPR_Y_OFF - 16, centre - wave);
        int vyBottom = centre + wave;

        int hx = Math.min(state.sorcererX - (256 - DIW_X - 10) + travel, DIW_X + 300);

        int x1 = hx + (256 - DIW_X);
        state.setCollision(slot, x1, vyTop + (256 - SPR_Y_OFF),
                x1 + 32, vyTop + (256 - SPR_Y_OFF) + 16);
        state.setCollision(slot + 1, x1, vyBottom + (256 - SPR_Y_OFF),
                x1 + 32, vyBottom + (256 - SPR_Y_OFF) + 16);

        add(sprites, channel, assets.fwFireBall[0], screenX(hx), screenY(vyTop));
        add(sprites, channel, assets.fwFireBall[1], screenX(hx), screenY(vyBottom));
    }
}
