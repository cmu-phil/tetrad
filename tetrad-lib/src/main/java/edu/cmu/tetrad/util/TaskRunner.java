/*
 * Copyright (C) 2024 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 * This class is for running a list of tasks that implement Callable.
 * <p>
 * Feb 23, 2024 8:41:11 PM
 *
 * @param <T> the type of the result of the tasks
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 */
public class TaskRunner<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskRunner.class);

    private final ExecutorService pool;

    /**
     * This class is responsible for running a list of tasks that implement the Callable interface in parallel using
     * multiple threads.
     */
    public TaskRunner() {
        this(Runtime.getRuntime().availableProcessors());
    }

    /**
     * Initializes a TaskRunner with the specified number of threads.
     *
     * @param numOfThreads the number of threads to be used by the TaskRunner
     */
    public TaskRunner(int numOfThreads) {
        this.pool = Executors.newFixedThreadPool(numOfThreads);
    }

    /**
     * Executes a list of tasks that implement Callable in parallel using multiple threads.
     *
     * @param tasks the list of tasks to execute
     * @return a list of results from the completed tasks
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

    /**
     * Shuts down an ExecutorService and awaits its termination.
     * <p>
     * This method gracefully shuts down the ExecutorService by calling {@link ExecutorService#shutdown()} and then
     * waits for the termination of all tasks for a specified timeout period using the
     * {@link ExecutorService#awaitTermination(long, TimeUnit)} method. If the tasks do not terminate within the timeout
     * period, the method forcefully shuts down the ExecutorService by calling {@link ExecutorService#shutdownNow()} and
     * waits again for the termination using {@link ExecutorService#awaitTermination(long, TimeUnit)}. If the tasks
     * still do not terminate, an error message is logged.
     *
     * @param pool the ExecutorService to shut down and await termination
     */
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
