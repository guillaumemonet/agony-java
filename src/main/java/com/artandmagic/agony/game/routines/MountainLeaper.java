package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Guide}, from the SOL SAUTE GUIDE block of
 * {@code AG_GAME_LMONTAGNES.S}: a ground creature that leaps at the sorcerer.
 *
 * <p>It walks in along the ground two pixels a frame until it reaches the x the
 * script gives it.  There it sizes the sorcerer up once and picks one of five
 * jumps, each with its own shape: straight up when he is within forty pixels
 * across, otherwise towards him, steep when the horizontal distance and his
 * height above the ground are within seventy pixels of each other, flat when
 * they are not.  It keeps that heading until it leaves the screen, which closes
 * it.
 *
 * <p>The height it compares with is {@code 350-40} minus {@code Sorcerer_Y},
 * an unbiased constant against a biased coordinate, exactly as the source has
 * it.
 */
final class MountainLeaper implements Routine {

    private static final int MODE = 0;

    private static final int P_LAUNCH = 0;

    /** {@code Obj_Sol_Guide_0} to {@code _5}: walking, then the five jumps. */
    private final int walk = 0x3aa;
    private final int[] jump = {0x3bc, 0x3d2, 0x3e8, 0x3fe, 0x414};

    MountainLeaper(AgonyBinary bin) {
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        bank.count = 1;
        bank.first = 0;

        switch (vars[MODE]) {
            case 0 -> {
                alien.x = 256 + 320;
                alien.y = 256 + 178;
                alien.objOff = walk;
                alien.energy = 5;
                alien.status = 0;
                alien.fireRateStat = 0;
                alien.fireRateDyn = 0;
                vars[MODE]++;
            }
            case 1 -> {
                alien.x -= 2;
                if (alien.x == MountainRoutines.param(params, P_LAUNCH, 256 + 200)) {
                    vars[MODE] = choose(state, alien.x);
                }
            }
            case 2 -> {
                alien.objOff = jump[0];
                alien.x -= 3;
                alien.y -= 1;
            }
            case 3 -> {
                alien.objOff = jump[1];
                alien.x -= 3;
                alien.y -= 3;
            }
            case 4 -> {
                alien.objOff = jump[2];
                alien.y -= 3;
            }
            case 5 -> {
                alien.objOff = jump[3];
                alien.x += 3;
                alien.y -= 3;
            }
            default -> {
                alien.objOff = jump[4];
                alien.x += 3;
                alien.y -= 1;
            }
        }

        if (alien.x < 200 || alien.x > 256 + 330 || alien.y < 200) {
            MountainRoutines.close(state);
            return false;
        }
        return true;
    }

    /** The jump it takes, decided once when it reaches its launch point. */
    private int choose(GameState state, int x) {
        int dx = x - (state.sorcererX + 20);
        boolean right = dx >= 0;
        dx = Math.abs(dx);
        int height = (350 - 40) - state.sorcererY;

        if (dx <= 40) {
            return 4;
        }
        int gap = Math.abs(dx - height);
        if (right) {
            return gap <= 70 ? 3 : 2;
        }
        return gap <= 70 ? 5 : 6;
    }
}
