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

    /** Increment the count for a given call-site label. */
    public void increment(String site) {
        counts.computeIfAbsent(site, k -> new LongAdder()).increment();
    }

    /** Total across all sites. */
    public long total() {
        return counts.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /** Count for a single site. */
    public long get(String site) {
        LongAdder a = counts.get(site);
        return a == null ? 0L : a.sum();
    }

    public void reset() {
        counts.clear();
    }

    /** Human-readable breakdown, sorted by descending count. */
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