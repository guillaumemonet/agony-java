package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.data.original.AgonyBinary;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Final}, the boss of the mountains, from the MONSTRE FINAL block of
 * {@code AG_GAME_LMONTAGNES.S}.
 *
 * <p>It rises from below the screen with two hundred points of energy, then
 * swings up and down three pixels a frame between lines 104 and 230 while it
 * keeps two hundred pixels to the right of the sorcerer, one pixel a frame,
 * never coming further right than x 260.  Every twenty five frames it stops
 * and swings its mace: a second alien appears 210 pixels to its left and plays
 * {@code Masse_Shape} over nineteen frames, after which the mace is gone and
 * the boss moves again.  The mace has an energy of -1, a word the collision
 * test counts down from 65535, so it cannot be shot away.
 *
 * <p>Unlike the sea's boss this one throws no waves and has no explosion
 * sequence of its own: once its own explosion has run out the routine closes,
 * sets {@code Quit_Delay} to 25 and raises {@code Clean_Up}, which is what ends
 * the level.
 */
final class MountainFinal implements Routine {

    private static final int MODE = 0;
    private static final int MASSE_DELAY = 1;
    private static final int MASSE_STEP = 2;
    private static final int SENS = 3;

    /** {@code Masse_Shape}, twenty words in front of the code. */
    private static final int MASSE_SHAPES = 0x04c860;

    /** {@code Obj_Final_0}, the boss, and {@code Obj_Final_1}, the mace. */
    private final int body = 0x676;
    private final int mace = 0x6a6;
    private final int[] masseShapes;

    MountainFinal(AgonyBinary bin) {
        masseShapes = SeaRoutines.words(bin, MASSE_SHAPES, 20);
    }

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien boss = bank.aliens[0];
        Alien masse = bank.aliens[1];
        bank.count = 1;
        bank.first = 0;

        if (vars[MODE] == 0) {
            boss.x = 256 + 240;
            boss.y = 256 + 300;
            boss.objOff = body;
            boss.energy = 200;
            boss.status = 0;
            boss.fireRateStat = 0x0a;
            boss.fireRateDyn = 0;
            vars[MODE]++;
            vars[SENS] = 0;
            return true;
        }

        if (boss.gone()) {
            // This is what ends the level and brings the next one in.
            state.quitDelay = 25;
            state.cleanUp = true;
            return false;
        }

        if (vars[MODE] == 2) {
            bank.count = 2;
            vars[MASSE_STEP] += 2;
            if (vars[MASSE_STEP] == 40) {
                vars[MODE]--;
                return true;
            }
            masse.objOff = masseShapes[vars[MASSE_STEP] / 2];
            return true;
        }

        if (vars[SENS] == 0) {
            boss.y -= 3;
            if (boss.y <= 256 + 104) {
                vars[SENS]++;
            }
        } else {
            boss.y += 3;
            if (boss.y >= 256 + 230) {
                vars[SENS]--;
            }
        }

        int target = state.sorcererX + 200;
        if (target < boss.x) {
            boss.x--;
        } else if (target > boss.x && boss.x != 256 + 260) {
            boss.x++;
        }

        vars[MASSE_DELAY]++;
        if (vars[MASSE_DELAY] == 25) {
            vars[MASSE_DELAY] = 0;
            vars[MASSE_STEP] = 0;
            vars[MODE]++;
            masse.x = boss.x - 210;
            masse.y = boss.y;
            masse.objOff = mace;
            masse.energy = 0xffff;
            masse.status = 0;
            masse.fireRateStat = 0;
            masse.fireRateDyn = 0;
        }
        return true;
    }
}
