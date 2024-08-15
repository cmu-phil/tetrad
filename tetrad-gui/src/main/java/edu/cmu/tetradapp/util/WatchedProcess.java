package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.Tetrad;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Runs a long process, watching it with a thread and popping up a Stop button that the user can click to stop the
 * process.
 * <p>
 * Replacement for the old WatchedProcess, which called the deprecated Thread.stop() method. This method is deprecated
 * because it can leave the program in an inconsistent state. This class uses Thread.interrupt() instead, which is the
 * recommended way to stop a thread.
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
 * @version $Id: $Id
 */
public abstract class WatchedProcess {
    private final JFrame frame;
    private Thread longRunningThread;
    private JDialog dialog;

    private boolean interrupted;

    /**
     * Constructor.
     */
    public WatchedProcess() {

        // Get the Tetrad frame.
        frame = Tetrad.frame;

        if (frame == null) {
            throw new RuntimeException("Tetrad frame is null. Cannot create WatchedProcess.");
        }

        startLongRunningThread();
    }

    private void positionDialogAboveFrameCenter(JFrame frame, JDialog dialog) {
        // Calculate the new position for the dialog
        Point newDialogPosition = new Point(
                frame.getX() + frame.getWidth() / 2 - dialog.getWidth() / 2, // Centered horizontally
                frame.getY() + frame.getHeight() / 2 - dialog.getHeight() / 2 // Centered vertically
        );

        // Set the dialog's new position
        dialog.setLocation(newDialogPosition);
    }

    /**
     * This is the method that will be called in a separate thread. It should be a long-running process that can be
     * interrupted by the user.
     *
     * @throws java.lang.InterruptedException if the process is interrupted while running.
     */
    public abstract void watch() throws InterruptedException;

    private synchronized void startLongRunningThread() {
        longRunningThread = new Thread(() -> {
            try {
                watch();
            } catch (InterruptedException e) {
                TetradLogger.getInstance().log("Thread was interrupted while watching. Stopping; see console for stack trace.");
                e.printStackTrace();
            } catch (Exception e) {
                TetradLogger.getInstance().log("Exception while watching; see console for stack trace.");
                e.printStackTrace();
            }

            if (dialog != null) {
                SwingUtilities.invokeLater(() -> dialog.dispose());
            }
        });

        showStopDialog();
        longRunningThread.start();
    }

    protected void disposeStopDialog() {
        if (dialog != null) {
            SwingUtilities.invokeLater(() -> dialog.dispose());
        }
    }

    private void showStopDialog() {
        dialog = new JDialog(frame, "Stop Process", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setUndecorated(true);
        dialog.setSize(200, 50);
        dialog.setResizable(false);
        dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                if (dialog != null) {
                    positionDialogAboveFrameCenter(frame, dialog);
                }
            }
        });

        JButton stopButton = new JButton("Processing (click to stop)...");

        stopButton.addActionListener(e -> {
            if (longRunningThread != null) {
                SwingUtilities.invokeLater(() -> longRunningThread.interrupt());
            }

            if (dialog != null) {
                SwingUtilities.invokeLater(() -> dialog.dispose());
            }

            interrupted = true;
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createBevelBorder(BevelBorder.RAISED, Color.BLACK, Color.BLACK));
        panel.add(stopButton);

        dialog.getContentPane().add(panel);
        positionDialogAboveFrameCenter(frame, dialog);

        SwingUtilities.invokeLater(() -> dialog.setVisible(true));
    }

    /**
     * Checks if the object has been interrupted.
     *
     * @return true if the object has been interrupted, false otherwise.
     */
    public boolean isInterrupted() {
        return interrupted;
    }

}
