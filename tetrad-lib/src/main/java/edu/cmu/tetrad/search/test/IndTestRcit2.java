package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.ffci_utils.*;
import edu.cmu.tetrad.util.Parameters;

import java.util.*;

/**
 * RCIT wrapper that delegates to the shared FF-CI/RCIT engine.
 *
 * Design intent:
 *  - Keep a single implementation of the math (in FfCiEngine).
 *  - Keep this class as a thin adapter to Tetrad's IndependenceTest interface.
 *  - Config is treated as immutable; setters replace cfg with a modified copy.
 */
public final class IndTestRcit2 implements IndependenceTest, RowsSettable {

    // ---- core ----
    private final DataSet data;
    private final List<Node> variables;

    // ---- engine plumbing ----
    private FfCiState state;
    private final FfCiEngine engine = new FfCiEngine();

    // immutable baseline; setters create modified copies
    private FfCiConfig cfg;

    // IndependenceTest state
    private double alpha = 0.05;
    private double lastP = Double.NaN;
    private boolean verbose = false;

    // RowsSettable
    private List<Integer> rows = null;

    private final List<Integer> allRows;

    // ------------------------------------------------------------
    // ctors
    // ------------------------------------------------------------

    public IndTestRcit2(DataSet dataSet) {
        this(dataSet, new Parameters());
    }

    public IndTestRcit2(DataSet dataSet, Parameters params) {
        this.data = Objects.requireNonNull(dataSet, "dataSet");
        this.variables = Collections.unmodifiableList(new ArrayList<>(dataSet.getVariables()));

        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < dataSet.getNumRows(); i++) rows.add(i);

        // RowsView: you likely already have a concrete implementation. If RowsView is an interface,
        // use your implementation here (e.g., new DefaultRowsView()).
//        edu.cmu.tetrad.search.test.RowsView rowsView = new edu.cmu.tetrad.search.test.RowsView(dataSet,
//                rows);

        long seed = params.getLong("rcit.seed", 1729L);

        // Build state (uses base RNG but engine should derive deterministic per-call RNG from cfg.seed + keys)
//        this.state = new FfCiState(this.data, rowsView, new Random(seed));

        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < dataSet.getNumRows(); i++) all.add(i);
        this.allRows = Collections.unmodifiableList(all);

        this.rows = new ArrayList<>(this.allRows);

        edu.cmu.tetrad.search.test.RowsView rowsView = new edu.cmu.tetrad.search.test.RowsView(dataSet, this.rows);
        this.state = new FfCiState(this.data, rowsView, new Random(seed));

        // Baseline config: "author spec" (immutable)
        this.cfg = RcitPresets.authorSpec().withSeed(seed);

        // honor a couple common params if you want (optional, safe defaults)
        this.alpha = params.getDouble("alpha", this.alpha);
        this.cfg = this.cfg.withLambda(Math.max(1e-12, params.getDouble("rcit.lambda", this.cfg.lambda())));
        this.cfg = this.cfg.withCenterFeatures(params.getBoolean("rcit.centerFeatures", this.cfg.centerFeatures()));
        this.cfg = this.cfg.withBandwidth(new BandwidthPolicy.MedianHeuristic(500, 1000, 1.0));
    }

    // ------------------------------------------------------------
    // IndependenceTest
    // ------------------------------------------------------------

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        IndependenceResult r = engine.test(state, cfg.withAlpha(alpha).withVerbose(verbose), x, y, z);
        this.lastP = r.getPValue();
        return r;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public DataSet getData() {
        return data;
    }

    @Override
    public double getAlpha() {
        return alpha;
    }

    @Override
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha in (0,1)");
        this.alpha = alpha;
    }

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public double getPValue() {
        return lastP;
    }

    // ------------------------------------------------------------
    // RowsSettable
    // ------------------------------------------------------------

    @Override
    public List<Integer> getRows() {
        return rows;
    }

    @Override
    public void setRows(List<Integer> rows) {
        // normalize: null => all rows
        List<Integer> use = (rows == null) ? this.allRows : rows;

        // validate
        for (int i = 0; i < use.size(); i++) {
            Integer r = use.get(i);
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0) throw new IllegalArgumentException("Row " + i + " is negative.");
            if (r >= data.getNumRows()) throw new IllegalArgumentException("Row " + i + " out of bounds: " + r);
        }

        // store a private copy (important if caller mutates their list)
        this.rows = new ArrayList<>(use);

        // IMPORTANT: rebuild RowsView and State so the engine actually uses the new rows
        edu.cmu.tetrad.search.test.RowsView rowsView = new edu.cmu.tetrad.search.test.RowsView(this.data, this.rows);

        // preserve determinism: keep using cfg.seed() (or your existing seed source)
        this.state = new FfCiState(this.data, rowsView, new Random(cfg.seed()));
    }

    // ------------------------------------------------------------
    // “author-approved” knobs (immutable cfg updates)
    // ------------------------------------------------------------

    /** RCIT vs RCoT (true = RCIT). */
    public void setDoRcit(boolean doRcit) {
        this.cfg = this.cfg.withDoRcit(doRcit);
    }

    public void setNumFeaturesXY(int d) {
        this.cfg = this.cfg.withNumFeatXY(Math.max(1, d));
    }

    public void setNumFeaturesZ(int d) {
        this.cfg = this.cfg.withNumFeatZ(Math.max(1, d));
    }

    /** Ridge added to Czz before inversion. */
    public void setLambda(double lambda) {
        this.cfg = this.cfg.withLambda(Math.max(1e-12, lambda));
    }

    /** Whether to z-score the RFF features prior to covariance. */
    public void setCenterFeatures(boolean center) {
        this.cfg = this.cfg.withCenterFeatures(center);
    }

    /** Deterministic seed for feature generation / permutations. */
    public void setSeed(long seed) {
        this.cfg = this.cfg.withSeed(seed);
        // state.rng is not mutated; engine derives deterministic per-call RNG from cfg.seed().
        // Keeping state.rng as-is is fine.
    }

    /** Enable permutation p-values (and set # permutations). */
    public void setPermutations(int b) {
        int B = Math.max(0, b);
        this.cfg = this.cfg.withPermutations(B).withApprox(PValueMethod.PERMUTATION);
    }

    /** Choose analytic approximation (Gamma / saddlepoint / Davies). */
    public void setApproximation(PValueMethod approx) {
        Objects.requireNonNull(approx, "approx");
        this.cfg = this.cfg.withApprox(approx);
    }

    // ------------------------------------------------------------
    // Optional: Tetrad API odds & ends
    // ------------------------------------------------------------

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        // If your engine/state support variable-subsetting, implement it.
        // Conservative default: return a new test over same data but with getVariables() limited.
        // Many Tetrad tests just return "this" or throw UnsupportedOperationException.
        throw new UnsupportedOperationException("Subset not implemented for IndTestRcit.");
    }

    @Override
    public boolean determines(Set<Node> z, Node x) {
        // RCIT doesn't have deterministic constraints in the linear algebra sense.
        return false;
    }

//    @Override
//    public double getScore() {
//        // If your IndependenceTest interface has this; many implementations return NaN.
//        return Double.NaN;
//    }

//    @Override
    public IndependenceResult checkIndependence(IndependenceFact fact) throws InterruptedException {
        return checkIndependence(fact.getX(), fact.getY(), fact.getZ());
    }
}