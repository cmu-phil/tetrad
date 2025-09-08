package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds a parent *superset* per node without assuming a single pooled DAG.
 * Combines fast statistical screening (|corr| or |Kendall τ|) with an optional
 * union of parents from several *shallow searches* run on subsamples ("bags").
 */
public final class ParentSupersetBuilder {

    public enum ScoreType { PEARSON, SPEARMAN, KENDALL }

    public static class Config {
        public int topM = 10;                 // keep up to M strongest candidates per node (from stats)
        public ScoreType scoreType = ScoreType.PEARSON;
        public boolean useBagging = false;    // if true, union with parents from shallow searches on subsamples
        public int bags = 5;                  // number of subsamples
        public double bagFraction = 0.5;      // fraction of rows per subsample
        public long seed = 13L;
        /** If provided, called on each subsample to get a shallow graph (e.g., PC-Max depth 1) */
        public Function<DataSet, Graph> shallowSearch = null;
    }

    /** Main API: returns a map v -> superset parents S_v (list order arbitrary). */
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
            // store (idx, scoreScaledBy1e6) to avoid custom comparator; we’ll keep raw double separately
            double[] scores = new double[p];
            Arrays.fill(scores, 0.0);

            for (int k = 0; k < p; k++) if (k != j) {
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

    // ---------- helpers ----------

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

    private static List<Integer> argTopK(double[] s, int k, int skipIdx) {
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
        for (int i = 0; i < s.length; i++) {
            if (i == skipIdx) continue;
            int scoreScaled = (int) Math.round(s[i] * 1_000_000.0);
            if (pq.size() < k) {
                pq.offer(new int[]{i, scoreScaled});
            } else if (scoreScaled > pq.peek()[1]) {
                pq.poll(); pq.offer(new int[]{i, scoreScaled});
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
            int tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
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
            sx += xi; sy += yi;
            sxx += xi*xi; syy += yi*yi; sxy += xi*yi;
        }
        double cov = sxy - sx*sy/n;
        double vx = sxx - sx*sx/n;
        double vy = syy - sy*sy/n;
        double den = Math.sqrt(Math.max(vx, 0.0) * Math.max(vy, 0.0));
        return den > 0 ? cov/den : 0.0;
    }

    private static double spearman(double[] x, double[] y) {
        return pearson(ranks(x), ranks(y));
    }

    private static double kendallTau(double[] x, double[] y) {
        int n = x.length;
        int concord = 0, discord = 0;
        for (int i = 0; i < n; i++) for (int j = i+1; j < n; j++) {
            double dx = Double.compare(x[i], x[j]);
            double dy = Double.compare(y[i], y[j]);
            if (dx == 0 || dy == 0) continue;
            if (dx == dy) concord++; else discord++;
        }
        int denom = concord + discord;
        return denom == 0 ? 0.0 : (double)(concord - discord) / denom;
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
}