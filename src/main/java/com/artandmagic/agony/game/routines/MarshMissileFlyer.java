package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Volant_Missile}, from the MONSTRE VOLANT + TIR MISSILE GUIDE block
 * of {@code AG_GAME_LMARAIS.S}.
 *
 * <p>The same launcher as on the sea, with no palette and a slower missile.  A
 * flier comes in from above the screen and settles a hundred pixels right of
 * the sorcerer and forty above him, following him a pixel a frame.  Every eight
 * seconds it lets a missile go that flies only along an axis, two pixels a
 * frame, and takes a fresh bearing on the sorcerer each time it passes the
 * point it was aimed at; the odd shot shapes are the four headings, the even
 * ones the corner it turns through.  A second before the next launch the
 * missile in the air is blown up, and the sorcerer dying stops the launcher.
 * After thirty five seconds it climbs out, and at forty five it closes.
 */
final class MarshMissileFlyer implements Routine {

    private static final int MODE = 0;
    private static final int ANIM_DELAY = 1;
    private static final int LAUNCH_DELAY = 2;
    private static final int FIRE_MODE = 3;
    private static final int TARGET_X = 4;
    private static final int TARGET_Y = 5;
    private static final int TIME = 6;

    /** The missile moves two pixels a frame here, three on the sea. */
    private static final int SPEED = 2;

    /** {@code Obj_Volant_Missile_1} and {@code _2}, the wing beat. */
    private final int flyer1 = 0x534;
    private final int flyer2 = 0x54a;

    /** {@code Obj_Tir_1} to {@code Obj_Tir_8}, the eight headings of the shot. */
    private final int[] tir = {0x4a4, 0x4b6, 0x4c8, 0x4da, 0x4ec, 0x4fe, 0x510, 0x522};

    MarshMissileFlyer(AgonyBinary bin) {
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien monster = bank.aliens[0];
        Alien missile = bank.aliens[1];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            monster.x = 256 + 200;
            // Left unbiased by the source: it comes in from above the screen.
            monster.y = 220;
            monster.objOff = flyer1;
            monster.energy = 20;
            monster.status = 0;
            monster.fireRateStat = 0;
            monster.fireRateDyn = 0;
            vars[FIRE_MODE] = 0;
            vars[TIME] = 0;
            vars[LAUNCH_DELAY] = 25 * 5;
            vars[MODE]++;
            return end(state, bank, vars);
        }

        bank.count = 1;
        bank.first = 0;
        if (vars[MODE] != 1) {
            return end(state, bank, vars);
        }

        if (state.die) {
            // While the sorcerer is dying it neither beats its wings nor counts
            // towards the next launch.
            stopFire(missile);
        } else {
            vars[ANIM_DELAY]++;
            monster.objOff = (vars[ANIM_DELAY] & 4) != 0 ? flyer1 : flyer2;
            vars[LAUNCH_DELAY]++;
            if (vars[LAUNCH_DELAY] == 25 * 7) {
                stopFire(missile);
            }
        }

        if (vars[LAUNCH_DELAY] == 25 * 8) {
            return initFire(state, bank, vars);
        }

        int mode = vars[FIRE_MODE];
        if (mode == 0) {
            return noFire(state, bank, vars);
        }
        bank.count = 2;

        switch (mode) {
            case 1 -> {
                missile.y -= SPEED;
                missile.objOff = tir[0];
                if (missile.y > vars[TARGET_Y]) {
                    return noFire(state, bank, vars);
                }
                return decideFx(state, bank, vars);
            }
            case 2 -> {
                missile.x += SPEED;
                missile.objOff = tir[2];
                if (missile.x < vars[TARGET_X]) {
                    return noFire(state, bank, vars);
                }
                return decideFy(state, bank, vars);
            }
            case 3 -> {
                missile.y += SPEED;
                missile.objOff = tir[4];
                if (missile.y > vars[TARGET_Y]) {
                    return decideFx(state, bank, vars);
                }
                return noFire(state, bank, vars);
            }
            default -> {
                missile.x -= SPEED;
                missile.objOff = tir[6];
                if (missile.x > vars[TARGET_X]) {
                    return noFire(state, bank, vars);
                }
                return decideFy(state, bank, vars);
            }
        }
    }

    /** {@code .stop_fire}: blows up the missile in the air, unless it is already going off. */
    private void stopFire(Alien missile) {
        if ((missile.status & 0xf) == 0) {
            missile.status = 1;
        }
    }

    /** {@code .init_fire}: a new missile leaves the launcher, and is aimed. */
    private boolean initFire(GameState state, AlienBank bank, int[] vars) {
        Alien monster = bank.aliens[0];
        Alien missile = bank.aliens[1];
        // cmp.b #$f compares the whole status byte, flash nibble included.
        if ((monster.status & 0xff) == 0xf) {
            return end(state, bank, vars);
        }
        vars[LAUNCH_DELAY] = 0;
        missile.x = monster.x;
        missile.y = monster.y;
        missile.energy = 10;
        missile.status = 0;
        missile.fireRateStat = 0;
        missile.fireRateDyn = 0;
        return decideFy(state, bank, vars);
    }

    /**
     * {@code .tacke_desision_fy}: a bearing after a run across, so the next leg
     * is up or down, or across again when the missile is on the line already.
     */
    private boolean decideFy(GameState state, AlienBank bank, int[] vars) {
        Alien missile = bank.aliens[1];
        int x = state.sorcererX + 16;
        int y = state.sorcererY + 40;
        vars[TARGET_X] = x;
        vars[TARGET_Y] = y;

        if (y == missile.y) {
            if (x == missile.x) {
                // Dead on: the heading is cleared, and the bgt that follows reads
                // the flags of that clr, so it is sent left.
                vars[FIRE_MODE] = 0;
                missile.objOff = tir[5];
                vars[FIRE_MODE] = 4;
            } else if (x > missile.x) {
                missile.objOff = vars[FIRE_MODE] == 1 ? tir[1] : tir[3];
                vars[FIRE_MODE] = 2;
            } else {
                missile.objOff = vars[FIRE_MODE] == 1 ? tir[7] : tir[5];
                vars[FIRE_MODE] = 4;
            }
        } else if (y < missile.y) {
            missile.objOff = vars[FIRE_MODE] == 4 ? tir[7] : tir[1];
            vars[FIRE_MODE] = 1;
        } else {
            missile.objOff = vars[FIRE_MODE] == 4 ? tir[5] : tir[3];
            vars[FIRE_MODE] = 3;
        }
        return end(state, bank, vars);
    }

    /** {@code .tacke_desision_fx}: the same after a run up or down. */
    private boolean decideFx(GameState state, AlienBank bank, int[] vars) {
        Alien missile = bank.aliens[1];
        int x = state.sorcererX + 16;
        int y = state.sorcererY + 40;
        vars[TARGET_X] = x;
        vars[TARGET_Y] = y;

        if (x == missile.x) {
            if (y > missile.y) {
                missile.objOff = vars[FIRE_MODE] == 4 ? tir[5] : tir[3];
                vars[FIRE_MODE] = 3;
            } else {
                missile.objOff = vars[FIRE_MODE] == 4 ? tir[7] : tir[1];
                vars[FIRE_MODE] = 1;
            }
        } else if (x < missile.x) {
            missile.objOff = vars[FIRE_MODE] == 1 ? tir[7] : tir[5];
            vars[FIRE_MODE] = 4;
        } else {
            missile.objOff = vars[FIRE_MODE] == 1 ? tir[1] : tir[3];
            vars[FIRE_MODE] = 2;
        }
        return end(state, bank, vars);
    }

    /** {@code .no_fire}: the launcher closes on the sorcerer. */
    private boolean noFire(GameState state, AlienBank bank, int[] vars) {
        if (vars[TIME] > 25 * 35) {
            return end(state, bank, vars);
        }
        Alien monster = bank.aliens[0];

        int dy = monster.y - 40;
        if (dy > state.sorcererY) {
            monster.y--;
        } else if (dy < state.sorcererY) {
            monster.y++;
        }

        int dx = monster.x - 100;
        if (dx > state.sorcererX) {
            monster.x--;
        } else if (dx < state.sorcererX) {
            monster.x++;
        }
        return end(state, bank, vars);
    }

    /** {@code .end}: the clock, the close at forty five seconds and the way out. */
    private boolean end(GameState state, AlienBank bank, int[] vars) {
        vars[TIME]++;
        boolean alive = true;
        if (vars[TIME] >= 25 * 45) {
            alive = false;
            MarshRoutines.closePalette(state);
        }
        if (vars[TIME] >= 25 * 35) {
            bank.aliens[0].y--;
        }
        return alive;
    }
}
