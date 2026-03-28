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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.FfCiContinuous;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GIN algorithm implementing the procedure described in the GIN papers, rewritten
 * to use the same left-null-space + pairwise HSIC math as TGIN's Stages 4 and 5.
 *
 * <p>The three stages are:
 * <ol>
 *   <li><b>Clustering.</b> For each candidate cluster C with complement Z, test
 *       whether the left null space of Cov(C, Z) yields a residual signal that is
 *       pairwise independent of every column of Z (TGIN Stage 4 math applied to
 *       cluster discovery). The smallest latent rank for which this holds is
 *       recorded.</li>
 *   <li><b>Causal ordering.</b> Root-peeling loop: cluster I is declared a root
 *       over the remaining clusters if ginIndepTest(colsI, colsJ ∪ orderedCols,
 *       rankI) returns true for every other remaining cluster J. This is the
 *       same left-null-space + all-pass HSIC test used in TGIN's Stage 4.</li>
 *   <li><b>Graph construction.</b> Ordered latents receive directed edges from
 *       all earlier latents; unordered latents receive undirected edges among
 *       themselves and directed edges from all ordered latents.</li>
 * </ol>
 *
 * <p>The core change relative to the original GIN.java is the replacement of
 * {@code calEWithGin} (last right singular vector of Cov(Z, X) projected onto
 * X-data, tested via Fisher's combined p-value) with TGIN's approach: compute
 * the full left null space of Cov(X, Z), project X-data through it, and require
 * every resulting residual row to be pairwise independent of every Z column via
 * HSIC. This is strictly more informative when the null space has dimension > 1
 * (i.e., when the cluster has more indicators than latent dimensions).
 *
 * <p>References:
 * <ul>
 *   <li>Xie et al. (2020). Generalized independent noise condition for estimating
 *       latent variable causal graphs. NeurIPS.</li>
 *   <li>Xie et al. (2024). Generalized independent noise condition for estimating
 *       causal structure with latent variables. JMLR, 25(191):1–61.</li>
 * </ul>
 */
public class Gin {

    // ------------------------------------------------------------------ fields

    private final double alpha;
    private boolean verbose = false;

    // set at search() entry
    private DataSet data;
    private List<Node> vars;

    /**
     * Shared HSIC test instance, initialised once per search() call.
     * Using FfCiContinuous directly (same class as TGIN) rather than the
     * injected RawMarginalIndependenceTest, so that alpha is always set
     * consistently and the (double[], double[]) pairwise signature is available.
     */
    private FfCiContinuous hsic;

    // ----------------------------------------------------------------- ctors

    /**
     * Preferred constructor.
     *
     * @param alpha significance level for all independence tests
     */
    public Gin(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Backward-compatible constructor; the injected test is ignored in favour
     * of the internally created FfCiContinuous instance.
     *
     * @param alpha       significance level
     * @param ignoredTest not used; present for API compatibility only
     */
    public Gin(double alpha, RawMarginalIndependenceTest ignoredTest) {
        this.alpha = alpha;
    }

    // --------------------------------------------------------------- options

    /**
     * Enables or disables verbose logging via TetradLogger.
     *
     * @param v true to enable
     */
    public void setVerbose(boolean v) {
        this.verbose = v;
    }

    // ------------------------------------------------------------------- API

    /**
     * Runs the GIN search on the supplied dataset and returns a mixed graph
     * over the observed variables with latent nodes and measurement edges added.
     *
     * @param data the dataset (raw data required; covariance-only is not sufficient
     *             because the HSIC test operates on individual observations)
     * @return the recovered causal graph
     */
    public Graph search(DataSet data) {
        this.data = data;
        this.vars = data.getVariables();
        this.hsic = new FfCiContinuous(data);
        this.hsic.setAlpha(alpha);
        return searchPaperStyle();
    }

    // ============================================================
    // Main algorithm
    // ============================================================

    private Graph searchPaperStyle() {
        final int p = vars.size();

        Set<Integer> varSet = new LinkedHashSet<>();
        for (int i = 0; i < p; i++) varSet.add(i);

        // ----------------------------------------------------------
        // Step 1: GIN clustering
        //
        // For each candidate cluster of size clusterSize, find the
        // smallest latent rank laLen for which the GIN independence
        // condition holds (using TGIN's left-null-space + pairwise
        // HSIC test).
        // ----------------------------------------------------------

        int clusterSize = 2;
        List<ClusterWithRank> clustersList = new ArrayList<>();

        while (clusterSize < varSet.size()) {
            List<ClusterWithRank> tmpClusters = new ArrayList<>();
            List<Integer> varList = new ArrayList<>(varSet);

            if (clusterSize <= varList.size()) {
                for (List<Integer> cluster : subsetsOfSize(varList, clusterSize)) {

                    // Complement of this candidate cluster within the remaining var set
                    List<Integer> remainZ = new ArrayList<>(varSet);
                    cluster.forEach(remainZ::remove);
                    if (remainZ.isEmpty()) continue;

                    // Find the smallest laLen in [1, clusterSize-1] for which the
                    // GIN condition holds.
                    for (int laLen = 1; laLen < clusterSize; laLen++) {
                        if (ginIndepTest(cluster, remainZ, laLen)) {
                            tmpClusters.add(new ClusterWithRank(new ArrayList<>(cluster), laLen));
                            break; // use smallest rank that works
                        }
                    }
                }
            }

            // Merge overlapping candidate clusters (union + max rank)
            tmpClusters = mergeOverlappingClusters(tmpClusters);

            // Remove committed variables from the search pool
            clustersList.addAll(tmpClusters);
            for (ClusterWithRank c : tmpClusters) c.vars().forEach(varSet::remove);

            // Post-process: try to split any rank-k > 1 clusters into
            // purer sub-clusters using the same ginIndepTest.
            List<ClusterWithRank> refined = new ArrayList<>();
            for (ClusterWithRank cwr : clustersList) {
                if (cwr.laLen() == 1) {
                    refined.add(cwr);
                } else {
                    List<Integer> complement = new ArrayList<>();
                    for (int i = 0; i < p; i++) {
                        if (!cwr.vars().contains(i)) complement.add(i);
                    }
                    refined.addAll(trySplitCluster(cwr, complement));
                }
            }
            clustersList = refined;

            clusterSize++;
        }

        if (verbose) {
            TetradLogger.getInstance().log("[GIN] clusters = " + clustersAsNames(clustersList));
            TetradLogger.getInstance().log("[GIN] unclustered vars = " + varSet);
        }

        // ----------------------------------------------------------
        // Step 2: Causal ordering via root-peeling
        //
        // Cluster I is a root over the remaining clusters if, for every
        // other remaining cluster J, ginIndepTest(colsI, colsJ ∪ orderedCols,
        // rankI) returns true.  This is TGIN's Stage 4 test repurposed for
        // ordering: the left null space of Cov(I-indicators, J-indicators)
        // should yield a signal independent of J (and of everything already
        // ordered), confirming that I is causally prior.
        // ----------------------------------------------------------

        List<ClusterWithRank> clustersRemaining = new ArrayList<>(clustersList);
        List<ClusterWithRank> causalOrder = new ArrayList<>();

        boolean updated = true;
        while (updated && !clustersRemaining.isEmpty()) {
            updated = false;

            // Column indices of all clusters already placed in causal order
            List<Integer> orderedCols = new ArrayList<>();
            for (ClusterWithRank oc : causalOrder) orderedCols.addAll(oc.vars());

            for (int i = 0; i < clustersRemaining.size(); i++) {
                ClusterWithRank clusterI = clustersRemaining.get(i);
                List<Integer> colsI  = clusterI.vars();
                int            rankI  = clusterI.laLen();
                boolean        isRoot = true;

                for (int j = 0; j < clustersRemaining.size(); j++) {
                    if (i == j) continue;

                    // Complement: J's indicators + already-ordered indicators
                    List<Integer> complementCols = new ArrayList<>(clustersRemaining.get(j).vars());
                    complementCols.addAll(orderedCols);

                    if (!ginIndepTest(colsI, complementCols, rankI)) {
                        isRoot = false;
                        break;
                    }
                }

                if (isRoot) {
                    causalOrder.add(clusterI);
                    clustersRemaining.remove(i);
                    updated = true;
                    break; // restart the outer loop with the updated ordered set
                }
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("[GIN] causal order = " + clustersAsNames(causalOrder));
            TetradLogger.getInstance().log("[GIN] unordered    = " + clustersAsNames(clustersRemaining));
        }

        // ----------------------------------------------------------
        // Step 3: Build graph (unchanged from original Gin.java)
        // ----------------------------------------------------------

        Graph g = new EdgeListGraph(vars);
        int latentId = 1;
        List<Node> lNodes = new ArrayList<>(); // all latent nodes emitted so far

        // Ordered clusters: directed edges from every earlier latent
        for (ClusterWithRank cluster : causalOrder) {
            List<Node> newLNodes = new ArrayList<>();

            for (int k = 0; k < cluster.laLen(); k++) {
                Node lNode = new GraphNode("L" + latentId++);
                lNode.setNodeType(NodeType.LATENT);
                g.addNode(lNode);
                for (Node parent : lNodes) g.addDirectedEdge(parent, lNode);
                newLNodes.add(lNode);
            }

            lNodes.addAll(newLNodes);
            for (int o : cluster.vars())
                for (Node lNode : newLNodes)
                    g.addDirectedEdge(lNode, vars.get(o));
        }

        // Unordered clusters: undirected among themselves,
        // directed from every ordered latent into them
        List<Node> undirectedLNodes = new ArrayList<>();

        for (ClusterWithRank cluster : clustersRemaining) {
            List<Node> newLNodes = new ArrayList<>();

            for (int k = 0; k < cluster.laLen(); k++) {
                Node lNode = new GraphNode("L" + latentId++);
                lNode.setNodeType(NodeType.LATENT);
                g.addNode(lNode);
                for (Node parent  : lNodes)          g.addDirectedEdge(parent, lNode);
                for (Node und     : undirectedLNodes) g.addUndirectedEdge(und, lNode);
                undirectedLNodes.add(lNode);
                newLNodes.add(lNode);
            }

            lNodes.addAll(newLNodes);
            for (int o : cluster.vars())
                for (Node lNode : newLNodes)
                    g.addDirectedEdge(lNode, vars.get(o));
        }

        return g;
    }

    // ============================================================
    // Core test — identical math to TGIN's groupGinTest / Stage 4
    // ============================================================

    /**
     * GIN independence test using TGIN's left-null-space approach.
     *
     * <p>Under the GIN / LiNGLaM assumption, if {@code clusterCols} is a valid
     * cluster with {@code latentRank} latent sources, the cross-covariance
     * Σ(cluster, complement) has rank ≤ {@code latentRank}.  The left null space
     * of that matrix gives a weight matrix Ω such that Ω·X_data is (in population)
     * independent of Z_data.
     *
     * <p>The test returns {@code true} iff every row of the residual matrix
     * Ω·X_data<sup>T</sup> is pairwise independent of every column of Z_data
     * under HSIC (all-pass criterion, mirroring TGIN Stage 4).
     *
     * @param clusterCols    column indices of the candidate cluster
     * @param complementCols column indices of the complement (or conditioning) set
     * @param latentRank     assumed number of latent dimensions in the cluster
     * @return true if the GIN independence condition holds at level {@code alpha}
     */
    private boolean ginIndepTest(List<Integer> clusterCols,
                                 List<Integer> complementCols,
                                 int latentRank) {
        if (clusterCols.isEmpty() || complementCols.isEmpty()) return false;

        Matrix Xdata = submatrixData(clusterCols);    // n × pX
        Matrix Zdata = submatrixData(complementCols); // n × pZ

        // pX × pZ cross-covariance (identical to TGIN's crossCovariance)
        Matrix SigmaXZ = crossCovMatrix(Xdata, Zdata);

        // Left null space: (pX − latentRank) × pX
        Matrix Omega = leftNullSpace(SigmaXZ, latentRank);
        if (Omega.getNumRows() == 0) return false; // no null space — cannot confirm

        // Residual: (pX − latentRank) × n
        Matrix residual = Omega.times(Xdata.transpose());

        // All-pass: every residual row must be HSIC-independent of every Z column
        for (int row = 0; row < residual.getNumRows(); row++) {
            double[] res = residual.row(row).toArray();
            for (int col = 0; col < complementCols.size(); col++) {
                double[] zCol = Zdata.col(col).toArray();
                try {
                    if (hsic.computePValue(res, zCol) < alpha) return false;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    // ============================================================
    // TGIN math helpers — exact copies of TGIN's private methods
    // ============================================================

    /**
     * Returns the left null space of M as a matrix whose rows span ker(M<sup>T</sup>),
     * assuming M has structural rank {@code rank}.
     *
     * <p>Uses a full (not thin) SVD so that the null-space columns of U are
     * available; columns from index {@code rank} onward of U are the left
     * null-space basis. Copied verbatim from {@code TginOrientationStages}.
     *
     * @param M    the input matrix (r × c)
     * @param rank assumed rank of M
     * @return a {@code (r − rank) × r} matrix whose rows are left null vectors,
     *         or a 0-row matrix if the null space is empty
     */
    private Matrix leftNullSpace(Matrix M, int rank) {
        // false = full SVD; thin SVD truncates the null-space columns we need
        SimpleSVD<SimpleMatrix> svd = M.getSimpleMatrix().svd(false);
        SimpleMatrix U = svd.getU(); // r × r

        int numCols = U.getNumCols(); // = r (number of rows of M)
        int nullDim  = numCols - rank;
        if (nullDim <= 0) return new Matrix(0, M.getNumRows());

        double[][] result = new double[nullDim][M.getNumRows()];
        for (int col = 0; col < nullDim; col++)
            for (int row = 0; row < M.getNumRows(); row++)
                result[col][row] = U.get(row, rank + col);

        return new Matrix(result);
    }

    /**
     * Sample cross-covariance between mean-centred X and mean-centred Y.
     * Returns an (X.cols × Y.cols) matrix.  Copied verbatim from
     * {@code TginOrientationStages.crossCovariance}.
     *
     * @param X n × pX data matrix
     * @param Y n × pY data matrix
     * @return pX × pY cross-covariance matrix, scaled by 1/(n−1)
     */
    private Matrix crossCovMatrix(Matrix X, Matrix Y) {
        int n = X.getNumRows();
        Matrix Xc = X.copy();
        Matrix Yc = Y.copy();

        for (int j = 0; j < X.getNumColumns(); j++) {
            double mean = 0;
            for (int i = 0; i < n; i++) mean += X.get(i, j);
            mean /= n;
            for (int i = 0; i < n; i++) Xc.set(i, j, X.get(i, j) - mean);
        }
        for (int j = 0; j < Y.getNumColumns(); j++) {
            double mean = 0;
            for (int i = 0; i < n; i++) mean += Y.get(i, j);
            mean /= n;
            for (int i = 0; i < n; i++) Yc.set(i, j, Y.get(i, j) - mean);
        }

        return Xc.transpose().times(Yc).scalarMult(1.0 / (n - 1));
    }

    /**
     * Extracts the columns identified by {@code cols} from the dataset as an
     * n × |cols| {@link Matrix}.  Copied verbatim from
     * {@code TginOrientationStages.submatrix}.
     *
     * @param cols column indices into {@code data}
     * @return n × |cols| data matrix
     */
    private Matrix submatrixData(List<Integer> cols) {
        int n = data.getNumRows();
        double[][] result = new double[n][cols.size()];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < cols.size(); j++)
                result[i][j] = data.getDouble(i, cols.get(j));
        return new Matrix(result);
    }

    // ============================================================
    // Cluster helpers — unchanged from original Gin.java
    // (trySplitCluster updated to use ginIndepTest)
    // ============================================================

    /**
     * Enumerates all combinations of {@code list} of size {@code size}.
     */
    private List<List<Integer>> subsetsOfSize(List<Integer> list, int size) {
        List<List<Integer>> result = new ArrayList<>();
        int n = list.size();
        if (size > n || size <= 0) return result;

        int[] idx = new int[size];
        for (int i = 0; i < size; i++) idx[i] = i;

        while (true) {
            List<Integer> subset = new ArrayList<>(size);
            for (int k = 0; k < size; k++) subset.add(list.get(idx[k]));
            result.add(subset);

            int t = size - 1;
            while (t >= 0 && idx[t] == t + (n - size)) t--;
            if (t < 0) break;
            idx[t]++;
            for (int i = t + 1; i < size; i++) idx[i] = idx[i - 1] + 1;
        }
        return result;
    }

    /**
     * Tries to split a rank-k (k > 1) cluster into purer sub-clusters by
     * re-running {@link #ginIndepTest} on sub-sets.  Uses the same test as the
     * clustering stage, now on subsets of the cluster against the complement.
     *
     * <p>Returns the list of sub-clusters only if all original variables are
     * accounted for AND more than one sub-cluster is found; otherwise returns
     * {@code List.of(cwr)} (no split).
     */
    private List<ClusterWithRank> trySplitCluster(ClusterWithRank cwr,
                                                  List<Integer> complement) {
        List<Integer>       clusterVars = new ArrayList<>(cwr.vars());
        Set<Integer>        remaining   = new LinkedHashSet<>(clusterVars);
        List<ClusterWithRank> subClusters = new ArrayList<>();

        outer:
        for (int subSize = 2; subSize < clusterVars.size() && !remaining.isEmpty(); subSize++) {
            for (List<Integer> sub : subsetsOfSize(new ArrayList<>(remaining), subSize)) {
                for (int laLen = 1; laLen < subSize; laLen++) {
                    if (ginIndepTest(sub, complement, laLen)) {
                        subClusters.add(new ClusterWithRank(new ArrayList<>(sub), laLen));
                        sub.forEach(remaining::remove);
                        break; // next subSize with updated remaining
                    }
                }
                if (remaining.isEmpty()) break outer;
            }
        }

        if (remaining.isEmpty() && subClusters.size() > 1) return subClusters;
        return List.of(cwr);
    }

    /**
     * Merges overlapping candidate clusters by union, taking the maximum laLen.
     * Equivalent to {@code merge_overlaping_cluster} in the original GIN.py.
     */
    private List<ClusterWithRank> mergeOverlappingClusters(List<ClusterWithRank> clusters) {
        List<Set<Integer>> sets    = new ArrayList<>();
        List<Integer>      laLens  = new ArrayList<>();

        for (ClusterWithRank c : clusters) {
            sets.add(new LinkedHashSet<>(c.vars()));
            laLens.add(c.laLen());
        }

        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < sets.size(); i++) {
                Set<Integer> a = sets.get(i);
                int j = i + 1;
                while (j < sets.size()) {
                    Set<Integer> b = sets.get(j);
                    if (!disjoint(a, b)) {
                        a.addAll(b);
                        laLens.set(i, Math.max(laLens.get(i), laLens.get(j)));
                        sets.remove(j);
                        laLens.remove(j);
                        changed = true;
                    } else {
                        j++;
                    }
                }
            }
        } while (changed);

        List<ClusterWithRank> out = new ArrayList<>();
        for (int i = 0; i < sets.size(); i++)
            out.add(new ClusterWithRank(new ArrayList<>(sets.get(i)), laLens.get(i)));
        return out;
    }

    private boolean disjoint(Set<Integer> a, Set<Integer> b) {
        for (Integer x : a) if (b.contains(x)) return false;
        return true;
    }

    private String clustersAsNames(List<ClusterWithRank> cl) {
        return cl.stream()
                .map(c -> c.vars().stream()
                        .map(i -> vars.get(i).getName())
                        .toList()
                        .toString() + "(rank=" + c.laLen() + ")")
                .collect(Collectors.joining(" | "));
    }

    // ============================================================
    // Records
    // ============================================================

    /**
     * A discovered cluster together with its latent rank (number of latent
     * dimensions needed to explain its cross-covariance with the complement).
     *
     * @param vars  column indices of the cluster's indicator variables
     * @param laLen latent rank (≥ 1)
     */
    record ClusterWithRank(List<Integer> vars, int laLen) {}
}
