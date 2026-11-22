package com.artandmagic.agony.data;

/**
 * One entry of {@code Objects.obj}, the table that describes an alien.
 *
 * <p>Original layout, relative to the object offset stored in
 * {@code AWO_Alien_Obj_Off}:
 * <pre>
 *   +0  word  offset of the collision rectangle list
 *   +2  byte  x of the muzzle the alien fires from
 *   +3  byte  y of the muzzle
 *   +4  ...   triplets (dx, dy, sprite number), ended by dy = $80
 * </pre>
 * The rectangle list holds (x1, y1, x2, y2) byte quadruplets relative to the
 * alien position and ends with x1 = $80.
 */
public final class ObjectDef {

    /** A collision rectangle, relative to the alien position. */
    public record Rect(int x1, int y1, int x2, int y2) {
    }

    /** One blitter object of the alien: an offset and a sprite number. */
    public record Part(int dx, int dy, int sprite) {
    }

    public final int fireX;
    public final int fireY;
    public final Rect[] rects;
    public final Part[] parts;

    public ObjectDef(int fireX, int fireY, Rect[] rects, Part[] parts) {
        this.fireX = fireX;
        this.fireY = fireY;
        this.rects = rects;
        this.parts = parts;
    }
}
