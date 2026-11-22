package com.artandmagic.agony.input;

/**
 * Port 1 joystick plus the keyboard functions read by {@code Keyboard_Int} in
 * {@code Agony_Parent_.s}.
 *
 * <p>The original decodes {@code Joy1dat} into four direction bits and reads the
 * fire button from bit 7 of {@code $bfe001}; the keyboard interrupt handles
 * pause (key $19, the P key), the spell menu (key $40, space), abort (key $45,
 * escape), the menu mode toggle (key $37) and the four cheat keys F1..F4, plus F5 for the
 * invincibility of the port.
 */
public final class Joystick {

    private volatile boolean up;
    private volatile boolean down;
    private volatile boolean left;
    private volatile boolean right;
    private volatile boolean fire;

    /** Edge triggered key events consumed once by the game loop. */
    private volatile boolean pausePressed;
    private volatile boolean menuPressed;
    private volatile boolean abortPressed;
    private volatile boolean modePressed;
    private final boolean[] cheat = new boolean[5];

    public boolean up() {
        return up;
    }

    public boolean down() {
        return down;
    }

    public boolean left() {
        return left;
    }

    public boolean right() {
        return right;
    }

    /** True while the fire button is held. */
    public boolean fire() {
        return fire;
    }

    public void setUp(boolean v) {
        up = v;
    }

    public void setDown(boolean v) {
        down = v;
    }

    public void setLeft(boolean v) {
        left = v;
    }

    public void setRight(boolean v) {
        right = v;
    }

    public void setFire(boolean v) {
        fire = v;
    }

    public void pressPause() {
        pausePressed = true;
    }

    public void pressMenu() {
        menuPressed = true;
    }

    public void pressAbort() {
        abortPressed = true;
    }

    public void pressMode() {
        modePressed = true;
    }

    /** Keys typed, as {@link java.awt.event.KeyEvent} codes, for the name entry. */
    private final java.util.concurrent.ConcurrentLinkedQueue<Integer> typed =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    public void pressKey(int keyCode) {
        if (typed.size() < 16) {
            typed.add(keyCode);
        }
    }

    /** The next key typed, or -1 when there is none. */
    public int takeKey() {
        Integer k = typed.poll();
        return k == null ? -1 : k;
    }

    /** Forgets the keys typed so far. */
    public void clearKeys() {
        typed.clear();
    }

    public void pressCheat(int n) {
        if (n >= 0 && n < cheat.length) {
            cheat[n] = true;
        }
    }

    public boolean takePause() {
        boolean v = pausePressed;
        pausePressed = false;
        return v;
    }

    public boolean takeMenu() {
        boolean v = menuPressed;
        menuPressed = false;
        return v;
    }

    public boolean takeAbort() {
        boolean v = abortPressed;
        abortPressed = false;
        return v;
    }

    public boolean takeMode() {
        boolean v = modePressed;
        modePressed = false;
        return v;
    }

    public boolean takeCheat(int n) {
        if (n < 0 || n >= cheat.length) {
            return false;
        }
        boolean v = cheat[n];
        cheat[n] = false;
        return v;
    }
}
