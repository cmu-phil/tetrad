/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TaskManager;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import static java.lang.Thread.sleep;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;

/**
 * Based on Joe's edu.cmu.tetradapp.util.WatchedProcess, but specific to data
 * loading
 *
 * @author Zhou Yuan (zhy19@pitt.edu)
 */
public abstract class DataLoadingIndicator {

    /**
     * The thread that the watched process dialog runs in. This thread can be
     * stopped (using wonderful yet deprecated stop( ) method, giving the user
     * control over any process that's running in it.
     */
    private Thread thread;

    /**
     * If the thread stops with an error, the error message is stored here.
     */
    private String errorMessage;

    /**
     * The number of milliseconds the thread sleeps before checking on user
     * input again.
     */
    private final long delay = 200L;

    /**
     * The dialog displayed to the user that lets them click "Stop" when they
     * want the process to stop.
     */
    private JDialog stopDialog;

    /**
     * The anstor Window in front of which the stop dialog is being displayed.
     */
    private Window owner;

    /**
     * True iff the "watch process" dialogs should display. These threads block,
     * so displaying them makes debugging difficult. On the other hand, not
     * displaying them means that users cannot stop processes, so they hate
     * that. SO...if you set this to false, make sure you set it to true before
     * you're done working!
     * <p/>
     * It must be set to true for posted versions. There's unit test that checks
     * for that.
     */
    private static boolean SHOW_DIALOG = true;

    /**
     * The object on which the watch dialog should be centered.
     */
    private Component centeringComp;

    /**
     * Constructs a new watched process.
     *
     * @param owner The ancestor window in front of which the stop dialog is
     * being displayed.
     */
    public DataLoadingIndicator(Window owner) {
        this(owner, JOptionUtils.centeringComp());
    }

    /**
     * Constructs a new watched process.
     *
     * @param owner The ancestor window in front of which the stop dialog is
     * being displayed.
     */
    private DataLoadingIndicator(Window owner, Component centeringComp) {
        if (owner == null) {
            throw new NullPointerException();
        }

        this.owner = owner;
        this.centeringComp = centeringComp;
        watchProcess();

    }

    //=============================PUBLIC METHODS========================//
    /**
     * To watch a process, override this method, as follows:
     * <pre>
     * Window owner = (Window) getTopLevelAncestor();
     *
     * new DataLoadingIndicator(owner) {
     *    public void watch() {
     *       ...your stuff to watch...
     *    }
     * };
     * </pre>
     */
    public abstract void watch();

    private String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    private void setStopDialog(JDialog stopDialog) {
        this.stopDialog = stopDialog;
    }

    private boolean isShowDialog() {
        return SHOW_DIALOG;
    }

    public void setShowDialog(boolean showDialog) {
        SHOW_DIALOG = showDialog;
    }

    public boolean isAlive() {
        return thread.isAlive();
    }

    //================================PRIVATE METHODS====================//
    private void watchProcess() {
        Runnable runnable = new Runnable() {
            public void run() {
                try {
                    watch();
                } catch (Exception e) {
                    e.printStackTrace();
                    String message = e.getMessage();

                    if (e.getCause() != null) {
                        message = e.getCause().getMessage();
                    }

                    setErrorMessage(message);
                    throw e;
                }
            }
        };

        Thread thread = new Thread(runnable);
        setThread(thread);
        thread.setPriority(7);
        thread.start();

        if (isShowDialog()) {
            Thread watcher = new Thread() {
                public void run() {
                    try {
                        sleep(delay);
                    } catch (InterruptedException e) {
                        return;
                    }

                    if (getErrorMessage() != null) {
                        JOptionPane.showMessageDialog(
                                centeringComp, getErrorMessage());
                        return;
                    }

                    JProgressBar progressBar = new JProgressBar(0, 100);
                    progressBar.setIndeterminate(true);

                    JButton stopButton = new JButton("Stop");

                    stopButton.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            if (getThread() != null) {
                                while (getThread().isAlive()) {
                                    TaskManager.getInstance().setCanceled(true);
                                    getThread().stop();

                                    try {
                                        sleep(500);
                                    } catch (InterruptedException e1) {
                                        JOptionPane.showMessageDialog(
                                                centeringComp,
                                                "Could not stop thread.");
                                        return;
                                    }
                                }
                            }
                        }
                    });

                    Box b = Box.createVerticalBox();
                    Box b1 = Box.createHorizontalBox();
                    b1.add(progressBar);
                    b1.add(stopButton);
                    b.add(b1);

                    if (isShowDialog()) {
                        Frame ancestor = (Frame) JOptionUtils.centeringComp().getTopLevelAncestor();
                        JDialog dialog = new JDialog(ancestor, "Executing...", false);
                        setStopDialog(dialog);

                        dialog.getContentPane().add(b);
                        dialog.pack();
                        dialog.setLocationRelativeTo(centeringComp);

                        while (getThread().isAlive()) {
                            try {
                                sleep(200);
                                dialog.setVisible(true);
                                dialog.toFront();
                                dialog.setFocusable(true);
                            } catch (InterruptedException e) {
                                return;
                            }
                        }

                        dialog.setVisible(false);
                        dialog.dispose();

                        if (getErrorMessage() != null) {
                            JOptionPane.showMessageDialog(
                                    centeringComp,
                                    "Stopped with error:\n"
                                    + getErrorMessage());
                        }
                    }
                }
            };

            watcher.start();
        }
    }

    private Thread getThread() {
        return thread;
    }

    private void setThread(Thread thread) {
        this.thread = thread;
    }

    /**
     * True if the thread is canceled. Implements of the watch() method should
     * check for this periodically and respond gracefully.
     *
     * @return
     */
    public boolean isCanceled() {
        boolean isCanceled = false;
        return isCanceled;
    }
}
