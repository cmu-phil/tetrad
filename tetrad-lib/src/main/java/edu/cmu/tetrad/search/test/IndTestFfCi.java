package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.ffci_utils.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.*;

/**
 * FF-CI wrapper that delegates to the shared FF-CI/RCIT engine.
 *
 * Design intent:
 *  - This is the *general* feature-based CI test.
 *  - RCIT is treated as a preset/specialization of FF-CI.
 *  - All math lives in FfCiEngine.
 *  - Config is immutable; setters replace cfg with modified copies.
 */
public final class IndTestFfCi implements IndependenceTest, RowsSettable {

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

    public IndTestFfCi(DataSet dataSet) {
        this(dataSet, new Parameters());
    }

    public IndTestFfCi(DataSet dataSet, Parameters params) {
        this.data = Objects.requireNonNull(dataSet, "dataSet");
        this.variables = Collections.unmodifiableList(new ArrayList<>(dataSet.getVariables()));

        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < dataSet.getNumRows(); i++) rows.add(i);

//        edu.cmu.tetrad.search.test.RowsView rowsView = new edu.cmu.tetrad.search.test.RowsView(dataSet, rows);

        long seed = params.getLong(Params.SEED, 1729L);
//        this.state = new FfCiState(this.data, rowsView, new Random(seed));

        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < dataSet.getNumRows(); i++) all.add(i);
        this.allRows = Collections.unmodifiableList(all);

        this.rows = new ArrayList<>(this.allRows);

        edu.cmu.tetrad.search.test.RowsView rowsView = new edu.cmu.tetrad.search.test.RowsView(dataSet, this.rows);
        this.state = new FfCiState(this.data, rowsView, new Random(seed));

        this.cfg = FfCiPresets.authorSpec()
                .withSeed(seed)
                .withDoRcit(false);

        this.alpha = params.getDouble(Params.ALPHA, this.alpha);

        this.cfg = this.cfg.withLambda(Math.max(1e-12, params.getDouble(Params.KML_LAMBDA, this.cfg.lambda())));
        this.cfg = this.cfg.withNumFeatXY(Math.max(1, params.getInt(Params.RCIT_NUM_FEATURES_XY, this.cfg.numFeatXY())));
        this.cfg = this.cfg.withNumFeatZ(Math.max(1, params.getInt(Params.RCIT_NUM_FEATURES_Z, this.cfg.numFeatZ())));

        int B = Math.max(0, params.getInt(Params.RCIT_PERMUTATIONS, this.cfg.permutations()));
        if (B > 0) this.cfg = this.cfg.withPermutations(B).withApprox(PValueMethod.PERMUTATION);

        // approx index (1-based)
        int a = params.getInt(Params.RCIT_APPROX, 1) - 1;
        if (a >= 0 && a < PValueMethod.values().length) {
            this.cfg = this.cfg.withApprox(PValueMethod.values()[a]);
        }

        this.verbose = params.getBoolean(Params.VERBOSE, false);
    }

    // ------------------------------------------------------------
    // IndependenceTest
    // ------------------------------------------------------------

    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z)
            throws InterruptedException {

        IndependenceResult r =
                engine.test(state, cfg.withAlpha(alpha).withVerbose(verbose), x, y, z);

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
        if (alpha <= 0 || alpha >= 1) {
            throw new IllegalArgumentException("alpha in (0,1)");
        }
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
    // FF-CI knobs (immutable cfg updates)
    // ------------------------------------------------------------

    /** Feature count for X and Y. */
    public void setNumFeaturesXY(int d) {
        this.cfg = this.cfg.withNumFeatXY(Math.max(1, d));
    }

    /** Feature count for Z. */
    public void setNumFeaturesZ(int d) {
        this.cfg = this.cfg.withNumFeatZ(Math.max(1, d));
    }

    /** Choose feature map for X/Y (e.g., RFF or ORF). */
    public void setFeatureMapXY(FeatureMap fm) {
        this.cfg = this.cfg.withFeatureMapXY(fm);
    }

    /** Choose feature map for Z (e.g., RFF or ORF). */
    public void setFeatureMapZ(FeatureMap fm) {
        this.cfg = this.cfg.withFeatureMapZ(fm);
    }

    /** Toggle RCIT augmentation (Y ← [Y,Z]). */
    public void setDoRcit(boolean doRcit) {
        this.cfg = this.cfg.withDoRcit(doRcit);
    }

    /** Ridge added to Czz before inversion. */
    public void setLambda(double lambda) {
        this.cfg = this.cfg.withLambda(Math.max(1e-12, lambda));
    }

    /** Whether to z-score feature columns before covariance. */
    public void setCenterFeatures(boolean center) {
        this.cfg = this.cfg.withCenterFeatures(center);
    }

    /** Deterministic seed for features and permutations. */
    public void setSeed(long seed) {
        this.cfg = this.cfg.withSeed(seed);
    }

    /** Enable permutation p-values. */
    public void setPermutations(int b) {
        int B = Math.max(0, b);
        this.cfg = this.cfg.withPermutations(B)
                .withApprox(PValueMethod.PERMUTATION);
    }

    /** Choose analytic approximation. */
    public void setApproximation(PValueMethod approx) {
        Objects.requireNonNull(approx, "approx");
        this.cfg = this.cfg.withApprox(approx);
    }

    // ------------------------------------------------------------
    // Odds & ends
    // ------------------------------------------------------------

    @Override
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("Subset not implemented for IndTestFfCi.");
    }

    @Override
    public boolean determines(Set<Node> z, Node x) {
        return false;
    }

//    @Override
    public IndependenceResult checkIndependence(IndependenceFact fact)
            throws InterruptedException {
        return checkIndependence(fact.getX(), fact.getY(), fact.getZ());
    }
}