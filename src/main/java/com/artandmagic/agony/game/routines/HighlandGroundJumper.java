package com.artandmagic.agony.game.routines;

import com.artandmagic.agony.data.Routine;
import com.artandmagic.agony.game.Alien;
import com.artandmagic.agony.game.AlienBank;
import com.artandmagic.agony.game.GameState;

/**
 * {@code R_Sol_Guide}, the ground creature that leaps at the sorcerer, from
 * the SOL SAUTE GUIDE block of {@code Ag_Game_LPLATEAUX.s}.
 *
 * <p>It crawls in along the floor two pixels a frame until it reaches the x the
 * script names, {@code P_SG_Launch}.  There it measures where the sorcerer is
 * and picks one of five fixed leaps, each with a shape of its own: straight up
 * when he is within forty pixels across; otherwise it compares how far across
 * he is with how far above, and leaps steeply (three across, three up) when the
 * two are within seventy pixels of each other, flat (three across, one up) when
 * not, towards whichever side he is on.  It never changes course after that,
 * and closes when it leaves the screen on any side but the bottom.
 */
final class HighlandGroundJumper implements Routine {

    private static final int MODE = 0;

    private static final int P_LAUNCH = 0;

    /** {@code Obj_Sol_Guide_0} to {@code _5}, as the code loads them. */
    private final int crawl = 0x5fc;
    private final int flatLeft = 0x60e;
    private final int steepLeft = 0x838;
    private final int straightUp = 0x84e;
    private final int steepRight = 0x868;
    private final int flatRight = 0x87e;

    @Override
    public boolean update(GameState state, AlienBank bank, int[] vars, int[] params) {
        Alien alien = bank.aliens[0];
        bank.count = 1;
        bank.first = 0;

        if (vars[MODE] == 0) {
            alien.x = 256 + 320;
            alien.y = 256 + 178;
            alien.objOff = crawl;
            alien.energy = 5;
            alien.status = 0;
            alien.fireRateStat = 0;
            alien.fireRateDyn = 0;
            vars[MODE]++;
            return true;
        }

        switch (vars[MODE]) {
            case 2 -> leap(alien, flatLeft, -3, -1);
            case 3 -> leap(alien, steepLeft, -3, -3);
            case 4 -> leap(alien, straightUp, 0, -3);
            case 5 -> leap(alien, steepRight, 3, -3);
            case 6 -> leap(alien, flatRight, 3, -1);
            default -> crawl(state, alien, vars, params);
        }

        if (alien.x < 200 || alien.x > 256 + 330 || alien.y < 200) {
            HighlandRoutines.dropPalette(state);
            return false;
        }
        return true;
    }

    /** Mode 1: crawl to the launch point, then choose the leap. */
    private void crawl(GameState state, Alien alien, int[] vars, int[] params) {
        alien.x -= 2;
        if (alien.x != HighlandRoutines.param(params, P_LAUNCH, 256 + 200)) {
            return;
        }
        int across = alien.x - (state.sorcererX + 20);
        boolean sorcererLeft = across >= 0;
        across = Math.abs(across);
        int above = (350 - 40) - state.sorcererY;

        if (across <= 40) {
            vars[MODE] = 4;
            return;
        }
        boolean steep = Math.abs(across - above) <= 70;
        if (sorcererLeft) {
            vars[MODE] = steep ? 3 : 2;
        } else {
            vars[MODE] = steep ? 5 : 6;
        }
    }

    private void leap(Alien alien, int shape, int dx, int dy) {
        alien.objOff = shape;
        alien.x += dx;
        alien.y += dy;
    }
}
