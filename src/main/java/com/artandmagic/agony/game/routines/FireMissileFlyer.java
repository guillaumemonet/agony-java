package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Volant_Missile}, from the MONSTRE VOLANT + TIR MISSILE GUIDE block
 * of {@code Ag_Game_LFEUX.s}.
 *
 * <p>The same flier as the first level's ({@link MissileFlyer}): it comes in
 * from above the screen and settles a hundred pixels to the right of the
 * sorcerer and forty above him, following him one pixel a frame, and every
 * eight seconds lets a missile go that only flies along an axis, taking a fresh
 * bearing on the sorcerer each time it passes the point it was aimed at.  A
 * second before the next one is due the missile in the air is blown up, and the
 * sorcerer dying stops the launcher where it stands.
 *
 * <p>Three things differ here.  It installs no palette of its own.  It is in a
 * hurry: it gives up the chase and climbs out after ten seconds and closes at
 * eighteen.  And the close returns at once when it gives the palette back, so
 * on that frame it does not take its last step up.
 */
final class FireMissileFlyer implements Routine {

    private static final int MODE = 0;
    private static final int ANIM_DELAY = 1;
    private static final int LAUNCH_DELAY = 2;
    private static final int FIRE_MODE = 3;
    private static final int TARGET_X = 4;
    private static final int TARGET_Y = 5;
    private static final int TIME = 6;

    /** {@code Obj_Volant_Missile_1} and {@code _2}, the two frames it beats its wings with. */
    private static final int FLYER_1 = 0x544;
    private static final int FLYER_2 = 0x55a;

    /** {@code Obj_Tir_1} to {@code Obj_Tir_8}, the eight headings of the shot. */
    private static final int[] TIR = {0x4a0, 0x4b2, 0x4cc, 0x4de, 0x4f4, 0x506, 0x51c, 0x52e};

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien monster = bank.aliens[0];
        Alien missile = bank.aliens[1];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            monster.x = 256 + 200;
            // Unbiased in the source, as on the first level: it comes in from
            // above the screen.
            monster.y = 220;
            monster.objOff = FLYER_1;
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
            monster.objOff = (vars[ANIM_DELAY] & 4) != 0 ? FLYER_1 : FLYER_2;
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
                missile.y -= 3;
                missile.objOff = TIR[0];
                if (missile.y > vars[TARGET_Y]) {
                    return noFire(state, bank, vars);
                }
                return decideFx(state, bank, vars);
            }
            case 2 -> {
                missile.x += 3;
                missile.objOff = TIR[2];
                if (missile.x < vars[TARGET_X]) {
                    return noFire(state, bank, vars);
                }
                return decideFy(state, bank, vars);
            }
            case 3 -> {
                missile.y += 3;
                missile.objOff = TIR[4];
                if (missile.y > vars[TARGET_Y]) {
                    return decideFx(state, bank, vars);
                }
                return noFire(state, bank, vars);
            }
            default -> {
                missile.x -= 3;
                missile.objOff = TIR[6];
                if (missile.x > vars[TARGET_X]) {
                    return noFire(state, bank, vars);
                }
                return decideFy(state, bank, vars);
            }
        }
    }

    /** Blows up the missile in the air, unless it is already going off. */
    private void stopFire(Alien missile) {
        if ((missile.status & 0xf) == 0) {
            missile.status = 1;
        }
    }

    /** Sends a new missile off from the launcher, and aims it. */
    private boolean initFire(GameState state, AlienBank bank, int[] vars) {
        Alien monster = bank.aliens[0];
        Alien missile = bank.aliens[1];
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
     * Takes a bearing after a run across, so the next leg is up or down — or
     * across again, when the missile is already on the line it aimed for.
     */
    private boolean decideFy(GameState state, AlienBank bank, int[] vars) {
        Alien missile = bank.aliens[1];
        int x = state.sorcererX + 16;
        int y = state.sorcererY + 40;
        vars[TARGET_X] = x;
        vars[TARGET_Y] = y;

        if (y == missile.y) {
            if (x == missile.x) {
                // Dead on: the original clears the heading here, and the test
                // that follows then reads that zero, which sends it left.
                missile.objOff = TIR[5];
                vars[FIRE_MODE] = 4;
            } else if (x > missile.x) {
                missile.objOff = vars[FIRE_MODE] == 1 ? TIR[1] : TIR[3];
                vars[FIRE_MODE] = 2;
            } else {
                missile.objOff = vars[FIRE_MODE] == 1 ? TIR[7] : TIR[5];
                vars[FIRE_MODE] = 4;
            }
        } else if (y < missile.y) {
            missile.objOff = vars[FIRE_MODE] == 4 ? TIR[7] : TIR[1];
            vars[FIRE_MODE] = 1;
        } else {
            missile.objOff = vars[FIRE_MODE] == 4 ? TIR[5] : TIR[3];
            vars[FIRE_MODE] = 3;
        }
        return end(state, bank, vars);
    }

    /** The same after a run up or down, so the next leg is across. */
    private boolean decideFx(GameState state, AlienBank bank, int[] vars) {
        Alien missile = bank.aliens[1];
        int x = state.sorcererX + 16;
        int y = state.sorcererY + 40;
        vars[TARGET_X] = x;
        vars[TARGET_Y] = y;

        if (x == missile.x) {
            if (y > missile.y) {
                missile.objOff = vars[FIRE_MODE] == 4 ? TIR[5] : TIR[3];
                vars[FIRE_MODE] = 3;
            } else {
                missile.objOff = vars[FIRE_MODE] == 4 ? TIR[7] : TIR[1];
                vars[FIRE_MODE] = 1;
            }
        } else if (x < missile.x) {
            missile.objOff = vars[FIRE_MODE] == 1 ? TIR[7] : TIR[5];
            vars[FIRE_MODE] = 4;
        } else {
            missile.objOff = vars[FIRE_MODE] == 1 ? TIR[1] : TIR[3];
            vars[FIRE_MODE] = 2;
        }
        return end(state, bank, vars);
    }

    /** With nothing in the air the launcher closes on the sorcerer. */
    private boolean noFire(GameState state, AlienBank bank, int[] vars) {
        if (vars[TIME] > 25 * 10) {
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

    /** The tail every path goes through: the clock, the way out and the close. */
    private boolean end(GameState state, AlienBank bank, int[] vars) {
        vars[TIME]++;
        boolean alive = true;

        if (vars[TIME] >= 25 * 18) {
            alive = false;
            if (state.routModPalCounter == 0) {
                // This level's version returns straight after giving the
                // palette back.
                return FireRoutines.close(state);
            }
        }
        if (vars[TIME] >= 25 * 10) {
            bank.aliens[0].y--;
        }
        return alive;
    }
}
