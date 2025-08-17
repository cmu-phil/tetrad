package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RankTests;
import org.ejml.simple.SimpleMatrix;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

/**
 * ScoredClusterFinder
 * -------------------
 * Given a DataSet and a subset of candidate variables Vsub (by column index),
 * enumerate all clusters C ⊆ Vsub of a fixed size s and keep those for which
 * a BIC-style RCCA score is maximized exactly at rank k when scored against
 * D = Vsub \ C.
 *
 * Scoring model (same spirit as BlocksBicScore):
 *   Fit(r) = -nEff * sum_{i=1..r} log(1 - rho_i^2)
 *   Pen(r) = c * [ r * (p + q - r) ] * log(n)  +  2*gamma * [ r * (p + q - r) ] * log(P_pool)
 * where p = |C|, q = |D|, m = min(p,q,n-1), r ∈ {0..m}, and
 * nEff = max(1, n - 1 - (p + q + 1)/2). We pick r* that maximizes Fit(r) - Pen(r).
 *
 * A cluster is accepted if r* == targetRank and, optionally, has margins over r*±1.
 *
 * Thread-safe; uses parallel enumeration and lock-free collections.
 */
public final class ScoredClusterFinder {

    /** Result holder for one accepted cluster. */
    public static final class ClusterHit {
        public final Set<Integer> members;   // indices in dataSet columns (subset of Vsub)
        public final double bestScore;       // score at chosenRank
        public final int chosenRank;         // argmax rank (should equal requested k)
        public final double scoreKm1;        // score at k-1 (NaN if k-1 < 0)
        public final double scoreKp1;        // score at k+1 (NaN if k+1 > m)
        ClusterHit(Set<Integer> members, double bestScore, int chosenRank, double scoreKm1, double scoreKp1) {
            this.members = members;
            this.bestScore = bestScore;
            this.chosenRank = chosenRank;
            this.scoreKm1 = scoreKm1;
            this.scoreKp1 = scoreKp1;
        }
        @Override public String toString() {
            List<Integer> sorted = new ArrayList<>(members);
            Collections.sort(sorted);
            return "ClusterHit{C=" + sorted + ", score=" + bestScore + ", r*=" + chosenRank + "}";
        }
    }

    // --------- knobs (with sensible defaults) ----------
    private double penaltyDiscount = 1.0;   // c
    private double ebicGamma = 0.0;         // gamma
    private double ridge = 1e-8;            // reg lambda for RCCA
    private double marginKm1 = 0.0;         // require score(k) - score(k-1) >= marginKm1 (if k>0)
    private double marginKp1 = 0.0;         // require score(k) - score(k+1) >= marginKp1 (if k+1<=m)
    private boolean verbose = false;

    // --------- data / cached state ----------
    private final DataSet dataSet;
    private final SimpleMatrix S;           // correlation (or covariance)
    private final int n;                    // sample size
    private final List<Integer> Vsub;       // candidate variable indices (columns in dataSet)

    // binomial cache for combinadic enumeration
    private static final ConcurrentHashMap<Long, long[][]> BINOM_CACHE = new ConcurrentHashMap<>();

    public ScoredClusterFinder(DataSet dataSet, Collection<Integer> candidateVarIndices) {
        this.dataSet = Objects.requireNonNull(dataSet);
        this.S = new CorrelationMatrix(dataSet).getMatrix().getSimpleMatrix();
        this.n = dataSet.getNumRows();
        this.Vsub = new ArrayList<>(Objects.requireNonNull(candidateVarIndices));
        if (Vsub.isEmpty()) {
            throw new IllegalArgumentException("candidateVarIndices must be non-empty.");
        }
        // validate indices
        int D = dataSet.getNumColumns();
        for (int v : Vsub) {
            if (v < 0 || v >= D) throw new IllegalArgumentException("Index out of bounds: " + v);
        }
        // deterministic order for combinadic mapping
        Collections.sort(this.Vsub);
    }

    // ----------------- public knobs -----------------

    public void setPenaltyDiscount(double c) { this.penaltyDiscount = c; }
    public void setEbicGamma(double gamma) { this.ebicGamma = gamma; }
    public void setRidge(double ridge) { this.ridge = ridge; }
    public void setMargins(double marginKm1, double marginKp1) {
        this.marginKm1 = Math.max(0.0, marginKm1);
        this.marginKp1 = Math.max(0.0, marginKp1);
    }
    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    // ----------------- main API -----------------

    /**
     * Find all clusters of size 'size' inside Vsub whose RCCA-BIC score is maximized at rank 'targetRank'
     * when contrasted with D = Vsub \ C. Returns hits sorted by (bestScore desc, lexicographic variable order).
     */
    public List<ClusterHit> findClusters(int size, int targetRank) {
        if (size <= 0) throw new IllegalArgumentException("size must be > 0.");
        if (targetRank < 0) throw new IllegalArgumentException("targetRank must be >= 0.");
        if (size >= Vsub.size() - size) {
            // D would be empty or smaller than C in a way that may collapse RCCA trivially.
            // We still allow, but warn in verbose mode.
            if (verbose) System.out.printf("[ScoredClusterFinder] Warning: size=%d, |Vsub|=%d -> |D|=%d%n",
                    size, Vsub.size(), (Vsub.size() - size));
        }

        final int nVars = Vsub.size();
        final int k = size;
        final long[][] Cbin = binom(nVars, k);
        final long total = Cbin[nVars][k];

        // Reusable thread-locals for speed
        final ThreadLocal<int[]> tlIdxs = ThreadLocal.withInitial(() -> new int[k]);
        final ThreadLocal<int[]> tlIds  = ThreadLocal.withInitial(() -> new int[k]);

        final List<ClusterHit> hits = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger counter = new AtomicInteger();

        LongStream.range(0, total).parallel().forEach(m -> {
            if (Thread.currentThread().isInterrupted()) return;

            int _c = counter.incrementAndGet();
            if (verbose && (_c % 5000 == 0)) {
                System.out.printf("[ScoredClusterFinder] Examined %d / %d subsets%n", _c, total);
            }

            // Decode k-combination (colex order) -> local indices 0..nVars-1
            int[] idxs = tlIdxs.get();
            combinadicDecodeColex(m, nVars, k, Cbin, idxs);

            // Map to dataset column IDs (sorted ascending)
            int[] Carr = tlIds.get();
            for (int i = 0; i < k; i++) Carr[i] = Vsub.get(idxs[i]);

            // Build D = Vsub \ C  (sorted ascending)
            int[] Darr = minus(Vsub, Carr);
            if (Darr.length == 0) return;

            // Score over ranks using RCCA singular values from RankTests
            ScoreSweep sweep = scoreSweep(Carr, Darr, n, ridge, penaltyDiscount, ebicGamma);
            if (sweep.mMax < 0) return; // no support

            // Accept if best rank equals targetRank and margins satisfied
            if (sweep.rStar == targetRank &&
                (Double.isNaN(sweep.scKm1) || sweep.scBest >= sweep.scKm1 + marginKm1) &&
                (Double.isNaN(sweep.scKp1) || sweep.scBest >= sweep.scKp1 + marginKp1)) {

                Set<Integer> cset = new TreeSet<>();
                for (int v : Carr) cset.add(v);
                hits.add(new ClusterHit(cset, sweep.scBest, sweep.rStar, sweep.scKm1, sweep.scKp1));
            }
        });

        // Sort for determinism and convenience
        hits.sort((a, b) -> {
            int cmp = -Double.compare(a.bestScore, b.bestScore);
            if (cmp != 0) return cmp;
            // tie-break lexicographically on member lists
            Iterator<Integer> ia = new TreeSet<>(a.members).iterator();
            Iterator<Integer> ib = new TreeSet<>(b.members).iterator();
            while (ia.hasNext() && ib.hasNext()) {
                int va = ia.next(), vb = ib.next();
                if (va != vb) return Integer.compare(va, vb);
            }
            return Integer.compare(a.members.size(), b.members.size());
        });

        return hits;
    }

    // ----------------- inner scoring sweep -----------------

    private static final class ScoreSweep {
        final int mMax;      // maximum admissible rank
        final int rStar;     // argmax rank
        final double scBest; // score at rStar
        final double scKm1;  // score at rStar-1 (or NaN)
        final double scKp1;  // score at rStar+1 (or NaN)
        ScoreSweep(int mMax, int rStar, double scBest, double scKm1, double scKp1) {
            this.mMax = mMax; this.rStar = rStar; this.scBest = scBest; this.scKm1 = scKm1; this.scKp1 = scKp1;
        }
    }

    /**
     * Compute BIC-style RCCA scores for ranks r=0..m and return argmax and margins.
     * Uses RankTests.getRccaEntry(S, X, Y, ridge).suffixLogs like BlocksBicScore.
     */
    private ScoreSweep scoreSweep(int[] C, int[] D, int n, double ridge, double c, double gamma) {
        // RCCA entry (cached internally by RankTests)
        RankTests.RccaEntry ent = RankTests.getRccaEntry(S, C, D, ridge);
        if (ent == null || ent.suffixLogs == null) return new ScoreSweep(-1, -1, Double.NEGATIVE_INFINITY, Double.NaN, Double.NaN);

        int p = C.length, q = D.length;
        int m = Math.min(Math.min(p, q), n - 1);
        m = Math.min(m, ent.suffixLogs.length - 1);
        if (m < 0) return new ScoreSweep(-1, -1, Double.NEGATIVE_INFINITY, Double.NaN, Double.NaN);
        if (m == 0) {
            double sc0 = -0.0; // Fit(0)=0, Pen(0)=0
            return new ScoreSweep(0, 0, sc0, Double.NaN, Double.NaN);
        }

        double nEff = n - 1.0 - 0.5 * (p + q + 1.0);
        if (nEff < 1.0) nEff = 1.0;

        // EBIC pool size: treat Vsub\C as available predictors; rough proxy:
        int Ppool = Math.max(q, 2);

        double[] suf = ent.suffixLogs;
        double base = suf[0];

        // Evaluate r=0..m
        int rStar = 0;
        double scStar = -1e300;
        double[] scByR = new double[m + 1];

        for (int r = 0; r <= m; r++) {
            double sumLogsTopR = base - suf[r];            // sum_{i=1..r} log(1 - rho_i^2), with convention suf[0]=sum_{i=1..0}(...) = 0
            double fit = -nEff * sumLogsTopR;              // larger is better
            int kParams = r * (p + q - r);
            double pen = c * kParams * Math.log(n);
            if (gamma > 0.0) pen += 2.0 * gamma * kParams * Math.log(Ppool);
            double sc = fit - pen;
            scByR[r] = sc;
            if (sc > scStar) { scStar = sc; rStar = r; }
        }

        double scKm1 = (rStar - 1 >= 0) ? scByR[rStar - 1] : Double.NaN;
        double scKp1 = (rStar + 1 <= m) ? scByR[rStar + 1] : Double.NaN;
        return new ScoreSweep(m, rStar, scStar, scKm1, scKp1);
    }

    // ----------------- small helpers -----------------

    /** D = Vsub \ C; both assumed sorted. */
    private static int[] minus(List<Integer> VsubSorted, int[] CarrSorted) {
        int ni = VsubSorted.size(), mi = CarrSorted.length;
        int[] out = new int[ni - mi];
        int i = 0, j = 0, k = 0;
        while (i < ni) {
            int v = VsubSorted.get(i);
            if (j < mi && v == CarrSorted[j]) { i++; j++; }
            else { out[k++] = v; i++; }
        }
        return out;
    }

    // ---- combinadic enumeration (colex) with binomial cache ----

    private static long[][] binom(int n, int k) {
        long key = (((long) n) << 32) ^ k;
        return BINOM_CACHE.computeIfAbsent(key, _k -> precomputeBinom(n, k));
    }

    private static long[][] precomputeBinom(int n, int k) {
        long[][] C = new long[n + 1][k + 1];
        for (int i = 0; i <= n; i++) {
            C[i][0] = 1L;
            int maxj = Math.min(i, k);
            for (int j = 1; j <= maxj; j++) {
                long v = C[i - 1][j - 1] + C[i - 1][j];
                if (v < 0 || v < C[i - 1][j - 1]) v = Long.MAX_VALUE; // clamp on overflow
                C[i][j] = v;
            }
        }
        return C;
    }

    /** Decode m into the k-combination (colex) of {0..n-1} into out[0..k-1] (sorted ascending). */
    private static void combinadicDecodeColex(long m, int n, int k, long[][] C, int[] out) {
        long r = m;
        int bound = n;
        for (int i = k; i >= 1; i--) {
            int lo = 0, hi = bound - 1, v = 0;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                long comb = choose(C, mid, i);
                if (comb <= r) { v = mid; lo = mid + 1; } else { hi = mid - 1; }
            }
            out[i - 1] = v;
            r -= choose(C, v, i);
            bound = v;
        }
    }

    private static long choose(long[][] C, int x, int j) {
        if (x < j || j < 0) return 0L;
        return C[x][j];
    }

    // ----------------- convenience: build Vsub by names -----------------

//    /** Utility builder: create finder over a named subset of variables. */
//    public static ScoredClusterFinder fromNames(DataSet dataSet, Collection<String> varNames) {
//        List<Integer> idxs = new ArrayList<>();
//        for (String nm : varNames) {
//            Node v = dataSet.getVariable(nm);
//            if (v == null) throw new IllegalArgumentException("Unknown variable: " + nm);
//            idxs.add(dataSet.getColumn(dataSet.getColumnIndex(v)));
//        }
//        return new ScoredClusterFinder(dataSet, idxs);
//    }
}