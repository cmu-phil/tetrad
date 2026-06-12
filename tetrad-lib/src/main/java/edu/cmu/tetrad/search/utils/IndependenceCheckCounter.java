package edu.cmu.tetrad.search.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts conditional independence checks broken down by the call site that
 * issued them. Thread-safe so it can be incremented from the parallelStream
 * in FCIT's edge-removal loop.
 */
public final class IndependenceCheckCounter {
    private final Map<String, LongAdder> counts = new ConcurrentHashMap<>();

    /**
     * Constructs an instance of IndependenceCheckCounter with an empty internal map.
     */
    public IndependenceCheckCounter() {
    }

    /**
     * Increment the count for a given call-site label.
     *
     * @param site the call-site label
     */
    public void increment(String site) {
        counts.computeIfAbsent(site, k -> new LongAdder()).increment();
    }

    /**
     * Total across all sites.
     *
     * @return the total count across all call-site labels
     */
    public long total() {
        return counts.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /**
     * Count for a single site.
     *
     * @param site the call-site label
     * @return the count for the given call-site label
     */
    public long get(String site) {
        LongAdder a = counts.get(site);
        return a == null ? 0L : a.sum();
    }

    /**
     * Resets the state of the counter by clearing all recorded counts.
     * This will remove all entries from the internal map, effectively
     * setting all site-specific and total counts back to zero.
     */
    public void reset() {
        counts.clear();
    }

    /**
     * Human-readable breakdown, sorted by descending count.
     *
     * @return a string representation of the independence check counter
     */
    public String report() {
        StringBuilder sb = new StringBuilder("Independence checks by site:\n");
        counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                .forEach(e -> sb.append(String.format("  %-40s %,d%n",
                        e.getKey(), e.getValue().sum())));
        sb.append(String.format("  %-40s %,d%n", "TOTAL", total()));
        return sb.toString();
    }
}