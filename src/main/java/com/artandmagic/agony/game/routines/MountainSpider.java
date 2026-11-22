package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Araignee}, the spider that rides up and down its thread, from the
 * ARAIGNEE DESCEND DE SON FIL block of {@code AG_GAME_LMONTAGNES.S}.
 *
 * <p>The sea's spider ({@link Spider}) without its palette: it comes in from the
 * right at the height the script gives it, drifts left two pixels a frame and
 * bobs between 340 and 150 at the speed of its second parameter.  Off the left
 * edge it closes, and hands the routine palette back if nobody holds it.
 */
final class MountainSpider implements Routine {

    private static final int MODE = 0;
    private static final int Y_MODE = 2;

    /** Where the spider starts, and how fast it travels along its thread. */
    private static final int P_Y_START = 0;
    private static final int P_STEP = 1;

    /** {@code Obj_Araignee}. */
    private final int shape = 0x9c;

    MountainSpider(AgonyBinary bin) {
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien spider = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            spider.x = 256 + 340;
            spider.y = MountainRoutines.param(params, P_Y_START, 150);
            spider.objOff = shape;
            spider.energy = 10;
            spider.status = 0;
            spider.fireRateStat = 0;
            spider.fireRateDyn = 0;
            vars[MODE]++;
            vars[Y_MODE] = 0;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        int step = MountainRoutines.param(params, P_STEP, 2);

        if (vars[Y_MODE] == 0) {
            spider.y += step;
            if (spider.y >= 340) {
                vars[Y_MODE] = 0xff;
            }
        } else {
            spider.y -= step;
            if (spider.y <= 150) {
                vars[Y_MODE] = 0;
            }
        }

        spider.x -= 2;
        if (spider.x <= 200) {
            MountainRoutines.close(state);
            return false;
        }
        return true;
    }
}
