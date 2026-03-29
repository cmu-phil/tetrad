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
 *       themselves and directed edges from all ordered latents.  For rank-k &gt; 1
 *       ordered clusters, ICA + pairwise LiNGAM (TGIN Stage 5) is applied to
 *       orient edges among the k latent copies within the cluster.</li>
 * </ol>
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
     */
    private FfCiContinuous hsic;

    // ----------------------------------------------------------------- ctors

    /**
     * Preferred constructor.
     *
     * @param data the dataset (raw data required)
     * @param alpha significance level for all independence tests
     */
    public Gin(DataSet data, double alpha) {
        this.data = data;
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
     * @return the recovered causal graph
     */
    public Graph search() {
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
        // ----------------------------------------------------------

        int clusterSize = 2;
        List<ClusterWithRank> clustersList = new ArrayList<>();

        while (clusterSize < varSet.size()) {
            List<ClusterWithRank> tmpClusters = new ArrayList<>();
            List<Integer> varList = new ArrayList<>(varSet);

            if (clusterSize <= varList.size()) {
                for (List<Integer> cluster : subsetsOfSize(varList, clusterSize)) {

                    List<Integer> remainZ = new ArrayList<>(varSet);
                    cluster.forEach(remainZ::remove);
                    if (remainZ.isEmpty()) continue;

                    for (int laLen = 1; laLen < clusterSize; laLen++) {
                        if (ginIndepTest(cluster, remainZ, laLen)) {
                            tmpClusters.add(new ClusterWithRank(new ArrayList<>(cluster), laLen));
                            break;
                        }
                    }
                }
            }

            tmpClusters = mergeOverlappingClusters(tmpClusters);

            clustersList.addAll(tmpClusters);
            for (ClusterWithRank c : tmpClusters) c.vars().forEach(varSet::remove);

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
        // ----------------------------------------------------------

        List<ClusterWithRank> clustersRemaining = new ArrayList<>(clustersList);
        List<ClusterWithRank> causalOrder = new ArrayList<>();

        boolean updated = true;
        while (updated && !clustersRemaining.isEmpty()) {
            updated = false;

            List<Integer> orderedCols = new ArrayList<>();
            for (ClusterWithRank oc : causalOrder) orderedCols.addAll(oc.vars());

            for (int i = 0; i < clustersRemaining.size(); i++) {
                ClusterWithRank clusterI = clustersRemaining.get(i);
                List<Integer> colsI  = clusterI.vars();
                int            rankI  = clusterI.laLen();
                boolean        isRoot = true;

                for (int j = 0; j < clustersRemaining.size(); j++) {
                    if (i == j) continue;

                    List<Integer> complementCols = new ArrayList<>(clustersRemaining.get(j).vars());
                    complementCols.addAll(orderedCols);

                    if (!ginIndepTest(colsI, complementCols, rankI)) {
                        isRoot = false;
                        break;
                    }
                }

                if (isRoot) {
                    causalOrder.addFirst(clusterI);
                    clustersRemaining.remove(i);
                    updated = true;
                    break;
                }
            }
        }

        if (verbose) {
            TetradLogger.getInstance().log("[GIN] causal order = " + clustersAsNames(causalOrder));
            TetradLogger.getInstance().log("[GIN] unordered    = " + clustersAsNames(clustersRemaining));
        }

        // ----------------------------------------------------------
        // Step 3: Build graph
        //
        // For rank-1 clusters: one latent, directed edges from all
        // earlier latents and down to indicators.
        // For rank-k > 1 ordered clusters: create k latent nodes,
        // then run ICA + pairwise LiNGAM (TGIN Stage 5) on the
        // cluster's indicators to orient edges among the k copies.
        // ----------------------------------------------------------

        Graph g = new EdgeListGraph(vars);
        int latentId = 1;
        List<Node> lNodes = new ArrayList<>(); // all latent nodes emitted so far

        for (ClusterWithRank cluster : causalOrder) {
            List<Node> newLNodes = new ArrayList<>();

            for (int k = 0; k < cluster.laLen(); k++) {
                Node lNode = new GraphNode("L" + latentId++);
                lNode.setNodeType(NodeType.LATENT);
                g.addNode(lNode);
                for (Node parent : lNodes) g.addDirectedEdge(parent, lNode);
                newLNodes.add(lNode);
            }

            // Intra-cluster ordering for rank > 1: ICA + LiNGAM (TGIN Stage 5)
            if (cluster.laLen() > 1) {
                try {
                    int r = cluster.laLen();
                    Matrix Xblock = submatrixData(cluster.vars());              // n × |C|
                    SimpleMatrix XblockT = Xblock.getSimpleMatrix().transpose(); // |C| × n

                    FastIca fastica = new FastIca(new Matrix(XblockT.toArray2()), r);
                    fastica.setAlgorithmType(FastIca.DEFLATION);
                    fastica.setMaxIterations(500);
                    fastica.setTolerance(1e-6);
                    FastIca.IcaResult icaResult = fastica.findComponents();

                    // sources: n × r
                    SimpleMatrix W = icaResult.W().getSimpleMatrix();            // r × |C|
                    SimpleMatrix sources = Xblock.getSimpleMatrix().mult(W.transpose()); // n × r

                    int[] order = lingamOrder(sources, r);
                    // order[0] is causally earliest, order[r-1] is latest
                    for (int pos = 0; pos < r - 1; pos++) {
                        Node earlier = newLNodes.get(order[pos]);
                        Node later   = newLNodes.get(order[pos + 1]);
                        g.addDirectedEdge(earlier, later);
                    }

                    if (verbose) {
                        TetradLogger.getInstance().log(
                                "[GIN] intra-cluster order for " + cluster.vars() + ": "
                                        + Arrays.toString(order));
                    }
                } catch (Exception e) {
                    // Fall back to undirected edges between consecutive copies
                    if (verbose) {
                        TetradLogger.getInstance().log(
                                "[GIN] intra-cluster ordering failed for cluster "
                                        + cluster.vars() + ": " + e.getMessage()
                                        + " — leaving edges undirected.");
                    }
                    for (int a = 0; a < newLNodes.size() - 1; a++)
                        g.addUndirectedEdge(newLNodes.get(a), newLNodes.get(a + 1));
                }
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
     */
    private boolean ginIndepTest(List<Integer> clusterCols,
                                 List<Integer> complementCols,
                                 int latentRank) {
        if (clusterCols.isEmpty() || complementCols.isEmpty()) return false;

        Matrix Xdata = submatrixData(clusterCols);    // n × pX
        Matrix Zdata = submatrixData(complementCols); // n × pZ

        Matrix SigmaXZ = crossCovMatrix(Xdata, Zdata);

        Matrix Omega = leftNullSpace(SigmaXZ, latentRank);
        if (Omega.getNumRows() == 0) return false;

        Matrix residual = Omega.times(Xdata.transpose());

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
    // Intra-cluster ordering — TGIN Stage 5 (ported from Tgin.java)
    // ============================================================

    /**
     * Recovers a total causal order over {@code r} ICA sources via pairwise
     * LiNGAM.  For each pair (i, j), regresses i on j and vice versa, tests
     * residual independence with HSIC.  Builds an adjacency matrix and returns
     * a topological sort.
     *
     * @param sources n × r matrix of ICA source signals
     * @param r       number of sources
     * @return causal order as an array of source indices, earliest first
     * @throws InterruptedException if the HSIC test is interrupted
     */
    private int[] lingamOrder(SimpleMatrix sources, int r) throws InterruptedException {
        boolean[][] adj = new boolean[r][r];

        for (int i = 0; i < r; i++) {
            for (int j = i + 1; j < r; j++) {
                double[] si = new double[sources.getNumRows()];
                double[] sj = new double[sources.getNumRows()];
                for (int row = 0; row < sources.getNumRows(); row++) {
                    si[row] = sources.get(row, i);
                    sj[row] = sources.get(row, j);
                }

                double[] res_j_on_i = residualOLS(sj, si);
                double pij = hsic.computePValue(res_j_on_i, si);
                boolean iToJ = pij > alpha;   // residual of j|i indep of i  =>  i -> j

                double[] res_i_on_j = residualOLS(si, sj);
                double pji = hsic.computePValue(res_i_on_j, sj);
                boolean jToI = pji > alpha;   // residual of i|j indep of j  =>  j -> i

                if (iToJ && !jToI) adj[i][j] = true;
                if (jToI && !iToJ) adj[j][i] = true;
            }
        }

        return topologicalSort(adj, r);
    }

    /**
     * OLS residual of {@code y} regressed on {@code x}.
     */
    private double[] residualOLS(double[] y, double[] x) {
        int n = y.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX  += x[i];
            sumY  += y[i];
            sumXY += x[i] * y[i];
            sumX2 += x[i] * x[i];
        }
        double meanX = sumX / n;
        double meanY = sumY / n;
        double beta  = (sumXY - n * meanX * meanY) / (sumX2 - n * meanX * meanX);
        double intercept = meanY - beta * meanX;
        double[] residuals = new double[n];
        for (int i = 0; i < n; i++)
            residuals[i] = y[i] - (intercept + beta * x[i]);
        return residuals;
    }

    /**
     * Topological sort of a boolean adjacency matrix.
     * Returns node indices in topological order (sources first).
     */
    private int[] topologicalSort(boolean[][] adj, int r) {
        int[] inDegree = new int[r];
        for (int i = 0; i < r; i++)
            for (int j = 0; j < r; j++)
                if (adj[i][j]) inDegree[j]++;

        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < r; i++)
            if (inDegree[i] == 0) queue.offer(i);

        int[] result = new int[r];
        int index = 0;
        while (!queue.isEmpty()) {
            int node = queue.poll();
            result[index++] = node;
            for (int j = 0; j < r; j++)
                if (adj[node][j] && --inDegree[j] == 0) queue.offer(j);
        }

        // If the graph had cycles (shouldn't happen but defensive), fill remainder
        if (index < r) {
            for (int i = 0; i < r; i++) {
                boolean found = false;
                for (int k = 0; k < index; k++) if (result[k] == i) { found = true; break; }
                if (!found) result[index++] = i;
            }
        }

        return result;
    }

    // ============================================================
    // TGIN math helpers
    // ============================================================

    /**
     * Returns the left null space of M, assuming M has structural rank {@code rank}.
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
     * Extracts columns {@code cols} from the dataset as an n × |cols| Matrix.
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
    // Cluster helpers
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
     * Tries to split a rank-k (k &gt; 1) cluster into purer sub-clusters.
     */
    private List<ClusterWithRank> trySplitCluster(ClusterWithRank cwr,
                                                  List<Integer> complement) {
        List<Integer>         clusterVars = new ArrayList<>(cwr.vars());
        Set<Integer>          remaining   = new LinkedHashSet<>(clusterVars);
        List<ClusterWithRank> subClusters = new ArrayList<>();

        outer:
        for (int subSize = 2; subSize < clusterVars.size() && !remaining.isEmpty(); subSize++) {
            for (List<Integer> sub : subsetsOfSize(new ArrayList<>(remaining), subSize)) {
                for (int laLen = 1; laLen < subSize; laLen++) {
                    if (ginIndepTest(sub, complement, laLen)) {
                        subClusters.add(new ClusterWithRank(new ArrayList<>(sub), laLen));
                        sub.forEach(remaining::remove);
                        break;
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
     */
    private List<ClusterWithRank> mergeOverlappingClusters(List<ClusterWithRank> clusters) {
        List<Set<Integer>> sets   = new ArrayList<>();
        List<Integer>      laLens = new ArrayList<>();

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
     * A discovered cluster together with its latent rank.
     *
     * @param vars  column indices of the cluster's indicator variables
     * @param laLen latent rank (≥ 1)
     */
    record ClusterWithRank(List<Integer> vars, int laLen) {}
}
