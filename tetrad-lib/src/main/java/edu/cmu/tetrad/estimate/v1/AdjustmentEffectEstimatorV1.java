// v1: Drop-in skeleton for adjustment-based effect estimation on mixed (continuous+discrete) data.
// v1: Binary treatment X (2-category discrete) and continuous outcome Y.
// v1: Outcome model = OLS with mixed-feature basis + X*features interactions.
// v1: Propensity model = logistic regression via IRLS on mixed-feature basis.
// v1: Estimator = OR + AIPW/DR, inference via nonparametric bootstrap.
//
// Dependencies (v1): EJML SimpleMatrix (org.ejml:simple). Tetrad already often bundles EJML.
// If your build uses a different linear algebra layer, swap the SimpleMatrix parts.
//
// Package name is illustrative; adjust to your project layout.
package edu.cmu.tetrad.estimate.v1;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;
import org.ejml.simple.SimpleMatrix;

import java.io.Serial;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import edu.cmu.tetrad.regression.v1.RegressionUtilV1;


public final class AdjustmentEffectEstimatorV1 {

    // =========================
    // v1: Public API
    // =========================

    /** v1: Configuration knobs with conservative defaults. */
    public static final class ConfigV1 implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;

        // v1: basis degree for continuous covariates
        public int basisDegree = 3;

        // v1: include X*phi(Z) interactions in outcome regression (recommended)
        public boolean includeTreatmentInteractions = true;

        // v1: propensity clipping epsilon
        public double propensityClipEps = 0.01;

        // v1: bootstrap samples
        public int bootstrapB = 200;

        // v1: CI alpha (two-sided)
        public double ciAlpha = 0.05;

        // v1: ridge stabilization (tiny) for OLS / IRLS. Set 0 to disable.
        public double ridge = 1e-8;

        // v1: IRLS iterations / tolerance
        public int maxIrlsIter = 50;
        public double irlsTol = 1e-8;

        // v1: winsorize for min/max scaling to [-1,1] (0 disables)
        public double winsorFrac = 0.01;
    }

    /** v1: Result object returned to UI/table. */
    public static final class EffectEstimateResultV1 implements TetradSerializable {
        @Serial
        private static final long serialVersionUID = 23L;

        public final List<String> adjustmentSet;
        public final double ateOr;
        public final double ateDr;
        public final double seOrBoot;
        public final double seDrBoot;
        public final double ciLoOr, ciHiOr;
        public final double ciLoDr, ciHiDr;

        // v1: diagnostics
        public final double minProp, maxProp;
        public final double fracClipped;
        public final int n;

        private EffectEstimateResultV1(
                List<String> adjustmentSet,
                double ateOr, double ateDr,
                double seOrBoot, double seDrBoot,
                double ciLoOr, double ciHiOr,
                double ciLoDr, double ciHiDr,
                double minProp, double maxProp, double fracClipped,
                int n
        ) {
            this.adjustmentSet = adjustmentSet;
            this.ateOr = ateOr;
            this.ateDr = ateDr;
            this.seOrBoot = seOrBoot;
            this.seDrBoot = seDrBoot;
            this.ciLoOr = ciLoOr;
            this.ciHiOr = ciHiOr;
            this.ciLoDr = ciLoDr;
            this.ciHiDr = ciHiDr;
            this.minProp = minProp;
            this.maxProp = maxProp;
            this.fracClipped = fracClipped;
            this.n = n;
        }
    }

    /**
     * v1: Estimate ATE using a provided adjustment set (already found by RA, Perkovic criterion, etc.).
     *
     * @param data v1: mixed dataset (continuous + discrete covariates allowed)
     * @param x    v1: binary treatment variable node (must be DiscreteVariable with 2 categories)
     * @param y    v1: continuous outcome variable node
     * @param z    v1: adjustment set nodes (mixed allowed)
     * @param cfg  v1: configuration
     */
    public static EffectEstimateResultV1 estimateAteV1(DataSet data, Node x, Node y, Set<Node> z, ConfigV1 cfg) {
        Objects.requireNonNull(data, "v1: data");
        Objects.requireNonNull(x, "v1: x");
        Objects.requireNonNull(y, "v1: y");
        Objects.requireNonNull(z, "v1: z");
        Objects.requireNonNull(cfg, "v1: cfg");

        // v1: extract (X,Y,Z) with row-wise complete cases only (simple v1 choice).
        ExtractedV1 ex = ExtractedV1.fromDataSet(data, x, y, z);

        if (ex.n < 10) {
            throw new IllegalArgumentException("v1: Too few complete cases after filtering missingness: n=" + ex.n);
        }

        // v1: feature builder fitted on this dataset (scaling params, one-hot maps, etc.)
        MixedFeatureBuilderV1 fb = new MixedFeatureBuilderV1(cfg);
        fb.fit(ex);

        // v1: fit models + compute point estimates
        OutcomeModelV1 om = new OutcomeModelV1(cfg);
        PropensityModelV1 pm = new PropensityModelV1(cfg);

        PointEstimatesV1 pe = computePointEstimatesV1(ex, fb, om, pm, cfg);

        // v1: bootstrap inference
        BootstrapSummaryV1 bs = bootstrapV1(ex, fb, om, pm, cfg);

        List<String> zNames = z.stream().map(Node::getName).sorted().collect(Collectors.toList());
        return new EffectEstimateResultV1(
                zNames,
                pe.ateOr, pe.ateDr,
                bs.seOr, bs.seDr,
                bs.ciLoOr, bs.ciHiOr,
                bs.ciLoDr, bs.ciHiDr,
                pe.minProp, pe.maxProp, pe.fracClipped,
                ex.n
        );
    }

    // =========================
    // v1: Core computation
    // =========================

    private static final class PointEstimatesV1 {
        final double ateOr, ateDr;
        final double minProp, maxProp, fracClipped;

        PointEstimatesV1(double ateOr, double ateDr, double minProp, double maxProp, double fracClipped) {
            this.ateOr = ateOr;
            this.ateDr = ateDr;
            this.minProp = minProp;
            this.maxProp = maxProp;
            this.fracClipped = fracClipped;
        }
    }

    private static PointEstimatesV1 computePointEstimatesV1(
            ExtractedV1 ex,
            MixedFeatureBuilderV1 fb,
            OutcomeModelV1 om,
            PropensityModelV1 pm,
            ConfigV1 cfg
    ) {
        // v1: build phi(Z)
        SimpleMatrix phi = fb.transformZ(ex); // n x p

        // v1: fit outcome model m(x,z)
        OutcomeModelV1.Fit fitOm = om.fit(ex, phi);

        // v1: predicted m(1,Z_i) and m(0,Z_i)
        double[] m1 = fitOm.predict(ex, phi, 1);
        double[] m0 = fitOm.predict(ex, phi, 0);

        double ateOr = mean(diff(m1, m0));

        // v1: fit propensity e(z)
        PropensityModelV1.Fit fitPm = pm.fit(ex, phi);
        double[] e = fitPm.predictProb(phi);

        // v1: clip and diagnostics
        double minE = Double.POSITIVE_INFINITY, maxE = Double.NEGATIVE_INFINITY;
        int clipped = 0;
        double[] eClip = new double[ex.n];
        for (int i = 0; i < ex.n; i++) {
            double ei = e[i];
            minE = Math.min(minE, ei);
            maxE = Math.max(maxE, ei);
            double c = clip(ei, cfg.propensityClipEps, 1.0 - cfg.propensityClipEps);
            if (c != ei) clipped++;
            eClip[i] = c;
        }
        double fracClipped = clipped / (double) ex.n;

        // v1: AIPW / DR
        double sum = 0.0;
        for (int i = 0; i < ex.n; i++) {
            int xi = ex.x01[i];
            double yi = ex.y[i];

            double term = (m1[i] - m0[i]);
            if (xi == 1) {
                term += (yi - m1[i]) / eClip[i];
            } else {
                term -= (yi - m0[i]) / (1.0 - eClip[i]);
            }
            sum += term;
        }
        double ateDr = sum / ex.n;

        return new PointEstimatesV1(ateOr, ateDr, minE, maxE, fracClipped);
    }

    // =========================
    // v1: Bootstrap
    // =========================

    private static final class BootstrapSummaryV1 {
        final double seOr, seDr;
        final double ciLoOr, ciHiOr;
        final double ciLoDr, ciHiDr;

        BootstrapSummaryV1(double seOr, double seDr, double ciLoOr, double ciHiOr, double ciLoDr, double ciHiDr) {
            this.seOr = seOr;
            this.seDr = seDr;
            this.ciLoOr = ciLoOr;
            this.ciHiOr = ciHiOr;
            this.ciLoDr = ciLoDr;
            this.ciHiDr = ciHiDr;
        }
    }

    private static BootstrapSummaryV1 bootstrapV1(
            ExtractedV1 ex,
            MixedFeatureBuilderV1 fb,
            OutcomeModelV1 om,
            PropensityModelV1 pm,
            ConfigV1 cfg
    ) {
        int B = Math.max(0, cfg.bootstrapB);
        if (B == 0) {
            return new BootstrapSummaryV1(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }

        double[] or = new double[B];
        double[] dr = new double[B];

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // v1: bootstrap by resampling rows with replacement; refit models each time.
        for (int b = 0; b < B; b++) {
            int[] idx = new int[ex.n];
            for (int i = 0; i < ex.n; i++) idx[i] = rng.nextInt(ex.n);

            ExtractedV1 bx = ex.resample(idx);

            // v1: IMPORTANT: fit feature builder on bootstrap sample (simplest choice).
            MixedFeatureBuilderV1 bfb = new MixedFeatureBuilderV1(cfg);
            bfb.fit(bx);
//            SimpleMatrix phi = bfb.transformZ(bx);

            PointEstimatesV1 pe = computePointEstimatesV1(bx, bfb, om, pm, cfg);
            or[b] = pe.ateOr;
            dr[b] = pe.ateDr;
        }

        double seOr = sd(or);
        double seDr = sd(dr);

        double alpha = cfg.ciAlpha;
        double loQ = alpha / 2.0;
        double hiQ = 1.0 - alpha / 2.0;

        double ciLoOr = quantile(or, loQ);
        double ciHiOr = quantile(or, hiQ);
        double ciLoDr = quantile(dr, loQ);
        double ciHiDr = quantile(dr, hiQ);

        return new BootstrapSummaryV1(seOr, seDr, ciLoOr, ciHiOr, ciLoDr, ciHiDr);
    }

    // =========================
    // v1: Data extraction
    // =========================

    /** v1: Extracted complete-case arrays for X (binary), Y (continuous), Z (mixed). */
    private static final class ExtractedV1 {
        final int n;
        final int[] x01;          // v1: treatment coded 0/1
        final double[] y;         // v1: outcome
        final List<ZColumnV1> zCols; // v1: covariates, each column knows if discrete/continuous

        private ExtractedV1(int n, int[] x01, double[] y, List<ZColumnV1> zCols) {
            this.n = n;
            this.x01 = x01;
            this.y = y;
            this.zCols = zCols;
        }

        static ExtractedV1 fromDataSet(DataSet data, Node x, Node y, Set<Node> z) {
            int xCol = data.getColumn(x);
            int yCol = data.getColumn(y);

            // v1: enforce binary discrete X
            if (!(x instanceof DiscreteVariable dx)) {
                throw new IllegalArgumentException("v1: Treatment X must be a 2-category DiscreteVariable.");
            }
            if (dx.getNumCategories() != 2) {
                throw new IllegalArgumentException("v1: Treatment X must have exactly 2 categories; got " + dx.getNumCategories());
            }

            // v1: collect Z columns in stable order
            List<Node> zList = z.stream().sorted(Comparator.comparing(Node::getName)).toList();
            List<ZColumnV1> zCols = new ArrayList<>(zList.size());
            for (Node zi : zList) {
                int col = data.getColumn(zi);
                if (zi instanceof DiscreteVariable dz) {
                    zCols.add(ZColumnV1.discrete(zi.getName(), col, dz.getNumCategories()));
                } else {
                    zCols.add(ZColumnV1.continuous(zi.getName(), col));
                }
            }

            // v1: complete case filtering (any missing in X,Y,Z -> drop row)
            List<Integer> keep = new ArrayList<>();
            for (int r = 0; r < data.getNumRows(); r++) {
                if (isMissing(data, r, xCol)) continue;
                if (isMissing(data, r, yCol)) continue;
                boolean miss = false;
                for (ZColumnV1 c : zCols) {
                    if (isMissing(data, r, c.dataCol)) {
                        miss = true;
                        break;
                    }
                }
                if (!miss) keep.add(r);
            }

            int n = keep.size();
            int[] x01 = new int[n];
            double[] yy = new double[n];

            // v1: store Z in column-wise arrays for faster feature building
//            List<ZColumnV1> built = zCols.stream().map(ZColumnV1::emptyCopyForN).collect(Collectors.toList());
            // v1: allocate arrays for the kept rows
            List<ZColumnV1> built = zCols.stream()
                    .map(c -> c.emptyCopyForN(n))
                    .collect(Collectors.toList());

            for (int i = 0; i < n; i++) {
                int r = keep.get(i);

                // v1: X is stored as int category index in Tetrad DataSet for discrete
                int xv = data.getInt(r, xCol);
                // v1: map category 0->0, 1->1 (if you prefer named mapping, add it here)
                x01[i] = (xv == 0) ? 0 : 1;

                yy[i] = data.getDouble(r, yCol);

                for (int j = 0; j < built.size(); j++) {
                    ZColumnV1 col = built.get(j);
                    if (col.isDiscrete) {
                        col.discreteVals[i] = data.getInt(r, col.dataCol);
                    } else {
                        col.continuousVals[i] = data.getDouble(r, col.dataCol);
                    }
                }
            }

            return new ExtractedV1(n, x01, yy, built);
        }

        ExtractedV1 resample(int[] idx) {
            int n2 = idx.length;
            int[] x2 = new int[n2];
            double[] y2 = new double[n2];
            List<ZColumnV1> z2 = this.zCols.stream().map(c -> c.emptyCopyForN(n2)).collect(Collectors.toList());

            for (int i = 0; i < n2; i++) {
                int s = idx[i];
                x2[i] = this.x01[s];
                y2[i] = this.y[s];
                for (int j = 0; j < z2.size(); j++) {
                    ZColumnV1 dst = z2.get(j);
                    ZColumnV1 src = this.zCols.get(j);
                    if (dst.isDiscrete) dst.discreteVals[i] = src.discreteVals[s];
                    else dst.continuousVals[i] = src.continuousVals[s];
                }
            }
            return new ExtractedV1(n2, x2, y2, z2);
        }

        private static boolean isMissing(DataSet data, int row, int col) {
            // v1: Tetrad uses NaN for missing continuous; for discrete it often uses -99 or similar
            // but DataSet provides isMissing() in some versions. If you have it, replace this logic.
            Node v = data.getVariable(col);
            if (v instanceof DiscreteVariable) {
                int val = data.getInt(row, col);
                return val < 0;
            } else {
                double val = data.getDouble(row, col);
                return Double.isNaN(val);
            }
        }
    }

    /** v1: Represents one covariate column Z_j. */
    private static final class ZColumnV1 {
        final String name;
        final int dataCol;
        final boolean isDiscrete;
        final int numCategories;     // v1: if discrete
        int[] discreteVals;          // v1: length n
        double[] continuousVals;     // v1: length n

        private ZColumnV1(String name, int dataCol, boolean isDiscrete, int numCategories) {
            this.name = name;
            this.dataCol = dataCol;
            this.isDiscrete = isDiscrete;
            this.numCategories = numCategories;
        }

        static ZColumnV1 discrete(String name, int dataCol, int k) {
            return new ZColumnV1(name, dataCol, true, k);
        }

        static ZColumnV1 continuous(String name, int dataCol) {
            return new ZColumnV1(name, dataCol, false, 0);
        }

        ZColumnV1 emptyCopyForN(int n) {
            ZColumnV1 c = new ZColumnV1(name, dataCol, isDiscrete, numCategories);
            if (isDiscrete) c.discreteVals = new int[n];
            else c.continuousVals = new double[n];
            return c;
        }

        // v1: Create an empty copy with the same length as the current column.
        ZColumnV1 emptyCopyForN() {
            int n;
            if (isDiscrete) {
                if (discreteVals == null) {
                    throw new IllegalStateException("v1: discreteVals is null; cannot infer length.");
                }
                n = discreteVals.length;
            } else {
                if (continuousVals == null) {
                    throw new IllegalStateException("v1: continuousVals is null; cannot infer length.");
                }
                n = continuousVals.length;
            }
            return emptyCopyForN(n);
        }
    }

    // =========================
    // v1: Mixed feature builder
    // =========================

    /**
     * v1: Builds phi(Z) with:
     *  - discrete Z: one-hot (k-1) with baseline category 0
     *  - continuous Z: Legendre basis degree 1..t after scaling to [-1,1]
     */
    private static final class MixedFeatureBuilderV1 {
        private final ConfigV1 cfg;

        // v1: per-continuous column scaling params
        private final Map<String, ScalingV1> scaling = new HashMap<>();

        // v1: per-discrete column category count
        private final Map<String, Integer> discK = new HashMap<>();

        // v1: feature layout (column ranges)
        private List<FeatSpecV1> specs = List.of();
        private int p = 0;

        MixedFeatureBuilderV1(ConfigV1 cfg) {
            this.cfg = cfg;
        }

        void fit(ExtractedV1 ex) {
            List<FeatSpecV1> s = new ArrayList<>();
            int colStart = 0;

            for (ZColumnV1 zc : ex.zCols) {
                if (zc.isDiscrete) {
                    int k = zc.numCategories;
                    discK.put(zc.name, k);

                    int cols = Math.max(0, k - 1); // v1: baseline 0
                    s.add(FeatSpecV1.discrete(zc.name, colStart, cols, k));
                    colStart += cols;
                } else {
                    ScalingV1 sc = ScalingV1.fit(zc.continuousVals, cfg.winsorFrac);
                    scaling.put(zc.name, sc);

                    int cols = Math.max(0, cfg.basisDegree); // v1: degrees 1..t
                    s.add(FeatSpecV1.continuous(zc.name, colStart, cols, cfg.basisDegree));
                    colStart += cols;
                }
            }

            this.specs = s;
            this.p = colStart;
        }

        /** v1: Returns n x p feature matrix for Z only (no intercept). */
        SimpleMatrix transformZ(ExtractedV1 ex) {
            if (p == 0) return new SimpleMatrix(ex.n, 0);
            SimpleMatrix Phi = new SimpleMatrix(ex.n, p);

            // v1: build by iterating columns
            Map<String, ZColumnV1> map = ex.zCols.stream().collect(Collectors.toMap(c -> c.name, c -> c));

            for (FeatSpecV1 sp : specs) {
                ZColumnV1 zc = map.get(sp.name);
                if (sp.isDiscrete) {
                    // v1: one-hot excluding baseline 0
                    for (int i = 0; i < ex.n; i++) {
                        int v = zc.discreteVals[i];
                        if (v <= 0) continue; // baseline or invalid
                        int jj = v - 1;
                        if (jj < sp.width) {
                            Phi.set(i, sp.start + jj, 1.0);
                        }
                    }
                } else {
                    ScalingV1 sc = scaling.get(sp.name);
                    int t = sp.degree;
                    for (int i = 0; i < ex.n; i++) {
                        double x = sc.toMinusOneToOne(zc.continuousVals[i]);
                        // v1: Legendre P1..Pt
                        double[] P = legendreUpTo(t, x);
                        for (int d = 1; d <= t; d++) {
                            Phi.set(i, sp.start + (d - 1), P[d]);
                        }
                    }
                }
            }

            return Phi;
        }

        private static final class FeatSpecV1 {
            final String name;
            final int start;
            final int width;
            final boolean isDiscrete;
            final int k;       // v1: for discrete
            final int degree;  // v1: for continuous

            private FeatSpecV1(String name, int start, int width, boolean isDiscrete, int k, int degree) {
                this.name = name;
                this.start = start;
                this.width = width;
                this.isDiscrete = isDiscrete;
                this.k = k;
                this.degree = degree;
            }

            static FeatSpecV1 discrete(String name, int start, int width, int k) {
                return new FeatSpecV1(name, start, width, true, k, 0);
            }

            static FeatSpecV1 continuous(String name, int start, int width, int degree) {
                return new FeatSpecV1(name, start, width, false, 0, degree);
            }
        }

        /** v1: Scaling for mapping to [-1,1] using winsorized min/max. */
        private static final class ScalingV1 {
            final double lo;
            final double hi;

            private ScalingV1(double lo, double hi) {
                this.lo = lo;
                this.hi = hi;
            }

            static ScalingV1 fit(double[] v, double winsorFrac) {
                double[] copy = Arrays.copyOf(v, v.length);
                Arrays.sort(copy);

                int n = copy.length;
                int k = (winsorFrac <= 0) ? 0 : (int) Math.floor(winsorFrac * n);
                int loIdx = Math.min(Math.max(0, k), n - 1);
                int hiIdx = Math.min(Math.max(0, n - 1 - k), n - 1);

                double lo = copy[loIdx];
                double hi = copy[hiIdx];
                if (!(hi > lo)) {
                    // v1: constant column fallback
                    lo = lo - 1.0;
                    hi = hi + 1.0;
                }
                return new ScalingV1(lo, hi);
            }

            double toMinusOneToOne(double x) {
                double c = clip(x, lo, hi);
                double t = (c - lo) / (hi - lo); // [0,1]
                return 2.0 * t - 1.0;            // [-1,1]
            }
        }

        /** v1: returns array P[0..t] (Legendre) at x in [-1,1]. */
        private static double[] legendreUpTo(int t, double x) {
            double[] P = new double[t + 1];
            P[0] = 1.0;
            if (t >= 1) P[1] = x;
            for (int n = 2; n <= t; n++) {
                P[n] = ((2.0 * n - 1.0) * x * P[n - 1] - (n - 1.0) * P[n - 2]) / n;
            }
            return P;
        }
    }

    // =========================
    // v1: Outcome regression model (OLS)
    // =========================

    private static final class OutcomeModelV1 {
        private final ConfigV1 cfg;

        OutcomeModelV1(ConfigV1 cfg) {
            this.cfg = cfg;
        }

        static final class Fit {
            final SimpleMatrix beta;      // v1: coefficients
            final int pPhi;               // v1: dimension of phi(Z)
            final boolean withInteractions;

            Fit(SimpleMatrix beta, int pPhi, boolean withInteractions) {
                this.beta = beta;
                this.pPhi = pPhi;
                this.withInteractions = withInteractions;
            }

            /** v1: predicts m(x,z) for each row with treatment xFixed in {0,1}. */
            double[] predict(ExtractedV1 ex, SimpleMatrix phi, int xFixed) {
                int n = ex.n;
                int p = designWidth(pPhi, withInteractions);
                if (phi.numRows() != n) throw new IllegalArgumentException("v1: phi row mismatch");
                double[] out = new double[n];

                for (int i = 0; i < n; i++) {
                    int col = 0;
                    double s = 0.0;

                    // intercept
                    s += beta.get(col++, 0);

                    // X
                    s += beta.get(col++, 0) * xFixed;

                    // phi(Z)
                    for (int j = 0; j < pPhi; j++) {
                        s += beta.get(col++, 0) * phi.get(i, j);
                    }

                    // X * phi(Z)
                    if (withInteractions) {
                        for (int j = 0; j < pPhi; j++) {
                            s += beta.get(col++, 0) * (xFixed * phi.get(i, j));
                        }
                    }

                    out[i] = s;
                }

                return out;
            }

            private static int designWidth(int pPhi, boolean withInteractions) {
                return 2 + pPhi + (withInteractions ? pPhi : 0); // intercept+X + phi + Xphi
            }
        }

        Fit fit(ExtractedV1 ex, SimpleMatrix phi) {
            int n = ex.n;
            int pPhi = phi.numCols();
            boolean inter = cfg.includeTreatmentInteractions;

            int p = 2 + pPhi + (inter ? pPhi : 0);

            // v1: build design matrix Xmat (n x p) and response y (n x 1)
            SimpleMatrix Xmat = new SimpleMatrix(n, p);
            SimpleMatrix y = new SimpleMatrix(n, 1);

            for (int i = 0; i < n; i++) {
                int col = 0;
                Xmat.set(i, col++, 1.0);           // intercept
                Xmat.set(i, col++, ex.x01[i]);     // treatment

                for (int j = 0; j < pPhi; j++) Xmat.set(i, col++, phi.get(i, j));

                if (inter) {
                    for (int j = 0; j < pPhi; j++) Xmat.set(i, col++, ex.x01[i] * phi.get(i, j));
                }

                y.set(i, 0, ex.y[i]);
            }

//            // v1: ridge-stabilized normal equation solve: (X'X + λI)β = X'y
//            SimpleMatrix XtX = Xmat.transpose().mult(Xmat);
//            if (cfg.ridge > 0) {
//                for (int j = 0; j < p; j++) XtX.set(j, j, XtX.get(j, j) + cfg.ridge);
//            }
//            SimpleMatrix Xty = Xmat.transpose().mult(y);
//            SimpleMatrix beta = XtX.solve(Xty);

            // v1: QR-based least squares (more stable than normal equations)
//            SimpleMatrix beta = Xmat.solve(y);

            // v1: QR-based OLS
//            var fit = edu.cmu.tetrad.regression.v1.RegressionUtilV1.olsFitV1(Xmat, y);
            var fit = RegressionUtilV1.olsFitV1(Xmat, y);
            SimpleMatrix beta = fit.beta;
            return new Fit(beta, pPhi, inter);
        }
    }

    // =========================
    // v1: Propensity model (logistic via IRLS)
    // =========================

    private static final class PropensityModelV1 {
        private final ConfigV1 cfg;

        PropensityModelV1(ConfigV1 cfg) {
            this.cfg = cfg;
        }

        static final class Fit {
            final SimpleMatrix w; // v1: coefficients for [intercept, phi...]

            Fit(SimpleMatrix w) {
                this.w = w;
            }

            double[] predictProb(SimpleMatrix phi) {
                int n = phi.numRows();
                int pPhi = phi.numCols();
                double[] out = new double[n];
                for (int i = 0; i < n; i++) {
                    double eta = w.get(0, 0);
                    for (int j = 0; j < pPhi; j++) eta += w.get(1 + j, 0) * phi.get(i, j);
                    out[i] = sigmoid(eta);
                }
                return out;
            }
        }

//        Fit fit(ExtractedV1 ex, SimpleMatrix phi) {
//            int n = ex.n;
//            int pPhi = phi.numCols();
//            int p = 1 + pPhi; // intercept + phi
//
//            // v1: initialize coefficients to 0
//            SimpleMatrix w = new SimpleMatrix(p, 1);
//
//            // v1: IRLS loop
//            double prevLl = Double.NEGATIVE_INFINITY;
//
//            for (int iter = 0; iter < cfg.maxIrlsIter; iter++) {
//                // v1: compute p_i, weights, and working response
//                double[] pHat = new double[n];
//                double[] wgt = new double[n];
//                double[] z = new double[n];
//
//                double ll = 0.0;
//
//                for (int i = 0; i < n; i++) {
//                    double eta = w.get(0, 0);
//                    for (int j = 0; j < pPhi; j++) eta += w.get(1 + j, 0) * phi.get(i, j);
//                    double pi = sigmoid(eta);
//
//                    // v1: stabilize
//                    pi = clip(pi, 1e-6, 1.0 - 1e-6);
//
//                    pHat[i] = pi;
//                    double vi = pi * (1.0 - pi);
//                    wgt[i] = Math.max(vi, 1e-9);
//
//                    int xi = ex.x01[i];
//                    z[i] = eta + (xi - pi) / wgt[i];
//
//                    ll += xi * Math.log(pi) + (1 - xi) * Math.log(1 - pi);
//                }
//
//                if (Math.abs(ll - prevLl) < cfg.irlsTol) break;
//                prevLl = ll;
//
//                // v1: solve weighted least squares for w
//                // minimize sum_i wgt_i (z_i - [1,phi_i]w)^2
//                SimpleMatrix XtWX = new SimpleMatrix(p, p);
//                SimpleMatrix XtWz = new SimpleMatrix(p, 1);
//
//                for (int i = 0; i < n; i++) {
//                    double wi = wgt[i];
//
//                    // row vector r = [1, phi_i]
//                    // accumulate XtWX += wi * r^T r
//                    // accumulate XtWz += wi * r^T z
//                    double r0 = 1.0;
//
//                    XtWX.set(0, 0, XtWX.get(0, 0) + wi * r0 * r0);
//                    XtWz.set(0, 0, XtWz.get(0, 0) + wi * r0 * z[i]);
//
//                    for (int a = 0; a < pPhi; a++) {
//                        double ra = phi.get(i, a);
//                        int ia = 1 + a;
//
//                        XtWX.set(0, ia, XtWX.get(0, ia) + wi * r0 * ra);
//                        XtWX.set(ia, 0, XtWX.get(ia, 0) + wi * ra * r0);
//
//                        XtWz.set(ia, 0, XtWz.get(ia, 0) + wi * ra * z[i]);
//
//                        for (int b = 0; b < pPhi; b++) {
//                            double rb = phi.get(i, b);
//                            int ib = 1 + b;
//                            XtWX.set(ia, ib, XtWX.get(ia, ib) + wi * ra * rb);
//                        }
//                    }
//                }
//
//                // v1: ridge stabilization
//                if (cfg.ridge > 0) {
//                    for (int j = 0; j < p; j++) XtWX.set(j, j, XtWX.get(j, j) + cfg.ridge);
//                }
//
//                w = XtWX.solve(XtWz);
//            }
//
//            return new Fit(w);
//        }

        Fit fit(ExtractedV1 ex, SimpleMatrix phi) {
            int n = ex.n;
            int pPhi = phi.numCols();

            // v1: build Xlogit = [1, phi]
            SimpleMatrix Xlogit = new SimpleMatrix(n, 1 + pPhi);
            for (int i = 0; i < n; i++) {
                Xlogit.set(i, 0, 1.0);
                for (int j = 0; j < pPhi; j++) {
                    Xlogit.set(i, 1 + j, phi.get(i, j));
                }
            }

            // v1: fit logistic regression via IRLS
            var fit = edu.cmu.tetrad.regression.v1.RegressionUtilV1.logitFitIrlsV1(
                    Xlogit, ex.x01, cfg.maxIrlsIter, cfg.irlsTol, cfg.ridge
            );

            // v1: store coefficients (intercept + phi coefs)
            return new Fit(fit.w);
        }
    }

    // =========================
    // v1: Math helpers
    // =========================

    private static double sigmoid(double x) {
        if (x >= 0) {
            double z = Math.exp(-x);
            return 1.0 / (1.0 + z);
        } else {
            double z = Math.exp(x);
            return z / (1.0 + z);
        }
    }

    private static double clip(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double[] diff(double[] a, double[] b) {
        double[] d = new double[a.length];
        for (int i = 0; i < a.length; i++) d[i] = a[i] - b[i];
        return d;
    }

    private static double mean(double[] v) {
        double s = 0.0;
        for (double x : v) s += x;
        return s / v.length;
    }

    private static double sd(double[] v) {
        double m = mean(v);
        double s2 = 0.0;
        for (double x : v) {
            double d = x - m;
            s2 += d * d;
        }
        return Math.sqrt(s2 / Math.max(1, v.length - 1));
    }

    private static double quantile(double[] v, double q) {
        double[] copy = Arrays.copyOf(v, v.length);
        Arrays.sort(copy);
        if (q <= 0) return copy[0];
        if (q >= 1) return copy[copy.length - 1];
        double pos = q * (copy.length - 1);
        int i = (int) Math.floor(pos);
        int j = Math.min(copy.length - 1, i + 1);
        double t = pos - i;
        return (1 - t) * copy[i] + t * copy[j];
    }
}