///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgIm;
import edu.cmu.tetrad.hybridcg.HybridCgModel.HybridCgPm;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Computes per-edge significance for a {@link HybridCgIm} against a dataset, and per-stratum t-tests for the linear
 * coefficients of continuous children.
 * <p>
 * <b>Per-edge test.</b> Every edge X&nbsp;&rarr;&nbsp;Y is tested by a nested likelihood-ratio test that drops X from
 * the local family of Y: the full local model (Y given all its parents) and the reduced local model (Y given its
 * parents minus X) are both fit by maximum likelihood <i>on the same case set</i> &mdash; the available cases of the
 * <i>full</i> family, i.e. the rows on which Y and all of its parents are observed. Fitting both models on the same
 * rows is what makes the models nested; fitting each on its own available cases would compare likelihoods over
 * different data and the statistic would be meaningless. The statistic is G = 2(&#8467;<sub>full</sub> &minus;
 * &#8467;<sub>reduced</sub>), referred to &chi;&sup2; with degrees of freedom equal to the difference in the number of
 * parameters the data could actually identify (see below). The test fits are pure MLE: the Dirichlet pseudocount used
 * for display estimation is <i>not</i> applied, since a shrunk fit breaks the &chi;&sup2; calibration.
 * <p>
 * The four edge kinds reduce to three fitting situations:
 * <ul>
 *   <li><b>Continuous child, drop a continuous parent.</b> Same discrete-parent strata in both models; the reduced
 *   model omits one regression column. Each nonempty stratum in which the coefficient was identifiable contributes
 *   one degree of freedom.</li>
 *   <li><b>Continuous child, drop a discrete parent.</b> Strata that differ only in the dropped parent's value merge;
 *   the reduced model fits one regression per merged group. Degrees of freedom are the difference in identifiable
 *   parameter counts (means, coefficients, and &mdash; unless variance is shared &mdash; per-stratum variances).</li>
 *   <li><b>Discrete child, drop any parent</b> (discrete, or continuous-binned via the PM's cutpoints). The reduced
 *   CPT collapses the dropped parent's dimension; the statistic is the multinomial G&sup2;. Degrees of freedom use
 *   the standard sparse-data adjustment: within each configuration of the remaining parents, (L&minus;1)(K&minus;1)
 *   where L is the number of nonempty rows over the dropped dimension and K the number of child categories observed
 *   in that configuration.</li>
 * </ul>
 * <p>
 * <b>Parameter counting.</b> A stratum with n cases can identify at most n mean/coefficient parameters, so per-stratum
 * counts are capped at n; empty strata contribute no likelihood and no parameters. When the identifiable counts of the
 * full and reduced models coincide (df = 0), the edge is reported as not testable rather than given a p-value.
 * <p>
 * <b>Known approximations, stated rather than hidden.</b> (1) The &chi;&sup2; reference is asymptotic; in small strata
 * the LRT for Gaussian children is anti-conservative relative to the exact F test. (2) For binned continuous parents
 * of discrete children, the cutpoints were themselves chosen from the data, which the reference distribution ignores.
 * (3) The variance floor applied to degenerate (near-interpolating) strata is applied identically to both fits but is
 * still a floor. (4) The identifiability cap on parameter counts is a proxy, not an exact rank computation. None of
 * these caveats are removed by this class; consumers displaying these p-values should not present them as exact.
 * <p>
 * <b>Coefficient t-tests.</b> Separately from the per-edge LRT, {@link #coefficientPValues} returns a classical OLS
 * two-sided t-test p-value for each (continuous child, discrete-parent stratum, continuous parent) coefficient, with
 * SE from s&sup2;(X&#7488;X)<sup>&minus;1</sup> and s&sup2; = RSS/(n &minus; m &minus; 1). These are per-cell
 * quantities for table display; the per-edge LRT, not these, is the edge-level verdict. Strata whose design is
 * singular or whose residual degrees of freedom are &le; 0 get NaN.
 * <p>
 * Nothing here corrects for testing many edges at once, and nothing here makes p-values computed after a
 * data-dependent pruning step valid; both caveats belong to the consumer.
 */
public final class HybridCgEdgeSignificance {

    private HybridCgEdgeSignificance() {
    }

    /**
     * Significance summary for one edge.
     *
     * @param pValue      the LRT p-value, or NaN if the edge is not testable on this data
     * @param statistic   G = 2(&#8467;_full &minus; &#8467;_reduced), clipped below at 0; NaN if not testable
     * @param df          the sparse-adjusted degrees of freedom; 0 if not testable
     * @param n           the number of available cases of the full family (the case set both models were fit on)
     * @param description a short human-readable account of the test
     */
    public record Result(double pValue, double statistic, int df, int n, String description) {

        /**
         * True if the test could be carried out (df &gt; 0 and a finite statistic).
         *
         * @return true if testable
         */
        public boolean testable() {
            return df > 0 && Double.isFinite(statistic);
        }

        /**
         * True if testable and pValue &le; alpha.
         *
         * @param alpha the significance level
         * @return true if significant at alpha
         */
        public boolean significantAt(double alpha) {
            return testable() && pValue <= alpha;
        }
    }

    /**
     * Computes a per-edge LRT significance for every edge of the IM's graph, with per-stratum variances for
     * continuous children (shareVariance = false).
     *
     * @param im   the instantiated model whose graph supplies the edges and whose PM supplies typing and cutpoints
     * @param data the dataset; variables are matched to PM nodes by name
     * @return map from each edge of {@code im.getPm().getGraph()} to its Result, in graph order
     */
    public static Map<Edge, Result> compute(HybridCgIm im, DataSet data) {
        return compute(im, data, false);
    }

    /**
     * Computes a per-edge LRT significance for every edge of the IM's graph.
     *
     * @param im            the instantiated model
     * @param data          the dataset; variables are matched to PM nodes by name
     * @param shareVariance if true, continuous children are fit with a single residual variance shared across
     *                      discrete-parent strata, in both the full and the reduced model, matching the estimator's
     *                      {@code hybridcg.shareVariance} option
     * @return map from each edge of {@code im.getPm().getGraph()} to its Result, in graph order
     */
    public static Map<Edge, Result> compute(HybridCgIm im, DataSet data, boolean shareVariance) {
        Objects.requireNonNull(im, "im");
        Objects.requireNonNull(data, "data");

        HybridCgPm pm = im.getPm();
        Graph g = pm.getGraph();
        Node[] nodes = pm.getNodes();
        int[] colIndex = resolveColumns(pm, data);

        Map<Edge, Result> out = new LinkedHashMap<>();

        for (int y = 0; y < nodes.length; y++) {
            int[] dps = pm.getDiscreteParents(y);
            int[] cps = pm.getContinuousParents(y);
            if (dps.length + cps.length == 0) continue;

            List<Integer> cases = availableCases(pm, data, y, dps, cps, colIndex);

            if (pm.isDiscrete(y)) {
                double[][] cuts;
                if (cps.length > 0) {
                    var opt = pm.getContParentCutpointsForDiscreteChild(y);
                    if (opt.isEmpty()) {
                        for (int p : dps) putIfEdge(out, g, nodes[p], nodes[y], unavailable(nodes[y].getName(), cases.size()));
                        for (int p : cps) putIfEdge(out, g, nodes[p], nodes[y], unavailable(nodes[y].getName(), cases.size()));
                        continue;
                    }
                    cuts = opt.get();
                } else {
                    cuts = new double[0][];
                }
                discreteChildTests(out, im, pm, data, y, dps, cps, cuts, colIndex, cases, g, nodes);
            } else {
                continuousChildTests(out, pm, data, y, dps, cps, colIndex, cases, shareVariance, g, nodes);
            }
        }
        return out;
    }

    /**
     * Classical OLS t-test p-values for the linear coefficients of continuous children, one per (child,
     * discrete-parent stratum, continuous parent), for table display.
     * <p>
     * Entry {@code [y][row][t]} is the two-sided p-value for the coefficient of the child's t-th continuous parent in
     * stratum {@code row}, computed on the available cases of the child's full family. {@code [y]} is null for
     * discrete children and for continuous children with no continuous parents. Entries are NaN when the stratum has
     * no cases, its design is singular, or its residual degrees of freedom are &le; 0.
     *
     * @param im   the instantiated model
     * @param data the dataset; variables are matched to PM nodes by name
     * @return the p-value array, indexed [node][stratum row][continuous-parent order index]
     */
    public static double[][][] coefficientPValues(HybridCgIm im, DataSet data) {
        Objects.requireNonNull(im, "im");
        Objects.requireNonNull(data, "data");

        HybridCgPm pm = im.getPm();
        Node[] nodes = pm.getNodes();
        int[] colIndex = resolveColumns(pm, data);

        double[][][] out = new double[nodes.length][][];

        for (int y = 0; y < nodes.length; y++) {
            if (pm.isDiscrete(y)) continue;
            int[] dps = pm.getDiscreteParents(y);
            int[] cps = pm.getContinuousParents(y);
            int m = cps.length;
            if (m == 0) continue;

            int rows = pm.getNumRows(y);
            double[][] p = new double[rows][m];
            for (double[] row : p) Arrays.fill(row, Double.NaN);

            List<Integer> cases = availableCases(pm, data, y, dps, cps, colIndex);
            Map<Integer, List<Integer>> byStratum = stratify(pm, data, y, dps, colIndex, cases);

            for (Map.Entry<Integer, List<Integer>> e : byStratum.entrySet()) {
                int row = e.getKey();
                List<Integer> cs = e.getValue();
                int n = cs.size();
                int k = m + 1;                       // intercept + coefficients
                int dfResid = n - k;
                if (dfResid <= 0) continue;

                double[][] X = new double[n][k];
                double[] yv = new double[n];
                fillDesign(data, colIndex, y, cps, cs, X, yv, k);

                SimpleMatrix Xm = new SimpleMatrix(X);
                SimpleMatrix ym = new SimpleMatrix(n, 1, true, yv);
                SimpleMatrix xtx = Xm.transpose().mult(Xm);

                SimpleMatrix xtxInv;
                try {
                    xtxInv = xtx.invert();
                } catch (RuntimeException singular) {
                    continue;                        // classical SEs undefined; leave NaN
                }

                SimpleMatrix beta = xtxInv.mult(Xm.transpose()).mult(ym);
                SimpleMatrix resid = ym.minus(Xm.mult(beta));
                double rss = resid.elementPower(2.0).elementSum();
                double s2 = rss / dfResid;

                TDistribution tDist = new TDistribution(dfResid);
                for (int t = 0; t < m; t++) {
                    double se = Math.sqrt(s2 * xtxInv.get(1 + t, 1 + t));
                    if (!(se > 0) || !Double.isFinite(se)) continue;
                    double tStat = beta.get(1 + t) / se;
                    if (!Double.isFinite(tStat)) continue;
                    p[row][t] = 2.0 * (1.0 - tDist.cumulativeProbability(Math.abs(tStat)));
                }
            }
            out[y] = p;
        }
        return out;
    }

    // ---------------------------------------------------------------- continuous child

    private static void continuousChildTests(Map<Edge, Result> out, HybridCgPm pm, DataSet data, int y,
                                             int[] dps, int[] cps, int[] colIndex, List<Integer> cases,
                                             boolean shareVariance, Graph g, Node[] nodes) {
        int m = cps.length;
        Map<Integer, List<Integer>> full = stratify(pm, data, y, dps, colIndex, cases);
        double floor = varianceFloor(data, colIndex[y], cases);

        GaussFit fullFit = gaussFit(data, colIndex, y, cps, full, shareVariance, floor);

        // Drop each continuous parent: same strata, one fewer column.
        for (int t = 0; t < m; t++) {
            Edge e = g.getEdge(nodes[cps[t]], nodes[y]);
            if (e == null) continue;
            int[] reducedCps = dropAt(cps, t);
            GaussFit redFit = gaussFit(data, colIndex, y, reducedCps, full, shareVariance, floor);
            out.put(e, lrtResult(fullFit, redFit, cases.size(), nodes[cps[t]].getName(), nodes[y].getName()));
        }

        // Drop each discrete parent: merge strata over that parent's dimension.
        for (int i = 0; i < dps.length; i++) {
            Edge e = g.getEdge(nodes[dps[i]], nodes[y]);
            if (e == null) continue;
            Map<Integer, List<Integer>> merged = mergeStrata(pm, y, dps, i, full);
            GaussFit redFit = gaussFit(data, colIndex, y, cps, merged, shareVariance, floor);
            out.put(e, lrtResult(fullFit, redFit, cases.size(), nodes[dps[i]].getName(), nodes[y].getName()));
        }
    }

    /**
     * Log-likelihood and identifiable-parameter count of the per-stratum Gaussian regressions of the child on the
     * given continuous parents, fit by MLE on the given strata.
     */
    private record GaussFit(double ll, int params) {
    }

    private static GaussFit gaussFit(DataSet data, int[] colIndex, int y, int[] cps,
                                     Map<Integer, List<Integer>> strata, boolean shareVariance, double floor) {
        int m = cps.length;
        int k = m + 1;

        double ll = 0.0;
        int meanParams = 0;
        int varParams = 0;
        double pooledRss = 0.0;
        int totalN = 0;
        List<double[]> perStratum = new ArrayList<>();   // {n, rss} for shared-variance second pass

        for (List<Integer> cs : strata.values()) {
            int n = cs.size();
            if (n == 0) continue;

            double[][] X = new double[n][k];
            double[] yv = new double[n];
            fillDesign(data, colIndex, y, cps, cs, X, yv, k);

            SimpleMatrix Xm = new SimpleMatrix(X);
            SimpleMatrix ym = new SimpleMatrix(n, 1, true, yv);
            SimpleMatrix beta = Xm.pseudoInverse().mult(ym);
            SimpleMatrix resid = ym.minus(Xm.mult(beta));
            double rss = resid.elementPower(2.0).elementSum();
            if (!Double.isFinite(rss)) rss = 0.0;

            meanParams += Math.min(n, k);
            totalN += n;
            pooledRss += rss;
            perStratum.add(new double[]{n, rss});

            if (!shareVariance) {
                varParams += 1;
                double s2 = Math.max(rss / n, floor);
                ll += gaussLl(n, rss, s2);
            }
        }

        if (shareVariance && totalN > 0) {
            varParams = 1;
            double s2 = Math.max(pooledRss / totalN, floor);
            for (double[] nr : perStratum) ll += gaussLl((int) nr[0], nr[1], s2);
        }

        return new GaussFit(ll, meanParams + varParams);
    }

    private static double gaussLl(int n, double rss, double s2) {
        return -0.5 * n * Math.log(2.0 * Math.PI * s2) - rss / (2.0 * s2);
    }

    private static void fillDesign(DataSet data, int[] colIndex, int y, int[] cps, List<Integer> cs,
                                   double[][] X, double[] yv, int k) {
        for (int i = 0; i < cs.size(); i++) {
            int r = cs.get(i);
            X[i][0] = 1.0;
            for (int t = 1; t < k; t++) X[i][t] = data.getDouble(r, colIndex[cps[t - 1]]);
            yv[i] = data.getDouble(r, colIndex[y]);
        }
    }

    /**
     * Scale-aware floor for MLE residual variances, so near-interpolating strata do not send the log-likelihood to
     * infinity. Applied identically to full and reduced fits.
     */
    private static double varianceFloor(DataSet data, int yCol, List<Integer> cases) {
        int n = cases.size();
        if (n == 0) return 1e-12;
        double sum = 0.0;
        for (int r : cases) sum += data.getDouble(r, yCol);
        double mean = sum / n;
        double ss = 0.0;
        for (int r : cases) {
            double d = data.getDouble(r, yCol) - mean;
            ss += d * d;
        }
        double marginal = ss / n;
        return marginal > 0 ? 1e-10 * marginal : 1e-12;
    }

    // ---------------------------------------------------------------- discrete child

    private static void discreteChildTests(Map<Edge, Result> out, HybridCgIm im, HybridCgPm pm, DataSet data, int y,
                                           int[] dps, int[] cps, double[][] cuts, int[] colIndex,
                                           List<Integer> cases, Graph g, Node[] nodes) {
        int rows = pm.getNumRows(y);
        int K = pm.getCardinality(y);
        int[] dims = pm.getRowDims(y);

        double[][] counts = new double[rows][K];
        for (int r : cases) {
            int yVal = data.getInt(r, colIndex[y]);
            int[] discVals = new int[dps.length];
            for (int i = 0; i < dps.length; i++) discVals[i] = data.getInt(r, colIndex[dps[i]]);
            int[] contBins = new int[cps.length];
            for (int t = 0; t < cps.length; t++) {
                contBins[t] = binFromCutpoints(cuts[t], data.getDouble(r, colIndex[cps[t]]));
            }
            counts[pm.getRowIndex(y, discVals, contBins)][yVal] += 1.0;
        }

        double llFull = multinomialLl(counts, K);

        // Dimension k of dims corresponds to discrete parent k for k < dps.length, else binned continuous parent.
        for (int k = 0; k < dims.length; k++) {
            Node parent = k < dps.length ? nodes[dps[k]] : nodes[cps[k - dps.length]];
            Edge e = g.getEdge(parent, nodes[y]);
            if (e == null) continue;

            List<int[]> groups = groupsVarying(dims, k, rows);

            // Reduced log-likelihood: within each group, rows collapse to their sum.
            double llRed = 0.0;
            int df = 0;
            for (int[] group : groups) {
                double[] collapsed = new double[K];
                int nonEmptyRows = 0;
                for (int row : group) {
                    double rowTotal = 0.0;
                    for (int c = 0; c < K; c++) {
                        collapsed[c] += counts[row][c];
                        rowTotal += counts[row][c];
                    }
                    if (rowTotal > 0) nonEmptyRows++;
                }
                double groupTotal = 0.0;
                int observedCats = 0;
                for (int c = 0; c < K; c++) {
                    groupTotal += collapsed[c];
                    if (collapsed[c] > 0) observedCats++;
                }
                if (groupTotal > 0) {
                    for (int c = 0; c < K; c++) {
                        if (collapsed[c] > 0) llRed += collapsed[c] * Math.log(collapsed[c] / groupTotal);
                    }
                }
                if (nonEmptyRows >= 2 && observedCats >= 2) {
                    df += (nonEmptyRows - 1) * (observedCats - 1);
                }
            }

            double stat = Math.max(0.0, 2.0 * (llFull - llRed));
            out.put(e, chi2Result(stat, df, cases.size(), parent.getName(), nodes[y].getName()));
        }
    }

    private static double multinomialLl(double[][] counts, int K) {
        double ll = 0.0;
        for (double[] row : counts) {
            double total = 0.0;
            for (int c = 0; c < K; c++) total += row[c];
            if (total == 0) continue;
            for (int c = 0; c < K; c++) {
                if (row[c] > 0) ll += row[c] * Math.log(row[c] / total);
            }
        }
        return ll;
    }

    // ---------------------------------------------------------------- shared helpers

    private static Result lrtResult(GaussFit full, GaussFit reduced, int n, String parentName, String childName) {
        int df = full.params() - reduced.params();
        double stat = Math.max(0.0, 2.0 * (full.ll() - reduced.ll()));
        if (df <= 0 || !Double.isFinite(stat)) {
            return new Result(Double.NaN, Double.NaN, Math.max(df, 0), n, String.format(
                    "LRT dropping %s from the family of %s: not testable on this data (0 identifiable df).",
                    parentName, childName));
        }
        double p = 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(stat);
        return new Result(p, stat, df, n, String.format(
                "LRT dropping %s from the family of %s: G = %.4g, df = %d, p = %.4g (available-case n = %d).",
                parentName, childName, stat, df, p, n));
    }

    private static Result chi2Result(double stat, int df, int n, String parentName, String childName) {
        if (df <= 0 || !Double.isFinite(stat)) {
            return new Result(Double.NaN, Double.NaN, Math.max(df, 0), n, String.format(
                    "G-squared dropping %s from the family of %s: not testable on this data (0 df after sparse-data adjustment).",
                    parentName, childName));
        }
        double p = 1.0 - new ChiSquaredDistribution(df).cumulativeProbability(stat);
        return new Result(p, stat, df, n, String.format(
                "G-squared dropping %s from the family of %s: G = %.4g, df = %d, p = %.4g (available-case n = %d).",
                parentName, childName, stat, df, p, n));
    }

    private static Result unavailable(String childName, int n) {
        return new Result(Double.NaN, Double.NaN, 0, n,
                "Significance unavailable: cutpoints are not set for the continuous parents of " + childName
                + ". Re-estimate the model to set them.");
    }

    private static void putIfEdge(Map<Edge, Result> out, Graph g, Node parent, Node child, Result r) {
        Edge e = g.getEdge(parent, child);
        if (e != null) out.put(e, r);
    }

    /**
     * The available cases of child y's full family: rows on which y, its discrete parents, and its continuous parents
     * are all observed. Discrete missing is {@link DiscreteVariable#MISSING_VALUE}; continuous missing is any
     * non-finite value. This mirrors the estimator's available-case policy.
     */
    private static List<Integer> availableCases(HybridCgPm pm, DataSet data, int y, int[] dps, int[] cps,
                                                int[] colIndex) {
        List<Integer> cases = new ArrayList<>();
        for (int r = 0; r < data.getNumRows(); r++) {
            if (familyHasMissing(pm, data, y, dps, cps, colIndex, r)) continue;
            cases.add(r);
        }
        return cases;
    }

    private static boolean familyHasMissing(HybridCgPm pm, DataSet data, int y, int[] dps, int[] cps,
                                            int[] colIndex, int r) {
        if (pm.isDiscrete(y)) {
            if (data.getInt(r, colIndex[y]) == DiscreteVariable.MISSING_VALUE) return true;
        } else {
            if (!Double.isFinite(data.getDouble(r, colIndex[y]))) return true;
        }
        for (int dp : dps) if (data.getInt(r, colIndex[dp]) == DiscreteVariable.MISSING_VALUE) return true;
        for (int cp : cps) if (!Double.isFinite(data.getDouble(r, colIndex[cp]))) return true;
        return false;
    }

    /**
     * Groups the given cases by the discrete-parent stratum of child y, keyed by the stratum's row index in mixed
     * radix over the discrete-parent dimensions (first dimension most significant, matching
     * {@link HybridCgPm#getRowIndex} for a continuous child).
     */
    private static Map<Integer, List<Integer>> stratify(HybridCgPm pm, DataSet data, int y, int[] dps,
                                                        int[] colIndex, List<Integer> cases) {
        Map<Integer, List<Integer>> byStratum = new LinkedHashMap<>();
        int[] discVals = new int[dps.length];
        for (int r : cases) {
            for (int i = 0; i < dps.length; i++) discVals[i] = data.getInt(r, colIndex[dps[i]]);
            int row = pm.getRowIndex(y, discVals, null);
            byStratum.computeIfAbsent(row, x -> new ArrayList<>()).add(r);
        }
        return byStratum;
    }

    /**
     * Merges the strata of a continuous child over discrete parent {@code i}: strata whose configurations differ only
     * in dimension i pool their cases. Keys of the returned map are the row index of the group's representative
     * (value 0 in dimension i); only nonempty groups appear.
     */
    private static Map<Integer, List<Integer>> mergeStrata(HybridCgPm pm, int y, int[] dps, int i,
                                                           Map<Integer, List<Integer>> full) {
        int[] dims = new int[dps.length];
        for (int j = 0; j < dps.length; j++) dims[j] = pm.getCardinality(dps[j]);
        int stride = 1;
        for (int j = i + 1; j < dims.length; j++) stride *= dims[j];

        Map<Integer, List<Integer>> merged = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : full.entrySet()) {
            int row = e.getKey();
            int valueAtI = (row / stride) % dims[i];
            int representative = row - valueAtI * stride;
            merged.computeIfAbsent(representative, x -> new ArrayList<>()).addAll(e.getValue());
        }
        return merged;
    }

    private static int[] dropAt(int[] a, int t) {
        int[] out = new int[a.length - 1];
        int j = 0;
        for (int i = 0; i < a.length; i++) if (i != t) out[j++] = a[i];
        return out;
    }

    private static int binFromCutpoints(double[] cuts, double v) {
        int b = 0;
        while (b < cuts.length && v > cuts[b]) b++;
        return b;
    }

    /**
     * Rows are indexed in mixed radix over {@code dims} with the first dimension most significant. Returns, for each
     * configuration of the other dimensions, the rows obtained by letting dimension {@code k} range over its values,
     * in value order. (Same convention as {@link HybridCgEdgeStrengths}.)
     */
    private static List<int[]> groupsVarying(int[] dims, int k, int rows) {
        List<int[]> groups = new ArrayList<>();
        if (dims.length == 0 || dims[k] <= 0) return groups;
        int stride = 1;
        for (int j = k + 1; j < dims.length; j++) stride *= dims[j];
        for (int base = 0; base < rows; base++) {
            if ((base / stride) % dims[k] != 0) continue;
            int[] group = new int[dims[k]];
            for (int v = 0; v < dims[k]; v++) group[v] = base + v * stride;
            groups.add(group);
        }
        return groups;
    }

    /**
     * Resolves each PM node to a dataset column by exact name, as the estimator does.
     */
    private static int[] resolveColumns(HybridCgPm pm, DataSet data) {
        Node[] nodes = pm.getNodes();
        Map<String, Integer> colByName = new HashMap<>(data.getNumColumns() * 2);
        for (int c = 0; c < data.getNumColumns(); c++) colByName.put(data.getVariable(c).getName(), c);
        int[] colIndex = new int[nodes.length];
        for (int j = 0; j < nodes.length; j++) {
            Integer c = colByName.get(nodes[j].getName());
            if (c == null) {
                throw new IllegalArgumentException("Dataset is missing variable required by PM: " + nodes[j].getName());
            }
            colIndex[j] = c;
        }
        return colIndex;
    }
}
