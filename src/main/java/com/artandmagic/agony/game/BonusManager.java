package com.artandmagic.agony.game;

import com.artandmagic.agony.audio.SoundFx;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.Palette;
import com.artandmagic.agony.core.SpriteEngine;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.SpriteFrame;

/**
 * The bonuses, the BONUS block of {@code Ag_Sprites.s}.
 *
 * <p>Every two thousand video frames the game arms a bonus; the next alien to
 * blow up drops it at its own position.  It then falls to the ground and rolls
 * off the left of the screen.  Which bonus appears is decided by need rather
 * than by chance: a weak weapon asks for a fire ball, a single life for a money
 * bag, a missing axe for a sword, and only when nothing is missing does the
 * player get the next spell of {@code Spell_Pri}.
 */
public final class BonusManager {

    /** Bonus numbers, as the original encodes them in {@code Bonus_Num}. */
    private static final int SWORD_UP = 0;
    private static final int MONEY = 1;
    private static final int FIRE_BALL = 2;
    private static final int FIRST_SPELL = 3;
    private static final int SWORD_DOWN = 11;

    /** Height at which a falling bonus reaches the ground. */
    private static final int GROUND = Hw.BIAS + 166;

    private final GameState state;
    private final GameAssets assets;
    private final SoundFx sound;

    public BonusManager(GameState state, GameAssets assets, SoundFx sound) {
        this.state = state;
        this.assets = assets;
        this.sound = sound;
    }

    /** One 50 Hz video frame. */
    public void update(SpriteEngine sprites, Palette palette) {
        if (state.curentSpell >= 0) {
            state.bonusMode = 0;
        }

        switch (state.bonusMode) {
            case 0 -> arm();
            case 1 -> {
                // Armed: the object renderer releases it where an alien explodes.
            }
            case 2 -> fall(sprites, palette);
            case 3 -> roll(sprites, palette);
            default -> {
            }
        }
    }

    private void arm() {
        // The clock keeps running through a death and through a spell: the
        // original only tests those once the forty seconds are up.
        if (state.stop2) {
            return;
        }
        state.bonusDelay++;
        if (state.bonusDelay <= 2000) {
            return;
        }
        if (state.die || state.curentSpell >= 0) {
            return;
        }

        state.bonusMode = 1;
        state.rainOn = false;
        state.bonusDelay = 0;
        state.bonusNum = pick();
    }

    /** Chooses the bonus the player needs most. */
    private int pick() {
        if (state.fwFireWeapon <= 1) {
            return FIRE_BALL;
        }
        if (state.life <= 1 && state.extraLife >= 0x40000) {
            return MONEY;
        }
        if (!state.axeUpOn) {
            return SWORD_UP;
        }
        if (state.fwFireWeapon == 2) {
            return FIRE_BALL;
        }
        if (!state.axeDownOn) {
            return SWORD_DOWN;
        }

        // Everything is in place: hand out the next spell the player is missing.
        for (int i = 0; i < 9; i++) {
            int spell = GameAssets.SPELL_PRI[state.spellNextBonus];
            state.spellNextBonus = (state.spellNextBonus + 1) & 7;
            if (!state.spellAvailable[spell]) {
                return FIRST_SPELL + spell;
            }
        }
        return MONEY;
    }

    private void fall(SpriteEngine sprites, Palette palette) {
        if (!state.pause2) {
            state.bonusX--;
            state.bonusY += 2;
        }
        if (state.bonusY >= GROUND) {
            state.bonusMode = 3;
        }
        show(sprites, palette);
    }

    private void roll(SpriteEngine sprites, Palette palette) {
        if (!state.pause2) {
            state.bonusX--;
        }
        state.bonusY = GROUND;
        show(sprites, palette);
    }

    private void show(SpriteEngine sprites, Palette palette) {
        if (state.bonusX <= 240) {
            close();
            return;
        }
        if (collected()) {
            award();
            close();
            return;
        }

        int shape = shapeOf(state.bonusNum);
        if (assets.bonusImage != null && state.bonusNum >= 0
                && state.bonusNum < assets.bonusImage.length) {
            shape = assets.bonusImage[state.bonusNum];
        }
        switch (shape) {
            case 0 -> spellColors(palette, 0x0f00, 0x0bbb, 0x0888);
            case 1 -> spellColors(palette, 0x0f00, 0x0e92, 0x0b72);
            case 2 -> spellColors(palette, 0x0f00, 0x0050, 0x0070);
            default -> spellColors(palette, 0x0f00, 0x0a86, 0x0753);
        }

        state.bonusAnim++;
        SpriteFrame frame = assets.bonus[shape % assets.bonus.length];
        if (frame != null) {
            // Channels 6 and 7 are written at the same position, so the pair is
            // attached; the three colours the code sets are the top of its range.
            // The original converts the bonus position with sub #256-$80 on x
            // and sub #256-SPR_Y_OFF+22 on y before handing it to the sprite.
            sprites.add(6, frame.toSprite(state.bonusX - Hw.BIAS - 16,
                    state.bonusY - Hw.BIAS - 22, 16, true));
        }
    }

    private void spellColors(Palette palette, int c29, int c30, int c31) {
        palette.setAll(29, c29);
        palette.setAll(30, c30);
        palette.setAll(31, c31);
    }

    /** Fallback for {@code Bonus_Image} when the table has not been loaded. */
    private static int shapeOf(int bonusNum) {
        return switch (bonusNum) {
            case SWORD_UP, SWORD_DOWN -> 0;
            case MONEY -> 1;
            case FIRE_BALL -> 2;
            default -> 3;
        };
    }

    /** The pick up box is generous: it reaches well above and below the hero. */
    private boolean collected() {
        int cx = state.sorcererX;
        int cy = state.sorcererY + 40;
        return state.bonusX >= cx - 16 && state.bonusX <= cx + 50
                && state.bonusY >= cy - 50 && state.bonusY <= cy + 30;
    }

    private void award() {
        sound.request(0, 63);
        state.point = 0x258;

        switch (state.bonusNum) {
            case SWORD_UP -> {
                state.axeUpOn = true;
                state.axeUpX = state.sorcererX;
                state.axeDownX = state.sorcererX;
            }
            case SWORD_DOWN -> {
                state.axeDownOn = true;
                state.axeUpX = state.sorcererX;
                state.axeDownX = state.sorcererX;
            }
            case MONEY -> state.point = 0x20000;
            case FIRE_BALL -> {
                if (state.fwFireWeapon < 3) {
                    state.fwFireWeapon++;
                }
            }
            default -> {
                int spell = state.bonusNum - FIRST_SPELL;
                if (spell >= 0 && spell < state.spellAvailable.length) {
                    state.spellAvailable[spell] = true;
                }
            }
        }
    }

    private void close() {
        state.bonusMode = 0;
    }
}
