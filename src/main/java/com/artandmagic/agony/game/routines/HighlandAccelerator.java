package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Volant_Accelaire}, the flier that speeds up when it is hit, from the
 * VOLANT ACCELAIRE SI TOUCHE block of {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>It crosses the screen right to left at the height the script gives it,
 * starting at two pixels a frame and flapping through the twenty steps of
 * {@code R_VA_Shape}, and recolours the first three colours of the top three
 * bands while it is on screen.  Every frame its status byte reads exactly
 * {@code $10} — the first frame of the white flash a hit gives it — it goes one
 * pixel a frame faster.  It closes once it is past x 220, giving back its share
 * of the palette.
 */
final class HighlandAccelerator implements Routine {

    private static final int MODE = 0;
    private static final int ANIM = 1;
    private static final int SPEED = 2;

    private static final int P_Y = 0;

    /** {@code R_VA_Pal} and {@code R_VA_Shape}. */
    private static final int PALETTE = 0x05082e;
    private static final int SHAPES = 0x050842;

    /** {@code Obj_Acc_1}, as the code loads it. */
    private final int firstShape = 0x442;

    private final int[] palette;
    private final int[] shapes;

    HighlandAccelerator(AgonyBinary bin) {
        palette = SeaRoutines.palette(bin, PALETTE);
        shapes = SeaRoutines.words(bin, SHAPES, 20);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        bank.count = 1;
        bank.first = 0;

        if (vars[MODE] == 0) {
            SeaRoutines.takePalette(state, palette);
            alien.x = 256 + 340;
            alien.y = HighlandRoutines.param(params, P_Y, 256 + 60);
            alien.objOff = firstShape;
            alien.energy = 6;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
            vars[SPEED] = 2;
            return true;
        }

        alien.x -= vars[SPEED];
        vars[ANIM] += 2;
        if (vars[ANIM] == 40) {
            vars[ANIM] = 0;
        }
        alien.objOff = shapes[vars[ANIM] / 2];

        if ((alien.status & 0xff) == 0x10) {
            vars[SPEED]++;
        }
        if (alien.x >= 220) {
            return true;
        }
        state.routModPalCounter--;
        HighlandRoutines.dropPalette(state);
        return false;
    }
}
