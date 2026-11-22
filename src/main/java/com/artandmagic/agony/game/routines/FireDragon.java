package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Dragon}, from the DRAGON block of {@code Ag_Game_LFEUX.s}.
 *
 * <p>The dragon flies in from far right at the height the script gives,
 * flapping through a ten step shape list, two pixels a frame, and it fires the
 * ordinary alien shots on its own every ten frames.  Every thirty frames, while
 * it is not done for, it breathes: a tongue of fire appears a hundred and ninety
 * pixels in front of it, then grows through the nineteen steps of
 * {@code Langue_Shape}, two pixels a frame to the left.  Once it has reached
 * its last shape it stops growing and races off, fourteen pixels a frame.  The
 * tongue is dropped from the bank once it is past x 130, or as soon as the
 * dragon starts to explode, and the count to the next breath only runs while
 * there is no tongue.  It is hard to put out: a hundred points of energy.
 *
 * <p>When the dragon is gone for good the breathing counter is left at thirty
 * and counts on past it, so it never breathes again; the routine itself only
 * closes when the dragon has flown off the left.
 */
final class FireDragon implements Routine {

    private static final int MODE = 0;
    private static final int TONGUE_DELAY = 1;
    private static final int TONGUE_STEP = 2;
    private static final int SHAPE = 3;

    private static final int P_Y = 0;

    /** {@code Dragon_Shape}, ten words, then {@code Langue_Shape}, nineteen. */
    private static final int DRAGON_SHAPES = 0x0510b8;
    private static final int TONGUE_SHAPES = 0x0510cc;

    /** {@code Obj_Dragon_1}, {@code Obj_Fire_1} and {@code Obj_Fire_8}. */
    private static final int DRAGON = 0x252;
    private static final int FIRE_1 = 0x292;
    private static final int FIRE_8 = 0x352;

    private final int[] dragonShapes;
    private final int[] tongueShapes;

    FireDragon(AgonyBinary bin) {
        dragonShapes = SeaRoutines.words(bin, DRAGON_SHAPES, 10);
        tongueShapes = SeaRoutines.words(bin, TONGUE_SHAPES, 19);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien dragon = bank.aliens[0];
        Alien tongue = bank.aliens[1];
        int mode = vars[MODE];

        if (mode == 0) {
            bank.count = 1;
            bank.first = 0;
            dragon.x = 256 + 350;
            dragon.y = params[P_Y];
            dragon.objOff = DRAGON;
            dragon.energy = 8;
            // move.l #$000a0000: status clear, a shot every ten frames.
            dragon.status = 0;
            dragon.fireRateStat = 10;
            dragon.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        vars[SHAPE] += 2;
        if (vars[SHAPE] == 20) {
            vars[SHAPE] = 0;
        }
        dragon.objOff = dragonShapes[vars[SHAPE] / 2];

        dragon.x -= 2;
        if (dragon.x <= 180) {
            return FireRoutines.close(state);
        }

        if (mode != 2) {
            vars[TONGUE_DELAY]++;
            if (vars[TONGUE_DELAY] != 30 || dragon.gone()) {
                return true;
            }
            vars[TONGUE_DELAY] = 0;
            bank.count = 2;
            bank.first = 0;
            tongue.x = dragon.x - 190;
            tongue.y = dragon.y;
            tongue.objOff = FIRE_1;
            tongue.energy = 100;
            tongue.status = 0;
            tongue.fireRateStat = 0;
            tongue.fireRateDyn = 0;
            vars[MODE]++;
            vars[TONGUE_STEP] = -2;
            return true;
        }

        if (dragon.exploState() != 0) {
            vars[MODE]--;
            return true;
        }
        bank.count = 2;
        bank.first = 0;
        tongue.x -= 2;
        if (tongue.objOff == FIRE_8) {
            tongue.x -= 12;
        }
        if (tongue.x <= 130) {
            vars[MODE]--;
            return true;
        }
        if (tongue.objOff == FIRE_8) {
            return true;
        }
        vars[TONGUE_STEP] += 2;
        tongue.objOff = tongueShapes[vars[TONGUE_STEP] / 2];
        return true;
    }
}
