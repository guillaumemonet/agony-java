package com.artandmagic.agony.data.original;

import java.util.HashMap;
import java.util.Map;

/**
 * Locates every data block inside a decrunched Agony level overlay.
 *
 * <p>An overlay is {@code Agony_Parent_.s} assembled at {@code ORG $600} with
 * all its {@code INCBIN}s in place, so each block sits at a fixed address.  Two
 * things give those addresses away:
 *
 * <ul>
 *   <li>the absolute blocks are named by a 32 bit operand, for instance
 *       {@code lea Sorcerer_Dat,a0} or {@code add.l #Front_Charset,d1};</li>
 *   <li>the relative blocks are reached as {@code d(a5)}, because the {@code _}
 *       macro defines every one of them as {@code *-Rel_Start-32768}.</li>
 * </ul>
 *
 * Matching whole instruction sequences from the sources, rather than a lone
 * opcode, keeps false positives out.  Several generated tables ({@code X60},
 * {@code X384}, {@code Fwm_Conv}...) have unmistakable contents and pin the
 * layout down further; the blocks in between follow by arithmetic, and the
 * result is cross checked (for instance {@code X60 + 256} must land exactly on
 * {@code Write_Table}).
 */
public final class AgonyBinary {

    /** The address the overlays are assembled and loaded at. */
    public static final int BASE = 0x600;

    private final byte[] data;
    private final Map<String, Integer> addr = new HashMap<>();

    public final int relStart;
    /** Value held in a5 while the game runs: {@code Rel_Start + 32768}. */
    public final int a5;
    /** Level number, 1 to 6, worked out from the object bank reservation. */
    public final int level;

    public AgonyBinary(byte[] data) {
        this.data = data;

        int anchor = find(bytes(0x4d, 0xf9, 0x00, 0xdf, 0xf0, 0x00, 0x4b, 0xf9), 0);
        if (anchor < 0) {
            throw new IllegalArgumentException("not an Agony level overlay");
        }
        relStart = int32(anchor + 8) - 0x8000;
        a5 = relStart + 0x8000;
        put("Rel_Start", relStart);

        absolutes();
        copperList();
        signatures();
        relatives();
        palettes();
        derive();
        level = levelFromReservation();
    }

    // ------------------------------------------------------------- accessors

    /** The whole overlay, as loaded at {@link #BASE}. */
    public byte[] data() {
        return data;
    }

    public boolean has(String label) {
        return addr.containsKey(label);
    }

    public int address(String label) {
        Integer v = addr.get(label);
        if (v == null) {
            throw new IllegalStateException("block not located: " + label);
        }
        return v;
    }

    public int offset(String label) {
        return address(label) - BASE;
    }

    /** Raw bytes of a block. */
    public byte[] slice(String label, int length) {
        return slice(address(label), length);
    }

    public byte[] slice(int address, int length) {
        int from = address - BASE;
        byte[] out = new byte[length];
        int n = Math.max(0, Math.min(length, data.length - from));
        if (from >= 0 && n > 0) {
            System.arraycopy(data, from, out, 0, n);
        }
        return out;
    }

    public int u8(int address) {
        int o = address - BASE;
        return o >= 0 && o < data.length ? data[o] & 0xff : 0;
    }

    public int s8(int address) {
        return (byte) u8(address);
    }

    public int u16(int address) {
        return (u8(address) << 8) | u8(address + 1);
    }

    public int s16(int address) {
        return (short) u16(address);
    }

    public int u32(int address) {
        return (u16(address) << 16) | u16(address + 2);
    }

    public Map<String, Integer> blocks() {
        return addr;
    }

    // ---------------------------------------------------------------- search

    private void put(String label, int address) {
        if (address >= BASE && address < BASE + data.length) {
            addr.put(label, address);
        }
    }

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = (byte) values[i];
        }
        return out;
    }

    /** Finds a byte sequence, with -1 meaning "any byte". */
    private int find(byte[] pattern, int from) {
        outer:
        for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (pattern[j] != -1 && data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private int int32(int offset) {
        return ((data[offset] & 0xff) << 24) | ((data[offset + 1] & 0xff) << 16)
                | ((data[offset + 2] & 0xff) << 8) | (data[offset + 3] & 0xff);
    }

    private int int16(int offset) {
        return (short) (((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff));
    }

    /** Reads a 32 bit operand that follows an instruction pattern. */
    private void absolute(String label, int operandAt, int addend, int... pattern) {
        int i = find(bytes(pattern), 0);
        while (i >= 0) {
            int v = int32(i + operandAt) + addend;
            if (v >= BASE && v < BASE + data.length) {
                put(label, v);
                return;
            }
            i = find(bytes(pattern), i + 1);
        }
    }

    /** Reads a 16 bit a5 displacement that follows an instruction pattern. */
    private void relative(String label, int operandAt, int occurrence, int... pattern) {
        int i = find(bytes(pattern), 0);
        int seen = 0;
        while (i >= 0) {
            int v = a5 + int16(i + operandAt);
            if (v >= BASE && v < BASE + data.length) {
                if (seen == occurrence) {
                    put(label, v);
                    return;
                }
                seen++;
            }
            i = find(bytes(pattern), i + 1);
        }
    }

    /**
     * The copper list the game runs.  The init writes {@code _Dummy_Cl} to
     * COP1LC first and {@code _Main_Cl} later, so the second of the two is the
     * one that matters.
     */
    private void copperList() {
        int[] pattern = {0x2d, 0x7c, -1, -1, -1, -1, 0x00, 0x80};
        int i = find(bytes(pattern), 0);
        int last = -1;
        while (i >= 0) {
            int v = int32(i + 2);
            if (v >= BASE && v < BASE + data.length) {
                last = v;
            }
            i = find(bytes(pattern), i + 1);
        }
        if (last >= 0) {
            put("Main_Cl", last);
        }
    }

    private void absolutes() {
        // lea Sound_Dat,a0 / move.l a0,d1 / lsl #3,d0
        absolute("Sound_Dat", 2, 0, 0x41, 0xf9, -1, -1, -1, -1, 0x22, 0x08, 0xe7, 0x48);
        // lea Sorcerer_Dat,a0 / move.l a0,a1 / a2 / a3 / a4
        absolute("Sorcerer_Dat", 2, 0, 0x41, 0xf9, -1, -1, -1, -1,
                0x22, 0x48, 0x24, 0x48, 0x26, 0x48, 0x28, 0x48);
        // lea Sorcerer2_Dat,a0 / move.l a0,a1 / move.l a0,a2 / add Sorcerer2_Shape(a5),a0
        absolute("Sorcerer2_Dat", 2, 0, 0x41, 0xf9, -1, -1, -1, -1,
                0x22, 0x48, 0x24, 0x48, 0xd0, 0xed);
        // lea Fw_Fire_Spr,a0 / clr.l 25*4(a0)
        absolute("Fw_Fire_Spr", 2, 0, 0x41, 0xf9, -1, -1, -1, -1, 0x42, 0xa8, 0x00, 0x64);
        // lea Bonus_Spr,a0 / lea Bonus_Shape(a5),a1 / add d2,d2
        absolute("Bonus_Spr", 2, 0, 0x41, 0xf9, -1, -1, -1, -1,
                0x43, 0xed, -1, -1, 0xd4, 0x42);
        // lea Alien_Fire_Spr,a0 / move.l a0,Spr5ptB(a5) / moveq #11,d0
        absolute("Alien_Fire_Spr", 2, 0, 0x41, 0xf9, -1, -1, -1, -1,
                0x2b, 0x48, -1, -1, 0x70, 0x0b);
        // lea Die_Spr,a0 / lea Die_Dyn_Ptr(a5),a1 / lea Spr0ptB(a5),a3
        absolute("Die_Spr", 2, 0, 0x41, 0xf9, -1, -1, -1, -1,
                0x43, 0xed, -1, -1, 0x47, 0xed);
        // lea Sky_Dat,a0 / add d1,a0
        absolute("Sky_Dat", 2, 0, 0x41, 0xf9, -1, -1, -1, -1, 0xd0, 0xc1);
        // and.l #$ffff,d4 / add.l #Back_Charset,d4
        absolute("Back_Charset", 8, 0, 0x02, 0x84, 0x00, 0x00, 0xff, 0xff, 0x06, 0x84);
        // add.l #Front_Charset,d1 / move.l d1,Bltapt(a6)
        absolute("Front_Charset", 2, 0, 0x06, 0x81, -1, -1, -1, -1, 0x2d, 0x41, 0x00, 0x50);
        // move.l (a0)+,a1 / add.l #Sprites_Bitmap,a1 / move.l a1,Bltapt(a6)
        absolute("Sprites_Bitmap", 4, 0, 0x22, 0x58, 0xd3, 0xfc, -1, -1, -1, -1,
                0x2d, 0x49, 0x00, 0x50);
        // move AWO_Alien_Obj_Off(a0),a1 / add.l #Objects_Struct,a1 / add (a1),a1
        absolute("Objects_Struct", 6, 0, 0x32, 0x68, 0x00, 0x04, 0xd3, 0xfc, -1, -1, -1, -1,
                0xd2, 0xd1);
        // lea Start_list,a0 / move (a0)+,Sl_Waiting(a5) / move.l a0,Start_List_Ptr(a5)
        absolute("Start_list", 2, 0, 0x41, 0xf9, -1, -1, -1, -1,
                0x3b, 0x58, -1, -1, 0x2b, 0x48);
        // lea Anim_Base,a5 / add d5,a5
        absolute("Anim_Base", 2, 0, 0x4b, 0xf9, -1, -1, -1, -1, 0xda, 0xc5);
        // lea Relative_Tracks,a0 / move.l a0,a6 / add AWS_Table_X_Off(a3),a0.
        // The absolute branch of the follower repeats the sequence, so the two
        // tables come out in the order the code names them.
        trackTables();
    }

    /** The two movement tables, relative first as the follower reads them. */
    private void trackTables() {
        int[] pattern = {0x41, 0xf9, -1, -1, -1, -1, 0x2c, 0x48, 0xd0, 0xeb, 0x00, 0x00};
        int i = find(bytes(pattern), 0);
        int found = 0;
        while (i >= 0 && found < 2) {
            int v = int32(i + 2);
            if (v >= BASE && v < BASE + data.length) {
                put(found == 0 ? "Relative_Tracks" : "Absolute_Tracks", v);
                found++;
            }
            i = find(bytes(pattern), i + 1);
        }
    }

    private void relatives() {
        // lea Back_Pattern(a5),a0 / move.l a0,a2
        relative("Back_Pattern", 2, 0, 0x41, 0xed, -1, -1, 0x24, 0x48);
        // lea Back_Char_Info(a5),a1 / move.l a1,a2 / move (a1)+,d0
        relative("Back_Char_Info", 2, 0, 0x43, 0xed, -1, -1, 0x24, 0x49, 0x30, 0x19);
        // lea Read_Table(a5),a3 / add Back_Config(a5),a3
        relative("Read_Table", 2, 0, 0x47, 0xed, -1, -1, 0xd6, 0xed);
        // lea Status_Digit(a5),a1 / lea (a1,d5.w),a2
        relative("Status_Digit", 2, 0, 0x43, 0xed, -1, -1, 0x45, 0xf1, 0x50, 0x00);
        // lea Text_Dat(a5),a0 / add (a0,d0.w),a0
        relative("Text_Dat", 2, 0, 0x41, 0xed, -1, -1, 0xd0, 0xf0, 0x00, 0x00);
        // lea Sprites_Struct(a5),a1 / add d2,d2 / add d2,a1
        relative("Sprites_Struct", 2, 0, 0x43, 0xed, -1, -1, 0xd4, 0x42, 0xd2, 0xc2);
        // lea Front_Table(a5),a1 / add d0,a1 / moveq #0,d0
        relative("Front_Table", 2, 0, 0x43, 0xed, -1, -1, 0xd2, 0xc0, 0x70, 0x00);
        // lea Icones_Spr(a5),a0 / move.l a0,Spr4ptB(a5)
        relative("Icones_Spr", 2, 0, 0x41, 0xed, -1, -1, 0x2b, 0x48, -1, -1, 0x43, 0xe8);
        // lea Sorcerer_Pal(a5),a0 / movem.l (a0),d0-d7 loads one of the two
        // sixteen colour sprite palettes; X224 pins the sorcerer one down, so
        // whichever other address the pattern yields is the icon palette.
        palettes();
        // lea Axe_Traj(a5),a0 / moveq #0,d1 / move.b (a0,d0.w),d1  (Time_Y_Table repeats it)
        relative("Axe_Traj", 2, 0, 0x41, 0xed, -1, -1, 0x72, 0x00, 0x12, 0x30);
        relative("Time_Y_Table", 2, 1, 0x41, 0xed, -1, -1, 0x72, 0x00, 0x12, 0x30);
        // lea Rot_Table(a5),a2 / lsl #4,d0
        relative("Rot_Table", 2, 0, 0x45, 0xed, -1, -1, 0xe9, 0x88);
        // lea Die_Table(a5),a0 / lea Die_Dyn_Ptr(a5),a1 / move.l a0,d1
        relative("Die_Table", 2, 0, 0x41, 0xed, -1, -1, 0x43, 0xed, -1, -1, 0x22, 0x08);
        // lea Icones_Spr(a5),a0 / move.l a0,Spr4ptB(a5) / lea 768(a0),a0
        relative("Icones_Spr", 2, 0, 0x41, 0xed, -1, -1, 0x2b, 0x48, -1, -1,
                0x41, 0xe8, 0x03, 0x00);
        // lea Arow_Spr(a5),a0 / move.l d0,(a0) / move.l a0,Spr0ptB(a5) / lea 68(a0),a0
        relative("Arow_Spr", 2, 0, 0x41, 0xed, -1, -1, 0x20, 0x80, 0x2b, 0x48, -1, -1,
                0x41, 0xe8, 0x00, 0x44);
        // lea Sin_Table(a5),a1 / move.l a1,a2
        relative("Sin_Table", 2, 0, 0x43, 0xed, -1, -1, 0x24, 0x49, 0x36, 0x00);
        // lea FBall_3spr0(a5),a0 / lea FBall_3spr1(a5),a1 / move.l a0,Spr6ptB(a5)
        relative("FBall_3spr0", 2, 0, 0x41, 0xed, -1, -1, 0x43, 0xed, -1, -1, 0x2b, 0x48);
        // lea Back_FB_Spr0(a5),a0 / move.l a0,Spr6ptB(a5) / move d4,d7
        relative("Back_FB_Spr0", 2, 0, 0x41, 0xed, -1, -1, 0x2b, 0x48, -1, -1, 0x3e, 0x04);
        // lea Time_Spr(a5),a0 / add d0,a0 / lea 128(a0),a1
        relative("Time_Spr", 2, 0, 0x41, 0xed, -1, -1, 0xd0, 0xc0, 0x43, 0xe8, 0x00, 0x80);
        // lea Smart_B_Spr(a5),a0 / lea 157*4*6(a0),a1
        relative("Smart_B_Spr", 2, 0, 0x41, 0xed, -1, -1, 0x43, 0xe8, 0x0e, 0xb8);
        // lea Mega_B_Spr(a5),a0 / lea 151*4(a0),a1 / lea 151*4(a1),a2
        relative("Mega_B_Spr", 2, 0, 0x41, 0xed, -1, -1, 0x43, 0xe8, 0x02, 0x5c,
                0x45, 0xe9, 0x02, 0x5c);
        // lea Rain_X_Table(a5),a0 / add Rain_X_Off0(a5),a0 / lea Rain_Spr0(a5),a1
        relative("Rain_X_Table", 2, 0, 0x41, 0xed, -1, -1, 0xd0, 0xed, -1, -1, 0x43, 0xed);
        // lea Rain_Spr0(a5),a1 comes straight after it
        relative("Rain_Spr0", 10, 0, 0x41, 0xed, -1, -1, 0xd0, 0xed, -1, -1, 0x43, 0xed);
        // lea Bonus_Image(a5),a0 / move Bonus_Num(a5),d2
        relative("Bonus_Image", 2, 0, 0x41, 0xed, -1, -1, 0x34, 0x2d);
    }

    /**
     * The two sprite palettes are loaded by the same instruction pair.  The
     * sorcerer one sits just in front of {@code X224}; the other is the menu.
     */
    private void palettes() {
        int[] pattern = {0x41, 0xed, -1, -1, 0x4c, 0xd0, 0x00, 0xff};
        java.util.TreeSet<Integer> seen = new java.util.TreeSet<>();
        int i = find(bytes(pattern), 0);
        while (i >= 0) {
            int v = a5 + int16(i + 2);
            if (v >= BASE && v < BASE + data.length) {
                seen.add(v);
            }
            i = find(bytes(pattern), i + 1);
        }
        Integer sorcerer = addr.get("X224") != null ? addr.get("X224") - 32 : null;
        for (int v : seen) {
            if (sorcerer != null && v == sorcerer) {
                put("Sorcerer_Pal", v);
            } else {
                put("Icones_Pal", v);
            }
        }
        if (sorcerer != null) {
            put("Sorcerer_Pal", sorcerer);
        }
    }

    /** Generated tables whose contents cannot be mistaken for anything else. */
    private void signatures() {
        signature("X60", "0000003c007800b400f0");
        signature("X384", "00000000000001800000030000000480");
        signature("X224", "000000e001c002a0");
        signature("Time_Table", "00150006000500100008000600120008");
        signature("Bonus_Shape", "0000032006400960");
        signature("X44", "0000002c0058008400b0");
        signature("Fwm_Conv", "0000000100030007000f001f003f007f00ff");
    }

    private void signature(String label, String hex) {
        byte[] pattern = new byte[hex.length() / 2];
        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        int i = find(pattern, 0);
        if (i >= 0) {
            put(label, i + BASE);
        }
    }

    /**
     * Fills in the blocks that have no reference of their own, using the fixed
     * sizes the sources give and the anchors already found.
     */
    private void derive() {
        addr.putIfAbsent("Back_Frame", relStart);

        if (has("X60")) {
            int x60 = address("X60");
            put("Back_Old_Char", x60 - 480);
            put("Write_Table", x60 + 256);
            put("Sky_Anim_Table", x60 + 256 + 120);
            put("Front_Map", x60 + 256 + 120 + 8);
        }
        if (has("X384")) {
            int x384 = address("X384");
            addr.putIfAbsent("Front_Table", x384 - 12);
            put("Front_Mask", x384 + 1024);
            addr.putIfAbsent("Rain_X_Table", x384 + 1024 + 128);
        }
        if (has("Front_Table")) {
            put("Front_Pal_Buffer", address("Front_Table") - 84);
        }
        if (has("X224")) {
            addr.putIfAbsent("Sorcerer_Pal", address("X224") - 32);
            put("Empty_Spr", address("X224") + 8);
        }
        if (has("Time_Table")) {
            addr.putIfAbsent("Time_Y_Table", address("Time_Table") + 16);
        }
        if (has("Bonus_Shape")) {
            addr.putIfAbsent("Bonus_Image", address("Bonus_Shape") + 8);
            addr.putIfAbsent("Rot_Table", address("Bonus_Shape") + 8 + 24);
        }
        // Status_Screen_Disp is 76*18 bytes of $ff followed by one blank line,
        // which closes the chain that starts at Rot_Table.
        int disp = findRun((byte) 0xff, 76 * 18, 0);
        if (disp >= 0) {
            put("Status_Screen_Disp", disp + BASE);
            put("Status_Screen", disp + BASE + 76 * 19);
            put("Die_Dyn_Ptr", disp + BASE - 32);
        }
        if (has("X44")) {
            put("Fwm_Conv", address("X44") + 386);
            put("Lwm_Conv", address("X44") + 386 + 34);
            put("Track_Table", address("X44") + 386 + 68);
        }

        // The front map and the front palettes share one run of bytes: the map
        // is twelve bytes per column and the palettes 84 bytes per step, with
        // one step for every ten columns.
        if (has("Front_Map") && has("Front_Pal_Buffer")) {
            int total = address("Front_Pal_Buffer") - address("Front_Map");
            int columns = total * 5 / 102;
            columns -= columns % 10;
            while (columns > 0 && columns * 12 + (columns / 10) * 84 > total) {
                columns -= 10;
            }
            if (columns > 0) {
                addr.put("Front_Map_Columns", columns);
                put("Front_Pal", address("Front_Map") + columns * 12);
                addr.put("Front_Pal_Steps", columns / 10);
            }
        }

        // AF_Struct is 256 bytes of $ff, and Track_Struct is the 16 track slots
        // that sit just in front of it.
        if (has("Objects_Struct")) {
            int af = findRun((byte) 0xff, 256, offset("Objects_Struct"));
            if (af >= 0) {
                put("AF_Struct", af + BASE);
                put("Track_Struct", af + BASE - 16 * 144);
                addr.put("Objects_Struct_Size", af + BASE - 16 * 144 - address("Objects_Struct"));
            }
        }
    }

    private int findRun(byte value, int length, int from) {
        int run = 0;
        for (int i = Math.max(0, from); i < data.length; i++) {
            if (data[i] == value) {
                run++;
                if (run == length
                        && (i + 1 >= data.length || data[i + 1] != value)) {
                    return i - length + 1;
                }
            } else {
                run = 0;
            }
        }
        return -1;
    }

    /**
     * {@code Sprites_Mask} reserves a fixed amount per level, so the gap between
     * the object bitmaps and the object table names the level.
     */
    private int levelFromReservation() {
        if (!has("Sprites_Bitmap") || !has("Objects_Struct")) {
            return 0;
        }
        int gap = address("Objects_Struct") - address("Sprites_Bitmap");
        return switch (gap) {
            case 52000 -> 1;
            case 55000 -> 2;
            case 53000 -> 3;
            case 56000 -> 4;
            case 57000 -> 5;
            case 63500 -> 6;
            default -> 0;
        };
    }
}
