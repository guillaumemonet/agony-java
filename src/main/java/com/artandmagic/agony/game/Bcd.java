package com.artandmagic.agony.game;

/**
 * The binary coded decimal arithmetic the original gets for free from the
 * 68000 {@code abcd} and {@code sbcd} instructions, used for the score, the
 * bonus counter and the spell timer.
 */
public final class Bcd {

    private Bcd() {
    }

    /** Adds two packed decimal values. */
    public static int add(int a, int b) {
        int result = 0;
        int carry = 0;
        for (int shift = 0; shift < 32; shift += 4) {
            int digit = ((a >>> shift) & 0xf) + ((b >>> shift) & 0xf) + carry;
            if (digit >= 10) {
                digit -= 10;
                carry = 1;
            } else {
                carry = 0;
            }
            result |= digit << shift;
        }
        return result;
    }

    /** Subtracts one packed decimal value from another, clamped at zero. */
    public static int sub(int a, int b) {
        int result = 0;
        int borrow = 0;
        for (int shift = 0; shift < 32; shift += 4) {
            int digit = ((a >>> shift) & 0xf) - ((b >>> shift) & 0xf) - borrow;
            if (digit < 0) {
                digit += 10;
                borrow = 1;
            } else {
                borrow = 0;
            }
            result |= digit << shift;
        }
        return borrow != 0 ? 0 : result;
    }
}
