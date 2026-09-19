///////////////////////////////////////////////////////////////////////////////
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the              //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program. If not, see <https://www.gnu.org/licenses/>.     //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Images;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestFisherZFisherPValue;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.util.Arrays;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orients a common adjacency graph over multiple datasets by pooling the FASK left-right
 * statistic across the datasets, rather than voting on per-dataset orientations as
 * {@link FaskVote} does.
 *
 * <p>The procedure has two stages:</p>
 *
 * <ol>
 *   <li>Obtain a common adjacency graph: from an external graph supplied by the caller;
 *   or by running IMaGES across the supplied datasets (the same adjacency stage as
 *   FASK-Vote; the default); or by a pooled FAS, a stable FAS run with a composite test
 *   that combines per-dataset Fisher Z p-values by Fisher's method; or by one of the
 *   moral-graph-based methods MG_FAS, MG_LING, and their intersection, which are
 *   designed to remain correct when the true graph is cyclic (see
 *   {@link AdjacencyMethod} and {@link #setAdjacencyMethod(AdjacencyMethod)}).</li>
 *   <li>For each adjacency X&mdash;Y, compute the signed FASK left-right statistic
 *   lr_k separately within each dataset k (on standardized columns, with FASK's
 *   skew-sign correction applied within each dataset, as
 *   {@link Fask#leftRightDiff(double[], double[], int)} does internally), and combine
 *   them as a weighted average, lr = &Sigma; w_k lr_k / &Sigma; w_k. The edge is
 *   oriented X&rarr;Y when lr &gt; 0 and Y&rarr;X when lr &lt; 0.</li>
 * </ol>
 *
 * <p>This is the meta-analytic fixed-effect analog of FASK-Vote: where a vote discards
 * the magnitude of each dataset's evidence, the pooled statistic retains it, so a
 * dataset in which the left-right statistic is decisive counts for more than one in
 * which it is a near coin flip. It is to FASK-Vote what the IMaGES averaged BIC score
 * is to voting over per-dataset graphs.</p>
 *
 * <p>Two weighting schemes are provided:</p>
 *
 * <ul>
 *   <li>{@link Weighting#N} (default): w_k = n_k, the sample size of dataset k. This is
 *   exact when the datasets are homogeneous in everything but sample size and requires
 *   no variance estimation.</li>
 *   <li>{@link Weighting#INVERSE_VARIANCE}: w_k = 1 / Var(lr_k), where Var(lr_k) is
 *   estimated by a within-dataset pairwise bootstrap. This is the standard fixed-effect
 *   meta-analysis estimator and downweights noisy datasets even when they are large.</li>
 * </ul>
 *
 * <p>Optionally, when the orientation alpha is set above zero, a bootstrap standard
 * error of the pooled statistic is computed and the edge is left undirected (an
 * abstention) unless the pooled statistic differs significantly from zero at that
 * level. With the default alpha of zero, every adjacency is oriented by the sign of
 * the pooled statistic, matching single-dataset FASK's behavior. A Cochran Q
 * heterogeneity statistic is recorded per edge whenever bootstrap variances are
 * available, so edges on which the datasets genuinely disagree in direction can be
 * inspected via {@link #getEdgeStats()}.</p>
 *
 * <p>Important: the datasets are never concatenated. Between-dataset differences in
 * location, scale, or skew would make the concatenated sample a mixture, and mixtures
 * can manufacture exactly the kind of asymmetric conditional-correlation structure the
 * left-right rules read as causal signal. Only the finished, within-dataset,
 * sign-corrected statistics are combined.</p>
 *
 * <p>Background knowledge is respected: a required or forbidden edge orients the pair
 * regardless of the pooled statistic. Two-cycle detection is not performed in this
 * multi-dataset setting, following FASK-Vote.</p>
 *
 * @author josephramsey
 * @see FaskVote
 * @see Fask
 */
public class FaskPool {

    /**
     * The score wrapper used to construct the IMaGES score for the adjacency stage.
     */
    private final ScoreWrapper score;

    /**
     * The datasets over which the composite graph is constructed.
     */
    private final List<DataSet> dataSets;

    /**
     * Background knowledge containing forbidden and required edges.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Optional external adjacency graph. When set, the IMaGES stage is skipped and this
     * graph's adjacencies (taken as undirected) are oriented instead.
     */
    private Graph externalGraph = null;

    /**
     * The left-right rule used within each dataset.
     */
    private Fask.LeftRight leftRight = Fask.LeftRight.FASK2;

    /**
     * The weighting scheme for pooling the per-dataset statistics.
     */
    private Weighting weighting = Weighting.N;

    /**
     * How the common adjacency structure is obtained when no external graph is set.
     */
    private AdjacencyMethod adjacencyMethod = AdjacencyMethod.IMAGES;

    /**
     * The alpha level for the pooled FAS adjacency search. Only used when the adjacency
     * method is POOLED_FAS.
     */
    private double fasAlpha = 0.05;

    /**
     * The depth of the pooled FAS adjacency search (-1 for unlimited). Only used when
     * the adjacency method is POOLED_FAS or MG_FAS (where -1 is capped at 4, since
     * sepset candidates are restricted to moral neighborhoods).
     */
    private int fasDepth = -1;

    /**
     * Threshold on the pooled (median across datasets) absolute LiNG B-hat entries above
     * which a pair is kept as an adjacency. Only used when the adjacency method involves
     * MG_LING.
     */
    private double lingThreshold = 0.1;

    /**
     * FastICA maximum iterations for the LiNG stage.
     */
    private int fastIcaMaxIter = 10000;

    /**
     * FastICA convergence tolerance for the LiNG stage.
     */
    private double fastIcaTolerance = 1e-6;

    /**
     * FastICA tanh nonlinearity parameter for the LiNG stage.
     */
    private double fastIcaA = 1.1;

    /**
     * Significance level for the orientation decision. Zero (the default) means every
     * adjacency is oriented by the sign of the pooled statistic; above zero, an edge is
     * left undirected unless the pooled statistic differs significantly from zero.
     */
    private double orientationAlpha = 0.0;

    /**
     * Number of bootstrap resamples per dataset used to estimate Var(lr_k). Only used
     * when the weighting is INVERSE_VARIANCE or the orientation alpha is above zero.
     */
    private int numBootstraps = 200;

    /**
     * Per-edge pooled statistics recorded during the last search.
     */
    private final Map<Edge, EdgeStat> edgeStats = new HashMap<>();

    /**
     * Constructs a pooled-FASK search from the supplied datasets and score wrapper.
     *
     * @param dataSets the datasets to search over
     * @param score    the score wrapper for the IMaGES adjacency stage
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if the dataset list is empty
     */
    public FaskPool(List<DataSet> dataSets, ScoreWrapper score) {
        if (dataSets == null) {
            throw new NullPointerException("Dataset list must not be null.");
        }

        if (score == null) {
            throw new NullPointerException("Score wrapper must not be null.");
        }

        if (dataSets.isEmpty()) {
            throw new IllegalArgumentException("At least one dataset is required.");
        }

        this.dataSets = dataSets;
        this.score = score;
    }

    /**
     * Runs the search and returns the composite graph.
     *
     * @param parameters the search parameters (passed to the IMaGES stage)
     * @return the composite graph
     * @throws InterruptedException if the underlying IMaGES search is interrupted
     */
    public Graph search(Parameters parameters) throws InterruptedException {
        this.edgeStats.clear();

        // Standardize each dataset once; both the IMaGES stage and the pooled
        // orientation stage use the standardized columns.
        List<DataSet> standardized = new ArrayList<>();
        for (DataSet dataSet : this.dataSets) {
            standardized.add(DataTransforms.standardizeData(dataSet));
        }

        // Column arrays per dataset, keyed by variable name so datasets need only agree
        // on names, not on Node identity or column order.
        List<Map<String, double[]>> columns = new ArrayList<>();
        for (DataSet dataSet : standardized) {
            Map<String, double[]> map = new HashMap<>();
            double[][] cols = dataSet.getDoubleData().transpose().toArray();
            List<Node> vars = dataSet.getVariables();
            for (int j = 0; j < vars.size(); j++) {
                map.put(vars.get(j).getName(), cols[j]);
            }
            columns.add(map);
        }

        Graph adjacency;

        if (this.externalGraph != null) {
            adjacency = GraphUtils.undirectedGraph(this.externalGraph);
            adjacency = GraphUtils.replaceNodes(adjacency, this.dataSets.get(0).getVariables());
        } else if (this.adjacencyMethod == AdjacencyMethod.POOLED_FAS) {
            // Pooled FAS: stable FAS run once, over all datasets jointly, using a composite
            // test that combines per-dataset Fisher Z p-values by Fisher's method. This is
            // the test-side analog of the IMaGES averaged score.
            IndependenceTest test = new IndTestFisherZFisherPValue(standardized, this.fasAlpha);
            Fas fas = new Fas(test);
            fas.setStable(true);
            fas.setDepth(this.fasDepth);
            fas.setVerbose(false);
            fas.setKnowledge(this.knowledge);
            adjacency = fas.search();
        } else if (this.adjacencyMethod == AdjacencyMethod.MG_FAS
                   || this.adjacencyMethod == AdjacencyMethod.MG_LING
                   || this.adjacencyMethod == AdjacencyMethod.MG_FAS_INTERSECT_LING) {
            adjacency = moralBasedAdjacency(standardized, columns);
        } else {
            List<DataModel> models = new ArrayList<>(standardized);
            Images images = new Images(this.score);
            images.setKnowledge(this.knowledge);
            adjacency = images.search(models, parameters);
        }

        Graph result = new EdgeListGraph(this.dataSets.get(0).getVariables());

        int ruleIndex = this.leftRight.ordinal() + 1;
        boolean needVariances = this.weighting == Weighting.INVERSE_VARIANCE || this.orientationAlpha > 0;
        double zCutoff = this.orientationAlpha > 0
                ? edu.cmu.tetrad.util.StatUtils.getZForAlpha(this.orientationAlpha) : 0.0;

        for (Edge edge : adjacency.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            Node rx = result.getNode(x.getName());
            Node ry = result.getNode(y.getName());

            if (knowledgeOrients(rx, ry)) {
                result.addDirectedEdge(rx, ry);
                continue;
            } else if (knowledgeOrients(ry, rx)) {
                result.addDirectedEdge(ry, rx);
                continue;
            }

            int m = this.dataSets.size();
            double[] lr = new double[m];
            double[] var = new double[m];
            double[] w = new double[m];

            for (int k = 0; k < m; k++) {
                double[] xk = columns.get(k).get(x.getName());
                double[] yk = columns.get(k).get(y.getName());

                if (xk == null || yk == null) {
                    throw new IllegalArgumentException("Variable missing from dataset "
                            + (k + 1) + ": " + (xk == null ? x.getName() : y.getName()));
                }

                // Skew-sign correction is applied inside leftRightDiff, per dataset,
                // which is essential: the correction is a per-dataset gauge fix and
                // must precede pooling.
                lr[k] = Fask.leftRightDiff(xk, yk, ruleIndex);

                if (needVariances) {
                    var[k] = bootstrapVariance(xk, yk, ruleIndex);
                }
            }

            double sumW = 0.0;
            for (int k = 0; k < m; k++) {
                if (this.weighting == Weighting.INVERSE_VARIANCE) {
                    // Guard against degenerate bootstrap variances.
                    w[k] = 1.0 / Math.max(var[k], 1e-12);
                } else {
                    w[k] = columns.get(k).get(x.getName()).length;
                }
                sumW += w[k];
            }

            double pooled = 0.0;
            for (int k = 0; k < m; k++) pooled += w[k] * lr[k];
            pooled /= sumW;

            // Standard error of the pooled statistic and Cochran Q, when variances exist.
            double se = Double.NaN;
            double z = Double.NaN;
            double q = Double.NaN;

            if (needVariances) {
                double varPooled = 0.0;
                for (int k = 0; k < m; k++) varPooled += w[k] * w[k] * var[k];
                varPooled /= (sumW * sumW);
                se = Math.sqrt(varPooled);
                z = pooled / se;

                q = 0.0;
                for (int k = 0; k < m; k++) {
                    double d = lr[k] - pooled;
                    q += d * d / Math.max(var[k], 1e-12);
                }
            }

            Edge added;

            if (this.orientationAlpha > 0 && Math.abs(z) < zCutoff) {
                result.addUndirectedEdge(rx, ry);
                added = result.getEdge(rx, ry);
            } else if (pooled > 0) {
                result.addDirectedEdge(rx, ry);
                added = result.getEdge(rx, ry);
            } else {
                result.addDirectedEdge(ry, rx);
                added = result.getEdge(rx, ry);
            }

            this.edgeStats.put(added, new EdgeStat(pooled, z, q, m));
        }

        return result;
    }

    /**
     * Estimates the sampling variance of the left-right statistic within one dataset by
     * a pairwise bootstrap: rows of (x, y) are resampled jointly, each resample is
     * re-standardized, and the statistic is recomputed.
     *
     * @param x         standardized series for X
     * @param y         standardized series for Y
     * @param ruleIndex the left-right rule index (1..5)
     * @return the bootstrap variance of the statistic
     */
    private double bootstrapVariance(double[] x, double[] y, int ruleIndex) {
        int n = x.length;
        RandomUtil random = RandomUtil.getInstance();

        double[] stats = new double[this.numBootstraps];
        double[] bx = new double[n];
        double[] by = new double[n];

        for (int b = 0; b < this.numBootstraps; b++) {
            for (int i = 0; i < n; i++) {
                int r = random.nextInt(n);
                bx[i] = x[r];
                by[i] = y[r];
            }

            double[] sx = bx.clone();
            double[] sy = by.clone();
            Fask.standardize(sx);
            Fask.standardize(sy);

            stats[b] = Fask.leftRightDiff(sx, sy, ruleIndex);
        }

        double mean = 0.0;
        for (double s : stats) mean += s;
        mean /= this.numBootstraps;

        double v = 0.0;
        for (double s : stats) {
            double d = s - mean;
            v += d * d;
        }

        return v / (this.numBootstraps - 1);
    }

    // ------------ Moral-graph-based adjacency (MG_FAS / MG_LING / intersection) ------------

    /**
     * Computes the adjacency graph for the MG_* methods. First the pooled moral graph is
     * estimated: for each pair, the full-order partial correlation (given all other
     * variables) is computed within each dataset and the two-sided p-values are combined
     * across datasets by Fisher's method, with a Bonferroni correction over pairs at the
     * FAS alpha. For linear SEMs with independent errors -- cyclic or not -- the
     * precision support is the moral graph, so this stage is cycle-safe and its recall
     * guarantee requires only that the relevant cancellation not occur in every dataset
     * at once. The moral graph is then de-moralized by MG_FAS, MG_LING, or their
     * intersection. Background knowledge is not consulted at this stage; required and
     * forbidden edges are enforced at orientation time as usual.
     */
    private Graph moralBasedAdjacency(List<DataSet> standardized, List<Map<String, double[]>> columns) {
        List<Node> vars = this.dataSets.get(0).getVariables();
        int p = vars.size();
        int numData = standardized.size();

        // Per-dataset correlation matrices in the common variable order.
        List<double[][]> Rs = new ArrayList<>();
        int[] ns = new int[numData];

        for (int d = 0; d < numData; d++) {
            double[][] cols = new double[p][];
            for (int i = 0; i < p; i++) {
                double[] c = columns.get(d).get(vars.get(i).getName());
                if (c == null) {
                    throw new IllegalArgumentException("Variable missing from dataset "
                            + (d + 1) + ": " + vars.get(i).getName());
                }
                cols[i] = c;
            }
            ns[d] = cols[0].length;
            Rs.add(correlationMatrix(cols));
        }

        // Pooled moral graph.
        double bonferroni = this.fasAlpha / (p * (p - 1) / 2.0);
        boolean[][] moral = new boolean[p][p];

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                int[] S = new int[p - 2];
                int t = 0;
                for (int k = 0; k < p; k++) if (k != i && k != j) S[t++] = k;
                if (pooledIndepP(Rs, ns, i, j, S) <= bonferroni) {
                    moral[i][j] = true;
                    moral[j][i] = true;
                }
            }
        }

        boolean[][] adj;

        if (this.adjacencyMethod == AdjacencyMethod.MG_FAS) {
            adj = mgFasPrune(moral, Rs, ns, p);
        } else if (this.adjacencyMethod == AdjacencyMethod.MG_LING) {
            adj = mgLingSelect(moral, standardized, vars, p);
        } else {
            boolean[][] a = mgFasPrune(moral, Rs, ns, p);
            boolean[][] b = mgLingSelect(moral, standardized, vars, p);
            adj = new boolean[p][p];
            for (int i = 0; i < p; i++)
                for (int j = 0; j < p; j++) adj[i][j] = a[i][j] && b[i][j];
        }

        Graph g = new EdgeListGraph(vars);
        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                if (adj[i][j]) g.addUndirectedEdge(vars.get(i), vars.get(j));
            }
        }
        return g;
    }

    /**
     * FAS-style pruning of the moral graph: for each moral edge, search conditioning
     * sets among the moral neighborhoods of its endpoints (fixed at the moral graph, as
     * FAS fixes candidates at the current graph); remove the edge when the pooled test
     * accepts independence at the FAS alpha.
     */
    private boolean[][] mgFasPrune(boolean[][] moral, List<double[][]> Rs, int[] ns, int p) {
        boolean[][] adj = new boolean[p][p];
        for (int i = 0; i < p; i++) adj[i] = moral[i].clone();

        int maxDepth = this.fasDepth < 0 ? 4 : this.fasDepth;

        for (int depth = 0; depth <= maxDepth; depth++) {
            boolean removed = true;
            while (removed) {
                removed = false;
                outer:
                for (int i = 0; i < p; i++) {
                    for (int j = i + 1; j < p; j++) {
                        if (!adj[i][j]) continue;

                        List<Integer> pool = new ArrayList<>();
                        for (int k = 0; k < p; k++) {
                            if (k != i && k != j && (moral[i][k] || moral[j][k])) pool.add(k);
                        }
                        if (pool.size() < depth) continue;

                        ChoiceGenerator gen = new ChoiceGenerator(pool.size(), depth);
                        int[] choice;
                        while ((choice = gen.next()) != null) {
                            int[] S = new int[depth];
                            for (int t = 0; t < depth; t++) S[t] = pool.get(choice[t]);
                            if (pooledIndepP(Rs, ns, i, j, S) > this.fasAlpha) {
                                adj[i][j] = false;
                                adj[j][i] = false;
                                removed = true;
                                break outer;
                            }
                        }
                    }
                }
            }
        }
        return adj;
    }

    /**
     * LiNG-style pooled adjacency selection inside the moral graph: per dataset,
     * estimate B by LingD (FastICA, Hungarian diagonal maximization, unit-diagonal
     * scaling); pool by the median across datasets of max(|B_ij|, |B_ji|); keep moral
     * pairs whose pooled statistic reaches the LiNG threshold. A dataset whose ICA
     * fails contributes zeros (the median is robust to a few failures).
     */
    private boolean[][] mgLingSelect(boolean[][] moral, List<DataSet> standardized,
                                     List<Node> vars, int p) {
        int numData = standardized.size();
        double[][][] absB = new double[numData][p][p];

        for (int d = 0; d < numData; d++) {
            DataSet dataSet = standardized.get(d);
            int[] idx = new int[p];
            for (int i = 0; i < p; i++) {
                idx[i] = dataSet.getColumnIndex(vars.get(i).getName());
                if (idx[i] < 0) {
                    throw new IllegalArgumentException("Variable missing from dataset "
                            + (d + 1) + ": " + vars.get(i).getName());
                }
            }
            try {
                Matrix w = LingD.estimateW(dataSet, this.fastIcaMaxIter,
                        this.fastIcaTolerance, this.fastIcaA);
                Matrix bHat = LingD.getScaledBHat(LingD.maximizeDiagonal(w));
                for (int i = 0; i < p; i++) {
                    for (int j = 0; j < p; j++) {
                        absB[d][i][j] = Math.abs(bHat.get(idx[i], idx[j]));
                    }
                }
            } catch (Exception e) {
                // Leave zeros for this dataset.
            }
        }

        boolean[][] adj = new boolean[p][p];
        double[] vals = new double[numData];

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                if (!moral[i][j]) continue;
                for (int d = 0; d < numData; d++) {
                    vals[d] = Math.max(absB[d][i][j], absB[d][j][i]);
                }
                double[] sorted = vals.clone();
                Arrays.sort(sorted);
                double median = numData % 2 == 1 ? sorted[numData / 2]
                        : 0.5 * (sorted[numData / 2 - 1] + sorted[numData / 2]);
                if (median >= this.lingThreshold) {
                    adj[i][j] = true;
                    adj[j][i] = true;
                }
            }
        }
        return adj;
    }

    /**
     * Pearson correlation matrix of the given columns.
     */
    private static double[][] correlationMatrix(double[][] cols) {
        int p = cols.length;
        int n = cols[0].length;
        double[] mean = new double[p];
        double[] sd = new double[p];

        for (int i = 0; i < p; i++) {
            double m = 0.0;
            for (double v : cols[i]) m += v;
            m /= n;
            mean[i] = m;
            double s = 0.0;
            for (double v : cols[i]) s += (v - m) * (v - m);
            sd[i] = Math.sqrt(s / (n - 1));
            if (sd[i] == 0) sd[i] = 1.0;
        }

        double[][] R = new double[p][p];
        for (int i = 0; i < p; i++) {
            R[i][i] = 1.0;
            for (int j = i + 1; j < p; j++) {
                double s = 0.0;
                for (int t = 0; t < n; t++) {
                    s += (cols[i][t] - mean[i]) * (cols[j][t] - mean[j]);
                }
                double r = s / ((n - 1) * sd[i] * sd[j]);
                R[i][j] = r;
                R[j][i] = r;
            }
        }
        return R;
    }

    /**
     * Partial correlation of i and j given S, from a correlation matrix, via inversion
     * of the submatrix over {i, j} union S. Returns NaN if the submatrix is singular.
     */
    private static double partialCorrelation(double[][] R, int i, int j, int[] S) {
        int m = 2 + S.length;
        int[] idx = new int[m];
        idx[0] = i;
        idx[1] = j;
        System.arraycopy(S, 0, idx, 2, S.length);

        double[][] sub = new double[m][m];
        for (int a = 0; a < m; a++) {
            for (int b = 0; b < m; b++) sub[a][b] = R[idx[a]][idx[b]];
        }

        try {
            Matrix inv = new Matrix(sub).inverse();
            double d = inv.get(0, 0) * inv.get(1, 1);
            if (d <= 0) return Double.NaN;
            double r = -inv.get(0, 1) / Math.sqrt(d);
            return Math.max(-0.9999999, Math.min(0.9999999, r));
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    /**
     * Combined two-sided p-value for independence of i and j given S across the
     * datasets, by Fisher's method on per-dataset Fisher-Z p-values. Sign-agnostic by
     * design: coefficients may differ in sign across datasets, so a signed combination
     * would cancel genuine dependence. Returns 1 if no dataset contributes.
     */
    private double pooledIndepP(List<double[][]> Rs, int[] ns, int i, int j, int[] S) {
        double sumLog = 0.0;
        int m = 0;

        for (int d = 0; d < Rs.size(); d++) {
            int df = ns[d] - S.length - 3;
            if (df <= 0) continue;
            double r = partialCorrelation(Rs.get(d), i, j, S);
            if (Double.isNaN(r)) continue;
            double z = Math.abs(0.5 * Math.log((1 + r) / (1 - r))) * Math.sqrt(df);
            double p2 = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, z));
            if (p2 <= 0.0) return 0.0;  // underflow: overwhelming dependence
            sumLog += Math.log(p2);
            m++;
        }

        if (m == 0) return 1.0;
        double t = -2.0 * sumLog;
        return 1.0 - new ChiSquaredDistribution(2.0 * m).cumulativeProbability(t);
    }

    private boolean knowledgeOrients(Node left, Node right) {
        return this.knowledge.isForbidden(right.getName(), left.getName())
                || this.knowledge.isRequired(left.getName(), right.getName());
    }

    /**
     * Sets background knowledge for the search. A defensive copy is stored.
     *
     * @param knowledge knowledge containing forbidden and required edges
     * @throws NullPointerException if the supplied knowledge is null
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets an external adjacency graph. When set, the IMaGES stage is skipped and this
     * graph's adjacencies (taken as undirected) are oriented instead.
     *
     * @param externalGraph the external graph, or null to use IMaGES
     */
    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    /**
     * Sets the left-right rule used within each dataset. Default: FASK2.
     *
     * @param leftRight the left-right rule
     */
    public void setLeftRight(Fask.LeftRight leftRight) {
        this.leftRight = leftRight;
    }

    /**
     * Sets the weighting scheme for pooling. Default: N (sample-size weights).
     *
     * @param weighting the weighting scheme
     */
    public void setWeighting(Weighting weighting) {
        this.weighting = weighting;
    }

    /**
     * Sets how the common adjacency structure is obtained when no external graph is set.
     * Default: IMAGES. Ignored when an external graph has been set.
     *
     * @param adjacencyMethod the adjacency method
     */
    public void setAdjacencyMethod(AdjacencyMethod adjacencyMethod) {
        this.adjacencyMethod = adjacencyMethod;
    }

    /**
     * Sets the alpha level for the pooled FAS adjacency search. Default: 0.05. Only used
     * when the adjacency method is POOLED_FAS.
     *
     * @param fasAlpha the alpha level, in (0, 1)
     */
    public void setFasAlpha(double fasAlpha) {
        if (fasAlpha <= 0.0 || fasAlpha >= 1.0) {
            throw new IllegalArgumentException("Alpha out of range: " + fasAlpha);
        }
        this.fasAlpha = fasAlpha;
    }

    /**
     * Sets the depth of the pooled FAS adjacency search (-1 for unlimited). Default: -1.
     * Only used when the adjacency method is POOLED_FAS or MG_FAS (where -1 is capped
     * at 4).
     *
     * @param fasDepth the depth
     */
    public void setFasDepth(int fasDepth) {
        this.fasDepth = fasDepth;
    }

    /**
     * Sets the threshold on the pooled absolute LiNG B-hat entries above which a moral
     * pair is kept as an adjacency. Default: 0.1. Only used when the adjacency method
     * involves MG_LING.
     *
     * @param lingThreshold the threshold, nonnegative
     */
    public void setLingThreshold(double lingThreshold) {
        if (lingThreshold < 0.0) {
            throw new IllegalArgumentException("Threshold must be nonnegative: " + lingThreshold);
        }
        this.lingThreshold = lingThreshold;
    }

    /**
     * Sets the FastICA maximum iterations for the LiNG stage. Default: 10000.
     *
     * @param fastIcaMaxIter maximum iterations, positive
     */
    public void setFastIcaMaxIter(int fastIcaMaxIter) {
        this.fastIcaMaxIter = fastIcaMaxIter;
    }

    /**
     * Sets the FastICA convergence tolerance for the LiNG stage. Default: 1e-6.
     *
     * @param fastIcaTolerance the tolerance, positive
     */
    public void setFastIcaTolerance(double fastIcaTolerance) {
        this.fastIcaTolerance = fastIcaTolerance;
    }

    /**
     * Sets the FastICA tanh nonlinearity parameter for the LiNG stage. Default: 1.1.
     *
     * @param fastIcaA the parameter
     */
    public void setFastIcaA(double fastIcaA) {
        this.fastIcaA = fastIcaA;
    }

    /**
     * Sets the significance level for the orientation decision. Zero (the default)
     * orients every adjacency by the sign of the pooled statistic; above zero, an edge
     * is left undirected unless the pooled statistic differs significantly from zero at
     * this level (two-sided, bootstrap standard error).
     *
     * @param orientationAlpha the significance level, in [0, 1]
     */
    public void setOrientationAlpha(double orientationAlpha) {
        if (orientationAlpha < 0.0 || orientationAlpha > 1.0) {
            throw new IllegalArgumentException("Alpha out of range: " + orientationAlpha);
        }
        this.orientationAlpha = orientationAlpha;
    }

    /**
     * Sets the number of bootstrap resamples per dataset used to estimate Var(lr_k).
     * Default: 200.
     *
     * @param numBootstraps the number of resamples, at least 2
     */
    public void setNumBootstraps(int numBootstraps) {
        if (numBootstraps < 2) {
            throw new IllegalArgumentException("At least 2 bootstraps required: " + numBootstraps);
        }
        this.numBootstraps = numBootstraps;
    }

    /**
     * Returns the per-edge pooled statistics recorded during the last search, keyed by
     * the edge added to the result graph. The z and Q entries are NaN when bootstrap
     * variances were not computed (n-weighting with orientation alpha zero).
     *
     * @return an unmodifiable view of the per-edge statistics
     */
    public Map<Edge, EdgeStat> getEdgeStats() {
        return java.util.Collections.unmodifiableMap(this.edgeStats);
    }

    /**
     * How the common adjacency structure is obtained when no external graph is set.
     */
    public enum AdjacencyMethod {
        /**
         * IMaGES: BOSS on the score averaged across datasets.
         */
        IMAGES,
        /**
         * Pooled FAS: stable FAS with a composite test combining per-dataset Fisher Z
         * p-values by Fisher's method.
         */
        POOLED_FAS,
        /**
         * Pooled moral graph (full-order partial correlations, Fisher-pooled across
         * datasets), then FAS-style sepset pruning restricted to moral neighborhoods.
         * The moral graph is a proved superset of the skeleton for linear cyclic SEMs
         * (precision support = adjacencies plus common-child pairs), so this method is
         * cycle-safe. CI-based pruning cannot remove Richardson-style virtual
         * adjacencies, which cap its precision around cycles.
         */
        MG_FAS,
        /**
         * Pooled moral graph, then LiNG-style de-moralization: per-dataset ICA estimate
         * of B (LingD: FastICA, Hungarian diagonal maximization, unit-diagonal scaling),
         * pooled by the median across datasets of max(|B_ij|, |B_ji|), thresholded.
         * Requires non-Gaussian errors; with Gaussian errors ICA is unidentified and
         * this degenerates toward the moral graph.
         */
        MG_LING,
        /**
         * The intersection of MG_FAS and MG_LING. Both tend to full recall inside the
         * moral graph with different false positives, so the intersection trades little
         * recall for precision. Best choice for skewed (FASK-appropriate) data; use
         * MG_FAS when errors may be Gaussian.
         */
        MG_FAS_INTERSECT_LING
    }

    /**
     * The weighting scheme for pooling per-dataset left-right statistics.
     */
    public enum Weighting {
        /**
         * Sample-size weights, w_k = n_k. Exact when datasets differ only in size.
         */
        N,
        /**
         * Inverse bootstrap-variance weights, w_k = 1 / Var(lr_k). The fixed-effect
         * meta-analysis estimator; downweights noisy datasets whatever their size.
         */
        INVERSE_VARIANCE
    }

    /**
     * Pooled statistics for one edge.
     *
     * @param pooledLr the pooled left-right statistic (positive favored node1 to node2
     *                 at decision time)
     * @param z        the pooled statistic divided by its bootstrap standard error, or
     *                 NaN if variances were not computed
     * @param q        the Cochran Q heterogeneity statistic across datasets, or NaN if
     *                 variances were not computed; large values flag edges on which the
     *                 datasets genuinely disagree
     * @param numDataSets the number of datasets pooled
     */
    public record EdgeStat(double pooledLr, double z, double q, int numDataSets) {
    }
}
