///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TMath;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

/**
 * Basis-expanded Generalised Covariance Measure (GCM) conditional independence test.
 * <p>
 * To test X _||_ Y | Z, this test residualizes the basis-expanded blocks of X and Y on the basis-expanded block of Z
 * (Shah &amp; Peters, 2020, generalized to a grid of functionals): each embedded column f_j(X) and g_k(Y) is regressed
 * on [1, basis(Z)] by ridge-stabilized OLS, and the test statistic is the maximum over the (j, k) grid of the
 * studentized residual cross-covariances
 * <pre>
 *     T_jk = sqrt(n) * mean(e_j * f_k) / sd(e_j * f_k),
 * </pre>
 * where e_j and f_k are the residual columns. Under the null, each T_jk is asymptotically standard normal, and the
 * p-value for the max statistic is computed by a Rademacher multiplier bootstrap over the recentered residual
 * products, which accounts for the dependence among the grid cells; a Sidak bound over the grid is used instead if
 * the number of multiplier samples is set to zero. The bootstrap is seeded deterministically from the variable
 * indices of the independence fact, so repeated calls for the same fact return the same p-value regardless of call
 * order.
 * <p>
 * Rationale. The GCM construction is Neyman-orthogonal: substituting e = eps + (f - fhat) and expanding the statistic,
 * the only bias term is the product of the two regression errors, so the regressor's complexity never enters the null
 * distribution -- only its convergence rate matters, and only through that product. Because the regressor used here
 * (OLS on the basis expansion of Z) is a linear smoother of modest dimension, no sample splitting is required (Shah
 * &amp; Peters, Sec. 5): the hat-matrix effect on the residual products is second order. Testing the full grid of
 * basis pairs, rather than the single residual covariance of raw X and raw Y, detects dependence expressed in any
 * basis direction (variance dependence, sign dependence, etc.), not only conditional-mean dependence. The max-type
 * statistic retains power under sparse alternatives, where only a few grid cells carry signal.
 * <p>
 * Contrast with {@link IndTestBasisFunctionBlocks}: the Wilks block test assumes a joint Gaussian model over the
 * embedded columns and derives its degrees of freedom from that model; this test makes no distributional assumption
 * beyond moments, is heteroskedasticity-robust through its self-normalization, and its validity is insensitive to
 * misspecification of the Z-regressions up to the product-of-errors term. Its known blind spots are dependencies
 * orthogonal to every tested functional (mitigated, not eliminated, by the basis grid) and interactions strictly
 * within Z (the sieve regressor is additive over Z's basis columns).
 * <p>
 * With adaptive basis selection enabled, uninformative higher-order basis columns are pruned once, on the full
 * sample, by the BIC-crossing screen of {@link Embedding#pruneUninformativeBasisColumns}, which controls the size of
 * the (j, k) grid as the truncation limit grows and gives the test convergent behavior in the truncation limit.
 * <p>
 * Limitations: the multiplier bootstrap assumes independent rows; under serial or spatial dependence the residual
 * products would require block or kernel multipliers (cf. the wild-bootstrap Markov check machinery), which is not
 * implemented here. Effective sample size is likewise not supported. Discrete variables enter through their
 * indicator columns via the embedding.
 *
 * @author josephramsey
 */
public class IndTestBasisExpandedGcm implements IndependenceTest, RawMarginalIndependenceTest, RowsSettable {

    /**
     * The original dataset supplied by the caller.
     */
    private final DataSet dataSet;
    /**
     * The block-level variables (exactly the caller's Node instances).
     */
    private final List<Node> variables;
    /**
     * Map from Node to its column index in the original dataset.
     */
    private final Map<Node, Integer> nodeHash;
    /**
     * Mapping from original variable index to its embedded column indices (possibly pruned).
     */
    private final List<List<Integer>> blocks;
    /**
     * The full embedded data matrix (all rows, all embedded columns) for the tested grid.
     */
    private final SimpleMatrix embeddedFull;
    /**
     * Mapping from original variable index to its embedded column indices in the REGRESSION embedding, which uses
     * the (typically larger) zTruncationLimit and is never pruned. The Z-side of every regression is drawn from
     * this embedding.
     */
    private final List<List<Integer>> blocksZ;
    /**
     * The full embedded data matrix for the regression (Z-side) embedding.
     */
    private final SimpleMatrix embeddedZFull;
    /**
     * The truncation limit used for the Z-side (regression) embedding.
     */
    private final int zTruncationLimit;
    /**
     * The truncation limit used for the embedding.
     */
    private final int truncationLimit;
    /**
     * The basis type used for the embedding (see StatUtils.basisFunctionValue).
     */
    private final int basisType;
    /**
     * Whether adaptive basis selection was applied at construction.
     */
    private final boolean adaptiveBasisSelection;
    /**
     * Ridge added to the Gram matrix of the Z-regression for numerical stability.
     */
    private final double lambda;
    /**
     * Number of Rademacher multiplier bootstrap samples; 0 = use the Sidak bound instead.
     */
    private int numMultiplierSamples = 500;
    /**
     * If true, each side's regression design is augmented with a univariate Legendre basis in that side's
     * first-stage fitted mean (a control function). Rationale: the additive Z-sieve cannot represent interactions
     * among Z's components, yet even under purely ADDITIVE truth X = g(A) + h(B) + eps, the higher grid cells have
     * conditional means containing products such as g(A)h(B) -- powers of sums create interactions -- so the
     * product-of-errors bias is nonzero at |Z| &gt;= 2 no matter how large the univariate Z-truncation is, and the
     * test over-rejects true conditional independencies (visible as an excess of implied CIs judged dependent in
     * the Markov Checker). If X = mu(Z) + eps with eps independent of Z, then E[f_j(X) | Z] is a function of mu(Z)
     * alone, so a univariate basis in the fitted mean m-hat(Z) captures exactly the compositions the additive sieve
     * misses, including the interaction terms, and also absorbs slowly-converging univariate compositions (kinked
     * or saturating links). Under heteroskedastic or non-additive noise the collapse to mu(Z) is approximate rather
     * than exact, and the fitted mean is a generated regressor (the design depends on the data), so the fixed
     * linear-smoother argument no longer applies exactly; calibration with this option should be (and has been)
     * checked empirically rather than assumed.
     */
    private boolean controlFunction = false;
    /**
     * The significance level.
     */
    private double alpha = 0.01;
    /**
     * Verbosity flag.
     */
    private boolean verbose = false;
    /**
     * Optional row subset (null = all rows).
     */
    private List<Integer> rows = null;

    /**
     * Constructs the test.
     *
     * @param dataSet                the dataset to test on; must not be null.
     * @param truncationLimit        the basis truncation limit; must be &gt;= 1.
     * @param basisType              the basis family (see StatUtils.basisFunctionValue); Legendre = 1.
     * @param lambda                 ridge added to the Gram matrix of the Z-regressions (&gt;= 0).
     * @param adaptiveBasisSelection if true, prune uninformative higher-order basis columns once, on the full
     *                               sample, by the BIC-crossing screen; see
     *                               {@link Embedding#pruneUninformativeBasisColumns}.
     */
    public IndTestBasisExpandedGcm(DataSet dataSet, int truncationLimit, int basisType, double lambda,
                                   boolean adaptiveBasisSelection) {
        this(dataSet, truncationLimit, 0, basisType, lambda, adaptiveBasisSelection);
    }

    /**
     * Constructs the test with a separate truncation limit for the Z-side (regression) embedding.
     * <p>
     * The GCM guarantee requires the regression sieve to be rich enough to approximate E[f_j(X) | Z] for every
     * tested grid function f_j. Under nonlinear relations these conditional means are COMPOSITIONS -- if X is
     * roughly a degree-r function of Z, then f_j(X) has conditional mean of degree about j * r in Z -- so the
     * regression basis must be strictly richer than the tested grid, or the product-of-errors bias term is O(1) and
     * the test over-rejects true conditional independence (the same mechanism, one level up, as OLS residualization
     * failing under a nonlinear conditional mean). By default (zTruncationLimit = 0), the Z-side truncation is set
     * to twice the grid truncation, which covers quadratic relations exactly and is a reasonable sieve default; it
     * can be raised for more strongly nonlinear systems.
     *
     * @param dataSet                the dataset to test on; must not be null.
     * @param truncationLimit        the basis truncation limit for the tested X/Y grid; must be &gt;= 1.
     * @param zTruncationLimit       the truncation limit for the Z-side regression embedding; 0 selects the default
     *                               2 * truncationLimit; otherwise must be &gt;= 1 (values below truncationLimit are
     *                               raised to truncationLimit).
     * @param basisType              the basis family (see StatUtils.basisFunctionValue); Legendre = 1.
     * @param lambda                 ridge added to the Gram matrix of the Z-regressions (&gt;= 0).
     * @param adaptiveBasisSelection if true, prune uninformative higher-order basis columns of the TESTED GRID once,
     *                               on the full sample, by the BIC-crossing screen; the Z-side regression embedding
     *                               is never pruned (richer is safer for the nuisance regressions); see
     *                               {@link Embedding#pruneUninformativeBasisColumns}.
     */
    public IndTestBasisExpandedGcm(DataSet dataSet, int truncationLimit, int zTruncationLimit, int basisType,
                                   double lambda, boolean adaptiveBasisSelection) {
        if (dataSet == null) throw new IllegalArgumentException("dataSet == null");
        if (truncationLimit < 1) throw new IllegalArgumentException("truncationLimit must be >= 1");
        if (zTruncationLimit < 0) throw new IllegalArgumentException("zTruncationLimit must be >= 0 (0 = auto)");
        if (lambda < 0) throw new IllegalArgumentException("lambda must be >= 0");

        this.dataSet = dataSet;
        this.truncationLimit = truncationLimit;
        this.basisType = basisType;
        this.lambda = lambda;
        this.adaptiveBasisSelection = adaptiveBasisSelection;
        this.variables = new ArrayList<>(dataSet.getVariables());

        this.nodeHash = new HashMap<>();
        for (int i = 0; i < variables.size(); i++) nodeHash.put(variables.get(i), i);

        Embedding.EmbeddedData embeddedData = Objects.requireNonNull(
                Embedding.getEmbeddedData(dataSet, truncationLimit, basisType, 1),
                "Embedding.getEmbeddedData returned null");

        DataSet embedded = embeddedData.embeddedData();

        Map<Integer, List<Integer>> embeddingMap = adaptiveBasisSelection
                ? Embedding.pruneUninformativeBasisColumns(dataSet, embeddedData.embedding(),
                new CorrelationMatrix(embedded))
                : embeddedData.embedding();

        this.blocks = new ArrayList<>(dataSet.getNumColumns());
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            this.blocks.add(embeddingMap.get(i));
        }

        int n = embedded.getNumRows();
        int m = embedded.getNumColumns();
        this.embeddedFull = new SimpleMatrix(n, m);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < m; c++) {
                this.embeddedFull.set(r, c, embedded.getDouble(r, c));
            }
        }

        // Separate, richer, never-pruned embedding for the Z-side regressions; see the constructor Javadoc.
        this.zTruncationLimit = zTruncationLimit == 0
                ? 2 * truncationLimit
                : TMath.max(zTruncationLimit, truncationLimit);

        Embedding.EmbeddedData embeddedDataZ = Objects.requireNonNull(
                Embedding.getEmbeddedData(dataSet, this.zTruncationLimit, basisType, 1),
                "Embedding.getEmbeddedData returned null");

        DataSet embeddedZ = embeddedDataZ.embeddedData();

        this.blocksZ = new ArrayList<>(dataSet.getNumColumns());
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            this.blocksZ.add(embeddedDataZ.embedding().get(i));
        }

        int mz = embeddedZ.getNumColumns();
        this.embeddedZFull = new SimpleMatrix(n, mz);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < mz; c++) {
                this.embeddedZFull.set(r, c, embeddedZ.getDouble(r, c));
            }
        }
    }

    /**
     * Checks the independence of x and y given z using the basis-expanded GCM statistic.
     *
     * @param x the first variable.
     * @param y the second variable.
     * @param z the conditioning set.
     * @return the independence result, with the max-statistic p-value.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        Integer ix = nodeHash.get(x);
        Integer iy = nodeHash.get(y);
        if (ix == null || iy == null) {
            throw new IllegalArgumentException("Unknown node(s): " + x + ", " + y);
        }

        int[] zIdx = new int[z.size()];
        int t = 0;
        for (Node zn : z) {
            Integer iz = nodeHash.get(zn);
            if (iz == null) throw new IllegalArgumentException("Unknown conditioning node: " + zn);
            zIdx[t++] = iz;
        }
        Arrays.sort(zIdx); // deterministic seed and column order

        double p = gcmPValue(ix, iy, zIdx);
        boolean indep = p > alpha;
        return new IndependenceResult(new IndependenceFact(x, y, z), indep, p, alpha - p);
    }

    /**
     * Computes the basis-expanded GCM p-value for original variable indices (ix, iy | zIdx).
     */
    private double gcmPValue(int ix, int iy, int[] zIdx) {
        List<Integer> xCols = blocks.get(ix);
        List<Integer> yCols = blocks.get(iy);
        if (xCols.isEmpty() || yCols.isEmpty()) return 1.0;

        List<Integer> zCols = new ArrayList<>();
        for (int iz : zIdx) zCols.addAll(blocksZ.get(iz));

        int[] useRows = currentRows();
        int n = useRows.length;
        int q = zCols.size() + 1; // + intercept
        int dx = xCols.size();
        int dy = yCols.size();

        // Not enough rows to residualize and studentize: cannot test.
        if (n < q + 3) return 1.0;

        // Gather G = [1, Z-basis], Xb, Yb over the current rows.
        SimpleMatrix G = new SimpleMatrix(n, q);
        SimpleMatrix Xb = new SimpleMatrix(n, dx);
        SimpleMatrix Yb = new SimpleMatrix(n, dy);
        for (int r = 0; r < n; r++) {
            int row = useRows[r];
            G.set(r, 0, 1.0);
            for (int c = 0; c < zCols.size(); c++) G.set(r, c + 1, embeddedZFull.get(row, zCols.get(c)));
            for (int c = 0; c < dx; c++) Xb.set(r, c, embeddedFull.get(row, xCols.get(c)));
            for (int c = 0; c < dy; c++) Yb.set(r, c, embeddedFull.get(row, yCols.get(c)));
        }

        // Ridge-stabilized OLS of [Xb | Yb] on G: (G'G + lambda I) B = G'[Xb | Yb].
        SimpleMatrix Gt = G.transpose();
        SimpleMatrix A = Gt.mult(G);
        double ridge = TMath.max(lambda, 1e-10);
        for (int i = 0; i < q; i++) A.set(i, i, A.get(i, i) + ridge);

        SimpleMatrix Bx = A.solve(Gt.mult(Xb));
        SimpleMatrix By = A.solve(Gt.mult(Yb));

        SimpleMatrix E, F;

        if (controlFunction && !zCols.isEmpty()) {
            // Two-stage control-function fit; see the controlFunction field Javadoc. Each side's design is
            // augmented with Legendre basis columns (orders 2..zTruncationLimit; order 1 is already in the span
            // of G) in that side's first-stage fitted mean, then the side is re-residualized on its augmented
            // design. Degenerate fitted means (no spread) leave that side's design unaugmented.
            SimpleMatrix mx = G.mult(Bx.extractVector(false, 0));
            SimpleMatrix my = G.mult(By.extractVector(false, 0));

            int cfOrders = TMath.max(zTruncationLimit - 1, 0);
            if (n < q + 2 * cfOrders + 3) return 1.0;

            E = residualizeAugmented(G, Xb, mx, cfOrders, ridge);
            F = residualizeAugmented(G, Yb, my, cfOrders, ridge);
        } else {
            E = Xb.minus(G.mult(Bx));
            F = Yb.minus(G.mult(By));
        }

        // Residual products, per grid cell: W[:, j * dy + k] = E[:, j] .* F[:, k].
        int d = dx * dy;
        double[][] W = new double[d][n];
        double[] mean = new double[d];
        double[] sd = new double[d];

        for (int j = 0; j < dx; j++) {
            for (int k = 0; k < dy; k++) {
                int cell = j * dy + k;
                double s = 0.0;
                for (int r = 0; r < n; r++) {
                    double w = E.get(r, j) * F.get(r, k);
                    W[cell][r] = w;
                    s += w;
                }
                double mu = s / n;
                mean[cell] = mu;
                double v = 0.0;
                for (int r = 0; r < n; r++) {
                    double dvi = W[cell][r] - mu;
                    v += dvi * dvi;
                }
                sd[cell] = TMath.sqrt(v / n);
            }
        }

        double sqrtN = TMath.sqrt(n);
        double maxStat = 0.0;
        boolean anyUsable = false;
        for (int cell = 0; cell < d; cell++) {
            if (sd[cell] > 1e-12) {
                anyUsable = true;
                double stat = TMath.abs(sqrtN * mean[cell] / sd[cell]);
                if (stat > maxStat) maxStat = stat;
            }
        }
        if (!anyUsable) return 1.0;

        if (numMultiplierSamples <= 0) {
            // Sidak bound over the grid using the normal tail.
            NormalDistribution normal = new NormalDistribution(0, 1);
            double pSingle = 2.0 * (1.0 - normal.cumulativeProbability(maxStat));
            pSingle = TMath.min(TMath.max(pSingle, 0.0), 1.0);
            double p = 1.0 - TMath.pow(1.0 - pSingle, d);
            if (!Double.isFinite(p)) return 1.0;
            return TMath.min(TMath.max(p, 0.0), 1.0);
        }

        // Rademacher multiplier bootstrap of the max statistic over the recentered products,
        // seeded deterministically from the fact so that p-values are call-order independent.
        long seed = 0x9E3779B97F4A7C15L;
        seed = 31L * seed + ix;
        seed = 31L * seed + iy;
        for (int iz : zIdx) seed = 31L * seed + iz;
        seed = 31L * seed + n;
        Random rng = new Random(seed);

        int exceed = 0;
        double tol = 1e-12;
        for (int b = 0; b < numMultiplierSamples; b++) {
            double maxStar = 0.0;
            // One shared multiplier vector per bootstrap replicate across all cells,
            // preserving the cross-cell dependence of the statistics.
            // (Draw per row, apply to each cell's recentered product.)
            double[] eps = new double[n];
            for (int r = 0; r < n; r++) eps[r] = rng.nextBoolean() ? 1.0 : -1.0;

            for (int cell = 0; cell < d; cell++) {
                if (sd[cell] <= 1e-12) continue;
                double s = 0.0;
                double[] w = W[cell];
                double mu = mean[cell];
                for (int r = 0; r < n; r++) s += eps[r] * (w[r] - mu);
                double stat = TMath.abs(s / (sqrtN * sd[cell]));
                if (stat > maxStar) maxStar = stat;
            }

            if (maxStar >= maxStat - tol) exceed++;
        }

        return (1.0 + exceed) / (1.0 + numMultiplierSamples);
    }

    /**
     * Residualizes the block on the design [G | Legendre(m-hat, orders 2..1+cfOrders)], where m-hat is the
     * first-stage fitted mean, min-max scaled to [-1, 1]. If the fitted mean has no usable spread or cfOrders is 0,
     * residualizes on G alone.
     */
    private SimpleMatrix residualizeAugmented(SimpleMatrix G, SimpleMatrix block, SimpleMatrix mHat,
                                              int cfOrders, double ridge) {
        int n = G.getNumRows();
        int q = G.getNumCols();

        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int r = 0; r < n; r++) {
            double v = mHat.get(r, 0);
            if (v < lo) lo = v;
            if (v > hi) hi = v;
        }

        boolean degenerate = !(hi - lo > 1e-12) || cfOrders <= 0;
        int qa = q + (degenerate ? 0 : cfOrders);

        SimpleMatrix Ga = new SimpleMatrix(n, qa);
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < q; c++) Ga.set(r, c, G.get(r, c));
            if (!degenerate) {
                double u = 2.0 * (mHat.get(r, 0) - lo) / (hi - lo) - 1.0;
                for (int k = 0; k < cfOrders; k++) {
                    Ga.set(r, q + k, StatUtils.basisFunctionValue(basisType, k + 2, u));
                }
            }
        }

        SimpleMatrix Gat = Ga.transpose();
        SimpleMatrix Aa = Gat.mult(Ga);
        for (int i = 0; i < qa; i++) Aa.set(i, i, Aa.get(i, i) + ridge);
        SimpleMatrix B = Aa.solve(Gat.mult(block));
        return block.minus(Ga.mult(B));
    }

    /**
     * The rows to use for the next test: the caller-set subset, or all rows.
     */
    private int[] currentRows() {
        if (rows == null) {
            int n = dataSet.getNumRows();
            int[] all = new int[n];
            for (int i = 0; i < n; i++) all[i] = i;
            return all;
        }
        int[] r = new int[rows.size()];
        for (int i = 0; i < rows.size(); i++) r[i] = rows.get(i);
        return r;
    }

    // ====== RawMarginalIndependenceTest ======

    /**
     * Computes the marginal p-value between two raw columns by building a two-column dataset and running the
     * marginal version of this test on it.
     *
     * @param x the first column.
     * @param y the second column.
     * @return the marginal basis-expanded GCM p-value.
     */
    @Override
    public double computePValue(double[] x, double[] y) {
        if (x == null || y == null) return 1.0;
        int n = x.length;
        if (y.length != n || n < 5) return 1.0;

        double[][] m = new double[n][2];
        for (int i = 0; i < n; i++) {
            m[i][0] = x[i];
            m[i][1] = y[i];
        }
        List<Node> vars = new ArrayList<>(2);
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        DataSet ds = new BoxDataSet(new DoubleDataBox(m), vars);

        IndTestBasisExpandedGcm test = new IndTestBasisExpandedGcm(ds, truncationLimit, zTruncationLimit,
                basisType, lambda, adaptiveBasisSelection);
        test.setNumMultiplierSamples(numMultiplierSamples);
        test.setControlFunction(controlFunction);

        IndependenceResult r = test.checkIndependence(ds.getVariable("X"), ds.getVariable("Y"),
                Collections.emptySet());
        double p = (r != null) ? r.getPValue() : 1.0;
        if (!Double.isFinite(p)) return 1.0;
        return TMath.max(0.0, TMath.min(p, 1.0));
    }

    /**
     * Multivariate fallback: tests x against each column of Y and combines with Fisher's method.
     *
     * @param x the first column.
     * @param Y the matrix of second columns.
     * @return the Fisher-combined p-value.
     */
    @Override
    public double computePValue(double[] x, double[][] Y) {
        if (Y == null || Y.length == 0) return 1.0;
        final int n = x.length;
        if (Y.length != n) return 1.0;

        double stat = 0.0;
        int k = 0;

        int m = Y[0].length;
        for (int j = 0; j < m; j++) {
            double[] yj = new double[n];
            for (int i = 0; i < n; i++) yj[i] = Y[i][j];
            double pj = computePValue(x, yj);
            if (Double.isNaN(pj)) continue;
            double pc = TMath.max(pj, 1e-300);
            stat += -2.0 * TMath.log(pc);
            k++;
        }
        if (k == 0) return 1.0;

        int df = 2 * k;
        org.apache.commons.math3.distribution.ChiSquaredDistribution chi2 =
                new org.apache.commons.math3.distribution.ChiSquaredDistribution(df);
        double p = 1.0 - chi2.cumulativeProbability(stat);
        if (!Double.isFinite(p)) return 1.0;
        return TMath.max(0.0, TMath.min(p, 1.0));
    }

    // ====== Plumbing ======

    /**
     * Returns the variables of this test (the caller's Node instances).
     *
     * @return the variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(variables);
    }

    /**
     * Returns the original dataset.
     *
     * @return the dataset.
     */
    @Override
    public DataModel getData() {
        return dataSet;
    }

    /**
     * Returns whether verbose output is enabled.
     *
     * @return true if verbose.
     */
    @Override
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output is enabled.
     *
     * @param verbose true to enable.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Returns the significance level.
     *
     * @return alpha.
     */
    @Override
    public double getAlpha() {
        return alpha;
    }

    /**
     * Sets the significance level.
     *
     * @param alpha the significance level, strictly between 0 and 1.
     */
    @Override
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha in (0,1)");
        this.alpha = alpha;
    }

    /**
     * Sets the number of Rademacher multiplier bootstrap samples used for the max-statistic p-value. Zero selects
     * the (conservative) Sidak bound over the grid instead.
     *
     * @param numMultiplierSamples the number of samples; must be &gt;= 0.
     */
    public void setNumMultiplierSamples(int numMultiplierSamples) {
        if (numMultiplierSamples < 0) throw new IllegalArgumentException("numMultiplierSamples must be >= 0");
        this.numMultiplierSamples = numMultiplierSamples;
    }

    /**
     * Sets whether each side's regression design is augmented with a univariate basis in that side's first-stage
     * fitted mean (a control function), which captures the interaction and composition terms the additive Z-sieve
     * cannot represent. See the field documentation for the rationale and caveats.
     *
     * @param controlFunction true to enable the control-function augmentation.
     */
    public void setControlFunction(boolean controlFunction) {
        this.controlFunction = controlFunction;
    }

    /**
     * Returns the current row subset, or null for all rows.
     *
     * @return the rows.
     */
    @Override
    public List<Integer> getRows() {
        return rows;
    }

    /**
     * Sets the row subset to test on (null = all rows). The embedding and any adaptive pruning are fixed at
     * construction, from the full sample, so the block structure is identical across subsets.
     *
     * @param rows the row indices, or null for all rows.
     */
    @Override
    public void setRows(List<Integer> rows) {
        if (rows == null) {
            this.rows = null;
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Integer r = rows.get(i);
            if (r == null) throw new NullPointerException("Row " + i + " is null.");
            if (r < 0 || r >= dataSet.getNumRows()) {
                throw new IllegalArgumentException("Row " + i + " is out of range: " + r);
            }
        }
        this.rows = new ArrayList<>(rows);
    }

    /**
     * Returns a string representation of this test.
     *
     * @return the name of this test.
     */
    @Override
    public String toString() {
        return "BE-GCM (Basis-Expanded GCM Test), trunc = " + truncationLimit
                + ", zTrunc = " + zTruncationLimit
                + (adaptiveBasisSelection ? ", adaptive" : "")
                + (controlFunction ? ", control-function" : "");
    }
}
