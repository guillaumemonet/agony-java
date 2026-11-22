package com.artandmagic.agony.core;

import com.artandmagic.agony.input.Joystick;

import javax.swing.JFrame;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * The window: shows the composed frame and feeds the joystick.
 *
 * <p>The frame is drawn from the game loop itself through a {@link
 * BufferStrategy}, not from a repaint the toolkit is free to coalesce: the
 * original puts one frame on the screen per vertical blank, and a dropped or
 * doubled one shows up at once in the scrolling.
 *
 * <p>Controls follow the original: the arrow keys are the stick, the right
 * control key or the left alt key is the fire button, P pauses, the space bar
 * opens the spell menu, escape aborts the level and F1..F4 are the debug keys
 * of {@code Keyboard_Int}.
 */
public final class Display {

    /**
     * The area the window is sized for: a PAL lores overscan screen, 352 by
     * 290, doubled.  The loading screens fill it; the game, 304 by 211, sits
     * in the middle of it, which is where the Amiga puts it on a monitor.
     */
    public static final int MONITOR_W = 352 * 2;
    public static final int MONITOR_H = 290 * 2;

    private BufferedImage image;
    private final Canvas canvas;
    private final JFrame frame;
    private final Joystick joystick;
    private final int[] backing;

    public Display(int[] pixels, Joystick joystick, String title) {
        this.joystick = joystick;
        if (pixels.length != Hw.OUT_W * Hw.OUT_H) {
            throw new IllegalArgumentException("frame buffer size mismatch");
        }
        this.backing = pixels;

        canvas = new Canvas();
        canvas.setPreferredSize(new Dimension(MONITOR_W, MONITOR_H));
        canvas.setBackground(Color.BLACK);
        canvas.setFocusable(true);
        canvas.setIgnoreRepaint(true);
        canvas.addKeyListener(new Keys());

        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIgnoreRepaint(true);
        frame.add(canvas);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        canvas.requestFocusInWindow();
        canvas.createBufferStrategy(2);
    }

    /** Puts the game frame on the screen. */
    public void present() {
        present(backing, Hw.OUT_W, Hw.OUT_H);
    }

    /**
     * Puts any frame on the screen, centred in the monitor area and scaled with
     * it, so a full screen picture and the game keep the same pixel size.
     */
    public void present(int[] pixels, int width, int height) {
        if (image == null || image.getWidth() != width || image.getHeight() != height) {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        }
        int[] target = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(pixels, 0, target, 0, target.length);

        BufferStrategy strategy = canvas.getBufferStrategy();
        if (strategy == null) {
            return;
        }
        do {
            do {
                Graphics2D g = (Graphics2D) strategy.getDrawGraphics();
                try {
                    int cw = canvas.getWidth();
                    int ch = canvas.getHeight();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, cw, ch);

                    double scale = Math.min(cw / (double) MONITOR_W,
                            ch / (double) MONITOR_H);
                    int w = (int) (image.getWidth() * scale);
                    int h = (int) (image.getHeight() * scale);
                    if (w > 0 && h > 0) {
                        g.drawImage(image, (cw - w) / 2, (ch - h) / 2, w, h, null);
                    }
                } finally {
                    g.dispose();
                }
            } while (strategy.contentsRestored());
            strategy.show();
        } while (strategy.contentsLost());

        // X11 and some Windows drivers buffer the blit otherwise.
        Toolkit.getDefaultToolkit().sync();
    }

    private final class Keys extends KeyAdapter {

        @Override
        public void keyPressed(KeyEvent e) {
            set(e.getKeyCode(), true);
            joystick.pressKey(e.getKeyCode());
            switch (e.getKeyCode()) {
                case KeyEvent.VK_P -> joystick.pressPause();
                case KeyEvent.VK_SPACE -> joystick.pressMenu();
                case KeyEvent.VK_ESCAPE -> joystick.pressAbort();
                case KeyEvent.VK_M -> joystick.pressMode();
                case KeyEvent.VK_F1 -> joystick.pressCheat(0);
                case KeyEvent.VK_F2 -> joystick.pressCheat(1);
                case KeyEvent.VK_F3 -> joystick.pressCheat(2);
                case KeyEvent.VK_F4 -> joystick.pressCheat(3);
                case KeyEvent.VK_F5 -> joystick.pressCheat(4);
                default -> {
                }
            }
        }

        @Override
        public void keyReleased(KeyEvent e) {
            set(e.getKeyCode(), false);
        }

        private void set(int code, boolean down) {
            switch (code) {
                case KeyEvent.VK_UP -> joystick.setUp(down);
                case KeyEvent.VK_DOWN -> joystick.setDown(down);
                case KeyEvent.VK_LEFT -> joystick.setLeft(down);
                case KeyEvent.VK_RIGHT -> joystick.setRight(down);
                case KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_ENTER -> joystick.setFire(down);
                default -> {
                }
            }
        }
    }
}
