package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.TetradLogger;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;

/**
 * Runs a long process, watching it with a thread and popping up a Stop button that the user can click to stop the
 * process.
 * <p>
 * Replacement for the old WatchedProcess, which called the deprecated Thread.stop() method. This method is
 * deprecated because it can leave the program in an inconsistent state. This class uses Thread.interrupt() instead,
 * which is the recommended way to stop a thread.
 * <p>
 * Example usage:
 * <pre>
 * class MyWatchedProcess extends WatchedProcess {
 *
 *     &#64;Override
 *     public void watch() throws InterruptedException {
 *         // Long process...
 *     }
 * };
 *
 * new MyWatchedProcess();
 * </pre>
 *
 * @author josephramsey
 * @author ChatGPT
 */
public abstract class WatchedProcess {
    private final JFrame frame;
    private Thread longRunningThread;
    private JDialog dialog;

    /**
     * Constructor.
     */
    public WatchedProcess() {
        frame = new JFrame("Hidden Frame");
        startLongRunningThread();
    }

    /**
     * This is the method that will be called in a separate thread. It should be a long-running process that can be
     * interrupted by the user.
     *
     * @throws InterruptedException if the process is interrupted while running.
     */
    public abstract void watch() throws InterruptedException;

    private void startLongRunningThread() {
        longRunningThread = new Thread(() -> {
            if (Thread.interrupted()) {
                // The Thread was interrupted, so exit the loop and terminate
                System.out.println("Thread was interrupted. Stopping...");
                return;
            }

            try {
                watch();
            } catch (InterruptedException e) {
                TetradLogger.getInstance().forceLogMessage("Thread was interrupted while watching. Stopping...");
                return;
            }

            if (dialog != null) {
                dialog.dispose();
                dialog = null;
            }
        });

        longRunningThread.start();

        showStopDialog();
    }

    private void stopLongRunningThread() {
        if (longRunningThread != null && longRunningThread.isAlive()) {
            longRunningThread.interrupt();
        }
    }

    private void showStopDialog() {
        dialog = new JDialog(frame, "Stop Process", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setUndecorated(true);
        dialog.setSize(200, 50);
        dialog.setResizable(false);

        JButton stopButton = new JButton("Processing (click to stop)...");

        stopButton.addActionListener(e -> {
            stopLongRunningThread();
            dialog.dispose();
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.BLACK, Color.BLACK));
        panel.add(stopButton);

        dialog.getContentPane().add(panel);

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
}
