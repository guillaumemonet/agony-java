package com.artandmagic.agony.game;

import com.artandmagic.agony.audio.SoundFx;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.SpriteEngine;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.SpriteFrame;
import com.artandmagic.agony.input.Joystick;

/**
 * The sorcerer, his axes and his forward shot: the SORCERER, FORWARD FIRE and
 * AXES blocks of {@code Ag_Sprites.s}.
 *
 * <p>All of this runs on the 50 Hz video interrupt, not on the 25 Hz game loop,
 * which is why the hero moves three pixels per video frame while the scenery
 * scrolls two pixels per game frame.  The sorcerer occupies sprite channels 0 to
 * 3, attached in pairs so he is 32 pixels wide in sixteen colours, and the same
 * channels carry the two axes above and below him.
 */
public final class Player {

    private final GameState state;
    private final GameAssets assets;
    private final Joystick joystick;
    private final SoundFx sound;

    public Player(GameState state, GameAssets assets, Joystick joystick, SoundFx sound) {
        this.state = state;
        this.assets = assets;
        this.joystick = joystick;
        this.sound = sound;
    }

    /** One 50 Hz video frame. */
    public void update(SpriteEngine sprites) {
        if (state.iconesMode) {
            return;
        }
        if (state.life == 0 || state.die) {
            state.clearCollisions(0, 2);
            return;
        }

        move();
        drawSorcerer(sprites);
        fireButton();

        if (state.pause2) {
            return;
        }
        forwardFire(sprites);
        axes();
    }

    // ----------------------------------------------------------------- moving

    private void move() {
        if (state.pause2) {
            return;
        }
        boolean moved = false;

        if (state.quitDelay == 0) {
            if (joystick.right()) {
                if (state.sorcererX < 280 - 32 + Hw.BIAS) {
                    state.sorcererX += 3;
                    moved = true;
                }
            } else if (joystick.left()) {
                if (state.sorcererX > Hw.BIAS) {
                    state.sorcererX -= 3;
                    moved = true;
                }
            }
            if (joystick.down()) {
                if (state.sorcererY < 192 - 60 + Hw.BIAS) {
                    state.sorcererY += 3;
                }
            } else if (joystick.up()) {
                if (state.sorcererY > Hw.BIAS - 20) {
                    state.sorcererY -= 3;
                }
            }
        }

        // The body of the sorcerer, the rectangle the aliens must hit to kill him.
        state.setCollision(Collisions.SLOT_SORCERER,
                state.sorcererX, state.sorcererY + 37,
                state.sorcererX + 32, state.sorcererY + 53);

        if (moved) {
            if (state.axeDelay < 10) {
                state.axeDelay++;
            }
        } else {
            state.axeDelay = 0;
            state.axeMove = false;
        }

        // The walk cycle advances every other video frame, so at 25 Hz.
        state.sorcererDelay ^= 1;
        if (state.sorcererDelay != 0) {
            state.sorcererShape++;
            state.sorcerer2Shape++;
            if (state.sorcererShape >= 16) {
                state.sorcererShape = 0;
                state.sorcerer2Shape = 0;
            }
        }
    }

    private void drawSorcerer(SpriteEngine sprites) {
        SpriteFrame frame = assets.sorcerer[state.sorcererShape % assets.sorcerer.length];
        if (frame == null) {
            return;
        }
        int x = state.sorcererX - Hw.BIAS;
        int y = state.sorcererY - Hw.BIAS;

        if (state.axeUpOn && assets.axe != null) {
            sprites.add(0, assets.axe.toSprite(state.axeUpX - Hw.BIAS, state.axeUpY - Hw.BIAS, 16, true));
        }
        if (state.sorcererOn) {
            sprites.add(0, frame.toSprite(x, y, 16, true));
        }
        if (state.axeDownOn && assets.axe != null) {
            sprites.add(0, assets.axe.toSprite(state.axeDownX - Hw.BIAS, state.axeDownY - Hw.BIAS, 16, true));
        }
    }

    // ------------------------------------------------------------------ firing

    private void fireButton() {
        // The original forces the fire bit high while the level is being left,
        // which keeps the button dead rather than jammed on.
        boolean fire = joystick.fire() && state.quitDelay == 0;

        if (fire) {
            if (state.pause2) {
                // Fire also releases the pause, as the original does.
                state.pause = false;
                state.stop = false;
                state.textDelay = 2;
                return;
            }
            state.fireCount++;
            if (state.fwFireStep == GameState.FW_F_OFF && !state.fwFireOff) {
                state.fwFireStep = 0;
                state.fwFirePhase = 0;
                sound.request(2, 50);
            }
        } else {
            state.fireCount = 0;
        }

        // Holding fire for thirty frames opens the spell menu.
        if (state.menuMode && state.curentSpell != 2 && state.fireCount == 30 && state.quitDelay == 0) {
            openMenu();
        }
    }

    /** Opens the spell menu, shared with the menu key of the keyboard handler. */
    public void openMenu() {
        state.safeCurSpell = state.curentSpell;
        state.pause = true;
        state.stop = true;
        state.iconesMode = true;
        state.arowY = 24 * 2;
        state.iconesFireUp = false;
    }

    private void forwardFire(SpriteEngine sprites) {
        if (state.fwFireStep == GameState.FW_F_OFF) {
            state.clearCollisions(Collisions.SLOT_FORWARD_FIRE, 1);
            return;
        }

        int weapon = Math.max(0, Math.min(3, state.fwFireWeapon));
        SpriteFrame frame = assets.forwardFire[weapon];
        if (frame == null) {
            return;
        }

        // The four weapons sit at a different height and reach further down.
        int yOffset = switch (weapon) {
            case 0 -> 32;
            case 1 -> 22;
            case 2 -> 12;
            default -> -8;
        };
        int minY = switch (weapon) {
            case 0 -> Hw.BIAS - 16;
            default -> Hw.BIAS;
        };
        int maxY = Hw.BIAS + 192 - frame.height + 32;

        int clampedY = Math.max(minY, Math.min(maxY, state.sorcererY));
        int screenY = clampedY - Hw.BIAS + yOffset;
        int screenX = Math.min(300, state.sorcererX - Hw.BIAS + 32 + state.fwFireStep);

        sprites.add(4, frame.toSprite(screenX, screenY, 16 + 4 * 2, false));

        state.fwFireStep += 20;
        state.fwFirePhase ^= 1;
        if (state.fwFirePhase == 0) {
            return;
        }

        int x1 = state.sorcererX + 32 + state.fwFireStep - 20;
        int y1 = screenY + Hw.BIAS + 8;
        state.setCollision(Collisions.SLOT_FORWARD_FIRE, x1, y1, x1 + 60, y1 + frame.height - 16);
    }

    // ------------------------------------------------------------------- axes

    private void axes() {
        if (state.quitDelay != 0) {
            return;
        }

        // The axes chase the sorcerer, and overshoot him while he is moving.
        int speed = 3;
        if (state.axeDelay != 0 && state.axeDelay != 10 && state.axeMove) {
            speed = -3;
        }
        if (state.sorcererX > state.axeUpX) {
            state.axeUpX += speed;
            state.axeDownX += speed;
        } else if (state.sorcererX < state.axeUpX) {
            state.axeUpX -= speed;
            state.axeDownX -= speed;
        }

        int delta = state.sorcererX - state.axeUpX;
        if (delta < 6 && delta > -6) {
            state.axeMove = true;
            state.axeDelay = 1;
        }

        // Axe_Traj gives the vertical travel for the horizontal offset the axes
        // currently sit at; the original reads it as an unsigned byte.
        int[] traj = assets.axeTraj;
        int index = state.axeUpX - state.sorcererX + 48 - 2;
        int height = traj == null ? 0
                : traj[Math.max(0, Math.min(traj.length - 1, index))];
        state.axeUpY = Math.max(Hw.BIAS - 32, state.sorcererY - height - 18);
        state.axeDownY = state.sorcererY + height + 98;

        state.clearCollisions(2, 2);
        if (state.axeUpOn) {
            state.setCollision(2, state.axeUpX, state.axeUpY,
                    state.axeUpX + 32, state.axeUpY + 8);
        }
        if (state.axeDownOn) {
            state.setCollision(3, state.axeDownX, state.axeDownY,
                    state.axeDownX + 32, state.axeDownY + 8);
        }
    }

}
