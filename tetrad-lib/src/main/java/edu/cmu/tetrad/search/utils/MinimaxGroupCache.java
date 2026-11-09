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
     * Default constructor for the MinimaxGroupCache class.
     * Initializes an instance of the MinimaxGroupCache, providing the structure to
     * store and manage cached group data utilized in binning and grouping operations.
     * This constructor does not perform any specific initialization steps beyond
     * preparing the object for use.
     */
    public MinimaxGroupCache() { }

    /**
     * Computes and returns groups of data indices based on the input dataset, feature indices,
     * rows to include, and binning configuration. This method utilizes the binning strategy
     * defined in the MinimaxBinningConfig object to determine the grouping.
     *
     * @param data the dataset used for grouping; must not be null
     * @param zIdx an array of feature indices defining the dimensions used for grouping; must not be null
     * @param useRows a list of row indices that specifies which rows to include in the grouping; must not be null
     * @param cfg the configuration object that specifies the binning parameters, including
     *            the number of bins per dimension and the minimum bin size; must not be null
     * @return a 2D array of integers where each inner array represents a group of data indices
     *         based on the binning configuration
     */
    public int[][] getGroups(
            DataSet data,
            int[] zIdx,
            List<Integer> useRows,
            MinimaxBinningConfig cfg
    ) {
        Objects.requireNonNull(cfg, "cfg");
        return getGroups(data, zIdx, useRows, cfg.binsPerDim(), cfg.minBinSize());
    }

    /**
     * Computes and returns a group of bins based on the input data and parameters.
     * If the groups for the given configuration are already cached, it retrieves
     * them from the cache; otherwise, it computes them and stores the result in the cache.
     *
     * @param data the dataset used for grouping; must not be null
     * @param zIdx the array of indices defining the features or dimensions used for grouping; must not be null
     * @param useRows the list of row indices to use for group computation; must not be null
     * @param binsPerDim the number of bins to use per dimension in the grouping
     * @param minBinSize the minimum size of each bin
     * @return a 2D array of integers representing the grouped data
     *         where each inner array corresponds to a group of data indices
     *         based on the binning configuration
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

    /**
     * Clears the cached groups stored in the internal cache.
     * This method removes all previously computed and stored group data,
     * ensuring that the cache is empty and ready for new data to be added.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Retrieves the number of cached groups present in the internal cache.
     * This method allows checking how many group entries are currently stored.
     *
     * @return the number of cached groups stored in the internal cache
     */
    public int size() {
        return cache.size();
    }

    // -------------------- row signature --------------------

    /**
     * Computes a signature for a list of integers based on the FNV-1a hashing algorithm.
     * This method is typically used to generate a deterministic hash for the row indices
     * in order to facilitate caching or comparisons in data processing tasks.
     *
     * @param rows the list of integer row indices for which the signature is computed
     * @return a 64-bit hash signature representing the input list of integers
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