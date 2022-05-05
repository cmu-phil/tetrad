package edu.cmu.tetrad.util;

import java.util.List;
import java.util.concurrent.*;

public class ConcurrencyUtils {
    public static void runCallables(List<Callable<Boolean>> tasks, boolean parallelized) {
        if (tasks.isEmpty()) return;

        if (parallelized) {
            for (Callable<Boolean> task : tasks) {
                try {
                    task.call();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            ForkJoinPool pool = ForkJoinPool.commonPool();
            pool.invokeAll(tasks);
        }
    }
}