package com.artandmagic.agony.screens;

import com.artandmagic.agony.data.original.TitleData;

import java.util.List;

/**
 * Draws the text pages of the title and of the ending over their picture.
 *
 * <p>Both programs draw text the same way, with two blits per character: the
 * character is ORed into all six planes at its place and one pixel up, down,
 * left and right, which leaves colour 63 (the half brite copy of 31) around it,
 * and then planes 1 to 5 are cleared under the character itself, which leaves
 * colour 1 inside.  A character advances by its width plus one; code 41 is the
 * space and is not drawn.
 */
final class PagePainter {

    static final int SPACE = 41;

    private final TitleData data;
    /** The picture with the text on it, one colour index per pixel. */
    final byte[] work;

    PagePainter(TitleData data) {
        this.data = data;
        this.work = data.picture.pixels.clone();
    }

    /** Puts the bare picture back. */
    void clear() {
        System.arraycopy(data.picture.pixels, 0, work, 0, work.length);
    }

    /** The picture with one page of text on it. */
    void page(int number) {
        clear();
        if (number < 0 || number >= data.pages.size()) {
            return;
        }
        List<TitleData.Line> lines = data.pages.get(number);
        for (TitleData.Line line : lines) {
            text(line.x(), line.y(), line.text());
        }
    }

    int width(byte[] text) {
        int w = 0;
        for (byte c : text) {
            w += advance(c & 0xff);
        }
        return w;
    }

    void text(int x, int y, byte[] text) {
        for (byte b : text) {
            int c = b & 0xff;
            if (c != SPACE && c < data.glyphs.length) {
                character(x, y, c);
            }
            x += advance(c);
        }
    }

    private int advance(int c) {
        return 1 + (c < data.widths.length ? data.widths[c] : 0);
    }

    private void character(int x, int y, int c) {
        int[][] around = {{0, 0}, {0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] d : around) {
            stamp(x + d[0], y + d[1], c, (byte) 63);
        }
        stamp(x, y, c, (byte) 1);
    }

    private void stamp(int x, int y, int c, byte colour) {
        byte[] glyph = data.glyphs[c];
        int w = data.picture.width;
        int h = data.picture.height;
        int ox = data.originX + x;
        int oy = data.originY + y;
        for (int gy = 0; gy < data.glyphHeight; gy++) {
            int py = oy + gy;
            if (py < 0 || py >= h) {
                continue;
            }
            for (int gx = 0; gx < data.glyphWidth; gx++) {
                int px = ox + gx;
                if (px < 0 || px >= w || glyph[gy * data.glyphWidth + gx] == 0) {
                    continue;
                }
                work[py * w + px] = colour;
            }
        }
    }
}
