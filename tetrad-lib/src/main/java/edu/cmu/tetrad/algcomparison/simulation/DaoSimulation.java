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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;
import java.util.*;

/**
 * Implements the DAG-adaptation of the Onion (DaO) simulation method for
 * generating linear Gaussian data from a DAG, as described in:
 * <p>
 * Andrews, B. &amp; Kummerfeld, E. (2024). "Better Simulations for Validating
 * Causal Discovery with the DAG-Adaptation of the Onion Method."
 * arXiv:2405.13100.
 * <p>
 * Reference Python implementation: <a href="https://github.com/bja43/DaO_simulation">...</a>
 * <p>
 * The key idea is to sample the correlation matrix R uniformly over the space
 * of all correlation matrices satisfying the Markov property for the given DAG,
 * rather than sampling edge weights directly (as most other simulations do).
 * This avoids simulation artifacts such as varsortability and R²-sortability.
 * <p>
 * The algorithm proceeds in topological order over the DAG. For each non-source
 * variable i with parents pa(i), it:
 * <ol>
 *   <li>Builds a permutation matrix P that puts parents first among predecessors.</li>
 *   <li>Computes the Cholesky factor L of the permuted sub-correlation matrix.</li>
 *   <li>Samples w from the multivariate Pearson Type II distribution mPII_k(gamma),
 *       with nonzero entries only in the first k (parent) slots.</li>
 *   <li>Recovers marginal correlations r = P L w, structural coefficients
 *       b = P L^{-T} w, and error variance o = 1 - w^T w.</li>
 * </ol>
 * <p>
 * Optional graph-rewiring steps:
 * <ul>
 *   <li>{@code daoSfOut} — rewires to produce a scale-free out-degree distribution</li>
 *   <li>{@code daoSfIn}  — rewires to produce a scale-free in-degree distribution</li>
 *   <li>{@code daoRandomizeOrder} — randomly permutes variable order after rewiring</li>
 * </ul>
 *
 * @author Bryan Andrews (algorithm); Java translation for Tetrad
 */
public class DaoSimulation implements Simulation {

    @Serial
    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph>   graphs   = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Constructs a DaoSimulation using the supplied {@link RandomGraph} to
     * produce the true DAG on each run.
     *
     * @param randomGraph source of true DAGs; must not be null.
     */
    public DaoSimulation(RandomGraph randomGraph) {
        if (randomGraph == null) throw new NullPointerException("randomGraph is null.");
        this.randomGraph = randomGraph;
    }

    /**
     * Convenience constructor that fixes a single, predetermined true DAG.
     *
     * @param trueGraph the DAG to use for every run; must not be null.
     */
    public DaoSimulation(Graph trueGraph) {
        if (trueGraph == null) throw new NullPointerException("trueGraph is null.");
        this.randomGraph = new SingleGraph(trueGraph);
    }

    // -----------------------------------------------------------------------
    // Simulation interface
    // -----------------------------------------------------------------------

    /**
     * {@inheritDoc}
     * <p>
     * Creates {@code NUM_RUNS} data sets, each drawn from the DaO method
     * applied to the true DAG. If {@code DIFFERENT_GRAPHS} is true, a fresh
     * DAG is generated for each run.
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {

        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        this.dataSets = new ArrayList<>();
        this.graphs   = new ArrayList<>();

        Graph graph = this.randomGraph.createGraph(parameters);

        int numRuns    = parameters.getInt(Params.NUM_RUNS);
        int sampleSize = parameters.getInt(Params.SAMPLE_SIZE);

        for (int run = 0; run < numRuns; run++) {

            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && run > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            // ------------------------------------------------------------------
            // 1. Optionally rewire to scale-free out/in degree
            // ------------------------------------------------------------------
            Graph workingGraph = graph;

            if (parameters.getBoolean(Params.DAO_SF_OUT, false)) {
                workingGraph = sfOut(workingGraph);
            }
            if (parameters.getBoolean(Params.DAO_SF_IN, false)) {
                workingGraph = sfIn(workingGraph);
            }

            // ------------------------------------------------------------------
            // 2. Optionally randomize variable order
            // ------------------------------------------------------------------
            if (parameters.getBoolean(Params.DAO_RANDOMIZE_ORDER, true)) {
                workingGraph = randomizeGraph(workingGraph);
            }

            // ------------------------------------------------------------------
            // 3. Obtain a source-first consistent topological ordering
            // ------------------------------------------------------------------
            List<Node> topoOrder = soficOrder(workingGraph);
            int p = topoOrder.size();

            // parents[i] = indices j < i such that topoOrder[j] -> topoOrder[i]
            int[][] parents = buildParentIndex(workingGraph, topoOrder);

            // ------------------------------------------------------------------
            // 4. Sample R, B, O via the DaO method (faithful to Python corr())
            // ------------------------------------------------------------------
            CorrResult result = corr(p, parents);

            // ------------------------------------------------------------------
            // 5. Simulate n samples in topological order
            // ------------------------------------------------------------------
            double[][] X = simulate(result.B, result.O, p, sampleSize);

            // ------------------------------------------------------------------
            // 6. Wrap into a Tetrad DataSet
            // ------------------------------------------------------------------
            DataSet dataSet = buildDataSet(X, topoOrder, p, sampleSize);
            dataSet.setName("Run" + (run + 1));

            if (parameters.getBoolean(Params.STANDARDIZE, false)) {
                dataSet = DataTransforms.standardizeData(dataSet);
            }

            this.graphs.add(workingGraph);
            this.dataSets.add(dataSet);
        }
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public String getDescription() {
        return "DaO (DAG-adaptation of the Onion method) simulation using "
                + this.randomGraph.getDescription();
    }

    @Override
    public String getShortName() {
        return "DaO Simulation";
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            params.addAll(this.randomGraph.getParameters());
        }

        params.add(Params.NUM_RUNS);
        params.add(Params.SAMPLE_SIZE);
        params.add(Params.DIFFERENT_GRAPHS);
        params.add(Params.STANDARDIZE);
        params.add(Params.SEED);
        params.add(Params.DAO_SF_OUT);
        params.add(Params.DAO_SF_IN);
        params.add(Params.DAO_RANDOMIZE_ORDER);

        return params;
    }

    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return randomGraph.getClass();
    }

    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }

    // -----------------------------------------------------------------------
    // Core DaO algorithm — faithful translation of Python corr()
    // -----------------------------------------------------------------------

    /**
     * Holds the outputs of {@link #corr}: the correlation matrix R, the
     * structural coefficient matrix B, and the error variance vector O.
     * Corresponds directly to the tuple (R, B, O) returned by the Python
     * {@code corr()} function.
     */
    private record CorrResult(double[][] R, double[][] B, double[] O) {}

    /**
     * Samples a correlation matrix R uniformly over the space of all
     * correlation matrices Markov to the DAG, together with the corresponding
     * structural coefficient matrix B and error variance vector O.
     * <p>
     * Faithful Java translation of the Python {@code corr(g, rng)} function.
     * Variables are assumed to be in a source-first consistent topological
     * order (i.e. {@code parents[i]} contains only indices {@code j < i}).
     * Source nodes (no parents) are skipped — their row/column in R is the
     * identity, B row is zero, and O entry is 1.
     * <p>
     * For each non-source node i:
     * <ol>
     *   <li>Build permutation array perm (parents first among predecessors 0..i-1).</li>
     *   <li>Cholesky-decompose the permuted i×i sub-correlation matrix:
     *       {@code L = chol(P^T R[0:i,0:i] P)}.</li>
     *   <li>Sample w ~ mPII_k((p-i+1)/2), nonzero in first k (parent) slots.</li>
     *   <li>Set:  r = P L w,  b = P L^{-T} w,  o = 1 - w^T w.</li>
     * </ol>
     *
     * @param p       number of variables (already in topological order)
     * @param parents parents[i] = sorted parent indices in [0, i)
     * @return CorrResult containing R (p×p), B (p×p), O (length p)
     */
    private static CorrResult corr(int p, int[][] parents) {
        double[][] R = eye(p);
        double[][] B = new double[p][p];
        double[]   O = ones(p);

        // Count source nodes: the Python skips range(0, m) where m = num_source(g).
        // Since we are already in topological order, sources are those i with
        // parents[i].length == 0 that appear before any non-source node.
        // Computing m as the total number of parentless nodes matches Python exactly.
        int m = 0;
        for (int[] pa : parents) if (pa.length == 0) m++;

        for (int i = m; i < p; i++) {
            int[] pa = parents[i];  // parent indices, length k >= 1
            int   k  = pa.length;

            // ------------------------------------------------------------------
            // Step 1: build permutation array of size i.
            // perm[col] = original index j that maps into permuted column col.
            // Parents occupy columns 0..k-1; non-parents occupy columns k..i-1.
            // This encodes the Python pmat(g, i) permutation matrix.
            // ------------------------------------------------------------------
            int[] perm = buildPerm(i, pa);

            // ------------------------------------------------------------------
            // Step 2: permuted sub-correlation matrix and its Cholesky factor.
            // Rperm[a][b] = R[perm[a]][perm[b]]  (= P^T R[:i,:i] P in Python)
            // L = chol(Rperm)  (lower triangular, L L^T = Rperm)
            // ------------------------------------------------------------------
            double[][] Rperm = permuteSymmetric(R, perm, i);
            double[][] L     = cholesky(Rperm);
            double[][] LInvT = invertLowerTriangularTranspose(L); // = inv(L).T in Python

            // ------------------------------------------------------------------
            // Step 3: sample w ~ mPII_k( (p-i+1)/2 )
            // w has length i; nonzero only in slots 0..k-1 (the parent positions).
            // Python: q ~ Beta(k/2, (p-i+1)/2); u ~ uniform unit sphere in R^k
            //         w[:k] = sqrt(q) * u
            // ------------------------------------------------------------------
            double[] w = new double[i];
            double q     = sampleBeta(k / 2.0, (p - i + 1) / 2.0);
            double sqrtQ = Math.sqrt(q);
            double[] u   = sampleUnitSphere(k);
            for (int j = 0; j < k; j++) w[j] = sqrtQ * u[j];

            // ------------------------------------------------------------------
            // Step 4: compute r, b, o in permuted space, then un-permute via P.
            // rPerm = L w        b_perm = L^{-T} w
            // r[perm[col]] = rPerm[col]   (P rPerm in Python: r = P @ L @ w)
            // b[perm[col]] = bPerm[col]   (P bPerm in Python: b = P @ inv(L).T @ w)
            // o = 1 - w^T w
            // ------------------------------------------------------------------
            double[] rPerm = matVecMul(L,     w);
            double[] bPerm = matVecMul(LInvT, w);

            double[] r = new double[i];
            double[] b = new double[i];
            for (int col = 0; col < i; col++) {
                r[perm[col]] = rPerm[col];
                b[perm[col]] = bPerm[col];
            }

            double o = 1.0;
            for (double wi : w) o -= wi * wi;
            o = Math.max(o, 1e-10); // numerical safety floor

            // ------------------------------------------------------------------
            // Store into R, B, O  (matching Python: R[:i,i]=r; B[i,:i]=b; O[i]=o)
            // ------------------------------------------------------------------
            for (int j = 0; j < i; j++) {
                R[j][i] = r[j];
                R[i][j] = r[j];
                B[i][j] = b[j];
            }
            O[i] = o;
        }

        return new CorrResult(R, B, O);
    }

    /**
     * Simulates {@code n} samples from the linear SEM defined by B and O,
     * processing variables in topological order (B is lower-triangular).
     * <p>
     * Faithful translation of Python {@code simulate(B, O, n, err, rng)}
     * with Gaussian errors. B and O are already in topological order so no
     * internal re-sorting is needed.
     *
     * @param B p×p lower-triangular structural coefficient matrix
     * @param O length-p error variance vector
     * @param p number of variables
     * @param n number of samples
     * @return n×p data matrix X
     */
    private static double[][] simulate(double[][] B, double[] O, int p, int n) {
        double[][] X = new double[n][p];

        for (int s = 0; s < n; s++) {
            for (int i = 0; i < p; i++) {
                double xi = 0.0;
                for (int j = 0; j < i; j++) {
                    if (B[i][j] != 0.0) xi += B[i][j] * X[s][j];
                }
                xi += Math.sqrt(O[i]) * RandomUtil.getInstance().nextGaussian();
                X[s][i] = xi;
            }
        }

        return X;
    }

    // -----------------------------------------------------------------------
    // Graph rewiring — translation of Python sf_out() and sf_in()
    // -----------------------------------------------------------------------

    /**
     * Rewires a DAG to produce a scale-free out-degree distribution.
     * <p>
     * Faithful translation of Python {@code sf_out(g, rng)}.
     * Works in lower-triangular (topological) adjacency form. For each row i,
     * it builds a candidate list J where each predecessor j appears once as a
     * base entry plus once per existing in-edge into column j from rows 0..i-1,
     * shuffles J, then greedily reassigns row i's edges. This rewires within
     * rows, preserving each node's in-degree.
     *
     * @param dag the input DAG
     * @return rewired DAG
     */
    private static Graph sfOut(Graph dag) {
        List<Node> topoOrder = soficOrder(dag);
        int p = topoOrder.size();
        int[][] g = toAdjacencyMatrix(dag, topoOrder);

        for (int i = 1; i < p; i++) {
            // Build candidate list: j appears 1 + (in-degree of j among rows 0..i-1) times
            List<Integer> J = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                J.add(j);
                int inDeg = 0;
                for (int row = 0; row < i; row++) inDeg += g[row][j];
                for (int c = 0; c < inDeg; c++) J.add(j);
            }
            RandomUtil.shuffle(J);

            // Count current in-degree of node i, then clear row i
            int inDeg = 0;
            for (int j = 0; j < i; j++) inDeg += g[i][j];
            Arrays.fill(g[i], 0);

            // Greedily reassign edges
            for (int j : J) {
                if (inDeg == 0) break;
                if (g[i][j] == 0) {
                    g[i][j] = 1;
                    inDeg--;
                }
            }
        }

        return fromAdjacencyMatrix(g, topoOrder);
    }

    /**
     * Rewires a DAG to produce a scale-free in-degree distribution.
     * <p>
     * Faithful translation of Python {@code sf_in(g, rng)}.
     * Works in lower-triangular (topological) adjacency form. For each column
     * (p-i-1), it builds a candidate list J where each predecessor-row index j
     * appears once as a base entry plus once per existing out-edge from row
     * (p-j-1) into columns p-i..p-1, shuffles J, then greedily reassigns the
     * column's edges. This rewires within columns, preserving each node's
     * out-degree.
     *
     * @param dag the input DAG
     * @return rewired DAG
     */
    private static Graph sfIn(Graph dag) {
        List<Node> topoOrder = soficOrder(dag);
        int p = topoOrder.size();
        int[][] g = toAdjacencyMatrix(dag, topoOrder);

        for (int i = 1; i < p; i++) {
            int col = p - i - 1;

            // Build candidate list: j maps to row (p-j-1);
            // j appears 1 + (out-degree of row p-j-1 into columns p-i..p-1) times
            List<Integer> J = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                J.add(j);
                int outDeg = 0;
                for (int c = p - i; c < p; c++) outDeg += g[p - j - 1][c];
                for (int c = 0; c < outDeg; c++) J.add(j);
            }
            RandomUtil.shuffle(J);

            // Count current out-degree of column col, then clear it
            int outDeg = 0;
            for (int row = 0; row < p; row++) outDeg += g[row][col];
            for (int row = 0; row < p; row++) g[row][col] = 0;

            // Greedily reassign edges
            for (int j : J) {
                if (outDeg == 0) break;
                int row = p - j - 1;
                if (g[row][col] == 0) {
                    g[row][col] = 1;
                    outDeg--;
                }
            }
        }

        return fromAdjacencyMatrix(g, topoOrder);
    }

    /**
     * Returns a copy of the graph with its node order randomly permuted.
     * Translation of Python {@code randomize_graph(g, rng)}.
     *
     * @param dag the DAG to shuffle
     * @return graph with node order permuted
     */
    private static Graph randomizeGraph(Graph dag) {
        List<Node> nodes = new ArrayList<>(dag.getNodes());
        RandomUtil.shuffle(nodes);
        Graph reordered = new EdgeListGraph(nodes);
        for (Edge e : dag.getEdges()) reordered.addEdge(e);
        return reordered;
    }

    // -----------------------------------------------------------------------
    // Graph / adjacency-matrix helpers
    // -----------------------------------------------------------------------

    /**
     * Returns a source-first consistent topological ordering of the DAG nodes.
     * Translation of Python {@code sofic_order(g)}: sources are placed first,
     * then each node is appended once all its parents have been placed.
     *
     * @param dag the DAG
     * @return nodes in source-first topological order
     */
    static List<Node> soficOrder(Graph dag) {
        Map<Node, Integer> inDegree = new HashMap<>();
        for (Node n : dag.getNodes()) inDegree.put(n, 0);
        for (Edge e : dag.getEdges()) {
            if (Edges.isDirectedEdge(e)) {
                inDegree.merge(e.getNode2(), 1, Integer::sum);
            }
        }

        List<Node> order     = new ArrayList<>();
        List<Node> remaining = new ArrayList<>();
        for (Node n : dag.getNodes()) {
            if (inDegree.get(n) == 0) order.add(n);
            else remaining.add(n);
        }

        while (!remaining.isEmpty()) {
            boolean found = false;
            for (Iterator<Node> it = remaining.iterator(); it.hasNext(); ) {
                Node n = it.next();
                if (order.containsAll(dag.getParents(n))) {
                    order.add(n);
                    it.remove();
                    found = true;
                    break;
                }
            }
            if (!found) throw new IllegalArgumentException(
                    "Graph is not a DAG (cycle detected in soficOrder).");
        }

        return order;
    }

    /**
     * Builds the parent index array in topological-order space.
     * parents[i] = sorted array of indices j &lt; i such that topoOrder[j]
     * is a parent of topoOrder[i] in the DAG.
     */
    private static int[][] buildParentIndex(Graph dag, List<Node> topoOrder) {
        int p = topoOrder.size();
        Map<Node, Integer> idx = new HashMap<>();
        for (int i = 0; i < p; i++) idx.put(topoOrder.get(i), i);

        int[][] parents = new int[p][];
        for (int i = 0; i < p; i++) {
            List<Node> pNodes = dag.getParents(topoOrder.get(i));
            int[] pa = new int[pNodes.size()];
            int m = 0;
            for (Node pn : pNodes) pa[m++] = idx.get(pn);
            Arrays.sort(pa);
            parents[i] = pa;
        }
        return parents;
    }

    /**
     * Converts a Tetrad graph to a lower-triangular adjacency matrix in the
     * given topological order. g[i][j] = 1 iff topoOrder[j] -&gt; topoOrder[i].
     */
    private static int[][] toAdjacencyMatrix(Graph dag, List<Node> topoOrder) {
        int p = topoOrder.size();
        Map<Node, Integer> idx = new HashMap<>();
        for (int i = 0; i < p; i++) idx.put(topoOrder.get(i), i);

        int[][] g = new int[p][p];
        for (Edge e : dag.getEdges()) {
            if (Edges.isDirectedEdge(e)) {
                int tail = idx.get(Edges.getDirectedEdgeTail(e));
                int head = idx.get(Edges.getDirectedEdgeHead(e));
                g[head][tail] = 1;  // lower triangular: row=head > col=tail
            }
        }
        return g;
    }

    /**
     * Reconstructs a Tetrad graph from a lower-triangular adjacency matrix
     * in the given topological order.
     */
    private static Graph fromAdjacencyMatrix(int[][] g, List<Node> topoOrder) {
        int p = topoOrder.size();
        Graph result = new EdgeListGraph(topoOrder);
        for (int i = 0; i < p; i++)
            for (int j = 0; j < i; j++)
                if (g[i][j] == 1)
                    result.addDirectedEdge(topoOrder.get(j), topoOrder.get(i));
        return result;
    }

    // -----------------------------------------------------------------------
    // Linear algebra helpers
    // -----------------------------------------------------------------------

    /**
     * Builds the permutation array {@code perm} of length {@code i} such that
     * {@code perm[0..k-1]} = parent indices and {@code perm[k..i-1]} =
     * non-parent indices, in their original order within each group.
     * <p>
     * This encodes the column mapping of the Python {@code pmat(g, i)} function:
     * {@code perm[col]} = the original index j that goes into permuted column col.
     *
     * @param i  number of predecessors
     * @param pa sorted parent indices (subset of 0..i-1)
     * @return perm array of length i
     */
    private static int[] buildPerm(int i, int[] pa) {
        Set<Integer> paSet = new HashSet<>();
        for (int p : pa) paSet.add(p);

        int[] perm = new int[i];
        int col = 0;
        for (int j = 0; j < i; j++) if ( paSet.contains(j)) perm[col++] = j;
        for (int j = 0; j < i; j++) if (!paSet.contains(j)) perm[col++] = j;
        return perm;
    }

    /**
     * Returns the i×i sub-matrix of R with rows and columns reordered by
     * {@code perm}: {@code Rperm[a][b] = R[perm[a]][perm[b]]}.
     * <p>
     * Corresponds to {@code P.T @ R[:i,:i] @ P} in the Python.
     */
    private static double[][] permuteSymmetric(double[][] R, int[] perm, int i) {
        double[][] Rperm = new double[i][i];
        for (int a = 0; a < i; a++)
            for (int b = 0; b < i; b++)
                Rperm[a][b] = R[perm[a]][perm[b]];
        return Rperm;
    }

    /**
     * Computes the lower-triangular Cholesky factor L such that L L^T = A.
     * A must be symmetric positive-definite.
     *
     * @param A symmetric positive-definite matrix
     * @return lower-triangular Cholesky factor L
     */
    private static double[][] cholesky(double[][] A) {
        int n = A.length;
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = A[i][j];
                for (int k = 0; k < j; k++) sum -= L[i][k] * L[j][k];
                if (i == j) {
                    if (sum <= 0.0) throw new ArithmeticException(
                            "Matrix is not positive definite at diagonal (" + i + "," + i + ").");
                    L[i][j] = Math.sqrt(sum);
                } else {
                    L[i][j] = sum / L[j][j];
                }
            }
        }
        return L;
    }

    /**
     * Computes L^{-T} = (L^{-1})^T for a lower-triangular matrix L by
     * forward substitution, then transposing.
     * Corresponds to {@code inv(L).T} in the Python.
     *
     * @param L lower-triangular matrix
     * @return L^{-T} (upper-triangular)
     */
    private static double[][] invertLowerTriangularTranspose(double[][] L) {
        int n = L.length;
        double[][] Linv = new double[n][n];
        for (int col = 0; col < n; col++) {
            Linv[col][col] = 1.0 / L[col][col];
            for (int row = col + 1; row < n; row++) {
                double sum = 0.0;
                for (int k = col; k < row; k++) sum -= L[row][k] * Linv[k][col];
                Linv[row][col] = sum / L[row][row];
            }
        }
        // Transpose
        double[][] LinvT = new double[n][n];
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                LinvT[r][c] = Linv[c][r];
        return LinvT;
    }

    /**
     * Multiplies matrix M (m×n) by column vector v (length n).
     *
     * @param M matrix
     * @param v vector
     * @return M * v, length m
     */
    private static double[] matVecMul(double[][] M, double[] v) {
        int m = M.length;
        int n = v.length;
        double[] result = new double[m];
        for (int i = 0; i < m; i++) {
            double sum = 0.0;
            for (int j = 0; j < n; j++) sum += M[i][j] * v[j];
            result[i] = sum;
        }
        return result;
    }

    /** Returns a p×p identity matrix. */
    private static double[][] eye(int p) {
        double[][] I = new double[p][p];
        for (int i = 0; i < p; i++) I[i][i] = 1.0;
        return I;
    }

    /** Returns a length-p vector of ones. */
    private static double[] ones(int p) {
        double[] v = new double[p];
        Arrays.fill(v, 1.0);
        return v;
    }

    // -----------------------------------------------------------------------
    // Statistical samplers
    // -----------------------------------------------------------------------

    /**
     * Samples from Beta(a, b) via the Gamma-ratio method.
     *
     * @param a shape a &gt; 0
     * @param b shape b &gt; 0
     * @return sample in (0, 1)
     */
    private static double sampleBeta(double a, double b) {
        double ga = sampleGamma(a);
        double gb = sampleGamma(b);
        return ga / (ga + gb);
    }

    /**
     * Samples from Gamma(shape, 1) using the Marsaglia-Tsang (2000) method
     * for shape &ge; 1, with the standard reduction for shape &lt; 1.
     *
     * @param shape shape parameter &gt; 0
     * @return sample &gt; 0
     */
    private static double sampleGamma(double shape) {
        if (shape < 1.0) {
            return sampleGamma(shape + 1.0)
                    * Math.pow(RandomUtil.getInstance().nextDouble(), 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x, v;
            do {
                x = RandomUtil.getInstance().nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0.0);
            v = v * v * v;
            double u  = RandomUtil.getInstance().nextDouble();
            double x2 = x * x;
            if (u < 1.0 - 0.0331 * x2 * x2) return d * v;
            if (Math.log(u) < 0.5 * x2 + d * (1.0 - v + Math.log(v))) return d * v;
        }
    }

    /**
     * Samples uniformly from the surface of the unit sphere in R^d by
     * normalising a vector of independent N(0,1) variates.
     *
     * @param d dimensionality
     * @return unit vector of length d
     */
    private static double[] sampleUnitSphere(int d) {
        double[] u = new double[d];
        double norm = 0.0;
        for (int i = 0; i < d; i++) {
            u[i] = RandomUtil.getInstance().nextGaussian();
            norm += u[i] * u[i];
        }
        norm = Math.sqrt(norm);
        if (norm < 1e-300) return sampleUnitSphere(d); // vanishingly unlikely
        for (int i = 0; i < d; i++) u[i] /= norm;
        return u;
    }

    // -----------------------------------------------------------------------
    // DataSet construction
    // -----------------------------------------------------------------------

    /**
     * Wraps the raw n×p simulation matrix into a Tetrad {@link DataSet},
     * with columns named by the nodes in {@code topoOrder}.
     *
     * @param X         n×p data matrix (columns in topological order)
     * @param topoOrder column nodes
     * @param p         number of variables
     * @param n         number of samples
     * @return continuous DataSet
     */
    private static DataSet buildDataSet(double[][] X, List<Node> topoOrder, int p, int n) {
        List<Node> varNodes = new ArrayList<>();
        for (Node node : topoOrder) varNodes.add(new ContinuousVariable(node.getName()));

        DataSet ds = new BoxDataSet(new VerticalDoubleDataBox(n, p), varNodes);
        for (int row = 0; row < n; row++)
            for (int col = 0; col < p; col++)
                ds.setDouble(row, col, X[row][col]);
        return ds;
    }
}
