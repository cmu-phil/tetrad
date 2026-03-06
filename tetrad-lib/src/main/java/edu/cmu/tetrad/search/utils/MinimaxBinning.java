package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.DataSet;
import org.apache.commons.math3.util.FastMath;

import java.util.*;

/**
 * Quantile-grid binning for conditioning variables Z, returning groups of row indices.
 *
 * <p>Given a set of conditioning columns (zIdx) and a list of dataset row indices (useRows),
 * this utility:</p>
 * <ol>
 *   <li>Extracts Z values for those rows,</li>
 *   <li>Builds per-dimension quantile cutpoints (equal-frequency-ish),</li>
 *   <li>Assigns each row a mixed-radix bin id,</li>
 *   <li>Returns groups of indices (in 0..n-1 space) for bins with size >= minBinSize.</li>
 * </ol>
 *
 * <p><b>Important:</b> The returned group indices are in the <i>local</i> index space of
 * {@code useRows} (i.e., they index into arrays built by iterating {@code useRows}).</p>
 */
public final class MinimaxBinning {

    private MinimaxBinning() {}

    /**
     * Computes within-bin groups for conditioning set Z using quantile binning.
     *
     * @param data       dataset (assumed continuous in your use-case)
     * @param zIdx       indices of conditioning variables (columns)
     * @param useRows    dataset row indices to include (defines local index space 0..n-1)
     * @param binsPerDim number of bins per Z dimension (>= 2)
     * @param minBinSize minimum samples required to keep a bin (>= 1; you use >= 3)
     * @return groups as int[][]; each group is an array of local row indices
     */
    public static int[][] computeGroups(
            DataSet data,
            int[] zIdx,
            List<Integer> useRows,
            int binsPerDim,
            int minBinSize
    ) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(zIdx, "zIdx");
        Objects.requireNonNull(useRows, "useRows");

        int n = useRows.size();
        int d = zIdx.length;

        if (d == 0 || n == 0) return new int[0][];
        binsPerDim = FastMath.max(2, binsPerDim);
        minBinSize = FastMath.max(1, minBinSize);

        // Pull Z into a local array Z[n][d] (faster than repeated data.getDouble)
        double[][] Z = new double[n][d];
        for (int i = 0; i < n; i++) {
            int r = useRows.get(i);
            for (int j = 0; j < d; j++) Z[i][j] = data.getDouble(r, zIdx[j]);
        }

        // Quantile edges per dim: edges[j][k] for k=1..binsPerDim-1
        double[][] edges = new double[d][binsPerDim - 1];
        for (int j = 0; j < d; j++) {
            double[] col = new double[n];
            for (int i = 0; i < n; i++) col[i] = Z[i][j];
            Arrays.sort(col);
            for (int k = 1; k < binsPerDim; k++) {
                int q = (int) FastMath.floor((k * (n - 1.0)) / binsPerDim);
                edges[j][k - 1] = col[q];
            }
        }

        // Assign bin ids using mixed radix
        int[] binId = new int[n];
        final int radix = binsPerDim;

        for (int i = 0; i < n; i++) {
            int id = 0;
            int mult = 1;
            for (int j = 0; j < d; j++) {
                double v = Z[i][j];
                double[] ej = edges[j];
                int b = upperBound(ej, v); // 0..binsPerDim-1
                id += b * mult;
                mult *= radix;
            }
            binId[i] = id;
        }

        // Group indices by binId (indices are local indices 0..n-1)
        Map<Integer, IntArrayList> map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            map.computeIfAbsent(binId[i], k -> new IntArrayList()).add(i);
        }

        ArrayList<int[]> groups = new ArrayList<>();
        for (IntArrayList g : map.values()) {
            if (g.size() >= minBinSize) groups.add(g.toArray());
        }

        return groups.toArray(new int[0][]);
    }

    /**
     * Finds the smallest index in the sorted array {@code edges} such that the value
     * at that index is greater than or equal to {@code v}.
     *
     * @param edges a sorted array of doubles representing boundaries or thresholds
     * @param v     the value to compare against the elements in {@code edges}
     * @return the smallest index {@code lo} such that {@code edges[lo] >= v},
     *         or {@code edges.length} if no such index exists
     */
    public static int upperBound(double[] edges, double v) {
        int lo = 0, hi = edges.length;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (v > edges[mid]) lo = mid + 1;
            else hi = mid;
        }
        return lo;
    }

    /** Tiny growable int list. */
    private static final class IntArrayList {
        private int[] a = new int[16];
        private int n = 0;
        void add(int v) {
            if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
            a[n++] = v;
        }
        int size() { return n; }
        int[] toArray() { return Arrays.copyOf(a, n); }
    }
}