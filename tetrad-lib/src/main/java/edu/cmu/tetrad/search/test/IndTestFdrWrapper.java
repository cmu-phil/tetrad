package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wrapper class for {@link IndependenceTest} that enforces False Discovery Rate (FDR) control on independence
 * decisions. This class uses either Benjamini-Hochberg (BH) or Benjamini-Yekutieli (BY) FDR control methods and
 * supports both global and stratified FDR control across conditioning sets.
 * <p>
 * The workflow is divided into two phases: 1. Recording Epoch: Raw p-values for independence tests are cached from the
 * underlying test. 2. Decision Epoch: Enforces FDR-controlled cutoffs based on cached p-values.
 * <p>
 * The cutoffs can be computed globally or within groups based on the cardinality of the conditioning set (|Z|).
 * <p>
 * This class also maintains a mechanism to track "mind-changes," i.e., decisions that change between subsequent
 * algorithm passes.
 * <p>
 * The wrapper allows for integrating FDR-controlled independence testing into iterative search algorithms without
 * re-computing p-values across epochs, ensuring reproducibility and efficiency.
 */
public final class IndTestFdrWrapper implements IndependenceTest {

    // Usage:
    //
    //IndependenceTest base = new IndTestFisherZ(ds, alpha);
    //((IndTestFisherZ) base).setShrinkageMode(LEDOIT_WOLF);
    //
    //IndTestFdrWrapper wrap = new IndTestFdrWrapper(base, FdrMode.BH, /*q=*/0.05, Scope.BY_COND_SET);
    //
    //int maxEpochs = 5, tauChanges = 0;
    //int changes;
    //
    //wrap.startRecordingEpoch();
    //Graph g = new Ccd(wrap).search();   // Epoch 0: record p's, baseline decisions (or just cache-only)
    //
    /// / Freeze FDR cutoffs from observed p's
    //wrap.computeCutoffsFromRecordedPvals();
    //
    //for (int epoch = 1; epoch <= maxEpochs; epoch++) {
    //    g = new Ccd(wrap).search();     // decisions now use α* (global or per-|Z|)
    //    changes = wrap.countMindChangesAndSnapshot();
    //    if (changes <= tauChanges) break;
    //}

    private final IndependenceTest base;
    private final FdrMode fdrMode;
    private final double q;             // target FDR level, e.g., 0.05
    private final Scope scope;
    // Cache of raw p-values per fact (stable across epochs)
    private final Map<IndependenceFact, Double> pvals = new ConcurrentHashMap<>();
    private final Map<Integer, Double> alphaStarByZ = new HashMap<>();
    private boolean recordOnly = true;  // Epoch 0: record p's; Epoch >=1: enforce α*
    // Per-epoch decisions for mind-change tracking
    private Map<IndependenceFact, Boolean> lastDecisions = new HashMap<>();
    // Current cutoffs
    private double globalAlphaStar = Double.NaN;

    public IndTestFdrWrapper(IndependenceTest base, FdrMode mode, double q, Scope scope) {
        this.base = Objects.requireNonNull(base);
        this.fdrMode = Objects.requireNonNull(mode);
        if (!(q > 0 && q < 1)) throw new IllegalArgumentException("q must be in (0,1)");
        this.q = q;
        this.scope = Objects.requireNonNull(scope);
    }

    /**
     * Convert “large p” acceptance to BH/BY on p' = 1 - p, then map back to α* = 1 - t'.
     */
    static double computeAlphaStar(List<Double> rawP, FdrMode mode, double q) {
        if (rawP.isEmpty()) return 1.0;
        int m = rawP.size();
        // Build p' list
        double[] pprime = new double[m];
        for (int i = 0; i < m; i++) {
            double p = rawP.get(i);
            pprime[i] = Math.max(0.0, Math.min(1.0, 1.0 - p));
        }
        Arrays.sort(pprime); // ascending

        double qAdj = q;
        if (mode == FdrMode.BY) {
            // harmonic correction
            double Hm = 0.0;
            for (int i = 1; i <= m; i++) Hm += 1.0 / i;
            qAdj = q / Hm;
        }

        int k = -1;
        for (int i = 1; i <= m; i++) {
            double thresh = (i * qAdj) / m;
            if (pprime[i - 1] <= thresh) k = i;
        }
        if (k < 0) return 1.0;             // no “large p” survives → never accept independence from FDR
        double tPrime = pprime[k - 1];     // BH/BY cutoff in p' space
        return 1.0 - tPrime;               // α* in original p space
    }

    public void startRecordingEpoch() {
        recordOnly = true;
        // don’t clear pvals; we want determinism across epochs
    }

    /* ===== Epoch control ===== */

    public void computeCutoffsFromRecordedPvals() {
        recordOnly = false;
        if (scope == Scope.GLOBAL) {
            this.globalAlphaStar = computeAlphaStar(new ArrayList<>(pvals.values()), fdrMode, q);
        } else {
            // stratify by |Z| count. Build map<k, list of p's for facts with |Z|=k>
            Map<Integer, List<Double>> byK = new HashMap<>();
            for (var e : pvals.entrySet()) {
                int k = e.getKey().getZ().size();
                byK.computeIfAbsent(k, _k -> new ArrayList<>()).add(e.getValue());
            }
            alphaStarByZ.clear();
            for (var e : byK.entrySet()) {
                alphaStarByZ.put(e.getKey(), computeAlphaStar(e.getValue(), fdrMode, q));
            }
        }
        // snapshot decisions for mind-change tracking
        lastDecisions = null; // will rebuild on first decision epoch
    }

    /**
     * Returns the number of mind-changes between the previous decision epoch and the current one. Call this AFTER you
     * complete an algorithm pass using this wrapper in decision mode.
     */
    public int countMindChangesAndSnapshot() {
        if (recordOnly) return 0;
        int changes = 0;
        Map<IndependenceFact, Boolean> current = new HashMap<>();
        for (var e : pvals.entrySet()) {
            var fact = e.getKey();
            boolean indep = decideIndependenceFromCutoff(fact, e.getValue());
            current.put(fact, indep);
            Boolean prev = (lastDecisions == null) ? null : lastDecisions.get(fact);
            if (prev != null && prev != indep) changes++;
        }
        lastDecisions = current;
        return changes;
    }

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        IndependenceFact fact = new IndependenceFact(x, y, z);

        // get (or compute) raw p
        Double p = pvals.get(fact);
        if (p == null) {
            IndependenceResult r = base.checkIndependence(x, y, z);
            p = r.getPValue();
            pvals.put(fact, p);
        }

        boolean indep;
        if (recordOnly) {
            // epoch 0: behave like base test (or: optionally accept only if p > baseAlpha)
            indep = p > baseAlphaOr1(); // base’s alpha if available; else treat as “record only”
        } else {
            indep = decideIndependenceFromCutoff(fact, p);
        }

        // Create a result anchored to the wrapper’s decision; reuse base alpha if available
        double alphaUsed = recordOnly ? baseAlphaOr1() : getAlphaStarFor(fact);
        return new IndependenceResult(fact, indep, p, alphaUsed - p);
    }

    /* ===== IndependenceTest implementation ===== */

    @Override
    public List<Node> getVariables() {
        return base.getVariables();
    }

    @Override
    public DataModel getData() {
        return base.getData();
    }

    @Override
    public boolean isVerbose() {
        return base.isVerbose();
    }

    @Override
    public void setVerbose(boolean verbose) {
        base.setVerbose(verbose);
    }

    @Override
    public int getSampleSize() {
        return base.getSampleSize();
    }

    @Override
    public List<DataSet> getDataSets() {
        return base.getDataSets();
    }

    private boolean decideIndependenceFromCutoff(IndependenceFact fact, double p) {
        double aStar = getAlphaStarFor(fact);
        return p >= aStar;  // “large p” => accept independence
    }

    /* ===== Helpers ===== */

    private double getAlphaStarFor(IndependenceFact fact) {
        if (scope == Scope.GLOBAL) return (Double.isNaN(globalAlphaStar) ? 1.0 : globalAlphaStar);
        return alphaStarByZ.getOrDefault(fact.getZ().size(), 1.0);
    }

    private double baseAlphaOr1() {
        try {
            var m = base.getClass().getMethod("getAlpha");
            Object v = m.invoke(base);
            return (v instanceof Number) ? ((Number) v).doubleValue() : 1.0;
        } catch (Exception ignore) {
            return 1.0; // recording epoch: we’re just caching p’s
        }
    }

    /// / g is your FDR-consistent output (per this heuristic)

    public enum FdrMode {BH, BY}              // BH = Benjamini–Hochberg, BY = Benjamini–Yekutieli

    public enum Scope {GLOBAL, BY_COND_SET}   // single cutoff vs per-|Z| cutoff
}