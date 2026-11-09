/// ////////////////////////////////////////////////////////////////////////////
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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.PermutationMatrixPair;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TMath;
import org.apache.commons.math3.distribution.TDistribution;
import org.ejml.simple.SimpleMatrix;

import java.util.*;

import static edu.cmu.tetrad.util.TMath.abs;

/**
 * LiNGAM (Shimizu et al., 2006), with small numerical hardening to match the stabilized LiNG-D pipeline.
 * <p>
 * Changes vs. the earlier version: - Optionally threshold W before Hungarian assignment (reduces spurious diagonals). -
 * After forming B̂ via IcaLingD.getScaledBHat (guarded diag), hard-threshold B̂ using bThreshold prior to acyclicity
 * trimming. - Always return a (possibly empty) trimmed matrix (never null). - Optionally (and by default) finish with
 * the paper's step 5: refit coefficients by OLS along the estimated causal order and prune by t-test
 * (regressAndPrune), replacing the noisier ICA-derived coefficients.
 */
public class Lingam {

    /**
     * Dummy graph and helpers for cycle detection during trimming. Instance fields (previously
     * static), so that two Lingam searches running at the same time — two search boxes, Grid
     * Search cells, or py-tetrad threads — cannot corrupt each other's trimming state.
     */
    private Graph dummyGraph;
    private ArrayList<Node> dummyVars;
    private Set<Node> dummyCyclicNodes;

    /**
     * Threshold for |B_ij| -> 0 before acyclicity trimming.
     */
    private double bThreshold = 0.1;

    /**
     * (Optional) pre-threshold for |W_ij| -> 0 before Hungarian; set to 0 to disable.
     */
    private double wThreshold = 0.0;

    /**
     * Whether fit() follows the acyclicity trimming with the regression refit-and-prune step
     * (Shimizu et al. 2006, step 5): the trimmed B̂ fixes the causal order, each variable is
     * regressed on all its predecessors in that order, and coefficients are kept when their
     * two-sided t-test p-value is below pruneAlpha. ICA-derived coefficients are replaced by the
     * regression estimates, which are far less noisy, and the pruning is scale-free.
     */
    private boolean refitAndPrune = true;

    /**
     * Alpha for the per-coefficient t-test in the refit-and-prune step.
     */
    private double pruneAlpha = 0.001;

    /**
     * Verbose logging.
     */
    private boolean verbose = false;

    /**
     * Constructor for the IcaLingam class. Initializes an instance of the LiNGAM algorithm for causal discovery.
     * This class provides methods to estimate causal structures from observational data, ensuring acyclic causal graphs
     * and performing weight matrix estimation and processing.
     */
    public Lingam() {
    }

    /**
     * Fits a dataset to estimate a weight matrix using the LiNGAM algorithm. The method utilizes stabilized FastICA
     * for the estimation process and further processes the weights to ensure they conform to an acyclic structure.
     *
     * @param D the dataset, represented as a DataSet object, which includes the observed data necessary for estimating
     *          the weight matrix.
     * @return a matrix representing acyclic trimmed weights derived from the dataset.
     */
    public Matrix fit(DataSet D) {
        // Slightly more conservative defaults play nicely with stabilized FastICA.
        Matrix W = LingD.estimateW(D, 5000, 1e-6, 1.2, verbose);
        Matrix bHat = getAcyclicTrimmedBHat(W);
        return refitAndPrune ? regressAndPrune(bHat, D) : bHat;
    }

    /**
     * Processes a given matrix W to generate a scaled and trimmed matrix B̂ that is guaranteed to correspond to an
     * acyclic directed graph (DAG). The method applies optional denoising, diagonal maximization via the Hungarian
     * algorithm, hard-thresholding, and iterative edge trimming to ensure the acyclicity of B̂.
     *
     * @param W the input matrix representing initial weights or coefficients; it will be processed to produce an
     *          acyclic trimmed matrix.
     * @return a matrix B̂ that corresponds to an acyclic directed graph derived from the input matrix W.
     */
    public Matrix getAcyclicTrimmedBHat(Matrix W) {
        W = new Matrix(W);

        // (1) Optional gentle denoising of W
        double wt = TMath.max(0.0, this.wThreshold);
        if (wt > 0) {
            for (int i = 0; i < W.getNumRows(); i++) {
                for (int j = 0; j < W.getNumColumns(); j++) {
                    if (abs(W.get(i, j)) < wt) W.set(i, j, 0.0);
                }
            }
        }

        // (2) Best diagonal via Hungarian, then robust scaling to produce B̂
        PermutationMatrixPair bestPair = LingD.maximizeDiagonal(W);
        Matrix scaledBHat = LingD.getScaledBHat(bestPair);

        // (3) Hard-threshold B̂ BEFORE trimming
        double bt = TMath.max(0.0, this.bThreshold);
        if (bt > 0) {
            for (int i = 0; i < scaledBHat.getNumRows(); i++) {
                for (int j = 0; j < scaledBHat.getNumColumns(); j++) {
                    if (abs(scaledBHat.get(i, j)) < bt) scaledBHat.set(i, j, 0.0);
                }
            }
        }

        // Quick pass: already acyclic?
        if (isAcyclic(scaledBHat)) {
            return scaledBHat;
        }

        // (4) Trim smallest-magnitude edges until DAG
        class Record {
            double coef;
            int i;
            int j;
        }
        LinkedList<Record> coefs = new LinkedList<>();

        for (int i = 0; i < scaledBHat.getNumRows(); i++) {
            for (int j = 0; j < scaledBHat.getNumColumns(); j++) {
                if (i != j && scaledBHat.get(i, j) != 0.0) {
                    Record r = new Record();
                    r.coef = abs(scaledBHat.get(i, j));
                    r.i = i;
                    r.j = j;
                    coefs.add(r);
                }
            }
        }

        // Smallest first (remove weakest edges before stronger ones)
        coefs.sort(Comparator.comparingDouble(o -> o.coef));

        // Ensure dummyGraph is initialized to the current B̂
        isAcyclic(scaledBHat); // sets dummyGraph & dummyVars

        while (!coefs.isEmpty()) {
            Record r = coefs.removeFirst();

            // Remove that edge from both matrix and graph
            scaledBHat.set(r.i, r.j, 0.0);
            Edge e = dummyGraph.getDirectedEdge(dummyVars.get(r.j), dummyVars.get(r.i));
            if (e != null) dummyGraph.removeEdge(e);

            // Check acyclicity
            if (!existsDirectedCycle()) {
                if (verbose) {
                    TetradLogger.getInstance().log("LiNGAM: effective trim threshold = " + r.coef);
                }
                return scaledBHat;
            }
        }

        // If we get here, all edges were removed; return empty DAG (still valid & acyclic).
        if (verbose) {
            TetradLogger.getInstance().log("LiNGAM: all edges removed to achieve acyclicity.");
        }
        return scaledBHat;
    }

    /**
     * The regression refit-and-prune step of Shimizu et al. (2006), step 5. The acyclic trimmed B̂
     * fixes a causal order (a topological order of its DAG); each variable is then regressed by
     * OLS on all its predecessors in that order, and a coefficient is kept when its two-sided
     * t-test p-value is below pruneAlpha. The returned matrix carries the regression coefficients,
     * which are far less noisy than the ICA-derived ones, and the pruning is scale-free, unlike
     * the fixed bThreshold cut. Because every predecessor is offered, this step can also restore a
     * true edge that the ICA threshold discarded, provided its endpoints are rightly ordered.
     * <p>
     * If the input matrix does not represent a DAG (it should, coming from
     * getAcyclicTrimmedBHat), it is returned unchanged.
     *
     * @param acyclicBHat the acyclic coefficient matrix from getAcyclicTrimmedBHat; entry (i, j)
     *                    nonzero means j -&gt; i.
     * @param data        the data set the model was estimated from.
     * @return the refit and pruned coefficient matrix, in the same (i, j) convention.
     */
    public Matrix regressAndPrune(Matrix acyclicBHat, DataSet data) {
        int p = acyclicBHat.getNumRows();

        // Topological order by Kahn's algorithm; entry (i, j) != 0 means j -> i.
        boolean[][] adj = new boolean[p][p];
        int[] indegree = new int[p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) {
                if (i != j && acyclicBHat.get(i, j) != 0.0) {
                    adj[i][j] = true;
                    indegree[i]++;
                }
            }
        }

        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < p; i++) if (indegree[i] == 0) queue.add(i);
        List<Integer> order = new ArrayList<>();

        while (!queue.isEmpty()) {
            int v = queue.poll();
            order.add(v);
            for (int i = 0; i < p; i++) {
                if (adj[i][v]) {
                    adj[i][v] = false;
                    if (--indegree[i] == 0) queue.add(i);
                }
            }
        }

        if (order.size() < p) {
            return acyclicBHat; // Not a DAG; nothing sensible to refit.
        }

        // Centered data columns.
        int n = data.getNumRows();
        SimpleMatrix x = new SimpleMatrix(n, p);
        for (int c = 0; c < p; c++) {
            double mean = 0.0;
            for (int r = 0; r < n; r++) mean += data.getDouble(r, c);
            mean /= n;
            for (int r = 0; r < n; r++) x.set(r, c, data.getDouble(r, c) - mean);
        }

        Matrix out = new Matrix(p, p);

        for (int pos = 1; pos < p; pos++) {
            int child = order.get(pos);
            List<Integer> preds = order.subList(0, pos);
            int k = preds.size();

            SimpleMatrix xp = new SimpleMatrix(n, k);
            for (int c = 0; c < k; c++) {
                for (int r = 0; r < n; r++) xp.set(r, c, x.get(r, preds.get(c)));
            }
            SimpleMatrix y = x.extractVector(false, child);

            SimpleMatrix xtxInv = xp.transpose().mult(xp).pseudoInverse();
            SimpleMatrix beta = xtxInv.mult(xp.transpose()).mult(y);
            SimpleMatrix resid = y.minus(xp.mult(beta));

            int df = n - k - 1;
            double sigma2 = df >= 1 ? resid.dot(resid) / df : Double.NaN;

            for (int c = 0; c < k; c++) {
                double coef = beta.get(c, 0);

                if (df < 1) {
                    // Too few rows to test; keep the regression estimate untested.
                    if (coef != 0.0) out.set(child, preds.get(c), coef);
                    continue;
                }

                double se = Math.sqrt(sigma2 * Math.max(0.0, xtxInv.get(c, c)));
                double t = se > 0.0 ? Math.abs(coef) / se : Double.POSITIVE_INFINITY;
                double pValue = 2.0 * (1.0 - new TDistribution(df).cumulativeProbability(t));

                if (pValue < this.pruneAlpha) {
                    out.set(child, preds.get(c), coef);
                }
            }
        }

        return out;
    }

    /**
     * Checks whether the graph induced by the scaled coefficient matrix represents an acyclic directed graph (DAG).
     *
     * @param scaledBHat the scaled coefficient matrix used to construct the graph.
     * @return true if the graph is acyclic, otherwise false.
     */
    public boolean isAcyclic(Matrix scaledBHat) {
        dummyVars = new ArrayList<>();
        for (int i = 0; i < scaledBHat.getNumRows(); i++) {
            dummyVars.add(new GraphNode("" + i));
        }
        dummyCyclicNodes = new HashSet<>(dummyVars);
        dummyGraph = LingD.makeGraph(scaledBHat, dummyVars);
        return !dummyGraph.paths().existsDirectedCycle();
    }

    private boolean existsDirectedCycle() {
        for (Node node : new HashSet<>(dummyCyclicNodes)) {
            if (dummyGraph.paths().existsDirectedPath(node, node)) {
                return true;
            } else {
                dummyCyclicNodes.remove(node);
            }
        }
        return false;
    }

    /**
     * Threshold for |B_ij| -> 0 before trimming.
     *
     * @param bThreshold the threshold value for B_ij coefficients.
     */
    public void setBThreshold(double bThreshold) {
        if (bThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + bThreshold);
        this.bThreshold = bThreshold;
    }

    /**
     * Optional pre-threshold for |W_ij| -> 0 before Hungarian; set to 0 to disable (default).
     *
     * @param wThreshold the threshold value for W_ij coefficients.
     */
    public void setWThreshold(double wThreshold) {
        if (wThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + wThreshold);
        this.wThreshold = wThreshold;
    }

    /**
     * Whether fit() follows the acyclicity trimming with the regression refit-and-prune step.
     *
     * @param refitAndPrune true to refit coefficients by regression along the estimated order and
     *                      prune by t-test; false to return the ICA-derived trimmed B̂ as before.
     */
    public void setRefitAndPrune(boolean refitAndPrune) {
        this.refitAndPrune = refitAndPrune;
    }

    /**
     * Alpha for the per-coefficient t-test in the refit-and-prune step.
     *
     * @param pruneAlpha the significance level; must be in (0, 1).
     */
    public void setPruneAlpha(double pruneAlpha) {
        if (!(pruneAlpha > 0 && pruneAlpha < 1)) {
            throw new IllegalArgumentException("Expecting a number in (0, 1): " + pruneAlpha);
        }
        this.pruneAlpha = pruneAlpha;
    }

    /**
     * Verbose logging.
     *
     * @param verbose whether to enable verbose logging.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}