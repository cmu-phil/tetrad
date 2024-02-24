package edu.cmu.tetrad.util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feb 23, 2024 8:41:11 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 * @param <T>
 */
public class TaskRunner<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskRunner.class);

    private final ExecutorService pool;

    public TaskRunner(int numOfThreads) {
        this.pool = Executors.newFixedThreadPool(numOfThreads);
    }

    /**
     * Run tasks in parallel.
     *
     * @param tasks
     * @return
     */
    public List<T> run(final List<Callable<T>> tasks) {
        if (tasks == null) {
            return Collections.EMPTY_LIST;
        }

        List<Future<T>> completedTasks = new LinkedList<>();
        try {
            completedTasks.addAll(pool.invokeAll(tasks));
        } catch (InterruptedException exception) {
            LOGGER.error("", exception);
        } finally {
            shutdownAndAwaitTermination(pool);
        }

        List<T> results = new LinkedList<>();
        try {
            for (Future<T> completedTask : completedTasks) {
                results.add(completedTask.get());
            }
        } catch (ExecutionException | InterruptedException exception) {
            LOGGER.error("", exception);
        }

        return results;
    }

    private void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();

                if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                    LOGGER.error("Pool did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}
