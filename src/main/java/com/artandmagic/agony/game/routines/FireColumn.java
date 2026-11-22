package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Colonne_flame}, the column of flames, from the COLONNE DE FLAME
 * block of {@code Ag_Game_LFEUX.s}.
 *
 * <p>Four big flames stacked thirty five pixels apart, from the floor up, come
 * in from the right two pixels a frame, each flickering through the eight
 * shapes of {@code R_CF_Shape}.  They cannot be put out (32767 points of
 * energy).  The column breathes: every three frames the routine writes the
 * next entry of {@code R_CF_Hight} into the alien count of its bank, so one to
 * four flames show, the bottom one always.  It closes when the column is past
 * x 200.
 *
 * <p>The init looks meant to start the four flames at staggered shapes, but it
 * writes those phases with {@code move.l #$00000002,R_CF_Shape_Num_0} and
 * {@code move.l #$00040006,R_CF_Shape_Num_2}: absolute addresses, the offsets
 * 2 and 6 of the variable block taken as addresses, rather than
 * {@code (a3)} relative ones.  The binary has the same two absolute writes, so
 * in the original the variables keep the zero the routine slot starts with and
 * the four flames flicker in step.  The port does the same.
 */
final class FireColumn implements Routine {

    private static final int MODE = 0;
    /** {@code R_CF_Shape_Num_0} to {@code _3}, one per flame. */
    private static final int SHAPE_NUM = 1;
    private static final int HIGHT_NUM = 5;
    private static final int HIGHT_DELAY = 6;

    /** {@code R_CF_Shape}, eight words, then {@code R_CF_Hight}, sixteen. */
    private static final int SHAPES = 0x051312;
    private static final int HIGHTS = 0x051322;

    private final int[] shapes;
    private final int[] hights;

    FireColumn(AgonyBinary bin) {
        shapes = SeaRoutines.words(bin, SHAPES, 8);
        hights = SeaRoutines.words(bin, HIGHTS, 16);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien bottom = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            int y = 256 + 190;
            for (int i = 0; i < 4; i++) {
                Alien flame = bank.aliens[i];
                flame.x = 256 + 360;
                flame.y = y;
                flame.objOff = shapes[0];
                flame.energy = 32767;
                flame.status = 0;
                flame.fireRateStat = 0;
                flame.fireRateDyn = 0;
                y -= 35;
            }
            vars[MODE]++;
            vars[HIGHT_NUM] = 0;
            vars[HIGHT_DELAY] = 0;
            return end(state, bottom);
        }

        // No header write here: the count is whatever the last height left.
        for (int i = 0; i < 4; i++) {
            vars[SHAPE_NUM + i] = (vars[SHAPE_NUM + i] + 2) & 0xf;
            Alien flame = bank.aliens[i];
            flame.objOff = shapes[vars[SHAPE_NUM + i] / 2];
            flame.x -= 2;
        }

        vars[HIGHT_DELAY]++;
        if (vars[HIGHT_DELAY] == 3) {
            vars[HIGHT_DELAY] = 0;
            vars[HIGHT_NUM] = (vars[HIGHT_NUM] + 2) & 0x1f;
            bank.count = hights[vars[HIGHT_NUM] / 2];
        }
        return end(state, bottom);
    }

    private boolean end(GameState state, Alien bottom) {
        if (bottom.x <= 200) {
            return FireRoutines.close(state);
        }
        return true;
    }
}
