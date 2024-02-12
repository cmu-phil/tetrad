package edu.cmu.tetrad.util;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;

/**
 * <p>ConcurrencyUtils class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ConcurrencyUtils {
    /**
     * <p>runCallables.</p>
     *
     * @param tasks        a {@link java.util.List} object
     * @param parallelized a boolean
     */
    public static void runCallables(List<Callable<Boolean>> tasks, boolean parallelized) {
        if (tasks.isEmpty()) return;

        if (!parallelized) {
            for (Callable<Boolean> task : tasks) {
                try {
                    task.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            ForkJoinPool pool = ForkJoin.getInstance().newPool(Runtime.getRuntime().availableProcessors());

            try {
                pool.invokeAll(tasks);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }
    }
}
