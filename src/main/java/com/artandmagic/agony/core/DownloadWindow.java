package com.artandmagic.agony.core;

import com.artandmagic.agony.data.original.DiskDownloader;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

/**
 * Shows the first start download of the disks in a small window.
 *
 * <p>The packaged game has no console, so without it the player would wait
 * through three downloads with nothing on screen, and a failed download would
 * go unseen.  The lines also go to the standard output, as before.
 */
public final class DownloadWindow {

    private DownloadWindow() {
    }

    /**
     * Downloads the missing disks into a folder, in a window when there is a
     * screen to show one on.
     *
     * @return true when the three images are there afterwards
     */
    public static boolean download(Path folder) {
        System.out.println("No Agony disk images found; fetching them into " + folder);
        if (GraphicsEnvironment.isHeadless()) {
            return DiskDownloader.download(folder);
        }
        JFrame frame = new JFrame("Agony - downloading the disks");
        JProgressBar bar = new JProgressBar(0, 3);
        JLabel label = new JLabel("Fetching the three Agony disks into " + folder);
        JTextArea log = new JTextArea(6, 60);
        log.setEditable(false);
        onScreen(() -> {
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            panel.add(label, BorderLayout.NORTH);
            panel.add(bar, BorderLayout.CENTER);
            panel.add(new JScrollPane(log), BorderLayout.SOUTH);
            bar.setPreferredSize(new Dimension(480, 20));
            bar.setStringPainted(true);
            bar.setString("Disk 1 of 3");
            frame.setContentPane(panel);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
        boolean ok = DiskDownloader.download(folder, (disk, of, line) -> {
            System.out.println(line);
            SwingUtilities.invokeLater(() -> {
                bar.setMaximum(of);
                bar.setValue(disk - 1);
                bar.setString(disk > of ? "Done" : "Disk " + disk + " of " + of);
                log.append(line + "\n");
            });
        });
        if (!ok) {
            onScreen(() -> JOptionPane.showMessageDialog(frame,
                    "The Agony disks could not all be downloaded:\n\n" + log.getText()
                            + "\nCheck the network and start the game again, or put the three"
                            + "\n.adf files in " + folder + " yourself.",
                    "Agony", JOptionPane.ERROR_MESSAGE));
        }
        onScreen(frame::dispose);
        return ok;
    }

    private static void onScreen(Runnable task) {
        try {
            SwingUtilities.invokeAndWait(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(e.getCause());
        }
    }
}
