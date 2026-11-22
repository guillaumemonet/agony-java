package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Final}, the boss of the highlands, from the MONSTRE FINAL block of
 * {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>It rises from below the screen with two hundred points of energy and then
 * sweeps up and down between lines 40 and 200, three pixels a frame, playing
 * the fourteen steps of {@code Final_Shape}; across, it keeps two hundred pixels
 * to the right of the sorcerer, one pixel a frame, but never comes further
 * right than x 260 once it is there.  Every eighteen frames it sends a copy of
 * itself at him: the routine walks {@code Rout_Struct} for a free slot and
 * {@code AWO_Struct} for a free bank, and starts {@code R_Final_2} there by
 * hand, with its own position in {@code Par_Buffer} as the parameters.
 *
 * <p>Unlike the boss of the sea there is no closing sequence: the frame its
 * explosion has run its course, the routine closes and sets {@code Quit_Delay}
 * and {@code Clean_Up}, which is what ends the level.
 */
final class HighlandFinalMonster implements Routine {

    private static final int MODE = 0;
    private static final int LAUNCH_DELAY = 1;
    private static final int ANIM = 2;
    private static final int SENS = 3;

    /** {@code Final_Shape}. */
    private static final int SHAPES = 0x050926;

    /** {@code Obj_Final_1}, as the code loads it. */
    private final int firstShape = 0xae;

    private final int[] shapes;
    private final HighlandFinalSlave slave;

    HighlandFinalMonster(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 14);
        slave = new HighlandFinalSlave(bin);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien boss = bank.aliens[0];
        bank.count = 1;
        bank.first = 0;

        if (vars[MODE] == 0) {
            boss.x = 256 + 240;
            boss.y = 256 + 300;
            boss.objOff = firstShape;
            boss.energy = 200;
            boss.status = 0;
            boss.fireRateStat = 8;
            boss.fireRateDyn = 0;
            vars[MODE]++;
            vars[SENS] = 0;
            return true;
        }

        if (boss.exploState() == 0xf) {
            // This is what ends the level and brings the next one in.
            state.quitDelay = 25;
            state.cleanUp = true;
            return false;
        }

        if (vars[SENS] == 0) {
            boss.y -= 3;
            if (boss.y <= 256 + 40) {
                vars[SENS]++;
            }
        } else {
            boss.y += 3;
            if (boss.y >= 256 + 200) {
                vars[SENS]--;
            }
        }

        int targetX = state.sorcererX + 200;
        if (targetX < boss.x) {
            boss.x--;
        } else if (targetX > boss.x && boss.x != 256 + 260) {
            boss.x++;
        }

        vars[ANIM] += 2;
        if (vars[ANIM] == 28) {
            vars[ANIM] = 0;
        }
        boss.objOff = shapes[vars[ANIM] / 2];

        vars[LAUNCH_DELAY]++;
        if (vars[LAUNCH_DELAY] == 18) {
            vars[LAUNCH_DELAY] = 0;
            state.pendingRoutines.add(new GameState.PendingRoutine(slave,
                    new int[]{boss.x, boss.y}));
        }
        return true;
    }
}
