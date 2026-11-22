package com.artandmagic.agony.game;

import com.artandmagic.agony.core.Bitmap;
import com.artandmagic.agony.core.Hw;
import com.artandmagic.agony.core.Sprite;
import com.artandmagic.agony.data.GameAssets;
import com.artandmagic.agony.data.LevelData;
import com.artandmagic.agony.data.ObjectDef;

/**
 * Draws the aliens into the front playfield, {@code Ag_Object_.s}.
 *
 * <p>Every alien is a small list of blitter objects, each with its own offset
 * and sprite number; a dying alien has all of them replaced by the current
 * frame of the explosion.  The original then picks one of twelve hand written
 * blit routines depending on the size of the sprite and on which screen edge it
 * straddles, work that {@link Bitmap#blit} does in one place here.
 */
public final class ObjectRenderer {

    /**
     * Width of the box {@code Ag_Object_.s} clips against, the 40 bytes of the
     * build screen an object may reach.  The four bytes past it on the 44 byte
     * rows are the room the barrel shift of the widest blit needs.
     */
    private static final int CLIP_W = 320;

    private final LevelData level;
    private final GameState state;
    private final TrackManager tracks;

    public ObjectRenderer(LevelData level, GameState state, TrackManager tracks) {
        this.level = level;
        this.state = state;
        this.tracks = tracks;
    }

    /**
     * One 25 Hz frame.
     *
     * @param target       the build screen the aliens are drawn into
     * @param bufferOrigin where that build screen starts in the ring
     */
    public void render(Bitmap target, int bufferOrigin) {
        state.flashPhase = !state.flashPhase;
        state.frontShiftPhase = state.frontShift;

        for (AlienBank bank : tracks.banks) {
            if (bank.isFree() || bank.count <= 0) {
                continue;
            }
            for (int i = 0; i < bank.count; i++) {
                draw(target, bufferOrigin, bank.aliens[(bank.first + i) % AlienBank.SIZE]);
            }
        }
    }

    private void draw(Bitmap target, int bufferOrigin, Alien alien) {
        // The white flash played when the alien is hit: the original counts the
        // high nibble up and clears it at $f0, but leaves the blink itself
        // commented out, so the counter has no visual effect.
        int flash = alien.status & 0xf0;
        if (flash != 0) {
            alien.status = flash == 0xf0 ? (alien.status & 0x0f) : (alien.status + 0x10);
        }

        int explo = alien.status & 0xf;
        if (explo == 0xf) {
            return;
        }

        // The position the original works from is a place in the build screen,
        // not on the screen: the term in front of the alien X grows by two every
        // game frame, which is exactly what the screen scrolls in that time, so
        // an alien that keeps its X keeps its place.  That is why the object is
        // laid down against the start of the ring and not against the window,
        // which already carries the same fine scroll.  The bonus inherits this
        // position as it stands, the way {@code Ag_Object_.s} hands it over.
        int shifted = 31 - ((state.frontShiftPhase + 1) & 31) + alien.x;
        int x = shifted - Hw.BIAS;
        int y = alien.y - Hw.BIAS;
        int exploSprite = 0;

        if (explo != 0) {
            // The bonus is released at the position of the first alien that blows up.
            if (state.bonusMode == 1) {
                state.bonusX = shifted;
                state.bonusY = alien.y;
                state.bonusMode = 2;
            }
            alien.status++;
            int frame = GameAssets.EXPLO_SPR_LIST[Math.min(explo, GameAssets.EXPLO_SPR_LIST.length - 1)];
            exploSprite = level.firstExploSprite + frame;
        }

        ObjectDef def = object(alien.objOff);
        if (def == null) {
            return;
        }

        int ringX = x + bufferOrigin;
        for (ObjectDef.Part part : def.parts) {
            int index = exploSprite != 0 ? exploSprite : part.sprite();
            if (index < 0 || index >= level.objectSprites.length) {
                continue;
            }
            Sprite sprite = level.objectSprites[index];
            if (sprite == null) {
                continue;
            }
            int ox = x + part.dx();
            int py = y + part.dy();

            // Ag_Object_.s sizes every part from Sprites_Struct and sends it to
            // a clipping routine the moment it leaves the 320 by 192 box of the
            // build screen; a part wholly outside is dropped there and then.
            // The ring has to be told, or a part that is merely waiting off the
            // right of the screen comes round the seam and shows on the left.
            if (ox >= CLIP_W || ox + sprite.width <= 0
                    || py >= Hw.SCREEN_H || py + sprite.height <= 0) {
                continue;
            }

            int px = Math.floorMod(ringX + part.dx(), target.width);
            // The ring wraps, so a part crossing the seam is drawn twice.
            target.blit(sprite, px, py);
            if (px + sprite.width > target.width) {
                target.blit(sprite, px - target.width, py);
            }
        }
    }

    ObjectDef object(int objOff) {
        ObjectDef def = level.objects.get(objOff);
        if (def == null && objOff >= 0 && level.objectDecoder != null) {
            def = level.objectDecoder.apply(objOff);
            level.objects.put(objOff, def);
        }
        return def;
    }
}
