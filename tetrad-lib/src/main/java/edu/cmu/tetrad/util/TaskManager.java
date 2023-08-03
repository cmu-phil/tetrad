package edu.cmu.tetrad.util;

/**
 * This cancels all processes that check the TaskManager.getInstance().isCanceled() method.
 *
 * @author josephramsey
 */
public class TaskManager {
    private static final TaskManager instance = new TaskManager();

    private boolean canceled;

    public static TaskManager getInstance() {
        return TaskManager.instance;
    }

    public boolean isCanceled() {
        return this.canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}