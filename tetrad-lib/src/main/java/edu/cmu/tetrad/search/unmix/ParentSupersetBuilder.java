/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a parent *superset* per node without assuming a single pooled DAG. Combines fast statistical screening (|corr|
 * or |Kendall τ) with an optional union of parents from several *shallow searches* run on subsamples ("bags").
 */
public final class ParentSupersetBuilder {
    /**
     * A private constructor to prevent instantiation of the {@code ParentSupersetBuilder} class.
     * This class is designed to provide utility methods for building parent supersets and should not be instantiated.
     */
    private ParentSupersetBuilder() {}

    /**
     * Builds a map representing a superset of parent nodes for each variable node in the given data set. This method
     * performs statistical screening based on provided configurations, and optionally refines the superset using bagged
     * subsamples and a shallow-search procedure.
     *
     * @param data The dataset containing the variables for which the parent superset will be constructed.
     * @param cfg  The configuration object that specifies scoring type, top variable selection, bagging preferences,
     *             and shallow-search method among other parameters.
     * @return A map where each key is a target node, and the corresponding value is a list of potential parent nodes.
     * These parent nodes are determined based on the statistical screening and optional bagging operations.
     */
    public static Map<Node, List<Node>> build(DataSet data, Config cfg) {
        Objects.requireNonNull(data);
        List<Node> vars = data.getVariables();
        int p = vars.size();

        // 1) Fast per-target statistical screening
        Map<Node, List<Node>> superset = new HashMap<>();
        double[][] X = toArray(data); // n x p

        for (int j = 0; j < p; j++) {
            Node target = vars.get(j);
            double[] y = column(X, j);
            PriorityQueue<int[]> top = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
            // store (idx, scoreScaledBy1e6) to avoid custom comparator; weâll keep raw double separately
            double[] scores = new double[p];
            Arrays.fill(scores, 0.0);

            for (int k = 0; k < p; k++)
                if (k != j) {
                    double s = switch (cfg.scoreType) {
                        case PEARSON -> Math.abs(pearson(column(X, k), y));
                        case SPEARMAN -> Math.abs(spearman(column(X, k), y));
                        case KENDALL -> Math.abs(kendallTau(column(X, k), y));
                    };
                    scores[k] = s;
                }

            // take topM indices by score
            int m = Math.min(cfg.topM, Math.max(0, p - 1));
            List<Integer> idxs = argTopK(scores, m, j);
            List<Node> cand = idxs.stream().map(vars::get).collect(Collectors.toList());
            superset.put(target, cand);
        }

        // 2) Optional: union with shallow-search parents on bagged subsamples
        if (cfg.useBagging && cfg.shallowSearch != null && cfg.bags > 0 && cfg.bagFraction > 0.0) {
            Random rnd = new Random(cfg.seed);
            int n = data.getNumRows();
            int m = Math.max(1, (int) Math.round(cfg.bagFraction * n));
            for (int b = 0; b < cfg.bags; b++) {
                List<Integer> rows = sampleWithoutReplacement(n, m, rnd);
                int[] rowArray = rows.stream().mapToInt(Integer::intValue).toArray();
                DataSet sub = data.subsetRows(rowArray);
                Graph G = cfg.shallowSearch.apply(sub);
                // union G-parents into superset
                for (Node v : vars) {
                    List<Node> union = new ArrayList<>(superset.get(v));
                    Set<Node> set = new LinkedHashSet<>(union);
                    for (Node pa : G.getParents(v)) {
                        if (vars.contains(pa) && !pa.equals(v)) set.add(pa);
                    }
                    superset.put(v, new ArrayList<>(set));
                }
            }
        }
        return superset;
    }

    private static double[][] toArray(DataSet ds) {
        int n = ds.getNumRows(), p = ds.getNumColumns();
        double[][] out = new double[n][p];
        for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) out[i][j] = ds.getDouble(i, j);
        return out;
    }

    private static double[] column(double[][] X, int j) {
        double[] c = new double[X.length];
        for (int i = 0; i < X.length; i++) c[i] = X[i][j];
        return c;
    }

    // ---------- helpers ----------

    private static List<Integer> argTopK(double[] s, int k, int skipIdx) {
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
        for (int i = 0; i < s.length; i++) {
            if (i == skipIdx) continue;
            int scoreScaled = (int) Math.round(s[i] * 1_000_000.0);
            if (pq.size() < k) {
                pq.offer(new int[]{i, scoreScaled});
            } else if (scoreScaled > pq.peek()[1]) {
                pq.poll();
                pq.offer(new int[]{i, scoreScaled});
            }
        }
        ArrayList<Integer> idxs = new ArrayList<>();
        while (!pq.isEmpty()) idxs.add(pq.poll()[0]);
        Collections.sort(idxs); // order not important
        return idxs;
    }

    private static List<Integer> sampleWithoutReplacement(int n, int m, Random rnd) {
        int[] arr = new int[n];
        for (int i = 0; i < n; i++) arr[i] = i;
        for (int i = 0; i < m; i++) {
            int j = i + rnd.nextInt(n - i);
            int tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
        List<Integer> out = new ArrayList<>(m);
        for (int i = 0; i < m; i++) out.add(arr[i]);
        return out;
    }

    private static double pearson(double[] x, double[] y) {
        int n = x.length;
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            double xi = x[i], yi = y[i];
            sx += xi;
            sy += yi;
            sxx += xi * xi;
            syy += yi * yi;
            sxy += xi * yi;
        }
        double cov = sxy - sx * sy / n;
        double vx = sxx - sx * sx / n;
        double vy = syy - sy * sy / n;
        double den = Math.sqrt(Math.max(vx, 0.0) * Math.max(vy, 0.0));
        return den > 0 ? cov / den : 0.0;
    }

    private static double spearman(double[] x, double[] y) {
        return pearson(ranks(x), ranks(y));
    }

    private static double kendallTau(double[] x, double[] y) {
        int n = x.length;
        int concord = 0, discord = 0;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                double dx = Double.compare(x[i], x[j]);
                double dy = Double.compare(y[i], y[j]);
                if (dx == 0 || dy == 0) continue;
                if (dx == dy) concord++;
                else discord++;
            }
        int denom = concord + discord;
        return denom == 0 ? 0.0 : (double) (concord - discord) / denom;
    }

    private static double[] ranks(double[] a) {
        int n = a.length;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> a[i]));
        double[] r = new double[n];
        for (int rank = 0; rank < n; rank++) r[idx[rank]] = rank + 1;
        return r;
    }

    /**
     * Represents the type of correlation score used for statistical calculations or comparisons.
     */
    public enum ScoreType {
        /**
         * Pearson correlation coefficient, which measures linear correlation between two variables.
         */
        PEARSON,
        /**
         * Spearman's rank correlation coefficient, which measures rank-based correlation.
         */
        SPEARMAN,
        /**
         * Kendall's tau correlation coefficient, which measures ordinal association between variables.
         */
        KENDALL
    }

    /**
     * Represents the configuration settings used for statistical screening and optional bagging operations in the
     * parent superset construction process. This class contains parameters that control behavior such as scoring type,
     * selection size, bagging, and optional shallow search functionality.
     */
    public static class Config {
        /**
         * Specifies the maximum number of strongest candidates to retain per node during the statistical screening
         * process. This value is utilized to limit the selection of candidates to the top M based on their strength,
         * determined using relevant scoring functions in the algorithm. A lower value of this parameter can reduce
         * memory and computational overhead, while a higher value allows for broader exploration of potential
         * candidates.
         */
        public int topM = 10;                 // keep up to M strongest candidates per node (from stats)
        /**
         * Specifies the type of correlation score to be used for statistical calculations in the screening process. The
         * value determines the method of determining correlation, such as Pearson, Spearman, or Kendall correlations.
         * This parameter influences the scoring mechanism applied while evaluating relationships between variables.
         */
        public ScoreType scoreType = ScoreType.PEARSON;
        /**
         * Determines whether bagging should be applied during the parent superset construction process. If set to true,
         * the algorithm performs shallow searches on data subsamples and takes the union of parent sets identified in
         * these subsamples. This can help increase robustness and improve statistical reliability by leveraging
         * ensemble-like techniques.
         */
        public boolean useBagging = false;    // if true, union with parents from shallow searches on subsamples
        /**
         * Specifies the number of subsamples to use during the bagging process. This parameter determines how many
         * subsets of the data are created during the statistical screening or shallow search. A higher value increases
         * the robustness of the aggregate parent set construction by incorporating results from more subsample
         * analyses, while a lower value reduces computational costs.
         */
        public int bags = 5;                  // number of subsamples
        /**
         * Specifies the fraction of rows to use in each subsample during the bagging process. This parameter defines
         * the proportion of the dataset that is randomly selected for each subsample. A lower value reduces the size of
         * each subset used in the analysis, potentially lowering computational costs but increasing variability. A
         * higher value results in larger subsets, contributing to more stable statistical outcomes at the expense of
         * increased resource usage.
         */
        public double bagFraction = 0.5;      // fraction of rows per subsample
        /**
         * The seed variable is used to initialize the random number generator, ensuring reproducibility in randomized
         * operations within the configuration or associated processes.
         * <p>
         * This variable allows for deterministic behavior by providing a fixed starting point for random number
         * generation. It is particularly useful in scenarios where consistency of results is required, such as testing
         * or simulations.
         */
        public long seed = 13L;

        /**
         * Constructs a new instance of the Config class with default settings.
         *
         * This constructor initializes a Config object with pre-defined default values
         * for its parameters. Instances of the Config class are used to store and manage
         * configuration settings for various operations or algorithms. The initialized
         * values can be modified after construction if specific configurations are required.
         */
        public Config
                () {}

        /**
         * If provided, called on each subsample to get a shallow graph (e.g., PC-Max depth 1)
         */
        public Function<DataSet, Graph> shallowSearch = null;
    }
}
