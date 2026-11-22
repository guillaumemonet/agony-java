package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Araignee}, the spider that rides up and down its thread, from the
 * ARAIGNEE DESCEND DE SON FIL block of {@code Ag_Game_LFORET.s}.
 *
 * <p>It comes in from the right at the height the script gives it, drifts left
 * two pixels a frame, and bobs between 340 and 150 at the speed of its second
 * parameter, turning at each end.  The forest script starts it at a height of
 * 50, which is above the top of the playfield (the coordinates are biased by
 * 256), so the spider drops in from out of sight.  Off the left it closes.
 * Unlike the sea version ({@link Spider}) it installs no palette.
 */
final class ForestSpider implements Routine {

    private static final int MODE = 0;
    private static final int Y_MODE = 1;

    private static final int P_Y_START = 0;
    private static final int P_STEP = 1;

    /** {@code Obj_Araignee}. */
    private final int shape = 0x3f4;

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien spider = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            spider.x = 256 + 340;
            spider.y = ForestRoutines.param(params, P_Y_START, 256 + 150);
            spider.objOff = shape;
            spider.energy = 10;
            ForestRoutines.clearStatus(spider);
            vars[MODE]++;
            vars[Y_MODE] = 0;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        int step = ForestRoutines.param(params, P_STEP, 2);

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
            ForestRoutines.dropPalette(state);
            return false;
        }
        return true;
    }
}
