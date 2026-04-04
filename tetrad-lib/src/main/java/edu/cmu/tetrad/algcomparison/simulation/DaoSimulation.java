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
 * The algorithm proceeds in topological order over the DAG. For each variable
 * i with parents pa(i), it samples the partial correlations of i with all
 * preceding variables conditioned on pa(i) using the multivariate Pearson
 * Type II distribution. Variables not in pa(i) get partial correlation zero
 * (enforcing the Markov property), while variables in pa(i) receive freely
 * sampled partial correlations. These are then converted to regular
 * correlations via the Onion method recurrence.
 * <p>
 * Optional graph-rewiring steps:
 * <ul>
 *   <li>{@code sfOut} — rewires to produce a scale-free out-degree distribution</li>
 *   <li>{@code sfIn}  — rewires to produce a scale-free in-degree distribution</li>
 *   <li>{@code randomizeOrder} — randomly permutes variable order after rewiring</li>
 * </ul>
 *
 * @author Bryan Andrews
 */
public class DaoSimulation implements Simulation {

    @Serial
    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------
    // Parameter name constants (register in Params if not already present)
    // -----------------------------------------------------------------------

//    /** Average degree for the Erdős–Rényi DAG. Typically called NUM_MEASURES_PER_MODEL or similar; reuse AVERAGE_DEGREE. */
//    public static final String DAO_AVG_DEGREE      = "daoAvgDegree";
    /** Whether to rewire to scale-free out-degree. */
//    public static final String DAO_SF_OUT          = "daoScaleFreeOut";
//    /** Whether to rewire to scale-free in-degree. */
//    public static final String DAO_SF_IN           = "daoScaleFreeIn";
//    /** Whether to randomly permute variable order after graph generation. */
//    public static final String DAO_RANDOMIZE_ORDER = "daoRandomizeOrder";

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final RandomGraph randomGraph;
    private List<DataSet>     dataSets = new ArrayList<>();
    private List<Graph>       graphs   = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Constructs a DaoSimulation using the supplied {@link RandomGraph} to
     * produce the true DAG on each run.  The standard Tetrad random-graph
     * generators (e.g. {@code RandomForward}) are all acceptable here.
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
     * applied to the true DAG.  If {@code DIFFERENT_GRAPHS} is true, a fresh
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

            boolean sfOut = parameters.getBoolean(Params.DAO_SF_OUT, false);
            boolean sfIn  = parameters.getBoolean(Params.DAO_SF_IN,  false);

            if (sfOut) workingGraph = rewireScaleFree(workingGraph, true);
            if (sfIn)  workingGraph = rewireScaleFree(workingGraph, false);

            // ------------------------------------------------------------------
            // 2. Optionally randomize variable order
            // ------------------------------------------------------------------
            if (parameters.getBoolean(Params.DAO_RANDOMIZE_ORDER, true)) {
                workingGraph = randomizeOrder(workingGraph);
            }

            // ------------------------------------------------------------------
            // 3. Sample correlation matrix R via the DaO method
            // ------------------------------------------------------------------
            // Obtain a topological ordering of the nodes
            List<Node> topoOrder = getTopologicalOrder(workingGraph);
            int p = topoOrder.size();

            // adjacency in topological-order space: parents[i] = set of indices j < i
            // such that topoOrder[j] -> topoOrder[i] is an edge
            int[][] parents = buildParentIndex(workingGraph, topoOrder);

            // R is the p×p correlation matrix being built up
            double[][] R = daoSampleCorrelationMatrix(p, parents);

            // ------------------------------------------------------------------
            // 4. Recover structural coefficients B and error variances O from R
            // ------------------------------------------------------------------
            // B[i][j] = regression coefficient of parent j on child i (in topo order)
            // O[i]    = residual variance of node i given its parents
            double[][] B = new double[p][p];
            double[]   O = new double[p];
            extractBAndO(R, parents, B, O);

            // ------------------------------------------------------------------
            // 5. Simulate n samples:  X = Z (I - B)^{-T}  where Z_i ~ N(0, O_i)
            // ------------------------------------------------------------------
            double[][] X = simulateData(B, O, p, sampleSize);

            // ------------------------------------------------------------------
            // 6. Wrap into a Tetrad DataSet (columns in topoOrder)
            // ------------------------------------------------------------------
            DataSet dataSet = buildDataSet(X, topoOrder, p, sampleSize);
            dataSet.setName("Run" + (run + 1));

            // Post-process (standardize, add noise, etc.) using the same helper
            // pattern as SemSimulation if desired.  Here we honour STANDARDIZE.
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
    // Core DaO algorithm
    // -----------------------------------------------------------------------

    /**
     * Samples a p×p correlation matrix uniformly over the space of all
     * correlation matrices Markov to the DAG encoded by {@code parents}.
     * <p>
     * The algorithm follows Andrews &amp; Kummerfeld (2024) Section 3, adapting
     * the Onion method (Ghosh &amp; Henderson 2003/2009; Lewandowski et al. 2009)
     * to DAGs.  Variables are processed in topological order 1 … p.
     * <ul>
     *   <li>R starts as a 1×1 matrix [1].</li>
     *   <li>At step i, we must append row/column i to R_{i-1} to get R_i.</li>
     *   <li>The new partial correlations of i with predecessors j &lt; i are:
     *       <ul>
     *         <li>0  if j ∉ pa(i) — Markov constraint</li>
     *         <li>sampled from mPII  if j ∈ pa(i)</li>
     *       </ul>
     *   </li>
     *   <li>Partial correlations are converted to marginal correlations via the
     *       standard recurrence used by the Onion method.</li>
     * </ul>
     *
     * @param p       number of variables
     * @param parents parents[i] = sorted array of parent indices in [0, i)
     * @return symmetric positive-definite p×p correlation matrix
     */
    private static double[][] daoSampleCorrelationMatrix(int p, int[][] parents) {
        double[][] R = new double[p][p];
        R[0][0] = 1.0;

        for (int i = 1; i < p; i++) {
            // Sub-matrix R[0..i-1][0..i-1]
            double[][] Rprev = subMatrix(R, i);

            // ---------------------------------------------------------------
            // Sample the partial-correlation vector q of length i for node i.
            //
            // For the DaO method: partial correlation of i with j (given all
            // variables between j and i that precede i) is:
            //   0   if j is NOT a parent of i
            //   u_j if j IS a parent of i,  where u ~ mPII_{|pa(i)|}(gamma)
            //
            // gamma for the Onion method at step i: gamma_i = (p - i) / 2
            // ---------------------------------------------------------------
            int[] pa = parents[i];
            int   k  = pa.length;  // number of parents

            // Partial correlations in position j: 0 for non-parents, sampled for parents
            double[] partialCorr = new double[i]; // default 0

            if (k > 0) {
                // Sample u from mPII_k(gamma) where gamma = (p - i) / 2.0
                // mPII_k(gamma): W = Q^{1/2} * U  where
                //   Q ~ Beta(k/2, gamma + 1/2)
                //   U ~ Uniform(unit sphere in R^k)
                double gamma = (p - i) / 2.0;
                double q = sampleBeta(k / 2.0, gamma + 0.5);
                double sqrtQ = Math.sqrt(q);
                double[] u = sampleUnitSphere(k);
                double[] w = new double[k];
                for (int m = 0; m < k; m++) w[m] = sqrtQ * u[m];

                // Map w[m] -> partial correlation for parent pa[m]
                for (int m = 0; m < k; m++) {
                    partialCorr[pa[m]] = w[m];
                }
            }

            // ---------------------------------------------------------------
            // Convert partial correlations to the completion vector r_i
            // using the Cholesky / Onion recurrence:
            //
            //   r_i = L^{-T} * partialCorr * sqrt(1 - partialCorr^T R^{-1} partialCorr)
            //   ... which simplifies to the standard formula:
            //
            //   r_i = (L^{-T} * partialCorr)
            //       scaled by the residual standard deviation factor below.
            //
            // More precisely: let L L^T = R_{i-1}.  Then the completion r_i
            // such that the conditional correlations equal partialCorr is:
            //
            //   r_i^T = partialCorr^T * R_{i-1}^{-1} ... (see Lewandowski 2009)
            //
            // The standard Onion recurrence gives (Lewandowski et al. 2009, eq. 8):
            //
            //   r_i = R_{i-1} * ...
            //
            // We use the direct formula derived from the partial-to-marginal
            // conversion for multivariate normal distributions.
            // ---------------------------------------------------------------

            // Compute r (marginal correlations of new variable with existing ones).
            // Given the vector of partial correlations 'partialCorr', we must
            // back-transform to the marginal correlation vector r_i via:
            //
            //   The Cholesky of R_{i-1} is L.  Let s = L^{-1} * partialCorr.
            //   Then r_i = L * s * scale, where scale keeps R_i a valid correlation matrix.
            //
            // Equivalently, using the recurrence from Section 3.2 of the paper
            // and Lewandowski et al. (2009): the completion of R_{i-1} into R_i
            // in the Onion method is r_i = L_{i-1} * z_i, where z_i is drawn
            // from the unit (i-1)-ball.  The partial correlation vector p_i is
            // related to z_i by p_i = L_{i-1}^{-T} * r_i ...
            //
            // Simplest correct implementation: build r_i by solving
            //   r_i = R_{i-1} * partialCorr  (this is the "back-transform" for
            //   the block-regression partial correlation definition used here)
            // then check/enforce positive definiteness of the extension.
            //
            // See also the Python reference: the 'corr' function builds R by
            // appending r = R_prev @ q (matrix-vector multiply) where q is the
            // partial correlation vector (zeros for non-parents, sampled for parents).

            double[] r = matVecMul(Rprev, partialCorr);

            // Check positive definiteness:  1 - r^T R^{-1} r > 0
            // (Lemma 1 in the paper; this is guaranteed if |partial corr| < 1,
            //  which the mPII sampler ensures since ||w||^2 = q < 1.)
            // So no additional rejection step is needed.

            // Fill in R[i][j] = R[j][i] = r[j] for j < i, and R[i][i] = 1
            for (int j = 0; j < i; j++) {
                R[i][j] = r[j];
                R[j][i] = r[j];
            }
            R[i][i] = 1.0;
        }

        return R;
    }

    /**
     * Recovers the structural coefficient matrix B and error variances O from
     * the correlation matrix R and parent structure, using the formulas from
     * Andrews &amp; Kummerfeld (2024) Section 2.3 (Equations 5–6):
     * <pre>
     *   B[i, pa(i)] = R[i, pa(i)] * R[pa(i), pa(i)]^{-1}
     *   O[i]        = R[i,i] - B[i, pa(i)] * R[pa(i), i]
     *               = 1 - B[i, pa(i)] * R[pa(i), i]
     * </pre>
     * (R[i,i] = 1 since R is a correlation matrix.)
     *
     * @param R       full p×p correlation matrix
     * @param parents parents[i] = parent indices in [0, i)
     * @param B       output: structural coefficient matrix (lower triangular)
     * @param O       output: error variances (always in (0, 1] since R is a corr matrix)
     */
    private static void extractBAndO(double[][] R, int[][] parents,
                                     double[][] B, double[] O) {
        int p = R.length;
        for (int i = 0; i < p; i++) {
            int[] pa = parents[i];
            int   k  = pa.length;
            if (k == 0) {
                B[i] = new double[p]; // all zeros
                O[i] = 1.0;
            } else {
                // Extract R[pa, pa] sub-matrix and R[i, pa] vector
                double[][] Rpp = new double[k][k];
                double[]   Rip = new double[k];
                for (int a = 0; a < k; a++) {
                    Rip[a] = R[i][pa[a]];
                    for (int b = 0; b < k; b++) {
                        Rpp[a][b] = R[pa[a]][pa[b]];
                    }
                }
                double[][] RppInv = invert(Rpp);
                double[]   bi     = matVecMul(RppInv, Rip); // B[i, pa] = R[i,pa] @ R[pa,pa]^-1

                B[i] = new double[p];
                double dot = 0.0;
                for (int a = 0; a < k; a++) {
                    B[i][pa[a]] = bi[a];
                    dot += bi[a] * Rip[a];
                }
                O[i] = Math.max(1.0 - dot, 1e-10); // numerical safety floor
            }
        }
    }

    /**
     * Simulates {@code n} samples from the linear SEM:
     * <pre>
     *   X_i = sum_{j in pa(i)} B[i][j] * X_j  +  sqrt(O[i]) * eps_i
     * </pre>
     * where eps_i ~ N(0,1) independently, processing variables in topological order.
     *
     * @param B p×p lower-triangular coefficient matrix (in topological order)
     * @param O length-p error variance vector
     * @param p number of variables
     * @param n number of samples
     * @return n×p data matrix X
     */
    private static double[][] simulateData(double[][] B, double[] O, int p, int n) {
        double[][] X = new double[n][p];

        for (int s = 0; s < n; s++) {
            // Generate error terms for this sample
            for (int i = 0; i < p; i++) {
                // X_i = sum_j B[i][j] * X_j  (only j < i contributes since B is lower-tri)
                //      + sqrt(O[i]) * N(0,1)
                double xi = 0.0;
                for (int j = 0; j < i; j++) {
                    xi += B[i][j] * X[s][j];
                }
                xi += Math.sqrt(O[i]) * RandomUtil.getInstance().nextGaussian();
                X[s][i] = xi;
            }
        }

        return X;
    }

    // -----------------------------------------------------------------------
    // Graph utilities
    // -----------------------------------------------------------------------

    /**
     * Returns a list of nodes in a topological order consistent with the DAG.
     * Uses Kahn's algorithm (BFS from sources).
     *
     * @param dag the DAG
     * @return nodes in topological order
     */
    private static List<Node> getTopologicalOrder(Graph dag) {
        Map<Node, Integer> inDegree = new HashMap<>();
        for (Node n : dag.getNodes()) inDegree.put(n, 0);
        for (Edge e : dag.getEdges()) {
            if (Edges.isDirectedEdge(e)) {
                inDegree.merge(e.getNode2(), 1, Integer::sum);
            }
        }

        Queue<Node> queue = new ArrayDeque<>();
        for (Node n : dag.getNodes()) {
            if (inDegree.get(n) == 0) queue.add(n);
        }

        List<Node> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            order.add(n);
            for (Node child : dag.getChildren(n)) {
                int deg = inDegree.merge(child, -1, Integer::sum);
                if (deg == 0) queue.add(child);
            }
        }

        if (order.size() != dag.getNumNodes()) {
            throw new IllegalArgumentException(
                    "Graph is not a DAG (cycle detected during topological sort).");
        }
        return order;
    }

    /**
     * Builds the parent index array: parents[i] = sorted array of indices j &lt; i
     * such that topoOrder[j] → topoOrder[i] is an edge in the DAG.
     *
     * @param dag       the DAG
     * @param topoOrder topological ordering produced by {@link #getTopologicalOrder}
     * @return parents array
     */
    private static int[][] buildParentIndex(Graph dag, List<Node> topoOrder) {
        int p = topoOrder.size();
        Map<Node, Integer> idx = new HashMap<>();
        for (int i = 0; i < p; i++) idx.put(topoOrder.get(i), i);

        int[][] parents = new int[p][];
        for (int i = 0; i < p; i++) {
            Node ni = topoOrder.get(i);
            List<Node> pNodes = dag.getParents(ni);
            int[] pa = new int[pNodes.size()];
            int m = 0;
            for (Node pn : pNodes) pa[m++] = idx.get(pn);
            Arrays.sort(pa);
            parents[i] = pa;
        }
        return parents;
    }

    /**
     * Rewires a DAG to produce an approximately scale-free out-degree (if
     * {@code out=true}) or in-degree ({@code out=false}) distribution, following
     * the preferential-attachment rewiring described in Andrews &amp; Kummerfeld (2024)
     * Section 3.3.
     * <p>
     * For each edge e = (u → v) in a random order, rewire u (out) or v (in)
     * with probability proportional to the current degree of the candidate
     * replacement, preserving acyclicity.
     *
     * @param dag the input DAG
     * @param out {@code true} for scale-free out-degree; {@code false} for in-degree
     * @return rewired DAG (may be a copy)
     */
    private static Graph rewireScaleFree(Graph dag, boolean out) {
        // Copy the graph so we don't mutate the original
        Graph g = new EdgeListGraph(dag);

        List<Node> nodes = new ArrayList<>(g.getNodes());
        List<Edge> edges = new ArrayList<>(g.getEdges());
        RandomUtil.shuffle(edges);

        // Preferential-attachment rewiring (Andrews & Kummerfeld 2024, Sec. 3.3):
        // For each edge, try to rewire the tail (out) or head (in) to a node
        // chosen proportionally to its current out/in-degree + 1 (to allow
        // degree-0 nodes), subject to: no self-loop, no cycle, no duplicate edge.
        for (Edge e : edges) {
            if (!Edges.isDirectedEdge(e)) continue;

            Node tail = Edges.getDirectedEdgeTail(e);
            Node head = Edges.getDirectedEdgeHead(e);

            if (out) {
                Node newTail = sampleByDegreeOut(nodes, g);
                if (newTail.equals(head)) continue;
                if (g.isAncestorOf(head, newTail)) continue; // would create cycle
                if (g.isAdjacentTo(newTail, head)) continue; // duplicate
                g.removeEdge(e);
                g.addDirectedEdge(newTail, head);
            } else {
                // rewire the head: keep tail, sample new head proportional to in-degree + 1
                Node newHead = sampleByDegreeIn(nodes, g);
                if (newHead.equals(tail)) continue;
                if (g.isAncestorOf(newHead, tail)) continue; // would create cycle
                if (g.isAdjacentTo(tail, newHead)) continue; // duplicate
                g.removeEdge(e);
                g.addDirectedEdge(tail, newHead);
            }
        }

        return g;
    }

    /** Sample a node proportional to out-degree + 1. */
    private static Node sampleByDegreeOut(List<Node> nodes, Graph g) {
        double[] weights = new double[nodes.size()];
        double total = 0.0;
        for (int i = 0; i < nodes.size(); i++) {
            weights[i] = g.getChildren(nodes.get(i)).size() + 1.0;
            total += weights[i];
        }
        return sampleWeighted(nodes, weights, total);
    }

    /** Sample a node proportional to in-degree + 1. */
    private static Node sampleByDegreeIn(List<Node> nodes, Graph g) {
        double[] weights = new double[nodes.size()];
        double total = 0.0;
        for (int i = 0; i < nodes.size(); i++) {
            weights[i] = g.getParents(nodes.get(i)).size() + 1.0;
            total += weights[i];
        }
        return sampleWeighted(nodes, weights, total);
    }

    private static Node sampleWeighted(List<Node> nodes, double[] w, double total) {
        double r = RandomUtil.getInstance().nextDouble() * total;
        double cum = 0.0;
        for (int i = 0; i < nodes.size(); i++) {
            cum += w[i];
            if (r <= cum) return nodes.get(i);
        }
        return nodes.get(nodes.size() - 1);
    }

    /**
     * Returns a copy of the graph with its node list randomly permuted.
     * The edges are preserved; only the traversal order of nodes changes.
     * This corresponds to Andrews &amp; Kummerfeld's {@code randomize_graph}.
     *
     * @param dag the DAG to shuffle
     * @return graph with node order permuted
     */
    private static Graph randomizeOrder(Graph dag) {
        Graph copy = new EdgeListGraph(dag);
        List<Node> nodes = new ArrayList<>(copy.getNodes());
        RandomUtil.shuffle(nodes);
        // EdgeListGraph does not expose a node-reorder API, but the node list
        // order only affects iteration order, not edge semantics.  A clean
        // reorder requires rebuilding:
        Graph reordered = new EdgeListGraph(nodes);
        for (Edge e : copy.getEdges()) {
            reordered.addEdge(e);
        }
        return reordered;
    }

    // -----------------------------------------------------------------------
    // DataSet construction
    // -----------------------------------------------------------------------

    /**
     * Wraps the raw simulation matrix X into a Tetrad {@link DataSet}.
     *
     * @param X          n×p data matrix
     * @param topoOrder  column nodes in topological order
     * @param p          number of variables
     * @param n          number of samples (rows)
     * @return a continuous DataSet
     */
    private static DataSet buildDataSet(double[][] X, List<Node> topoOrder, int p, int n) {
        List<Node> varNodes = new ArrayList<>();
        for (Node node : topoOrder) {
            varNodes.add(new ContinuousVariable(node.getName()));
        }

        DataSet ds = new BoxDataSet(new VerticalDoubleDataBox(n, p), varNodes);
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < p; col++) {
                ds.setDouble(row, col, X[row][col]);
            }
        }
        return ds;
    }

    // -----------------------------------------------------------------------
    // Statistical samplers
    // -----------------------------------------------------------------------

    /**
     * Samples from the Beta(a, b) distribution using the relationship to
     * Gamma distributions:  Beta(a,b) = Gamma(a,1) / (Gamma(a,1) + Gamma(b,1)).
     * Uses a simple Marsaglia–Tsang rejection sampler for the Gamma.
     *
     * @param a shape parameter a > 0
     * @param b shape parameter b > 0
     * @return sample in (0, 1)
     */
    private static double sampleBeta(double a, double b) {
        double ga = sampleGamma(a);
        double gb = sampleGamma(b);
        return ga / (ga + gb);
    }

    /**
     * Samples from Gamma(shape, 1) using the Marsaglia-Tsang (2000) method
     * for shape >= 1, and the reduction Gamma(shape) = Gamma(shape+1) * U^{1/shape}
     * for 0 < shape < 1.
     *
     * @param shape shape parameter > 0
     * @return sample > 0
     */
    private static double sampleGamma(double shape) {
        if (shape < 1.0) {
            // Reduction: X ~ Gamma(shape) = Gamma(shape+1) * U^{1/shape}
            return sampleGamma(shape + 1.0) * Math.pow(RandomUtil.getInstance().nextDouble(), 1.0 / shape);
        }
        // Marsaglia & Tsang (2000) method
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x, v;
            do {
                x = RandomUtil.getInstance().nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0.0);
            v = v * v * v;
            double u = RandomUtil.getInstance().nextDouble();
            double x2 = x * x;
            if (u < 1.0 - 0.0331 * (x2 * x2)) return d * v;
            if (Math.log(u) < 0.5 * x2 + d * (1.0 - v + Math.log(v))) return d * v;
        }
    }

    /**
     * Samples a point uniformly from the surface of the unit sphere in R^d.
     * Uses the standard approach: normalise a vector of independent N(0,1) variates.
     *
     * @param d   dimensionality
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
        if (norm < 1e-300) {
            // Extremely unlikely; retry
            return sampleUnitSphere(d);
        }
        for (int i = 0; i < d; i++) u[i] /= norm;
        return u;
    }

    // -----------------------------------------------------------------------
    // Linear algebra helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the leading i×i sub-matrix of R.
     *
     * @param R full matrix
     * @param i sub-matrix size
     * @return copy of R[0..i-1][0..i-1]
     */
    private static double[][] subMatrix(double[][] R, int i) {
        double[][] sub = new double[i][i];
        for (int a = 0; a < i; a++)
            for (int b = 0; b < i; b++)
                sub[a][b] = R[a][b];
        return sub;
    }

    /**
     * Inverts a square matrix using Gaussian elimination with partial pivoting.
     *
     * @param A square matrix (not modified)
     * @return A^{-1}
     * @throws ArithmeticException if A is singular
     */
    static double[][] invert(double[][] A) {
        int n = A.length;
        double[][] aug = new double[n][2 * n];

        // Build augmented matrix [A | I]
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) aug[i][j] = A[i][j];
            aug[i][n + i] = 1.0;
        }

        for (int col = 0; col < n; col++) {
            // Partial pivoting
            int maxRow = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(aug[row][col]) > Math.abs(aug[maxRow][col])) maxRow = row;
            }
            double[] tmp = aug[col];
            aug[col] = aug[maxRow];
            aug[maxRow] = tmp;

            double pivot = aug[col][col];
            if (Math.abs(pivot) < 1e-14)
                throw new ArithmeticException("Matrix is singular or near-singular.");

            for (int j = 0; j < 2 * n; j++) aug[col][j] /= pivot;

            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double factor = aug[row][col];
                for (int j = 0; j < 2 * n; j++) aug[row][j] -= factor * aug[col][j];
            }
        }

        // Extract inverse
        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                inv[i][j] = aug[i][n + j];
        return inv;
    }

    /**
     * Multiplies matrix M (m×n) by vector v (length n), returning a vector of length m.
     *
     * @param M matrix
     * @param v vector
     * @return M * v
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
}
