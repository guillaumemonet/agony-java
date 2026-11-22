package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Final}, Mentor, the last monster of the game, from the MONSTRE
 * FINAL block of {@code Ag_Game_LFEUX.s}.
 *
 * <p>He installs his palette ({@code R_Final_Pal}, without counting himself in
 * {@code Rout_Mod_Pal_Counter}), comes in at the right with three hundred
 * points of energy, and cycles four shapes two frames each.  He then shadows
 * the sorcerer, two pixels a frame on each axis, aiming for a point a hundred
 * and fifty pixels to his right and forty below his top, but never moves right
 * of x 256+270 to get there.  Every thirty five frames he throws a burst of
 * fire balls: he starts {@link FireFinalBall} himself in a free routine slot,
 * with a free alien bank, handing it his position through {@code Par_Buffer}.
 *
 * <p>He keeps following and throwing while his explosion plays; only once it
 * is over ({@code $f} in the explosion state) does he close, and that close is
 * the end of the level and of the game: {@code Quit_Delay} 25 and
 * {@code Clean_Up}.
 */
final class FireFinalMonster implements Routine {

    private static final int MODE = 0;
    private static final int SHAPE = 1;
    private static final int LAUNCH_DELAY = 2;

    /** {@code Final_Shape}, eight words, then {@code R_Final_Pal}. */
    private static final int SHAPES = 0x051434;
    private static final int PALETTE = 0x051444;

    /** {@code Obj_Final_1}. */
    private static final int FINAL_1 = 0xb52;

    private final int[] shapes;
    private final int[] palette;
    private final FireFinalBall ball;

    FireFinalMonster(AgonyBinary bin, FireFinalBall ball) {
        shapes = SeaRoutines.words(bin, SHAPES, 8);
        palette = SeaRoutines.palette(bin, PALETTE);
        this.ball = ball;
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien boss = bank.aliens[0];

        if (vars[MODE] == 0) {
            state.routPalette = palette;
            state.refreshPal = GameState.PAL_ASKED;
            bank.count = 1;
            bank.first = 0;
            boss.x = 256 + 300;
            boss.y = 256 + 80;
            boss.objOff = FINAL_1;
            boss.energy = 300;
            boss.status = 0;
            boss.fireRateStat = 0;
            boss.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        vars[SHAPE] += 2;
        if (vars[SHAPE] == 16) {
            vars[SHAPE] = 0;
        }
        boss.objOff = shapes[vars[SHAPE] / 2];

        if (boss.gone()) {
            // This is what ends the level, and the game.
            state.quitDelay = 25;
            state.cleanUp = true;
            return false;
        }

        // Across: towards the sorcerer plus 150, but never right of 256+270.
        int targetX = state.sorcererX + 150;
        if (Math.abs(targetX - boss.x) > 2) {
            if (boss.x >= targetX) {
                boss.x -= 2;
            } else if (boss.x < 256 + 270) {
                boss.x += 2;
            }
        }
        int targetY = state.sorcererY + 40;
        if (Math.abs(targetY - boss.y) > 2) {
            boss.y += boss.y < targetY ? 2 : -2;
        }

        vars[LAUNCH_DELAY]++;
        if (vars[LAUNCH_DELAY] == 35) {
            vars[LAUNCH_DELAY] = 0;
            // Par_Buffer: xpos, ypos, Par_End.
            state.pendingRoutines.add(new GameState.PendingRoutine(ball,
                    new int[]{boss.x, boss.y}));
        }
        return true;
    }
}
