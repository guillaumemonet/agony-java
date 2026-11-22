package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Bomber}, from the GROS MONSTRE LARGUEUR DE BOMBE block of
 * {@code AG_GAME_LMONTAGNES.S}.
 *
 * <p>The sea's bomber ({@link Bomber}) with this level's sprites: a bag crosses
 * the top of the screen and lets a boulder go every twenty four frames, up to
 * four of them.  Each boulder walks its own cursor along {@code Sin_Table1},
 * the curve it falls on; the cursor also sets how far behind the bag it has
 * drifted, and a negative byte of the table ends its fall.
 *
 * <p>The four cursors are bytes in front of the routine's words, so the first
 * four slots here are theirs.  The palette lines are commented out in the
 * source, here as in the sea.
 */
final class MountainBomber implements Routine {

    /** One cursor per bomb, -1 once the bomb has finished its fall. */
    private static final int TABLE_POS = 0;
    private static final int MODE = 4;
    private static final int LAUNCH_DELAY = 7;
    private static final int BOMB_NUM = 8;
    private static final int AWO_INDEX = 9;

    /** How many bombs it carries. */
    private static final int BOMBS = 4;

    /** {@code Sin_Table1}, behind the relative and absolute tracks. */
    private static final int SIN_TABLE_1 = 0x04d43c;

    /** {@code Obj_Sac} and {@code Obj_Boulle_1}. */
    private final int carrier = 0xdc;
    private final int bomb = 0xf6;
    private final int[] curve;

    MountainBomber(AgonyBinary bin) {
        // Sin_Table1 is read until it turns negative, so its length is its own.
        curve = SeaRoutines.bytes(bin, SIN_TABLE_1, 256);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien monster = bank.aliens[0];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            monster.x = 256 + 340;
            monster.y = 256 + 32;
            monster.objOff = carrier;
            monster.energy = 15;
            monster.status = 0;
            monster.fireRateStat = 0;
            monster.fireRateDyn = 0;
            vars[MODE]++;
            vars[LAUNCH_DELAY] = 0;
            vars[AWO_INDEX] = 0;
            // The bombs stay out of sight until they are dropped.
            for (int i = 1; i <= 5; i++) {
                bank.aliens[i].status = 0x0f;
            }
            return true;
        }

        bank.count = 5;
        bank.first = 0;
        monster.x -= 2;
        boolean over = monster.x <= 200;
        if (over) {
            MountainRoutines.close(state);
        }

        int carried = vars[BOMB_NUM];
        if (carried != BOMBS && monster.exploState() != 0xf) {
            vars[LAUNCH_DELAY]++;
            if (vars[LAUNCH_DELAY] == 24) {
                vars[LAUNCH_DELAY] = 0;
                vars[BOMB_NUM]++;
                vars[AWO_INDEX]++;
                Alien dropped = bank.aliens[vars[AWO_INDEX]];
                dropped.objOff = bomb;
                dropped.energy = 3;
                dropped.status = 0;
                dropped.fireRateStat = 0xff;
                dropped.x = 0;
                dropped.y = 0;
                vars[TABLE_POS + carried] = 0;
            }
        }

        // Every bomb already in the air walks one step of its curve.  The
        // dbra runs one more time than there are bombs; with all four out
        // that extra pass lands on the mode word and moves a sixth alien the
        // bank never shows, which the port leaves out.
        if (carried != 0) {
            for (int i = 0; i <= carried && i < BOMBS; i++) {
                int cursor = vars[TABLE_POS + i];
                if (cursor < 0) {
                    continue;
                }
                vars[TABLE_POS + i] = cursor + 1;
                int height = (byte) curve[cursor & 0xff];
                if (height < 0) {
                    vars[TABLE_POS + i] = -1;
                    continue;
                }
                Alien falling = bank.aliens[1 + i];
                falling.y = 256 + 172 - height;
                falling.x = monster.x - cursor - 30;
            }
        }

        return !over;
    }
}
