package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Final}, the boss of the marshes, from the MONSTRE FINAL block of
 * {@code AG_GAME_LMARAIS.S}.
 *
 * <p>It enters at the right with a hundred and seventy points of energy,
 * cycling its ten frames of breathing, and shadows the sorcerer two pixels a
 * frame on each axis, aiming a hundred and fifty pixels to his right and forty
 * below his top.  Every fifty frames of chase it stops and shoots out its
 * tongue, a second alien of a hundred points laid a hundred and forty pixels
 * to its left, which unrolls and rolls back over the twenty six frames of
 * {@code Langue_Shape}; then the chase resumes.
 *
 * <p>Once its own explosion has run to the end (state {@code $f}) the routine
 * closes and ends the level the way the original does: {@code Quit_Delay} 25
 * and {@code Clean_Up}.
 *
 * <p>Two slips of the source are kept as they assemble:
 * <ul>
 * <li>{@code R_F_Langue_Delay} is used without its {@code (a3)}, so the tongue
 * counter is the word at absolute address 2 and not a routine variable
 * ({@code addq.w #1,$0002.w} in the binary).  It belongs to no slot, so it
 * lives here, starting at zero, and is never reset by a new start.</li>
 * <li>When the boss starts exploding with its tongue out, the routine means to
 * blow the tongue up but writes through {@code a4}, which at that point holds
 * {@code Final_Shape}: the byte lands on the high byte of the fifth shape,
 * which is already 1, so nothing changes, and the tongue simply drops out of
 * the bank when the count goes back to one.</li>
 * </ul>
 */
final class MarshFinalMonster implements Routine {

    private static final int MODE = 0;
    private static final int LANGUE_STEP = 2;
    private static final int SHAPE = 3;

    /** {@code Final_Shape}, ten words, followed by {@code Langue_Shape}, twenty six. */
    static final int FINAL_SHAPE = 0x04fe2c;
    static final int LANGUE_SHAPE = 0x04fe40;

    private final int[] body;
    private final int[] tongue;

    /** The word at absolute address 2 the source counts the tongue delay in. */
    private int langueDelay;

    MarshFinalMonster(AgonyBinary bin) {
        body = SeaRoutines.words(bin, FINAL_SHAPE, 10);
        tongue = SeaRoutines.words(bin, LANGUE_SHAPE, 26);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien boss = bank.aliens[0];
        Alien langue = bank.aliens[1];

        if (vars[MODE] == 0) {
            bank.count = 1;
            bank.first = 0;
            boss.x = 256 + 300;
            boss.y = 256 + 80;
            boss.objOff = body[0];
            boss.energy = 170;
            // move.l #$000c0000: status 0, a shot every twelve frames.
            boss.status = 0;
            boss.fireRateStat = 12;
            boss.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        bank.count = 1;
        bank.first = 0;
        vars[SHAPE] += 2;
        if (vars[SHAPE] == 20) {
            vars[SHAPE] = 0;
        }
        boss.objOff = body[vars[SHAPE] / 2];

        if (boss.exploState() == 0xf) {
            // .close: this is what ends the level and brings the next one in.
            state.quitDelay = 25;
            state.cleanUp = true;
            return false;
        }

        if (vars[MODE] == 2) {
            return tongue(bank, vars);
        }

        boss.x = chase(boss.x, state.sorcererX + 150);
        boss.y = chase(boss.y, state.sorcererY + 40);

        langueDelay = (langueDelay + 1) & 0xffff;
        if (langueDelay != 50) {
            return true;
        }
        langueDelay = 0;
        bank.count = 2;
        bank.first = 0;
        langue.x = boss.x - 140;
        langue.y = boss.y;
        langue.objOff = tongue[0];
        langue.energy = 100;
        langue.status = 0;
        langue.fireRateStat = 0;
        langue.fireRateDyn = 0;
        vars[MODE]++;
        vars[LANGUE_STEP] = -2;
        return true;
    }

    /** Mode 2: the tongue unrolls and rolls back while the boss holds still. */
    private boolean tongue(AlienBank bank, int[] vars) {
        Alien boss = bank.aliens[0];
        if (boss.exploState() != 0) {
            // The write meant for the tongue's status changes nothing (see above).
            vars[MODE]--;
            return true;
        }
        bank.count = 2;
        bank.first = 0;
        vars[LANGUE_STEP] += 2;
        if (vars[LANGUE_STEP] == 52) {
            vars[MODE]--;
            return true;
        }
        bank.aliens[1].objOff = tongue[vars[LANGUE_STEP] / 2];
        return true;
    }

    /** One axis of the chase, two pixels a frame, still when within two. */
    private static int chase(int current, int target) {
        if (Math.abs(target - current) <= 2) {
            return current;
        }
        return current < target ? current + 2 : current - 2;
    }
}
