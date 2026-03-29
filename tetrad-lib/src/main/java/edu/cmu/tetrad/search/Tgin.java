package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.mimic.TrekMeasurementModelBuilderBoss;
import edu.cmu.tetrad.search.mimic.TrekMeasurementModelBuilderPc;
import edu.cmu.tetrad.search.test.FfCiContinuous;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.ejml.simple.SimpleMatrix;
import org.ejml.simple.SimpleSVD;

import java.util.*;

/**
 * TGIN (Trek Rule Generalized Independent Noise) is a causal discovery algorithm for
 * learning the structure of linear latent variable models from observational data.
 *
 * <p>The algorithm proceeds in several stages:
 *
 * <ol>
 *   <li><b>Measurement model recovery.</b> A PC- or BOSS-based trek separation
 *       procedure (TSC) is applied to the observed variables to identify latent
 *       clusters, their indicator sets, and the rank of each cluster's latent
 *       subspace. This produces a skeleton over latent and measured nodes with
 *       directed latent→measured edges.</li>
 *
 *   <li><b>Rank expansion.</b> Each latent node of rank r is expanded into r
 *       distinct latent copies (e.g., L3 of rank 2 becomes L3.1 and L3.2), each
 *       inheriting the same measured indicators. Undirected edges are placed
 *       between copies of different clusters that were adjacent in the original
 *       skeleton.</li>
 *
 *   <li><b>Intra-cluster orientation (Stage 5).</b> For rank-r > 1 clusters,
 *       FastICA is applied to the cluster's indicator block to recover r latent
 *       source signals. Pairwise LiNGAM tests on the recovered sources determine
 *       a causal ordering over the r copies within each cluster.</li>
 *
 *   <li><b>Inter-cluster orientation (Stage 4).</b> For each undirected edge
 *       between clusters, a group GIN (Generalized Independence Noise) test is
 *       applied under the LiNGLaM assumption. If the left null space of the
 *       cross-covariance matrix between the two clusters' indicators yields a
 *       residual signal that is independent of the other cluster's indicators,
 *       causal priority is assigned and the edge is oriented accordingly.</li>
 * </ol>
 *
 * <p>The algorithm assumes a linear non-Gaussian acyclic model (LiNGAM) over the
 * latent variables, which is required for the GIN and LiNGAM orientation steps to
 * be identifiable. Under Gaussian noise, latent-latent edges may not be fully
 * orientable and the output may contain undirected edges.
 *
 * <p>References:
 * <ul>
 *   <li>Shimizu et al. (2006). A linear non-Gaussian acyclic model for causal
 *       discovery. JMLR.</li>
 *   <li>Entner &amp; Hoyer (2011). Discovering unconfounded causal relationships
 *       using linear non-Gaussian models. JSAI.</li>
 *   <li>Spirtes et al. (2000). Causation, Prediction, and Search. MIT Press.</li>
 *   <li>Xie, F., Cai, R., Huang, B., Glymour, C., Hao, Z., and Zhang, K. (2020).
 *       Generalized independent noise condition for estimating latent variable
 *       causal graphs. In <i>Advances in Neural Information Processing Systems</i>,
 *       pages 14891–14902.</li>
 *   <li>Xie, F., Huang, B., Chen, Z., Cai, R., Glymour, C., Geng, Z., and Zhang, K.
 *       (2024). Generalized independent noise condition for estimating causal
 *       structure with latent variables. <i>Journal of Machine Learning Research</i>,
 *       25(191):1–61.</li>* </ul>
 *
 * @author [your name]
 * @see TrekMeasurementModelBuilderPc
 * @see TrekMeasurementModelBuilderBoss
 */
public class Tgin implements IGraphSearch {

    /**
     * Optional known measured inputs by name.
     */
    private final Set<String> inputNames = new LinkedHashSet<>();
    /**
     * Optional known measured outputs by name.
     */
    private final Set<String> outputNames = new LinkedHashSet<>();
    /**
     * Optional knowledge.
     */
    private final Knowledge knowledge = new Knowledge();
    private final RawMarginalIndependenceTest hsic;
    /**
     * Input data set.
     */
    private DataSet dataSet;
    /**
     * Parameters controlling the search.
     */
    private Parameters parameters;
    /**
     * PC depth.
     */
    private int depth = -1;

    /**
     * Verbosity flag.
     */
    private boolean verbose = false;

    /**
     * Whether to orient latent-latent edges after pruning.
     */
    private boolean orientAndPrune = true;

    /**
     * Working graph.
     */
    private Graph graph;

    /**
     * Working latent list.
     */
    private List<Node> allLatents;

    /**
     * Sample size.
     */
    private int sampleSize;

    /**
     * Alpha level.
     */
    private double alpha = 0.01;

    private Map<Node, Integer> latentRanks = new LinkedHashMap<>();

    /**
     * Constructs a TrekMimic search with data and parameters.
     *
     * @param dataSet    the data set
     * @param parameters the parameters
     */
    public Tgin(DataSet dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        this.parameters = parameters;
        this.depth = parameters.getInt(Params.DEPTH);
        this.alpha = parameters.getDouble(Params.ALPHA);
        this.verbose = parameters.getBoolean(Params.VERBOSE);

        hsic = new edu.cmu.tetrad.search.test.FfCiContinuous(dataSet);
        ((edu.cmu.tetrad.search.test.FfCiContinuous) hsic).setAlpha(alpha);

        setDataSet(dataSet);
        setParameters(parameters);
    }

    public Graph search() throws InterruptedException {
        runPcTsc();

        Graph _graph = new EdgeListGraph();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                _graph.addNode(node);
            }
        }

        Map<Node, Node> nodeMap = new HashMap<>();
        Map<Node, List<Node>> originalToExpanded = new HashMap<>();

        // First pass: copies + latent→measured edges only
        for (Node latent : graph.getNodes()) {
            if (latent.getNodeType() != NodeType.LATENT) continue;

            int rank = latentRanks.getOrDefault(latent, 1);
            List<Node> copies = new ArrayList<>();

            for (int r = 1; r <= rank; r++) {
                Node copy = new ContinuousVariable(latent.getName() + "." + r);
                copy.setNodeType(NodeType.LATENT);
                _graph.addNode(copy);
                nodeMap.put(copy, latent);
                copies.add(copy);

                for (Edge edge : graph.getEdges(latent)) {
                    Node other = edge.getNode1() == latent ? edge.getNode2() : edge.getNode1();
                    if (other.getNodeType() == NodeType.MEASURED) {
                        Edge toAdd = Edges.directedEdge(copy, other);  // always latent → measured
                        if (!_graph.containsEdge(toAdd)) _graph.addEdge(toAdd);
                    }
                    // latent-latent handled in second pass — original nodes must not enter _graph
                }
            }
            originalToExpanded.put(latent, copies);
        }

        // Second pass: latent-latent undirected edges between copies only
        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();
            if (n1.getNodeType() != NodeType.LATENT || n2.getNodeType() != NodeType.LATENT) continue;

            List<Node> copies1 = originalToExpanded.get(n1);
            List<Node> copies2 = originalToExpanded.get(n2);
            if (copies1 == null || copies2 == null) continue;

            for (Node c1 : copies1)
                for (Node c2 : copies2) {
                    Edge toAdd = Edges.undirectedEdge(c1, c2);
                    if (!_graph.containsEdge(toAdd)) _graph.addEdge(toAdd);
                }
        }
        graph = _graph.copy();
        this.allLatents = new ArrayList<>(nodeMap.keySet());

        TginOrientationStages stages = new TginOrientationStages(
                this.graph, this.allLatents, nodeMap,   // nodeMap replaces spec + latentRanks
                this.dataSet, this.alpha, this.sampleSize, hsic);

        System.out.println("=== Expansion check ===");
        for (Map.Entry<Node, Node> e : nodeMap.entrySet()) {
            System.out.println("  copy=" + e.getKey().getName() + " -> original=" + e.getValue().getName());
        }
        System.out.println("latentRanks: " + latentRanks);

        stages.orientIntraClusterEdges();   // Stage 5 first: resolve intra-cluster order


        for (int i = 0; i < 3; i++) {
            stages.orientInterClusterEdges();   // Stage 4: orient inter-cluster edges using GIN
        }

        return graph;
    }

    /**
     * Sets the data set.
     *
     * @param dataSet the data set
     */
    public void setDataSet(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Data set must not be null.");
        }

        this.dataSet = dataSet;
    }

    /**
     * Sets the parameters.
     *
     * @param parameters the parameters
     */
    public void setParameters(Parameters parameters) {
        if (parameters == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        this.parameters = parameters;
    }

    private void runPcTsc() throws InterruptedException {
        boolean usePc = true;

        BlockSpec spec;

        if (usePc) {
            TrekMeasurementModelBuilderPc builder =
                    new TrekMeasurementModelBuilderPc(dataSet, parameters);

            builder.setKnowledge(this.knowledge);
            builder.setInputNames(this.inputNames);
            builder.setOutputNames(this.outputNames);
            builder.setDepth(this.depth);
            builder.setVerbose(this.verbose);

            TrekMeasurementModelBuilderPc.MeasurementBuildResult result = builder.build();

            this.graph = result.graph();
            this.allLatents = new ArrayList<>(result.latents());
            this.sampleSize = result.sampleSize();
            this.alpha = result.alpha();

            this.latentRanks = new LinkedHashMap<>();
            spec = result.spec();
            for (int i = 0; i < spec.blockVariables().size(); i++) {
                this.latentRanks.put(spec.blockVariables().get(i), spec.ranks().get(i));
            }
        } else {
            TrekMeasurementModelBuilderBoss builder =
                    new TrekMeasurementModelBuilderBoss(dataSet, parameters);

            builder.setKnowledge(this.knowledge);
            builder.setInputNames(this.inputNames);
            builder.setOutputNames(this.outputNames);
            builder.setVerbose(this.verbose);

            TrekMeasurementModelBuilderBoss.MeasurementBuildResult result = builder.build();

            this.graph = result.graph();
            this.allLatents = new ArrayList<>(result.latents());
            this.sampleSize = result.sampleSize();
            this.alpha = result.alpha();

            this.latentRanks = new LinkedHashMap<>();
            spec = result.spec();
            for (int i = 0; i < spec.blockVariables().size(); i++) {
                this.latentRanks.put(spec.blockVariables().get(i), spec.ranks().get(i));
            }
        }
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }


    // ---------------------------------------------------------------
    // GIN Stages 4 and 5
    // Assumes all fields set above are in scope.
    // dataSet is the original DataSet passed to TrekMeasurementModelBuilderPc.
    // ---------------------------------------------------------------

    public static class TginOrientationStages {

        private final Graph graph;
        private final Map<Node, Node> nodeMap;                      // expanded → original
        private final DataSet dataSet;
        private final double alpha;

        // Derived in constructor
        private final Map<Node, List<Node>> originalToExpanded;     // original → expanded copies
        private final Map<Node, Integer> rankByOriginal;            // original → rank (# copies)
        private final Map<Node, List<Integer>> originalToIndicatorCols; // original → data col indices
        private final RawMarginalIndependenceTest hsic;

        public TginOrientationStages(Graph graph, List<Node> allLatents,
                                     Map<Node, Node> nodeMap,
                                     DataSet dataSet, double alpha, int sampleSize,
                                     RawMarginalIndependenceTest hsic) {
            this.graph = graph;
            this.nodeMap = nodeMap;
            this.dataSet = dataSet;
            this.alpha = alpha;
            this.originalToExpanded = buildOriginalToExpanded();
            this.rankByOriginal = buildRankMap();
            this.originalToIndicatorCols = buildIndicatorMap();
            this.hsic = hsic;;
        }

        // ==============================================================
        // Stage 4: inter-cluster GIN orientation
        // ==============================================================

        public void orientInterClusterEdges() throws InterruptedException {
            List<Node> originals = new ArrayList<>(originalToExpanded.keySet());

            for (int i = 0; i < originals.size(); i++) {
                for (int j = i + 1; j < originals.size(); j++) {
                    Node origX = originals.get(i);
                    Node origY = originals.get(j);

                    if (!hasUndirectedEdgeBetweenClusters(origX, origY)) continue;

                    OrientationResult xToY = groupGinTest(origX, origY);
                    OrientationResult yToX = groupGinTest(origY, origX);

                    if (xToY.independent() && !yToX.independent()) {
                        removeUndirectedEdgesBetweenClusters(origX, origY);
                        expandEdge(origX, origY);
                    } else if (yToX.independent() && !xToY.independent()) {
                        removeUndirectedEdgesBetweenClusters(origY, origX);
                        expandEdge(origY, origX);
                    }
                    // Both or neither: leave undirected for Meek.
                }
            }

            MeekRules meekRules = new MeekRules();
            meekRules.setRevertToUnshieldedColliders(false);
            meekRules.orientImplied(graph);
        }

        /**
         * Group-GIN test for the null hypothesis "block x is causally prior to block y".
         * <p>
         * Under the LiNGLaM assumption, if X is causally prior to Y, there exists
         * a weight matrix Omega in the left null space of Sigma_{Cx,Cy} such that
         * Omega * X_data^T is independent of Y_data.
         * <p>
         * We estimate Omega from the sample cross-covariance and test with HSIC.
         */
//        private OrientationResult groupGinTest(Node origX, Node origY) throws InterruptedException {
//            List<Integer> colsX = originalToIndicatorCols.get(origX);
//            List<Integer> colsY = originalToIndicatorCols.get(origY);
//            int rankY = rankByOriginal.get(origY);
//
//            Matrix Xdata = submatrix(dataSet, colsX);   // n x px
//            Matrix Ydata = submatrix(dataSet, colsY);   // n x py
//
//            Matrix SigmaYX = crossCovariance(Ydata, Xdata);  // py x px
//
//            // Omega spans the left null space of SigmaYX;
//            // has (py - rankY) rows under the null hypothesis that x is prior to y.
//            Matrix Omega = leftNullSpace(SigmaYX, rankY);
//
//            if (Omega.getNumRows() == 0) {
//                // No null space — cannot confirm priority.
//                return new OrientationResult(false);
//            }
//
//            // Residual signal: (py - rankY) x n
//            Matrix residual = Omega.times(Ydata.transpose());
//
//            // HSIC test: each residual row vs each column of Xdata.
//            boolean allIndependent = true;
//            RawMarginalIndependenceTest hsic = new FfCiContinuous(dataSet);
//            ((FfCiContinuous) hsic).setAlpha(alpha);
//
//            for (int row = 0; row < residual.getNumRows(); row++) {
//                double[] res = residual.row(row).toArray();
//                for (int col = 0; col < colsX.size(); col++) {
//                    double[] xCol = Xdata.col(col).toArray();
//                    if (hsic.computePValue(res, xCol) < alpha) {
//                        allIndependent = false;
//                        break;
//                    }
//                }
//                if (!allIndependent) break;
//            }
//
//            return new OrientationResult(allIndependent);
//        }

        private OrientationResult groupGinTest(Node origX, Node origY) throws InterruptedException {
            List<Integer> colsX = originalToIndicatorCols.get(origX);
            List<Integer> colsY = originalToIndicatorCols.get(origY);
            int rankY = rankByOriginal.get(origY);

            Matrix Xdata = submatrix(dataSet, colsX);   // n x px
            Matrix Ydata = submatrix(dataSet, colsY);   // n x py

            Matrix SigmaYX = crossCovariance(Ydata, Xdata);  // py x px

            // Omega spans the left null space of SigmaYX;
            // has (py - rankY) rows under the null hypothesis that X is prior to Y.
            Matrix Omega = leftNullSpace(SigmaYX, rankY);

            if (Omega.getNumRows() == 0) {
                // No null space — cannot confirm priority.
                return new OrientationResult(false);
            }

            // Residual signal: (py - rankY) x n
            Matrix residual = Omega.times(Ydata.transpose());

            // Collect all p-values from pairwise HSIC tests (residual rows vs X columns).
            // Use Fisher's method to combine them into a single omnibus p-value rather
            // than the all-pass criterion, which is overly conservative and fragile when
            // individual tests have low power.
//            RawMarginalIndependenceTest hsic = new FfCiContinuous(dataSet);
//            ((FfCiContinuous) hsic).setAlpha(alpha);

            final double EPSILON = 1e-15;  // guard against log(0)
            double fisherStat = 0.0;
            int df = 0;

            for (int row = 0; row < residual.getNumRows(); row++) {
                double[] res = residual.row(row).toArray();
                for (int col = 0; col < colsX.size(); col++) {
                    double[] xCol = Xdata.col(col).toArray();
                    double p = hsic.computePValue(res, xCol);
                    p = Math.max(p, EPSILON);   // avoid log(0)
                    fisherStat += -2.0 * Math.log(p);
                    df += 2;
                }
            }

            // Fisher's combined statistic is chi-squared with 2k degrees of freedom,
            // where k is the number of p-values combined.
            // A large statistic means the null (independence) is rejected.
            // We want independence to hold, so we need a small statistic (large combined p-value).
            double combinedP = 1.0 - chiSquaredCdf(fisherStat, df);

            return new OrientationResult(combinedP > alpha);
        }

        /**
         * Chi-squared CDF via the regularized lower incomplete gamma function,
         * computed using the Lanczos approximation to the log-gamma function
         * and a continued-fraction expansion.  Sufficient precision for p-value
         * combination; no external library required.
         *
         * @param x  the chi-squared statistic (non-negative)
         * @param df degrees of freedom (positive even integer in our usage)
         * @return P(X <= x) for X ~ chi-squared(df)
         */
        private double chiSquaredCdf(double x, int df) {
            if (x <= 0.0) return 0.0;
            return regularizedGammaP(df / 2.0, x / 2.0);
        }

        /**
         * Regularized lower incomplete gamma function P(a, x),
         * using a series expansion for x < a+1 and a continued-fraction
         * expansion otherwise.
         */
        private double regularizedGammaP(double a, double x) {
            if (x < 0.0) return 0.0;
            if (x == 0.0) return 0.0;

            if (x < a + 1.0) {
                // Series expansion
                double ap  = a;
                double sum = 1.0 / a;
                double del = sum;
                for (int i = 0; i < 200; i++) {
                    ap  += 1.0;
                    del *= x / ap;
                    sum += del;
                    if (Math.abs(del) < Math.abs(sum) * 1e-12) break;
                }
                return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
            } else {
                // Continued-fraction expansion (Lentz's method)
                double fpmin = 1e-300;
                double b = x + 1.0 - a;
                double c = 1.0 / fpmin;
                double d = 1.0 / b;
                double h = d;
                for (int i = 1; i <= 200; i++) {
                    double an = -i * (i - a);
                    b += 2.0;
                    d  = an * d + b;
                    if (Math.abs(d) < fpmin) d = fpmin;
                    c  = b + an / c;
                    if (Math.abs(c) < fpmin) c = fpmin;
                    d  = 1.0 / d;
                    h *= d * c;
                    if (Math.abs(d * c - 1.0) < 1e-12) break;
                }
                return 1.0 - Math.exp(-x + a * Math.log(x) - logGamma(a)) * h;
            }
        }

        /**
         * Log-gamma function via Lanczos approximation.
         */
        private double logGamma(double x) {
            double[] c = {
                    76.18009172947146, -86.50532032941677,
                    24.01409824083091, -1.231739572450155,
                    0.001208650973866179, -5.395239384953e-6
            };
            double y   = x;
            double tmp = x + 5.5;
            tmp -= (x + 0.5) * Math.log(tmp);
            double ser = 1.000000000190015;
            for (double ci : c) ser += ci / ++y;
            return -tmp + Math.log(2.5066282746310005 * ser / x);
        }

        private boolean hasUndirectedEdgeBetweenClusters(Node origX, Node origY) {
            for (Node lx : originalToExpanded.get(origX))
                for (Node ly : originalToExpanded.get(origY))
                    if (graph.containsEdge(Edges.undirectedEdge(lx, ly))) return true;
            return false;
        }

        private void removeUndirectedEdgesBetweenClusters(Node origX, Node origY) {
            for (Node lx : originalToExpanded.get(origX))
                for (Node ly : originalToExpanded.get(origY)) {
                    Edge e = Edges.undirectedEdge(lx, ly);
                    if (graph.containsEdge(e)) graph.removeEdge(e);
                }
        }

        /**
         * Add directed edges from each expanded copy in origX's cluster
         * to each expanded copy in origY's cluster.
         */
        private void expandEdge(Node origX, Node origY) {
            for (Node lx : originalToExpanded.get(origX))
                for (Node ly : originalToExpanded.get(origY))
                    if (!graph.containsEdge(Edges.directedEdge(lx, ly)))
                        graph.addDirectedEdge(lx, ly);
        }

        // ==============================================================
        // Stage 5: intra-cluster ordering for rank-r > 1 clusters
        // ==============================================================

        public void orientIntraClusterEdges() throws InterruptedException {
            for (Map.Entry<Node, List<Node>> entry : originalToExpanded.entrySet()) {
                Node original = entry.getKey();
                List<Node> copies = entry.getValue();
                int r = rankByOriginal.get(original);
                if (r <= 1) continue;

                System.out.println("=== Intra-cluster ordering for " + original.getName() + " ===");

                List<Integer> cols = originalToIndicatorCols.get(original);

                // --- 5a: ICA demixing ---
                SimpleMatrix Xblock = submatrix(dataSet, cols).getSimpleMatrix();  // n x |Cj|

                // FastICA expects variables x samples (p x n), not samples x variables.
                SimpleMatrix XblockT = Xblock.transpose();                         // |Cj| x n

                System.out.println("Xblock.numRows()=" + Xblock.numRows() + ", Xblock.numCols()=" + Xblock.numCols());

                FastIca fastica = new FastIca(new Matrix(XblockT.toArray2()), r);
                fastica.setAlgorithmType(FastIca.DEFLATION);
                fastica.setMaxIterations(500);       // prevent infinite loop
                fastica.setTolerance(1e-6);          // explicit convergence threshold

                FastIca.IcaResult icaResult = fastica.findComponents();

                System.out.println("FastICA done.");

                // W is r x p (unmixing), sources = (W * X^T)^T = X * W^T, giving n x r
                SimpleMatrix W = icaResult.W().getSimpleMatrix();                  // r x |Cj|
                SimpleMatrix sources = Xblock.mult(W.transpose());                 // n x r

                // --- 5b: Pairwise LiNGAM on sources to find causal order ---
                int[] causalOrder = lingamOrder(sources, r);
                // causalOrder[0] is causally earliest, causalOrder[r-1] is latest.

                // --- 5c: Assign causal order to latent copies and orient edges ---
                for (int pos = 0; pos < r - 1; pos++) {
                    Node earlier = copies.get(causalOrder[pos]);
                    Node later = copies.get(causalOrder[pos + 1]);
                    if (graph.containsEdge(Edges.undirectedEdge(earlier, later)))
                        graph.removeEdge(Edges.undirectedEdge(earlier, later));
                    graph.addDirectedEdge(earlier, later);
                }

                // --- 5d: Consistency check with Stage 4 ---
                // Any inter-cluster parent that landed on the wrong copy gets
                // re-routed to the causally latest copy. Original nodes no longer
                // exist in _graph, so we inspect copies directly.
                Node latest = copies.get(causalOrder[r - 1]);
                for (Node copy : copies) {
                    if (copy == latest) continue;
                    for (Node parent : new ArrayList<>(graph.getParents(copy))) {
                        if (!copies.contains(parent)) {    // external (inter-cluster) parent
                            graph.removeEdge(Edges.directedEdge(parent, copy));
                            graph.addDirectedEdge(parent, latest);
                        }
                    }
                }
            }
        }

        // ==============================================================
        // Helpers
        // ==============================================================

        /**
         * Returns the left null space of M as a matrix whose rows are orthogonal
         * to the column space of M, assuming M has rank r. Uses thin SVD.
         */
        private Matrix leftNullSpace(Matrix M, int rank) {
            // false = full SVD; thin SVD truncates the null-space columns we need
            SimpleSVD<SimpleMatrix> svd = M.getSimpleMatrix().svd(false);
            SimpleMatrix U = svd.getU();

            // U is now px x px; its columns from index `rank` onward span the left null space.
            int numCols = U.getNumCols();   // was: svd.getSingularValues().length — wrong for non-square M
            int nullDim = numCols - rank;
            if (nullDim <= 0) return new Matrix(0, M.getNumRows());

            double[][] result = new double[nullDim][M.getNumRows()];
            for (int col = 0; col < nullDim; col++) {
                for (int row = 0; row < M.getNumRows(); row++) {
                    result[col][row] = U.get(row, rank + col);
                }
            }
            return new Matrix(result);
        }

        /**
         * Recover a total causal order over r ICA sources via pairwise LiNGAM.
         * For each pair (i, j), regress i on j and vice versa, test residual
         * independence with HSIC. Build a DAG; topological sort gives the order.
         */
        private int[] lingamOrder(SimpleMatrix sources, int r) throws InterruptedException {
            System.out.println("lingamOrder: sources.numRows()=" + sources.numRows() + ", sources.numCols()=" + sources.numCols());

            boolean[][] adj = new boolean[r][r];
//            RawMarginalIndependenceTest hsic = new edu.cmu.tetrad.search.test.FfCiContinuous(dataSet);

            for (int i = 0; i < r; i++) {
                for (int j = i + 1; j < r; j++) {
                    double[] si = new double[sources.getNumRows()];
                    for (int row = 0; row < sources.getNumRows(); row++) {
                        si[row] = sources.get(row, i);
                    }

                    double[] sj = new double[sources.getNumRows()];
                    for (int row = 0; row < sources.getNumRows(); row++) {
                        sj[row] = sources.get(row, j);   // fixed: was writing into si
                    }

                    double[] res_j_on_i = residualOLS(sj, si);
                    double pij = hsic.computePValue(res_j_on_i, si);
                    boolean iToJ = pij > alpha;

                    double[] res_i_on_j = residualOLS(si, sj);
                    double pji = hsic.computePValue(res_i_on_j, sj);
                    boolean jToI = pji > alpha;

                    System.out.printf("i=%d, j=%d, pij=%.4f, pji=%.4f\n", i, j, pij, pji);

                    if (iToJ && !jToI) adj[i][j] = true;   // i --> j
                    if (jToI && !iToJ) adj[j][i] = true;   // j --> i
                }
            }

            return topologicalSort(adj, r);
        }

        /**
         * OLS residual of y regressed on x (both length-n arrays).
         */
        private double[] residualOLS(double[] y, double[] x) {
            int n = y.length;
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                sumX += x[i];
                sumY += y[i];
                sumXY += x[i] * y[i];
                sumX2 += x[i] * x[i];
            }
            double meanX = sumX / n;
            double meanY = sumY / n;
            double beta = (sumXY - n * meanX * meanY) / (sumX2 - n * meanX * meanX);
            double intercept = meanY - beta * meanX;
            double[] residuals = new double[n];
            for (int i = 0; i < n; i++) {
                residuals[i] = y[i] - (intercept + beta * x[i]);
            }
            return residuals;
        }

        private Matrix submatrix(DataSet ds, List<Integer> cols) {
            int n = ds.getNumRows();
            int p = cols.size();
            double[][] result = new double[n][p];
            for (int i = 0; i < n; i++)
                for (int j = 0; j < p; j++)
                    result[i][j] = ds.getDouble(i, cols.get(j));
            return new Matrix(result);
        }

        private Matrix crossCovariance(Matrix X, Matrix Y) {
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
                for (int j = 0; j < r; j++) {
                    if (adj[node][j] && --inDegree[j] == 0)
                        queue.offer(j);
                }
            }
            return result;
        }

        private Map<Node, List<Node>> buildOriginalToExpanded() {
            Map<Node, List<Node>> map = new LinkedHashMap<>();
            for (Map.Entry<Node, Node> e : nodeMap.entrySet())
                map.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
            return map;
        }

        private Map<Node, Integer> buildRankMap() {
            Map<Node, Integer> map = new LinkedHashMap<>();
            for (Map.Entry<Node, List<Node>> e : originalToExpanded.entrySet())
                map.put(e.getKey(), e.getValue().size());
            return map;
        }

        private Map<Node, List<Integer>> buildIndicatorMap() {
            Map<Node, List<Integer>> map = new LinkedHashMap<>();
            for (Map.Entry<Node, List<Node>> e : originalToExpanded.entrySet()) {
                Node original = e.getKey();
                List<Node> copies = e.getValue();
                if (copies.isEmpty()) continue;

                // All copies share the same measured children; use the first.
                List<Integer> colIndices = new ArrayList<>();
                for (Node child : graph.getChildren(copies.get(0))) {
                    if (child.getNodeType() == NodeType.MEASURED) {
                        int col = dataSet.getColumnIndex(child);
                        if (col >= 0) colIndices.add(col);
                    }
                }
                map.put(original, colIndices);
            }
            return map;
        }

        record OrientationResult(boolean independent) {
        }
    }
}
