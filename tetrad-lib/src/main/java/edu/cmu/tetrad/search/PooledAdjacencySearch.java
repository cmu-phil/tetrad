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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Estimates a common adjacency (skeleton) structure across multiple datasets sharing the
 * same variables. This is the shared adjacency stage of the multi-dataset FASK
 * algorithms (FASK-Pool and FASK-Vote); the caller supplies standardized datasets and
 * receives an undirected graph whose edges are the estimated adjacencies.
 *
 * <p>Methods:</p>
 *
 * <ul>
 *   <li>{@link Method#IMAGES}: BOSS on the score averaged across datasets (the
 *   classical IMaGES adjacency stage; assumes acyclicity).</li>
 *   <li>{@link Method#POOLED_FAS}: stable FAS with a composite test that combines
 *   per-dataset Fisher Z p-values by Fisher's method.</li>
 *   <li>{@link Method#MG_FAS}: the pooled moral graph (full-order partial
 *   correlations, Fisher-combined across datasets, Bonferroni over pairs), pruned by
 *   FAS-style sepset tests restricted to moral neighborhoods. For linear SEMs with
 *   independent errors -- cyclic or not -- the precision support is the moral graph
 *   (adjacencies plus common-child pairs), so this method is cycle-safe; CI-based
 *   pruning cannot remove Richardson-style virtual adjacencies, which cap its
 *   precision around cycles.</li>
 *   <li>{@link Method#MG_LING}: the pooled moral graph filtered by per-dataset ICA
 *   (LiNG) estimates of B, pooled by the median across datasets of max(|B_ij|,
 *   |B_ji|), thresholded. Requires non-Gaussian errors; with Gaussian errors ICA is
 *   unidentified and this degenerates toward the moral graph.</li>
 *   <li>{@link Method#MG_FAS_INTERSECT_LING}: the intersection of MG_FAS and MG_LING.
 *   Both tend to full recall inside the moral graph with different false positives, so
 *   the intersection trades little recall for precision. Best for skewed
 *   (FASK-appropriate) data; use MG_FAS when errors may be Gaussian.</li>
 *   <li>{@link Method#IMAGES_RESTRICT_MG_FAS}: IMaGES restricted to the MG_FAS
 *   skeleton, which is imposed as a superstructure by forbidding both directions of
 *   every pair outside it. A DAG-based score search on cyclic data adds adjacencies to
 *   compensate for the cycles it cannot represent, many of them moralized pairs, which
 *   is exactly what MG_FAS prunes; restricting the search to that skeleton keeps the
 *   moral-graph method's precision while letting the score decide which of its
 *   adjacencies survive. Output adjacencies are a subset of MG_FAS's, so recall cannot
 *   exceed MG_FAS's.</li>
 * </ul>
 *
 * <p>Background knowledge is passed to the IMaGES and pooled-FAS stages; the
 * moral-graph stages do not consult knowledge (required and forbidden edges are for
 * the caller's orientation stage to enforce).</p>
 *
 * @author josephramsey
 * @see FaskPool
 * @see FaskVote
 */
public class PooledAdjacencySearch {

    /**
     * The score wrapper used to construct the IMaGES score when the method is IMAGES.
     */
    private final ScoreWrapper score;

    /**
     * Background knowledge, passed to the IMaGES and pooled-FAS stages.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The adjacency method.
     */
    private Method method = Method.IMAGES;

    /**
     * The alpha level for the FAS-style tests (and, Bonferroni-corrected, for the moral
     * graph stage).
     */
    private double fasAlpha = 0.05;

    /**
     * The depth of the FAS-style searches (-1 for unlimited; capped at 4 in MG_FAS,
     * where sepset candidates are restricted to moral neighborhoods).
     */
    private int fasDepth = -1;

    /**
     * Threshold on the pooled (median across datasets) absolute LiNG B-hat entries
     * above which a moral pair is kept as an adjacency.
     */
    private double lingThreshold = 0.1;

    /**
     * FastICA maximum iterations for the LiNG stage.
     */
    private int fastIcaMaxIter = 2000;

    /**
     * FastICA convergence tolerance for the LiNG stage.
     */
    private double fastIcaTolerance = 1e-6;

    /**
     * FastICA tanh nonlinearity parameter for the LiNG stage.
     */
    private double fastIcaA = 1.1;

    /**
     * Constructs the search with the given score wrapper (used only by the IMAGES
     * method; may be null for the other methods).
     *
     * @param score the score wrapper for IMaGES
     */
    public PooledAdjacencySearch(ScoreWrapper score) {
        this.score = score;
    }

    /**
     * Estimates the common adjacency structure over the given standardized datasets,
     * discarding any orientations the method produces.
     *
     * @param standardized the standardized datasets, sharing variable names
     * @param parameters   search parameters (passed through to the IMaGES stage)
     * @return an undirected graph over the first dataset's variables whose edges are
     * the estimated adjacencies
     * @throws InterruptedException if an underlying search is interrupted
     */
    public Graph search(List<DataSet> standardized, Parameters parameters) throws InterruptedException {
        return GraphUtils.undirectedGraph(searchWithOrientations(standardized, parameters));
    }

    /**
     * Estimates the common adjacency structure over the given standardized datasets,
     * retaining whatever orientations the method produces. Only {@link Method#IMAGES}
     * produces any: the CPDAG of the BOSS search over the averaged score, whose
     * compelled orientations are score-based evidence about direction. The other
     * methods return undirected graphs, so for them this is the same as
     * {@link #search(List, Parameters)}.
     *
     * @param standardized the standardized datasets, sharing variable names
     * @param parameters   search parameters (passed through to the IMaGES stage)
     * @return a graph over the first dataset's variables whose edges are the estimated
     * adjacencies, directed where the method compels a direction
     * @throws InterruptedException if an underlying search is interrupted
     */
    public Graph searchWithOrientations(List<DataSet> standardized, Parameters parameters) throws InterruptedException {
        if (standardized == null || standardized.isEmpty()) {
            throw new IllegalArgumentException("At least one dataset is required.");
        }

        if (this.method == Method.IMAGES) {
            if (this.score == null) {
                throw new IllegalStateException("The IMAGES adjacency method requires a score wrapper.");
            }
            List<DataModel> models = new ArrayList<>(standardized);
            Images images = new Images(this.score);
            images.setKnowledge(this.knowledge);
            return images.search(models, parameters);
        }

        if (this.method == Method.POOLED_FAS) {
            IndependenceTest test = new IndTestFisherZFisherPValue(standardized, this.fasAlpha);
            Fas fas = new Fas(test);
            fas.setStable(true);
            fas.setDepth(this.fasDepth);
            fas.setVerbose(false);
            fas.setKnowledge(this.knowledge);
            return GraphUtils.undirectedGraph(fas.search());
        }

        if (this.method == Method.IMAGES_RESTRICT_MG_FAS) {
            if (this.score == null) {
                throw new IllegalStateException("The restricted-IMaGES adjacency method requires a score wrapper.");
            }

            // Stage 1: the MG-FAS skeleton, which is cycle-safe (it never leaves the
            // pooled moral graph, and its sepset pruning removes the married pairs).
            Graph superstructure = moralBasedAdjacency(standardized, Method.MG_FAS);

            // Stage 2: IMaGES restricted to that skeleton. BOSS consults knowledge when
            // collecting candidate parents (PermutationSearch.setKnowledge hands each
            // node's forbidden parents to its grow-shrink tree), so forbidding BOTH
            // directions of a pair keeps it non-adjacent in the result.
            Knowledge restricted = new Knowledge(this.knowledge);
            List<Node> vars = superstructure.getNodes();

            for (int i = 0; i < vars.size(); i++) {
                for (int j = i + 1; j < vars.size(); j++) {
                    Node a = vars.get(i);
                    Node b = vars.get(j);

                    if (!superstructure.isAdjacentTo(a, b)) {
                        restricted.setForbidden(a.getName(), b.getName());
                        restricted.setForbidden(b.getName(), a.getName());
                    }
                }
            }

            List<DataModel> models = new ArrayList<>(standardized);
            Images images = new Images(this.score);
            images.setKnowledge(restricted);
            Graph result = GraphUtils.undirectedGraph(images.search(models, parameters));

            // Belt and braces: intersect with the superstructure, so the guarantee that
            // no adjacency outside it can appear does not depend on knowledge being
            // honored everywhere downstream.
            result = GraphUtils.replaceNodes(result, vars);

            for (Edge edge : new ArrayList<>(result.getEdges())) {
                if (!superstructure.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    result.removeEdge(edge);
                }
            }

            return result;
        }

        return moralBasedAdjacency(standardized, this.method);
    }

    // ------------ Moral-graph-based methods ------------

    /**
     * Computes the adjacency graph for the MG_* methods; see the class Javadoc. All
     * full-order partial correlations come from one inversion of the correlation matrix
     * per dataset (the precision matrix K: r_ij.rest = -K_ij / sqrt(K_ii K_jj)).
     */
    private Graph moralBasedAdjacency(List<DataSet> standardized, Method effectiveMethod) {
        List<Node> vars = standardized.get(0).getVariables();
        int p = vars.size();
        int numData = standardized.size();

        // Per-dataset column arrays and correlation matrices in the common variable
        // order, matched by name.
        List<double[][]> Rs = new ArrayList<>();
        int[] ns = new int[numData];

        for (int d = 0; d < numData; d++) {
            DataSet dataSet = standardized.get(d);
            double[][] all = dataSet.getDoubleData().transpose().toArray();
            double[][] cols = new double[p][];
            for (int i = 0; i < p; i++) {
                int idx = dataSet.getColumnIndex(vars.get(i).getName());
                if (idx < 0) {
                    throw new IllegalArgumentException("Variable missing from dataset "
                            + (d + 1) + ": " + vars.get(i).getName());
                }
                cols[i] = all[idx];
            }
            ns[d] = cols[0].length;
            Rs.add(correlationMatrix(cols));
        }

        // Pooled moral graph.
        double bonferroni = this.fasAlpha / (p * (p - 1) / 2.0);
        boolean[][] moral = new boolean[p][p];

        List<double[][]> Ks = new ArrayList<>();
        List<Integer> okData = new ArrayList<>();
        for (int d = 0; d < numData; d++) {
            try {
                Ks.add(new Matrix(Rs.get(d)).inverse().toArray());
                okData.add(d);
            } catch (Exception e) {
                // Singular correlation matrix; this dataset contributes nothing here.
            }
        }

        double[] rBuf = new double[okData.size()];
        int[] dfBuf = new int[okData.size()];

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                for (int t = 0; t < okData.size(); t++) {
                    double[][] K = Ks.get(t);
                    double den = K[i][i] * K[j][j];
                    double r = den > 0 ? -K[i][j] / Math.sqrt(den) : 0.0;
                    rBuf[t] = Math.max(-0.9999999, Math.min(0.9999999, r));
                    dfBuf[t] = ns[okData.get(t)] - (p - 2) - 3;
                }
                if (fisherCombinedP(rBuf, dfBuf) <= bonferroni) {
                    moral[i][j] = true;
                    moral[j][i] = true;
                }
            }
        }

        boolean[][] adj;

        if (effectiveMethod == Method.MG_FAS) {
            adj = mgFasPrune(moral, Rs, ns, p);
        } else if (effectiveMethod == Method.MG_LING) {
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
     * sets among the moral neighborhoods of each endpoint separately (subsets of
     * N(i) minus j, then of N(j) minus i, as FAS does); remove the edge when the pooled
     * test accepts independence at the FAS alpha. One pass per depth; a removal takes
     * effect immediately and the scan continues.
     */
    private boolean[][] mgFasPrune(boolean[][] moral, List<double[][]> Rs, int[] ns, int p) {
        boolean[][] adj = new boolean[p][p];
        for (int i = 0; i < p; i++) adj[i] = moral[i].clone();

        int maxDepth = this.fasDepth < 0 ? 4 : this.fasDepth;

        for (int depth = 0; depth <= maxDepth; depth++) {
            for (int i = 0; i < p; i++) {
                for (int j = i + 1; j < p; j++) {
                    if (!adj[i][j]) continue;
                    if (searchSepset(moral, Rs, ns, p, i, j, depth)
                        || (depth > 0 && searchSepset(moral, Rs, ns, p, j, i, depth))) {
                        adj[i][j] = false;
                        adj[j][i] = false;
                    }
                }
            }
        }
        return adj;
    }

    /**
     * @return true if some subset of the moral neighbors of {@code from} (excluding
     * {@code other}) of the given size renders the pair independent under the pooled
     * test.
     */
    private boolean searchSepset(boolean[][] moral, List<double[][]> Rs, int[] ns, int p,
                                 int from, int other, int depth) {
        List<Integer> pool = new ArrayList<>();
        for (int k = 0; k < p; k++) {
            if (k != from && k != other && moral[from][k]) pool.add(k);
        }
        if (pool.size() < depth) return false;

        ChoiceGenerator gen = new ChoiceGenerator(pool.size(), depth);
        int[] choice;
        while ((choice = gen.next()) != null) {
            int[] S = new int[depth];
            for (int t = 0; t < depth; t++) S[t] = pool.get(choice[t]);
            if (pooledIndepP(Rs, ns, from, other, S) > this.fasAlpha) {
                return true;
            }
        }
        return false;
    }

    /**
     * LiNG-style pooled adjacency selection inside the moral graph: per dataset,
     * estimate B by LingD (FastICA, Hungarian diagonal maximization, unit-diagonal
     * scaling); pool by the median across datasets of max(|B_ij|, |B_ji|); keep moral
     * pairs whose pooled statistic reaches the LiNG threshold. The per-dataset ICA
     * estimates are independent and dominate this method's runtime, so they run in
     * parallel; a dataset whose ICA fails contributes zeros (the median is robust to a
     * few failures).
     */
    private boolean[][] mgLingSelect(boolean[][] moral, List<DataSet> standardized,
                                     List<Node> vars, int p) {
        int numData = standardized.size();
        double[][][] absB = new double[numData][p][p];

        java.util.stream.IntStream.range(0, numData).parallel().forEach(d -> {
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
        });

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

    // ------------ Statistics ------------

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
     * would cancel genuine dependence.
     */
    private double pooledIndepP(List<double[][]> Rs, int[] ns, int i, int j, int[] S) {
        double[] rs = new double[Rs.size()];
        int[] dfs = new int[Rs.size()];

        for (int d = 0; d < Rs.size(); d++) {
            dfs[d] = ns[d] - S.length - 3;
            rs[d] = partialCorrelation(Rs.get(d), i, j, S);
        }

        return fisherCombinedP(rs, dfs);
    }

    /**
     * Fisher's method on per-dataset two-sided Fisher-Z p-values for the given partial
     * correlations and degrees of freedom. Entries with NaN correlation or nonpositive
     * df are skipped. The chi-square tail with 2m degrees of freedom is the regularized
     * upper incomplete gamma function Q(m, t/2), computed without constructing a
     * distribution object per call.
     */
    private static double fisherCombinedP(double[] rs, int[] dfs) {
        double sumLog = 0.0;
        int m = 0;

        for (int d = 0; d < rs.length; d++) {
            if (dfs[d] <= 0 || Double.isNaN(rs[d])) continue;
            double z = Math.abs(0.5 * Math.log((1 + rs[d]) / (1 - rs[d]))) * Math.sqrt(dfs[d]);
            double p2 = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, z));
            if (p2 <= 0.0) return 0.0;  // underflow: overwhelming dependence
            sumLog += Math.log(p2);
            m++;
        }

        if (m == 0) return 1.0;
        double t = -2.0 * sumLog;
        return org.apache.commons.math3.special.Gamma.regularizedGammaQ(m, t / 2.0);
    }

    // ------------ Setters ------------

    /**
     * Sets background knowledge, passed to the IMaGES and pooled-FAS stages.
     *
     * @param knowledge the knowledge; not null
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException("Knowledge must not be null.");
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the adjacency method. Default: IMAGES.
     *
     * @param method the method
     */
    public void setMethod(Method method) {
        this.method = method;
    }

    /**
     * Sets the alpha level for the FAS-style tests. Default: 0.05.
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
     * Sets the depth of the FAS-style searches (-1 for unlimited; capped at 4 in
     * MG_FAS). Default: -1.
     *
     * @param fasDepth the depth
     */
    public void setFasDepth(int fasDepth) {
        this.fasDepth = fasDepth;
    }

    /**
     * Sets the threshold on the pooled absolute LiNG B-hat entries. Default: 0.1.
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
     * Sets the FastICA maximum iterations for the LiNG stage. Default: 2000.
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
     * How the common adjacency structure is estimated.
     */
    public enum Method {
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
         * Pooled moral graph pruned by FAS-style sepset tests restricted to moral
         * neighborhoods; cycle-safe.
         */
        MG_FAS,
        /**
         * Pooled moral graph filtered by per-dataset ICA (LiNG) estimates of B;
         * requires non-Gaussian errors.
         */
        MG_LING,
        /**
         * The intersection of MG_FAS and MG_LING.
         */
        MG_FAS_INTERSECT_LING,
        /**
         * IMaGES restricted to the MG_FAS skeleton: the MG-FAS adjacencies are computed
         * first and supplied to the IMaGES (BOSS) search as a superstructure, by
         * forbidding both directions of every pair outside it, so the score search can
         * only remove adjacencies from that skeleton, never add them. Motivation: on
         * cyclic data a DAG-based score search adds adjacencies to compensate for the
         * cycles it cannot represent, many of them moralized (married) pairs, whereas
         * MG_FAS never leaves the pooled moral graph and prunes the married pairs by
         * sepset tests. Restricting the score search to that skeleton keeps MG-FAS's
         * adjacency precision while letting the score decide which of those adjacencies
         * survive. The result's adjacencies are a subset of the MG_FAS adjacencies, so
         * its recall cannot exceed MG_FAS's.
         */
        IMAGES_RESTRICT_MG_FAS
    }
}
