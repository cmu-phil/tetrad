package edu.cmu.tetrad.util;

/**
 * This cancels all processes that check the TaskManager.getInstance().isCanceled()
 * method.
 *
 * @author Joseph Ramsey
 */
public class TaskManager {
    private static final TaskManager instance = new TaskManager();

    private boolean canceled;

    public static TaskManager getInstance() {
        return TaskManager.instance;
    }

    public void setCanceled(final boolean canceled) {
        this.canceled = canceled;
    }

    public boolean isCanceled() {
        return this.canceled;
    }
}