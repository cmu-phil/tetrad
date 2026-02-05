package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.DataSet;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for quantile-grid binning groups used by minimax-style conditional independence tests/scores.
 *
 * <p>Groups are keyed by:</p>
 * <ul>
 *   <li>conditioning set indices {@code zIdx} (copied defensively)</li>
 *   <li>a signature of the active row list {@code useRows}</li>
 *   <li>{@code binsPerDim}</li>
 *   <li>{@code minBinSize}</li>
 * </ul>
 *
 * <p>The cached value is an {@code int[][]} of groups, where each group is an array
 * of <i>local indices</i> in the 0..n-1 space of {@code useRows} (matching
 * {@link MinimaxBinning#computeGroups(DataSet, int[], List, int, int)}).</p>
 */
public final class MinimaxGroupCache {

    private final ConcurrentHashMap<Key, int[][]> cache = new ConcurrentHashMap<>();

    /**
     * Returns cached groups if present; otherwise computes them and caches the result.
     */
    public int[][] getGroups(
            DataSet data,
            int[] zIdx,
            List<Integer> useRows,
            int binsPerDim,
            int minBinSize
    ) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(zIdx, "zIdx");
        Objects.requireNonNull(useRows, "useRows");

        long rowsSig = rowsSignature(useRows);
        Key key = new Key(zIdx, rowsSig, binsPerDim, minBinSize);

        return cache.computeIfAbsent(key, k ->
                MinimaxBinning.computeGroups(data, zIdx, useRows, binsPerDim, minBinSize)
        );
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    // -------------------- row signature --------------------

    /**
     * FNV-1a-ish 64-bit signature of the active row list.
     * Deterministic across JVM runs given identical row sequences.
     */
    public static long rowsSignature(List<Integer> rows) {
        long h = 1469598103934665603L; // FNV-1a 64 offset basis
        for (int r : rows) {
            h ^= (r * 0x9E3779B97F4A7C15L);
            h *= 1099511628211L;
        }
        h ^= rows.size();
        h *= 1099511628211L;
        return h;
    }

    // -------------------- cache key --------------------

    private record Key(int[] z, long rowsSig, int binsPerDim, int minBinSize) {
        Key {
            z = (z == null ? new int[0] : Arrays.copyOf(z, z.length));
        }

        @Override public int hashCode() {
            int h = Arrays.hashCode(z);
            h = 31 * h + Long.hashCode(rowsSig);
            h = 31 * h + Integer.hashCode(binsPerDim);
            h = 31 * h + Integer.hashCode(minBinSize);
            return h;
        }

        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return rowsSig == k.rowsSig
                    && binsPerDim == k.binsPerDim
                    && minBinSize == k.minBinSize
                    && Arrays.equals(z, k.z);
        }
    }
}