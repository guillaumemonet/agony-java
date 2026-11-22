package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Dragon}, from the DRAGON block of {@code AG_GAME_LMONTAGNES.S}.
 *
 * <p>A dragon flies in from the right at the height the script gives it, two
 * pixels a frame, flapping through {@code Dragon_Shape}.  Every thirty frames,
 * unless it is already gone, it breathes: a second alien, its tongue of fire,
 * appears 190 pixels to its left and runs through {@code Langue_Shape}, which
 * holds the first flame for twelve steps and then lets it flare over seven
 * more.  The tongue drifts left two pixels a frame, fourteen once it has
 * reached its last shape, and the breath ends when it gets past x 130 or when
 * the dragon is hit hard enough to start exploding; the bank then drops back
 * to one alien and the count to the next breath starts again.  Past x 180 the
 * dragon closes.
 *
 * <p>The breath counter is only reset when the dragon breathes, so a dragon
 * that has finished exploding lets it run past thirty and never breathes
 * again.
 */
final class MountainDragon implements Routine {

    private static final int MODE = 0;
    private static final int LANGUE_DELAY = 1;
    private static final int LANGUE_STEP = 2;
    private static final int SHAPE = 3;

    private static final int P_Y = 0;

    /** {@code Dragon_Shape}, with {@code Langue_Shape} right behind it. */
    private static final int DRAGON_SHAPES = 0x04c6d6;
    private static final int LANGUE_SHAPES = 0x04c6ea;

    /** {@code Obj_Dragon_1}, {@code Obj_Fire_1} and {@code Obj_Fire_8}. */
    private final int dragon1 = 0x54e;
    private final int fire1 = 0x58e;
    private final int fire8 = 0x64e;

    private final int[] dragonShapes;
    private final int[] langueShapes;

    MountainDragon(AgonyBinary bin) {
        dragonShapes = SeaRoutines.words(bin, DRAGON_SHAPES, 10);
        langueShapes = SeaRoutines.words(bin, LANGUE_SHAPES, 19);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien dragon = bank.aliens[0];
        Alien tongue = bank.aliens[1];
        int mode = vars[MODE];
        bank.count = 1;
        bank.first = 0;

        if (mode == 0) {
            dragon.x = 256 + 350;
            dragon.y = MountainRoutines.param(params, P_Y, 256 + 60);
            dragon.objOff = dragon1;
            dragon.energy = 8;
            dragon.status = 0;
            dragon.fireRateStat = 0x0a;
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
            MountainRoutines.close(state);
            return false;
        }

        if (mode != 2) {
            vars[LANGUE_DELAY]++;
            if (vars[LANGUE_DELAY] != 30 || dragon.gone()) {
                return true;
            }
            vars[LANGUE_DELAY] = 0;
            bank.count = 2;
            tongue.x = dragon.x - 190;
            tongue.y = dragon.y;
            tongue.objOff = fire1;
            tongue.energy = 100;
            tongue.status = 0;
            tongue.fireRateStat = 0;
            tongue.fireRateDyn = 0;
            vars[MODE]++;
            vars[LANGUE_STEP] = -2;
            return true;
        }

        // Mode 2: the breath is out.
        if (dragon.exploState() != 0) {
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
        vars[LANGUE_STEP] += 2;
        tongue.objOff = langueShapes[vars[LANGUE_STEP] / 2];
        return true;
    }
}
