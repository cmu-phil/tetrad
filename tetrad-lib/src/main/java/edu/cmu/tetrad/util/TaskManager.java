package edu.cmu.tetrad.util;

/**
 * This cancels all processes that check the TaskManager.getInstance().isCanceled()
 * method.
 *
 * @author Joseph Ramsey
 */
public class TaskManager {
    private static TaskManager instance = new TaskManager();

    private boolean canceled;

    public static TaskManager getInstance() {
        return instance;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isCanceled() {
        return canceled;
    }
}