package com.artandmagic.agony.data.original;

import com.artandmagic.agony.data.Picture;

import java.util.ArrayList;
import java.util.List;

/**
 * The title screen, read from its program ({@code Agony.07} on the disks, the
 * file every level loads when the last life is gone).
 *
 * <p>There is no source for it in the release.  Reading the program shows a
 * picture, a hiscore table and a cycle of text pages, all drawn with the
 * blitter over a copy of the picture:
 *
 * <ul>
 *   <li>the picture is a 352 by 290 screen of six planes, extra half brite,
 *       held as it is in the program, {@code move.l #picture,d0} handing it to
 *       the routine that fills the bitplane pointers, {@code addi.l #stride}
 *       stepping from plane to plane; its thirty two colours are what the fade
 *       in reads with {@code lea palette,a0 / moveq #0,d0};</li>
 *   <li>the font is one plane, 32 pixels wide and 20 lines high, {@code $54}
 *       bytes a character ({@code move.l #font,d6 / mulu #$54,d3}), with a
 *       table of advances beside it;</li>
 *   <li>the text pages sit behind a table of word offsets: each line is an x
 *       and a y word followed by character codes, ended by a negative byte, a
 *       further {@code $fe} saying another line follows on an odd address and
 *       a negative x closing the page;</li>
 *   <li>the hiscores are six records of eight bytes: three character codes,
 *       two spare bytes and a six digit packed decimal score.  The program
 *       loads them from {@code Agony.00} on the second disk and saves them back
 *       there.</li>
 * </ul>
 */
public final class TitleData {

    /** Rows of the hiscore table. */
    public static final int SCORES = 6;
    /** Bytes per hiscore record. */
    public static final int RECORD = 8;

    public final Picture picture;
    /** Font glyphs, one byte per pixel, 0 or 1. */
    public final byte[][] glyphs;
    public final int glyphWidth;
    public final int glyphHeight;
    /** Advance of each character, in pixels. */
    public final int[] widths;
    /** The text pages, by number. */
    public final List<List<Line>> pages;
    /** Where the blits put the origin of a character, from the screen corner. */
    public final int originX;
    public final int originY;

    /** One line of a text page. */
    public record Line(int x, int y, byte[] text) {
    }

    private TitleData(Picture picture, byte[][] glyphs, int glyphWidth, int glyphHeight,
                      int[] widths, List<List<Line>> pages, int originX, int originY) {
        this.picture = picture;
        this.glyphs = glyphs;
        this.glyphWidth = glyphWidth;
        this.glyphHeight = glyphHeight;
        this.widths = widths;
        this.pages = pages;
        this.originX = originX;
        this.originY = originY;
    }

    /** The title program found on the disks, with the hiscore table they hold. */
    public record Found(String file, TitleData data, byte[] hiscores) {
    }

    /**
     * Scans a folder of disk images for the title program.
     *
     * <p>Its disk routine is handed file number 0 to load and save the
     * hiscores ({@code moveq #0,d1 / lea table,a0 / jsr}), which on these
     * disks is {@code Agony.00}, forty eight bytes on the second disk.
     */
    public static Found scan(java.nio.file.Path folder) {
        return scan(Disks.read(folder));
    }

    /** Finds the title program and the hiscore table among the files of the disks. */
    public static Found scan(Disks disks) {
        String file = null;
        TitleData data = null;
        byte[] scores = null;
        for (java.util.Map.Entry<String, byte[]> entry : disks.files().entrySet()) {
            byte[] program = entry.getValue();
            if (program.length == TitleData.SCORES * TitleData.RECORD) {
                // Agony.00 on the floppies, SCORES.SAV on the hard disk release.
                if (scores == null) {
                    scores = program;
                }
                continue;
            }
            // The title program carries a font and a picture, like the end of
            // the game, and draws the hiscore table besides:
            // moveq #5,d6 / move.w #16,d1 / lea table,a0.
            if (data == null && program.length > 150_000 && program.length < 320_000) {
                try {
                    TitleData t = Code.find(program, 0, 0x7c05, 0x323c, -1, 0x41f9) >= 0
                            ? decode(program) : null;
                    if (t != null) {
                        data = t;
                        file = entry.getKey();
                    }
                } catch (RuntimeException e) {
                    // Not the title.
                }
            }
        }
        return data == null ? null : new Found(file, data, scores);
    }

    /**
     * Finds the end of the game among the files of the disks: the other
     * program with a picture and text pages, the one without a hiscore table.
     *
     * @return the file name and its data, or null
     */
    public static Found scanEnding(Disks disks) {
        for (java.util.Map.Entry<String, byte[]> entry : disks.files().entrySet()) {
            byte[] program = entry.getValue();
            if (program.length < 150_000 || program.length > 380_000
                    || Code.find(program, 0, 0x7c05, 0x323c, -1, 0x41f9) >= 0) {
                continue;
            }
            try {
                TitleData t = decode(program);
                if (t != null) {
                    return new Found(entry.getKey(), t, null);
                }
            } catch (RuntimeException e) {
                // Not the ending.
            }
        }
        return null;
    }

    /** Decodes the title program, or returns null when it is not that program. */
    public static TitleData decode(byte[] p) {
        Code.Origin origin = Code.origin(p);
        if (origin == null) {
            return null;
        }
        int org = origin.org();
        // The copper list gives the shape of the screen.
        Code.Screen shape = Code.screen(p, origin.copper());
        if (shape == null || shape.planes() != 6) {
            return null;
        }

        int pictureAt = Code.operandBefore(p, 0x203c, 0x6100, org);
        int stride = Code.operand(p, 0x0680, org, false);
        int rowBytes = shape.width() / 8;
        if (pictureAt < 0 || stride != rowBytes * shape.height()) {
            return null;
        }

        // lea palette,a0 / moveq #0,d0 / bsr: the fade in.
        int fade = Code.find(p, 0, 0x41f9, -1, -1, 0x7000, 0x6100);
        // move.l #font,d6 / move.w d2,d3 / mulu #size,d3.
        int fontOp = Code.find(p, 0, 0x2c3c, -1, -1, 0x3602, 0xc6fc);
        // lea widths,a1 / lea pages,a0 / add.w d0,d0.
        int pagesOp = Code.find(p, 0, 0x43f9, -1, -1, 0x41f9, -1, -1, 0xd040);
        // addi.l #origin,d7 / addq.l #2,d7: where a character lands.
        int originOp = Code.find(p, 0, 0x0687, -1, -1, 0x5487);
        if (fade < 0 || fontOp < 0 || pagesOp < 0 || originOp < 0) {
            return null;
        }

        int paletteAt = Code.u32(p, fade + 2) - org;
        int[] palette = new int[32];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = Code.u16(p, paletteAt + i * 2) & 0x0fff;
        }
        byte[] pixels = Picture.chunky(p, pictureAt, stride, rowBytes,
                shape.width(), shape.height(), 6);
        Picture picture = new Picture(shape.width(), shape.height(), pixels, palette, true);

        int fontAt = Code.u32(p, fontOp + 2) - org;
        int glyphSize = Code.u16(p, fontOp + 10);
        int widthsAt = Code.u32(p, pagesOp + 2) - org;
        int pagesAt = Code.u32(p, pagesOp + 8) - org;

        // The blit is three words wide with the last one masked off and an A
        // modulo of -2: two words, 32 pixels, of each line are read, four bytes
        // apart.  BLTSIZE gives the height.
        int bltsize = Code.u16(p, Code.find(p, fontOp, 0x3d7c, -1, 0x0058) + 2);
        int glyphHeight = bltsize >> 6;
        int glyphWidth = 32;
        // The advances follow the glyphs, one byte per character.
        int count = (widthsAt - fontAt) / glyphSize;
        if (glyphHeight <= 0 || count <= 0 || count > 128) {
            return null;
        }
        byte[][] glyphs = new byte[count][glyphWidth * glyphHeight];
        for (int c = 0; c < count; c++) {
            int base = fontAt + c * glyphSize;
            for (int y = 0; y < glyphHeight; y++) {
                for (int x = 0; x < glyphWidth; x++) {
                    int b = p[base + y * 4 + (x >> 3)];
                    glyphs[c][y * glyphWidth + x] = (byte) ((b >> (7 - (x & 7))) & 1);
                }
            }
        }
        int[] widths = new int[pagesAt - widthsAt];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = p[widthsAt + i] & 0xff;
        }

        List<List<Line>> pages = new ArrayList<>();
        int first = Code.u16(p, pagesAt);
        for (int n = 0; n * 2 < first; n++) {
            int off = (short) Code.u16(p, pagesAt + n * 2);
            if (off <= 0) {
                break;
            }
            pages.add(page(p, pagesAt + off));
        }

        int textOrigin = Code.u32(p, originOp + 2) + 2;
        int originY = textOrigin / rowBytes;
        int originX = (textOrigin % rowBytes) * 8;
        return new TitleData(picture, glyphs, glyphWidth, glyphHeight, widths, pages,
                originX, originY);
    }

    private static List<Line> page(byte[] p, int at) {
        List<Line> lines = new ArrayList<>();
        while (at + 4 <= p.length && lines.size() < 32) {
            int x = (short) Code.u16(p, at);
            if (x < 0) {
                break;
            }
            int y = (short) Code.u16(p, at + 2);
            at += 4;
            int start = at;
            while (at < p.length && p[at] >= 0) {
                at++;
            }
            byte[] text = new byte[at - start];
            System.arraycopy(p, start, text, 0, text.length);
            at++;
            if (at < p.length && (p[at] & 0xff) == 0xfe) {
                at++;
            }
            lines.add(new Line(x, y, text));
        }
        return lines;
    }
}
