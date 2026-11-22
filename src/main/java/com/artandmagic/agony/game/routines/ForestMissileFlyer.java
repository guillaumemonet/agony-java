package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Volant_Missile}, from the MONSTRE VOLANT + TIR MISSILE GUIDE block
 * of {@code Ag_Game_LFORET.s}.
 *
 * <p>A flying monster that keeps itself a hundred pixels ahead of the sorcerer
 * and forty pixels below him, one pixel a frame on each axis, flapping between
 * two frames.  Every eight seconds (the first time after three) it launches a
 * guided missile from where it is.  The missile only travels along one axis at
 * a time, one pixel a frame: when it passes the sorcerer's bearing on that
 * axis it takes a new bearing and turns onto the other one, so it closes in on
 * him in a staircase.  Seven seconds into each cycle the missile blows up on
 * its own, and so does it at once when the sorcerer dies.
 *
 * <p>After thirty five seconds the monster stops chasing and climbs away; ten
 * seconds later the routine closes.  The palette {@code R_VM_Pal} that sits in
 * front of the code is commented out in the source and stays unused.
 *
 * <p>The missile picks its frame from the turn it makes: a straight frame
 * while it flies, and a diagonal one on the frame it turns, the diagonal
 * depending on the axis it comes from.
 */
final class ForestMissileFlyer implements Routine {

    private static final int MODE = 0;
    private static final int ANIM_DELAY = 1;
    private static final int LAUNCH_DELAY = 2;
    private static final int FIRE_MODE = 3;
    private static final int TARGET_X = 4;
    private static final int TARGET_Y = 5;
    private static final int TIME = 6;

    /** {@code R_VM_Fire_Mode}: 0 none, then up, right, down, left. */
    private static final int NONE = 0;
    private static final int UP = 1;
    private static final int RIGHT = 2;
    private static final int DOWN = 3;
    private static final int LEFT = 4;

    /** {@code Obj_Volant_Missile_1} and {@code _2}. */
    private final int frame1 = 0x4a6;
    private final int frame2 = 0x4bc;

    private static final int[] TIR = ForestStarFire.SHOTS;

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien monster = bank.aliens[0];
        Alien missile = bank.aliens[1];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            monster.x = 256 + 200;
            monster.y = 220;
            monster.objOff = frame1;
            monster.energy = 20;
            ForestRoutines.clearStatus(monster);
            vars[FIRE_MODE] = NONE;
            vars[TIME] = 0;
            vars[LAUNCH_DELAY] = 25 * 5;
            vars[MODE]++;
            return end(state, monster, vars);
        }

        bank.count = 1;
        bank.first = 0;
        if (vars[MODE] != 1) {
            return end(state, monster, vars);
        }

        boolean stopFire = state.die;
        if (!state.die) {
            vars[ANIM_DELAY]++;
            monster.objOff = (vars[ANIM_DELAY] & 4) != 0 ? frame1 : frame2;
            vars[LAUNCH_DELAY]++;
            stopFire = vars[LAUNCH_DELAY] == 25 * 7;
        }
        if (stopFire && missile.exploState() == 0) {
            missile.status = 1;
        }

        if (vars[LAUNCH_DELAY] == 25 * 8) {
            // .init_fire: not from a monster that has finished exploding.
            if ((monster.status & 0xff) == 0x0f) {
                return end(state, monster, vars);
            }
            vars[LAUNCH_DELAY] = 0;
            missile.x = monster.x;
            missile.y = monster.y;
            missile.energy = 10;
            ForestRoutines.clearStatus(missile);
            decideAlongY(state, missile, vars);
            return end(state, monster, vars);
        }

        int mode = vars[FIRE_MODE];
        if (mode != NONE) {
            bank.count = 2;
            switch (mode) {
                case UP -> {
                    missile.y--;
                    missile.objOff = TIR[0];
                    if (missile.y <= vars[TARGET_Y]) {
                        decideAlongX(state, missile, vars);
                        return end(state, monster, vars);
                    }
                }
                case RIGHT -> {
                    missile.x++;
                    missile.objOff = TIR[2];
                    if (missile.x >= vars[TARGET_X]) {
                        decideAlongY(state, missile, vars);
                        return end(state, monster, vars);
                    }
                }
                case DOWN -> {
                    missile.y++;
                    missile.objOff = TIR[4];
                    // bgt: it turns one pixel past the target, not on it.
                    if (missile.y > vars[TARGET_Y]) {
                        decideAlongX(state, missile, vars);
                        return end(state, monster, vars);
                    }
                }
                default -> {
                    missile.x--;
                    missile.objOff = TIR[6];
                    if (missile.x <= vars[TARGET_X]) {
                        decideAlongY(state, missile, vars);
                        return end(state, monster, vars);
                    }
                }
            }
        }

        // .no_fire: the monster keeps its place in front of the sorcerer.
        if (vars[TIME] <= 25 * 35) {
            int dy = monster.y - 40;
            if (dy != state.sorcererY) {
                monster.y += dy > state.sorcererY ? -1 : 1;
            }
            int dx = monster.x - 100;
            if (dx != state.sorcererX) {
                monster.x += dx > state.sorcererX ? -1 : 1;
            }
        }
        return end(state, monster, vars);
    }

    /** {@code .end}: the clock, the close, and the climb out. */
    private boolean end(GameState state, Alien monster, int[] vars) {
        vars[TIME]++;
        boolean open = true;
        if (vars[TIME] >= 25 * 45) {
            ForestRoutines.dropPalette(state);
            open = false;
        }
        if (vars[TIME] >= 25 * 35) {
            monster.y--;
        }
        return open;
    }

    /** Takes a new bearing on the sorcerer. */
    private static void target(GameState state, int[] vars) {
        vars[TARGET_X] = state.sorcererX + 16;
        vars[TARGET_Y] = state.sorcererY + 40;
    }

    /**
     * {@code .tacke_desision_fy}: the missile has finished a horizontal run
     * (or has just been launched) and turns up or down.  Level with the
     * sorcerer it goes left or right instead; right on him, the
     * {@code clr R_VM_Fire_Mode} leaves the zero flag set and the branch that
     * follows sends it left.
     */
    private static void decideAlongY(GameState state, Alien missile, int[] vars) {
        target(state, vars);
        int tx = vars[TARGET_X];
        int ty = vars[TARGET_Y];
        int mode = vars[FIRE_MODE];
        if (ty != missile.y) {
            if (ty < missile.y) {
                missile.objOff = mode == LEFT ? TIR[7] : TIR[1];
                vars[FIRE_MODE] = UP;
            } else {
                missile.objOff = mode == LEFT ? TIR[5] : TIR[3];
                vars[FIRE_MODE] = DOWN;
            }
            return;
        }
        boolean right = tx > missile.x;
        if (tx == missile.x) {
            vars[FIRE_MODE] = NONE;
            mode = NONE;
            right = false;
        }
        if (right) {
            missile.objOff = mode == UP ? TIR[1] : TIR[3];
            vars[FIRE_MODE] = RIGHT;
        } else {
            missile.objOff = mode == UP ? TIR[7] : TIR[5];
            vars[FIRE_MODE] = LEFT;
        }
    }

    /**
     * {@code .tacke_desision_fx}: the missile has finished a vertical run and
     * turns left or right, or up or down again when it is right above or
     * below the sorcerer.
     */
    private static void decideAlongX(GameState state, Alien missile, int[] vars) {
        target(state, vars);
        int tx = vars[TARGET_X];
        int ty = vars[TARGET_Y];
        int mode = vars[FIRE_MODE];
        if (tx != missile.x) {
            if (tx < missile.x) {
                missile.objOff = mode == UP ? TIR[7] : TIR[5];
                vars[FIRE_MODE] = LEFT;
            } else {
                missile.objOff = mode == UP ? TIR[1] : TIR[3];
                vars[FIRE_MODE] = RIGHT;
            }
            return;
        }
        if (ty > missile.y) {
            missile.objOff = mode == LEFT ? TIR[5] : TIR[3];
            vars[FIRE_MODE] = DOWN;
        } else {
            missile.objOff = mode == LEFT ? TIR[7] : TIR[1];
            vars[FIRE_MODE] = UP;
        }
    }
}
