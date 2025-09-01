// file: edu/cmu/tetrad/search/TscStability.java
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.function.Function;

public final class TscStability {

    private TscStability() {}

    /** Run TSC once and return canonicalized cluster sets (TreeSet for stable equality). */
    public static Set<Set<Integer>> runOnce(List<Node> vars, CovarianceMatrix cov, double alpha, int ess) {
        Tsc tsc = new Tsc(vars, cov);
        tsc.setAlpha(alpha);
        tsc.setExpectedSampleSize(ess);
        tsc.setVerbose(false);
        Map<Set<Integer>, Integer> out = tsc.findClusters();
        Set<Set<Integer>> canon = new HashSet<>();
        for (Set<Integer> C : out.keySet()) canon.add(Collections.unmodifiableSet(new TreeSet<>(C)));
        return canon;
    }

    /** Keep clusters appearing at both α and α/scale (e.g., scale=5). */
    public static Set<Set<Integer>> dualAlpha(List<Node> vars, CovarianceMatrix cov, double alpha, int ess, double scale) {
        Set<Set<Integer>> a = runOnce(vars, cov, alpha, ess);
        Set<Set<Integer>> b = runOnce(vars, cov, alpha / scale, ess);
        a.retainAll(b);
        return a;
    }

    /** Bootstrap stability: resample rows B times; keep clusters seen in >= keepFrac of runs. */
    public static Set<Set<Integer>> bootstrap(int B, double keepFrac,
                                              Function<Integer, CovarianceMatrix> covBuilder, List<Node> vars, double alpha, int ess) {
        Map<Set<Integer>, Integer> counts = new HashMap<>();
        for (int b = 0; b < B; b++) {
            CovarianceMatrix covB = covBuilder.apply(b);
            for (Set<Integer> C : runOnce(vars, covB, alpha, ess)) {
                counts.merge(C, 1, Integer::sum);
            }
        }
        int thresh = (int)Math.ceil(keepFrac * B);
        Set<Set<Integer>> keep = new HashSet<>();
        for (var e : counts.entrySet()) if (e.getValue() >= thresh) keep.add(e.getKey());
        return keep;
    }

    /** Convenience builder: bootstrap resampling for a DataSet (rows with replacement). */
    public static Function<Integer, CovarianceMatrix> bootstrapCovBuilder(DataSet data, long seed) {
        return (Integer b) -> {
            int n = data.getNumRows();
            int[] idx = new int[n];
            Random r = new Random(seed ^ (0x9E3779B97F4A7C15L + b));
            for (int i = 0; i < n; i++) idx[i] = r.nextInt(n);
            DataSet boot = data.subsetRows(idx);
            return new CovarianceMatrix(boot);
        };
    }
}