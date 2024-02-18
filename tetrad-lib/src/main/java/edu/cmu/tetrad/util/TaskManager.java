package edu.cmu.tetrad.util;

/**
 * This cancels all processes that check the TaskManager.getInstance().isCanceled() method.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TaskManager {
    private static final TaskManager instance = new TaskManager();

    private boolean canceled;

    /**
     * <p>Getter for the field <code>instance</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.TaskManager} object
     */
    public static TaskManager getInstance() {
        return TaskManager.instance;
    }

    /**
     * <p>isCanceled.</p>
     *
     * @return a boolean
     */
    public boolean isCanceled() {
        return this.canceled;
    }

    /**
     * <p>Setter for the field <code>canceled</code>.</p>
     *
     * @param canceled a boolean
     */
    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }
}
