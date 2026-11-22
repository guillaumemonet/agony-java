package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Final_2}, the burst of fire balls Mentor throws, from the FINAL 2
 * (SLAVE) block of {@code Ag_Game_LFEUX.s}.
 *
 * <p>The script never starts it: {@link FireFinalMonster} does, every thirty
 * five frames, and hands it his position.  It sets up eight fire balls
 * thirty two pixels up and left of that point, all playing the same six frame
 * animation, but for forty frames only the first one is in the bank: a single
 * ball with thirty points of energy, flying left four pixels a frame.  Then the
 * other seven are brought to its x and the bank shows all eight, which fly
 * apart, one along each point of the compass, for another eighty frames, and
 * the routine closes.  The seven are worth a single hit each, and the burst
 * happens whether or not the first ball was shot down on the way.
 *
 * <p>It does not touch the palette when it closes.
 */
final class FireFinalBall implements Routine {

    /** Where {@code R_Final_2} sits in the overlay. */
    static final int ADDRESS = 0x0515ca;

    private static final int MODE = 0;
    private static final int ANIM = 1;
    private static final int EXP_DELAY = 2;

    private static final int P_X = 0;
    private static final int P_Y = 1;

    /** {@code R_F2_Shape}, six words in front of the code. */
    private static final int SHAPES = 0x0515be;

    /** {@code Obj_FireBall_1}. */
    private static final int FIREBALL_1 = 0x868;

    /** The step of each ball once they fly apart, in the order the routine moves them. */
    private static final int[][] STEP = {
        {0, -4}, {3, -3}, {4, 0}, {3, 3}, {0, 4}, {-3, 3}, {-4, 0}, {-3, -3}
    };

    private final int[] shapes;

    FireFinalBall(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 6);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien lead = bank.aliens[0];
        int mode = vars[MODE];

        if (mode == 0) {
            bank.count = 1;
            bank.first = 0;
            for (int i = 0; i < 8; i++) {
                Alien ball = bank.aliens[i];
                ball.x = params[P_X] - 32;
                ball.y = params[P_Y] - 32;
                ball.objOff = FIREBALL_1;
                ball.energy = 1;
                ball.status = 0;
                ball.fireRateStat = 0;
                ball.fireRateDyn = 0;
            }
            lead.energy = 30;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        vars[ANIM] += 2;
        if (vars[ANIM] == 12) {
            vars[ANIM] = 0;
        }
        int shape = shapes[vars[ANIM] / 2];
        for (int i = 0; i < 8; i++) {
            bank.aliens[i].objOff = shape;
        }

        if (mode != 2) {
            lead.x -= 4;
            vars[EXP_DELAY]++;
            if (vars[EXP_DELAY] == 40) {
                vars[MODE]++;
                for (int i = 0; i < 8; i++) {
                    bank.aliens[i].x = lead.x;
                }
            }
            return true;
        }

        bank.count = 8;
        for (int i = 0; i < 8; i++) {
            bank.aliens[i].x += STEP[i][0];
            bank.aliens[i].y += STEP[i][1];
        }
        vars[EXP_DELAY]++;
        return vars[EXP_DELAY] != 120;
    }
}
