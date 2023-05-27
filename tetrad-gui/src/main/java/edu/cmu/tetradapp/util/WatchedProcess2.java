package edu.cmu.tetradapp.util;

import javax.swing.*;
import java.awt.*;

/**
 * Replacement for the old WatchedProcess, which called the deprecated Thread.stop() method. This method is
 * deprecated because it can leave the program in an inconsistent state. This class uses Thread.interrupt() instead,
 * which is the recommended way to stop a thread.
 *
 * @author josephramsey
 * @author ChatGPT
 */
public abstract class WatchedProcess2 {
    private final JFrame frame;
    private Thread longRunningThread;
    private JDialog dialog;

    public WatchedProcess2() {
        frame = new JFrame("Thread Stop Example");
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
                // Thread was interrupted, so exit the loop and terminate
                System.out.println("Thread was interrupted. Stopping...");
                return;
            }

            try {
                watch();
            } catch (InterruptedException e) {
                // Thread was interrupted while sleeping, so exit the loop and terminate
                System.out.println("Thread was interrupted while sleeping. Stopping...");
                return;
            }

            if (dialog != null) {
                dialog.dispose();
                dialog = null;
            }

            // Process completed successfully
            System.out.println("Process completed successfully.");
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
        dialog.setSize(200, 100);
        dialog.setResizable(false);

        JButton stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> {
            stopLongRunningThread();
            dialog.dispose();
        });

        JPanel panel = new JPanel();
        panel.add(stopButton);
        dialog.getContentPane().add(panel);

        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }
}
