package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Dragon}, from the DRAGON block of {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>The dragon flies in from the right at the height the script gives it, two
 * pixels a frame, beating its wings through the ten steps of
 * {@code Dragon_Shape}, and recolours two of the colours of the top three
 * bands while it is on screen.  Thirty frames after it arrives, and thirty
 * frames after each breath, it breathes fire: its tongue, a second alien with
 * a hundred points of energy, comes out 190 pixels ahead of its x and plays the
 * nineteen steps of {@code Langue_Shape} as it drifts along with it.  Once the
 * last frame of the flame is reached, the flame leaves the dragon and shoots
 * off left at fourteen pixels a frame; it is gone when it reaches x 130, or as
 * soon as the dragon is hit hard enough to explode.  The dragon closes once it
 * is past x 180, giving back its share of the palette.
 */
final class HighlandDragon implements Routine {

    private static final int MODE = 0;
    private static final int TONGUE_DELAY = 1;
    private static final int TONGUE_STEP = 2;
    private static final int SHAPE = 3;

    private static final int P_Y = 0;

    /** {@code R_D_Pal}, {@code Dragon_Shape} and {@code Langue_Shape}. */
    private static final int PALETTE = 0x05067c;
    private static final int DRAGON_SHAPES = 0x050690;
    private static final int TONGUE_SHAPES = 0x0506a4;

    /** {@code Obj_Dragon_1}, {@code Obj_Fire_1} and {@code Obj_Fire_8}, as the code loads them. */
    private final int dragonShape = 0x27e;
    private final int fire1 = 0x2be;
    private final int fire8 = 0x37e;

    private final int[] palette;
    private final int[] dragonShapes;
    private final int[] tongueShapes;

    HighlandDragon(AgonyBinary bin) {
        palette = SeaRoutines.palette(bin, PALETTE);
        dragonShapes = SeaRoutines.words(bin, DRAGON_SHAPES, 10);
        tongueShapes = SeaRoutines.words(bin, TONGUE_SHAPES, 19);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien dragon = bank.aliens[0];
        Alien tongue = bank.aliens[1];
        int mode = vars[MODE];
        bank.count = 1;
        bank.first = 0;

        if (mode == 0) {
            SeaRoutines.takePalette(state, palette);
            dragon.x = 256 + 350;
            dragon.y = HighlandRoutines.param(params, P_Y, 256 + 60);
            dragon.objOff = dragonShape;
            dragon.energy = 8;
            dragon.status = 0;
            dragon.fireRateStat = 10;
            dragon.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        vars[SHAPE] += 2;
        if (vars[SHAPE] == 20) {
            vars[SHAPE] = 0;
        }
        dragon.objOff = dragonShapes[vars[SHAPE] / 2];

        dragon.x -= 2;
        if (dragon.x <= 180) {
            // This one holds a share of the palette and gives it back.
            state.routModPalCounter--;
            HighlandRoutines.dropPalette(state);
            return false;
        }

        if (mode != 2) {
            vars[TONGUE_DELAY]++;
            if (vars[TONGUE_DELAY] != 30 || dragon.exploState() == 0xf) {
                return true;
            }
            vars[TONGUE_DELAY] = 0;
            bank.count = 2;
            tongue.x = dragon.x - 190;
            tongue.y = dragon.y;
            tongue.objOff = fire1;
            tongue.energy = 100;
            tongue.status = 0;
            tongue.fireRateStat = 0;
            tongue.fireRateDyn = 0;
            vars[MODE]++;
            vars[TONGUE_STEP] = -2;
            return true;
        }

        if (dragon.exploState() != 0) {
            // The flame goes out with the dragon.
            vars[MODE]--;
            return true;
        }
        bank.count = 2;
        tongue.x -= 2;
        if (tongue.objOff == fire8) {
            tongue.x -= 12;
        }
        if (tongue.x <= 130) {
            vars[MODE]--;
            return true;
        }
        if (tongue.objOff == fire8) {
            return true;
        }
        vars[TONGUE_STEP] += 2;
        tongue.objOff = tongueShapes[vars[TONGUE_STEP] / 2];
        return true;
    }
}
