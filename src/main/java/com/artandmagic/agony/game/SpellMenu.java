package com.artandmagic.agony.game;

import com.artandmagic.agony.core.Palette;
import com.artandmagic.agony.core.SpriteEngine;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.SpriteFrame;
import com.artandmagic.agony.input.Joystick;

/**
 * The spell menu, the ICONES SPRITES block of {@code Ag_Sprites.s}.
 *
 * <p>Holding fire for thirty frames, or pressing the menu key, freezes the game
 * and shows the eight spells in a column with a cursor beside them.  The cursor
 * moves two pixels per video frame and only settles on a spell when its
 * position is an exact multiple of 24, which is what gives the menu its glide.
 * Choosing a spell the player does not own simply closes the menu again.
 */
public final class SpellMenu {

    /** Vertical spacing of the icons, in pixels. */
    private static final int ITEM_H = 24;
    /**
     * Where the menu sits.  The icon strip carries its own sprite control words,
     * $4188 and $4190, which put it at hardware x 272 and 288 with a vertical
     * start of $41; the cross comes from {@code Mask_Spr}, whose control long
     * $418c0082 puts it at hardware x 280; and the cursor is placed by the code,
     * {@code or #$7c} on the position word and {@code add #$46} on the line.
     */
    private static final int ICON_X = 272 - 0x90;
    private static final int ICON_Y = 0x41 - 0x40;
    private static final int MASK_X = 280 - 0x90;
    /** The cross sits four lines into each 24 line slot. */
    private static final int MASK_OFFSET = 4;
    private static final int ARROW_X = 0x7c * 2 - 0x90;
    private static final int ARROW_Y = 0x46 - 0x40;

    private final GameState state;
    private final GameAssets assets;
    private final Joystick joystick;

    public SpellMenu(GameState state, GameAssets assets, Joystick joystick) {
        this.state = state;
        this.assets = assets;
        this.joystick = joystick;
    }

    /** One 50 Hz video frame. */
    public void update(SpriteEngine sprites, Palette palette) {
        if (state.iconesOff) {
            state.iconesOff = false;
            state.iconesMode = false;
            palette.setSpritePalette(assets.sorcererPalette);
        }
        if (!state.iconesMode) {
            return;
        }

        moveCursor();
        draw(sprites, palette);
        select();
    }

    private void moveCursor() {
        if (!state.arowDown && !state.arowUp) {
            if (joystick.down() && state.curentSpell != 7) {
                state.arowDown = true;
            } else if (joystick.up() && state.curentSpell != 0) {
                state.arowUp = true;
            }
        }
        if (state.arowDown) {
            state.arowY += 2;
        }
        if (state.arowUp) {
            state.arowY -= 2;
        }

        state.selectionOn = false;
        if (state.arowY % ITEM_H == 0) {
            state.curentSpell = state.arowY / ITEM_H;
            state.textNum = state.curentSpell + 1;
            state.textDelay = -1;
            state.arowUp = false;
            state.arowDown = false;
            state.selectionOn = true;
        }
    }

    private void draw(SpriteEngine sprites, Palette palette) {
        palette.setSpritePalette(assets.iconesPalette);

        for (int i = 0; i < 8; i++) {
            SpriteFrame icon = assets.icons[i];
            if (icon != null) {
                sprites.add(4, icon.toSprite(ICON_X, ICON_Y + i * ITEM_H, 16, true));
            }
            // A spell the player does not own is crossed out.
            if (!state.spellAvailable[i] && assets.lock != null) {
                sprites.add(2, assets.lock.toSprite(MASK_X,
                        ICON_Y + i * ITEM_H + MASK_OFFSET, 16, true));
            }
        }
        if (assets.arrow != null) {
            sprites.add(0, assets.arrow.toSprite(ARROW_X, ARROW_Y + state.arowY, 16, true));
        }
    }

    private void select() {
        if (!joystick.fire()) {
            state.iconesFireUp = true;
            return;
        }
        if (!state.iconesFireUp || !state.selectionOn) {
            return;
        }

        state.oldTextNum = -1;
        state.pause = false;
        state.stop = false;
        state.fireCount = 0;
        state.textDelay = 2;
        state.iconesOff = true;

        int spell = state.curentSpell;
        if (spell < 0 || spell >= 8 || !state.spellAvailable[spell]) {
            // Not owned: put the previous spell back and leave the menu.
            state.curentSpell = state.safeCurSpell;
            return;
        }

        state.spellAvailable[spell] = false;
        state.spellTimeDelay = 50;
        state.spellTime = GameAssets.SPELL_TIME[spell];
        state.sorcerer2X = state.sorcererX;
        state.sorcerer2Y = state.sorcererY;
    }
}
