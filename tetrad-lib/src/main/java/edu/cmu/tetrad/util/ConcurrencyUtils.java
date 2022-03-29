package edu.cmu.tetrad.util;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConcurrencyUtils {
    public static void runCallables(final List<Callable<Boolean>> tasks, final int parallelism) {
        if (tasks.isEmpty()) return;

        if (parallelism == 1) {
            for (final Callable<Boolean> task : tasks) {
                try {
                    task.call();
                } catch (final Exception e) {
                    e.printStackTrace();
                }
            }
        } else {

            final ExecutorService executorService = Executors.newWorkStealingPool();

            try {
                executorService.invokeAll(tasks);
            } catch (final InterruptedException e) {
                e.printStackTrace();
            }

            executorService.shutdown();

            try {
                if (!executorService.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (final InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }
}