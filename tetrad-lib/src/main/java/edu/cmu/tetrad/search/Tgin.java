package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.mimic.TrekMeasurementModelBuilderBoss;
import edu.cmu.tetrad.search.mimic.TrekMeasurementModelBuilderPc;
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
 *       directed latent-to-measured edges.</li>
 *
 *   <li><b>Rank expansion.</b> Each latent node of rank r is expanded into r
 *       distinct latent copies (e.g., L3 of rank 2 becomes L3.1 and L3.2), each
 *       inheriting the same measured indicators. Undirected edges are placed
 *       between copies of different clusters that were adjacent in the original
 *       skeleton.</li>
 *
 *   <li><b>Intra-cluster orientation (Stage 5).</b> For rank-r &gt; 1 clusters,
 *       FastICA is applied to the cluster's indicator block to recover r latent
 *       source signals. Pairwise LiNGAM tests on the recovered sources determine
 *       a causal ordering over the r copies within each cluster.</li>
 *
 *   <li><b>Inter-cluster orientation (Stage 4).</b> For each connected component
 *       of clusters linked by undirected edges (an "impure cluster"), the
 *       LaHiCaSl Algorithm 6 procedure is applied: iteratively find the local
 *       root latent using the Proposition 7 GIN test (which splits each cluster's
 *       indicators and conditions on already-identified confounders), orient all
 *       edges away from it, add it to the confounder set LC, and repeat until
 *       the component is fully ordered. Redundant edges are then pruned using
 *       the Proposition 8 rank test.</li>
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
 *       causal graphs. NeurIPS.</li>
 *   <li>Xie, F., Huang, B., Chen, Z., Cai, R., Glymour, C., Geng, Z., and Zhang, K.
 *       (2024). Generalized independent noise condition for estimating causal
 *       structure with latent variables. JMLR 25(191):1-61.</li>
 * </ul>
 *
 * @see TrekMeasurementModelBuilderPc
 * @see TrekMeasurementModelBuilderBoss
 *
 */
@Deprecated
public class Tgin implements IGraphSearch {

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    /**
     * Optional known measured inputs by name.
     */
    private final Set<String> inputNames = new LinkedHashSet<>();

    /**
     * Optional known measured outputs by name.
     */
    private final Set<String> outputNames = new LinkedHashSet<>();

    /**
     * Optional background knowledge.
     */
    private final Knowledge knowledge = new Knowledge();

    /**
     * HSIC-based marginal independence test.
     */
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
     * PC search depth (-1 = unlimited).
     */
    private int depth = -1;

    /**
     * Verbosity flag.
     */
    private boolean verbose = false;

    /**
     * Working graph (modified in place during search).
     */
    private Graph graph;

    /**
     * All expanded latent copies in the working graph.
     */
    private List<Node> allLatents;

    /**
     * Sample size (set from TSC result).
     */
    private int sampleSize;

    /**
     * Significance level.
     */
    private double alpha = 0.01;

    /**
     * Rank of each original latent node from TSC.
     */
    private Map<Node, Integer> latentRanks = new LinkedHashMap<>();

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructs a TGIN search with data and parameters.
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

    // -----------------------------------------------------------------------
    // IGraphSearch
    // -----------------------------------------------------------------------

    @Override
    public Graph search() throws InterruptedException {
        runPcTsc();

        expandGraph();

        // --- DEBUG: print pre-expansion children ---
        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                System.out.println("Pre-expansion: " + node.getName()
                        + " children=" + graph.getChildren(node));
            }
        }
        // --- END DEBUG ---

        // Build expanded graph: one copy per rank unit for each latent,
        // plus latent->measured edges and undirected latent-latent edges.
        Graph _graph = new EdgeListGraph();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                _graph.addNode(node);
            }
        }

        Map<Node, Node> nodeMap = new HashMap<>();               // copy -> original
        Map<Node, List<Node>> originalToExpanded = new HashMap<>();

        // First pass: copies + latent->measured edges only.
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
                        Edge toAdd = Edges.directedEdge(copy, other);
                        if (!_graph.containsEdge(toAdd)) _graph.addEdge(toAdd);
                    }
                }
            }
            originalToExpanded.put(latent, copies);
        }

        // Second pass: undirected latent-latent edges between all copy pairs.
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

        if (verbose) {
            System.out.println("=== Expansion check ===");
            for (Map.Entry<Node, Node> e : nodeMap.entrySet())
                System.out.println("  copy=" + e.getKey().getName()
                        + " -> original=" + e.getValue().getName());
            System.out.println("latentRanks: " + latentRanks);
        }

        TginOrientationStages stages = new TginOrientationStages(
                graph, allLatents, nodeMap, dataSet, alpha, sampleSize, hsic);

        // Stage 5: intra-cluster ordering for rank > 1 clusters.
        stages.orientIntraClusterEdges();

        // Stage 4: inter-cluster orientation via Algorithm 6 (LaHiCaSl Phase II).
        List<List<Node>> impureClusters = stages.findImpureClusters();
        for (List<Node> cluster : impureClusters) {
            stages.orientImpureCluster(cluster);
        }

        return graph;
    }

    private void expandGraph() {
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
    }

    // -----------------------------------------------------------------------
    // Setters
    // -----------------------------------------------------------------------

    public void setDataSet(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("Data set must not be null.");
        this.dataSet = dataSet;
    }

    public void setParameters(Parameters parameters) {
        if (parameters == null) throw new NullPointerException("Parameters must not be null.");
        this.parameters = parameters;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    // -----------------------------------------------------------------------
    // TSC measurement model recovery
    // -----------------------------------------------------------------------

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
            spec = result.spec();
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
            spec = result.spec();
        }

        this.latentRanks = new LinkedHashMap<>();
        for (int i = 0; i < spec.blockVariables().size(); i++) {
            this.latentRanks.put(spec.blockVariables().get(i), spec.ranks().get(i));
        }
    }

    // -----------------------------------------------------------------------
    // Orientation stages (static inner class)
    // -----------------------------------------------------------------------

    public static class TginOrientationStages {

        // All fields needed by both Stage 4 and Stage 5.
        private final Graph graph;
        private final Map<Node, Node> nodeMap;                       // expanded copy -> original
        private final DataSet dataSet;
        private final double alpha;
        private final Map<Node, List<Node>> originalToExpanded;      // original -> copies
        private final Map<Node, Integer> rankByOriginal;             // original -> # copies
        private final Map<Node, List<Integer>> originalToIndicatorCols; // original -> data col indices
        private final RawMarginalIndependenceTest hsic;

        public TginOrientationStages(Graph graph, List<Node> allLatents,
                                     Map<Node, Node> nodeMap,
                                     DataSet dataSet, double alpha, int sampleSize,
                                     RawMarginalIndependenceTest hsic) {
            this.graph = graph;
            this.nodeMap = nodeMap;
            this.dataSet = dataSet;
            this.alpha = alpha;
            this.hsic = hsic;
            this.originalToExpanded = buildOriginalToExpanded();
            this.rankByOriginal = buildRankMap();
            this.originalToIndicatorCols = buildIndicatorMap();
        }

        // ==================================================================
        // Stage 5: intra-cluster ordering for rank-r > 1 clusters
        // ==================================================================

//        public void orientIntraClusterEdges() throws InterruptedException {
//            for (Map.Entry<Node, List<Node>> entry : originalToExpanded.entrySet()) {
//                Node original = entry.getKey();
//                List<Node> copies = entry.getValue();
//                int r = rankByOriginal.get(original);

        //
//                if (r <= 1) continue;
        public void orientIntraClusterEdges() throws InterruptedException {
            for (Map.Entry<Node, List<Node>> entry : originalToExpanded.entrySet()) {
                Node original = entry.getKey();
                List<Node> copies = entry.getValue();
                int r = rankByOriginal.get(original);

                System.out.println("=== orientIntraClusterEdges: " + original.getName()
                        + "  rank=" + r + "  copies=" + copies);

                if (r <= 1) continue;
                // ... rest of method

                System.out.println("=== Intra-cluster ordering for " + original.getName() + " ===");

                List<Integer> cols = originalToIndicatorCols.get(original);

                // 5a: ICA demixing.
                // FastICA expects variables x samples (p x n).
                SimpleMatrix Xblock = submatrix(dataSet, cols).getSimpleMatrix();  // n x |Cj|
                SimpleMatrix XblockT = Xblock.transpose();                          // |Cj| x n

                FastIca fastica = new FastIca(new Matrix(XblockT.toArray2()), r);
                fastica.setAlgorithmType(FastIca.DEFLATION);
                fastica.setMaxIterations(5000);
                fastica.setTolerance(1e-6);

                FastIca.IcaResult icaResult = fastica.findComponents();

                // S is r x n; transpose to n x r for lingamOrder.
                SimpleMatrix sources = icaResult.S().transpose().getSimpleMatrix(); // n x r

                // 5b: Pairwise LiNGAM on ICA sources to get causal order.
                int[] causalOrder = lingamOrder(sources, r);

                // 5c: Orient edges between copies according to causal order.
                for (int pos = 0; pos < r - 1; pos++) {
                    Node earlier = copies.get(causalOrder[pos]);
                    Node later = copies.get(causalOrder[pos + 1]);
                    Edge ue = Edges.undirectedEdge(earlier, later);
                    if (graph.containsEdge(ue)) graph.removeEdge(ue);
                    graph.addDirectedEdge(earlier, later);
                }
            }
        }

        // ==================================================================
        // Stage 4: inter-cluster orientation via LaHiCaSl Algorithm 6
        // ==================================================================

        /**
         * Groups original latents into connected components joined by undirected
         * edges. Each component with more than one member is an impure cluster
         * that needs Algorithm 6 orientation.
         */
        public List<List<Node>> findImpureClusters() {
            List<Node> originals = new ArrayList<>(originalToExpanded.keySet());

            // Union-Find over originals.
            Map<Node, Node> parent = new LinkedHashMap<>();
            for (Node n : originals) parent.put(n, n);

            for (int i = 0; i < originals.size(); i++) {
                for (int j = i + 1; j < originals.size(); j++) {
                    Node a = originals.get(i);
                    Node b = originals.get(j);
                    if (hasUndirectedEdgeBetweenClusters(a, b)) {
                        Node rootA = find(parent, a);
                        Node rootB = find(parent, b);
                        if (rootA != rootB) parent.put(rootA, rootB);
                    }
                }
            }

            // Group by root.
            Map<Node, List<Node>> components = new LinkedHashMap<>();
            for (Node n : originals) {
                Node root = find(parent, n);
                components.computeIfAbsent(root, k -> new ArrayList<>()).add(n);
            }

            // Return only multi-member components.
            List<List<Node>> result = new ArrayList<>();
            for (List<Node> component : components.values()) {
                if (component.size() > 1) result.add(component);
            }
            return result;
        }

        /**
         * Orients all latent-latent edges within one impure cluster using the
         * LaHiCaSl Algorithm 6 procedure (Xie et al. 2024, Section 4.3.3).
         *
         * <p>Iteratively peels the local root latent from the cluster using the
         * Proposition 7 GIN test, orienting edges away from it, until a total
         * causal order is established. Then prunes redundant edges via
         * Proposition 8.
         */
        public void orientImpureCluster(List<Node> impureCluster) throws InterruptedException {
            System.out.println("=== orientImpureCluster: originalToIndicatorCols = " + originalToIndicatorCols);
            System.out.println("=== orientImpureCluster: nodeMap = " + nodeMap);
            System.out.println("=== orientImpureCluster: originalToExpanded = " + originalToExpanded);

            List<Node> remaining = new ArrayList<>(impureCluster);

            // LC: latents external to the cluster that are known parents of
            // cluster members (identified from already-directed edges in the graph).
            List<Node> LC = new ArrayList<>();
            for (Node n : impureCluster) {
                for (Node expandedCopy : originalToExpanded.get(n)) {
                    for (Node parentCopy : graph.getParents(expandedCopy)) {
                        Node origParent = nodeMap.get(parentCopy);
                        if (origParent != null
                                && !impureCluster.contains(origParent)
                                && !LC.contains(origParent)) {
                            LC.add(origParent);
                        }
                    }
                }
            }

            System.out.println("=== orientImpureCluster: " + clusterNames(impureCluster)
                    + "  LC=" + clusterNames(LC) + " ===");

// Peel local roots one at a time.
            while (remaining.size() > 1) {

                // Check if all remaining nodes are mutual twins.
                // If so, use ICA+LiNGAM to order them directly.
                Set<Integer> firstCols = new HashSet<>(
                        originalToIndicatorCols.get(remaining.get(0)));
                boolean allTwins = remaining.stream().allMatch(n ->
                        new HashSet<>(originalToIndicatorCols.get(n)).equals(firstCols));

                if (allTwins) {
                    System.out.println("  All remaining are twins — using ICA ordering: "
                            + clusterNames(remaining));
                    orientTwinsByICA(remaining);

                    // Fire deferred LC edges for each twin.
                    for (Node twin : remaining) {
                        for (Node lcMember : LC) {
                            if (hasUndirectedEdgeBetweenClusters(twin, lcMember)) {
                                System.out.println("  Deferred LC edge (post-ICA): "
                                        + twin.getName() + " --> " + lcMember.getName());
                                removeUndirectedEdgesBetweenClusters(twin, lcMember);
                                expandEdge(twin, lcMember);
                            }
                        }
                    }
                    break;
                }

                Node root = findLocalRoot(remaining, LC);

                if (root == null) {
                    System.out.println("WARNING: no local root found in "
                            + clusterNames(remaining) + " with LC=" + clusterNames(LC)
                            + ". Leaving remaining edges undirected.");
                    break;
                }

                System.out.println("  Local root: " + root.getName()
                        + "  remaining=" + clusterNames(remaining));

                // Step 1: Orient root against any LC members with leftover undirected edges.
                for (Node lcMember : LC) {
                    if (hasUndirectedEdgeBetweenClusters(root, lcMember)) {
                        System.out.println("  Deferred LC edge: " + root.getName()
                                + " --> " + lcMember.getName());
                        removeUndirectedEdgesBetweenClusters(root, lcMember);
                        expandEdge(root, lcMember);
                    }
                }

                // Step 2: Orient root -> non-twin remaining members.
                for (Node other : remaining) {
                    if (other == root) continue;
                    boolean isTwin = false;

                    if (areTwins(other, root)) isTwin = true;

                    if (!isTwin) {
                        for (Node unprocessed : remaining) {
                            if (unprocessed == other || unprocessed == root) continue;
                            if (areTwins(unprocessed, other)) {
                                isTwin = true;
                                break;
                            }
                        }
                    }

                    if (!isTwin) {
                        for (Node lcMember : LC) {
                            if (areTwins(lcMember, other)) {
                                isTwin = true;
                                break;
                            }
                        }
                    }

                    if (isTwin) continue;

                    System.out.println("  expandEdge: " + root.getName() + " --> " + other.getName());
                    removeUndirectedEdgesBetweenClusters(root, other);
                    expandEdge(root, other);
                }

                // Step 3: Orient intra-cluster twin edge.
                for (Node other : remaining) {
                    if (other == root) continue;
                    if (areTwins(other, root)) {
                        System.out.println("  Orienting intra-cluster twin edge: "
                                + root.getName() + " --> " + other.getName());
                        removeUndirectedEdgesBetweenClusters(root, other);
                        expandEdge(root, other);
                    }
                }

                remaining.remove(root);
                LC.add(root);
            }

            // Proposition 8: prune redundant directed edges.
            pruneRedundantEdges(impureCluster);

            MeekRules meekRules = new MeekRules();
            meekRules.setRevertToUnshieldedColliders(false);
            meekRules.orientImplied(graph);
        }

        private void orientTwinsByICA(List<Node> twins) throws InterruptedException {
            if (twins.size() <= 1) return;

            // All twins share the same indicator columns — use the first one.
            List<Integer> cols = originalToIndicatorCols.get(twins.get(0));
            if (cols == null || cols.isEmpty()) {
                System.out.println("  orientTwinsByICA: no indicator cols for "
                        + twins.get(0).getName() + " — skipping.");
                return;
            }

            int r = twins.size();
            System.out.println("  orientTwinsByICA: r=" + r + " cols=" + cols);

            // FastICA expects variables x samples (p x n).
            SimpleMatrix Xblock = submatrix(dataSet, cols).getSimpleMatrix();  // n x |cols|
            SimpleMatrix XblockT = Xblock.transpose();                          // |cols| x n

            FastIca fastica = new FastIca(new Matrix(XblockT.toArray2()), r);
            fastica.setAlgorithmType(FastIca.DEFLATION);
            fastica.setMaxIterations(5000);
            fastica.setTolerance(1e-6);

            FastIca.IcaResult icaResult = fastica.findComponents();

            // S is r x n; transpose to n x r.
            SimpleMatrix sources = icaResult.S().transpose().getSimpleMatrix(); // n x r

            // Pairwise LiNGAM on sources to get causal order.
            int[] causalOrder = lingamOrder(sources, r);

            // Orient edges between twin copies according to causal order.
            for (int pos = 0; pos < r - 1; pos++) {
                Node earlier = twins.get(causalOrder[pos]);
                Node later = twins.get(causalOrder[pos + 1]);

                removeUndirectedEdgesBetweenClusters(earlier, later);
                expandEdge(earlier, later);

                System.out.println("  orientTwinsByICA: " + earlier.getName()
                        + " --> " + later.getName());
            }

            // Also orient deferred LC edges for all twins now that order is known.
            // The causally earliest twin should point away from LC members,
            // but since all twins share the same LC relationship, orient all of them.
            // (Deferred LC edges are handled by the main loop's step 1 — nothing extra needed here.)
        }

        private boolean areTwins(Node a, Node b) {
            List<Integer> aCols = originalToIndicatorCols.get(a);
            List<Integer> bCols = originalToIndicatorCols.get(b);
            if (aCols == null || bCols == null) return false;
            if (aCols.size() != bCols.size()) return false;
            return new HashSet<>(aCols).equals(new HashSet<>(bCols));
        }

        private Node findLocalRoot(List<Node> remaining, List<Node> LC)
                throws InterruptedException {

            // Standard Proposition 7 test.
            for (Node candidate : remaining) {
                List<Node> others = new ArrayList<>(remaining);
                others.remove(candidate);
                if (proposition7Test(candidate, others, LC)) {
                    return candidate;
                }
            }

            if (remaining.size() == 2) {
                Node a = remaining.get(0);
                Node b = remaining.get(1);

                // Twin inheritance: check BEFORE fallback pairwise tests.
                for (Node lc : LC) {
                    if (areTwins(lc, a)) {
                        System.out.println("  Twin inheritance (early): " + a.getName()
                                + " inherits root status from LC member " + lc.getName());
                        return a;
                    }
                    if (areTwins(lc, b)) {
                        System.out.println("  Twin inheritance (early): " + b.getName()
                                + " inherits root status from LC member " + lc.getName());
                        return b;
                    }
                }

                // Fallback pairwise test ignoring LC.
                System.out.println("  Fallback pairwise test (ignoring LC): "
                        + a.getName() + " vs " + b.getName());

                boolean aToB = proposition7Test(a, List.of(b), List.of());
                boolean bToA = proposition7Test(b, List.of(a), List.of());

                if (aToB && !bToA) return a;
                if (bToA && !aToB) return b;

                // Fallback with reducedLC: exclude LC members that are twins of a or b.
                List<Node> reducedLC = new ArrayList<>();
                for (Node lc : LC) {
                    if (!areTwins(lc, a) && !areTwins(lc, b)) {
                        reducedLC.add(lc);
                    }
                }

                if (!reducedLC.isEmpty()) {
                    System.out.println("  Fallback with reducedLC=" + clusterNames(reducedLC));
                    aToB = proposition7Test(a, List.of(b), reducedLC);
                    bToA = proposition7Test(b, List.of(a), reducedLC);
                    if (aToB && !bToA) return a;
                    if (bToA && !aToB) return b;
                }

                System.out.println("  Fallback exhausted — cannot orient "
                        + a.getName() + " vs " + b.getName());
            }

            return null;
        }

        /**
         * Proposition 7 GIN test (Xie et al. 2024).
         *
         * <p>Tests whether {@code origP} is causally prior to all latents in
         * {@code others} given confounders {@code LC}.
         *
         * <p>Constructs:
         * <ul>
         *   <li>P1 = first {@code pRank} indicator columns of origP (Y side)</li>
         *   <li>P2 = next {@code pRank} indicator columns of origP (Z side, balanced split)</li>
         *   <li>Q1 = one indicator column per latent in others</li>
         *   <li>T1, T2 = split indicator columns for each confounder in LC</li>
         * </ul>
         * Then tests GIN on Z={P2,T2}, Y={P1,Q1,T1}.
         *
         * <p><b>Adaptive P2 fallback.</b> When the balanced split passes, that result
         * is returned immediately. If it fails, and more indicator columns remain
         * unused beyond the balanced P2, a second attempt is made using <em>all</em>
         * remaining indicators in P2 (giving Z a larger column set and hence more
         * statistical power). The expanded test is only tried when either:
         * <ul>
         *   <li>the balanced P2 had {@code <=1} column (too few for reliable HSIC), or</li>
         *   <li>the balanced combined p-value was within {@code adaptiveThreshold}
         *       of {@code alpha} (the test nearly passed — additional power may tip it).</li>
         * </ul>
         * Using both passes reduces false negatives in the low-indicator regime without
         * materially inflating type I error when the balanced test clearly rejects.
         */
        private boolean proposition7Test(Node origP, List<Node> others, List<Node> LC)
                throws InterruptedException {

            List<Integer> pCols = originalToIndicatorCols.get(origP);
            if (pCols == null || pCols.isEmpty()) return false;

            int pRank = rankByOriginal.get(origP);
            List<Integer> P1 = new ArrayList<>(pCols.subList(0, Math.min(pRank, pCols.size())));

            // Balanced split: P2 gets exactly pRank columns (same width as P1).
            int p2BalancedEnd = Math.min(P1.size() + pRank, pCols.size());
            List<Integer> P2balanced = new ArrayList<>(pCols.subList(P1.size(), p2BalancedEnd));

            // Full split: P2 gets every remaining indicator beyond P1.
            List<Integer> P2full = new ArrayList<>(pCols.subList(P1.size(), pCols.size()));

            // Q1: one indicator per other latent, deduplicating within Q1 only.
            // Overlap with P1/P2 is permitted — copies of the same cluster legitimately
            // share indicators.
            Set<Integer> usedInQ = new LinkedHashSet<>();
            List<Integer> Q1 = new ArrayList<>();
            for (Node q : others) {
                List<Integer> qCols = originalToIndicatorCols.get(q);
                if (qCols == null) continue;
                for (Integer col : qCols) {
                    if (!usedInQ.contains(col)) {
                        Q1.add(col);
                        usedInQ.add(col);
                        break;
                    }
                }
            }

            // T1, T2: split each confounder's fresh indicators in half,
            // deduplicating against P1, P2balanced, and Q1.
            // (T deduplication uses the balanced P2 in both attempts so that
            // T1/T2/Q1/yCols are identical between the two runs — only zCols differs.)
            Set<Integer> usedInT = new LinkedHashSet<>();
            usedInT.addAll(P1);
            usedInT.addAll(P2balanced);
            usedInT.addAll(Q1);
            List<Integer> T1 = new ArrayList<>();
            List<Integer> T2 = new ArrayList<>();
            for (Node lt : LC) {
                List<Integer> tCols = originalToIndicatorCols.get(lt);
                if (tCols == null || tCols.isEmpty()) continue;
                List<Integer> freshT = new ArrayList<>();
                for (Integer col : tCols) {
                    if (!usedInT.contains(col)) {
                        freshT.add(col);
                        usedInT.add(col);
                    }
                }
                int half = freshT.size() / 2;
                T1.addAll(freshT.subList(0, half));
                T2.addAll(freshT.subList(half, freshT.size()));
            }

            // Y is the same for both attempts.
            List<Integer> yCols = new ArrayList<>();
            yCols.addAll(P1);
            yCols.addAll(Q1);
            yCols.addAll(T1);

            if (yCols.isEmpty()) {
                System.out.println("  proposition7Test: empty Y for "
                        + origP.getName() + " — skipping.");
                return false;
            }

            // --- Attempt 1: balanced P2 ---
            List<Integer> zColsBalanced = new ArrayList<>();
            zColsBalanced.addAll(P2balanced);
            zColsBalanced.addAll(T2);

            if (zColsBalanced.isEmpty()) {
                System.out.println("  proposition7Test: empty Z (balanced) for "
                        + origP.getName() + " — skipping.");
                return false;
            }

            OrientationResult r1 = ginConditionHolds(zColsBalanced, yCols);
            System.out.printf("  P7 test (balanced): candidate=%s  GIN=%b  p=%.4f  |Z|=%d |Y|=%d%n",
                    origP.getName(), r1.independent(), r1.combinedP(),
                    zColsBalanced.size(), yCols.size());

            if (r1.independent()) return true;

            // --- Attempt 2: expanded P2 (adaptive fallback) ---
            // Only worthwhile when more indicators are available AND the balanced
            // test either had too little data (|P2| <= 1) or came tantalisingly
            // close to passing (combinedP within adaptiveThreshold of alpha).
            boolean moreAvailable = P2full.size() > P2balanced.size();
            boolean tooFewCols   = P2balanced.size() <= 1;
            // adaptiveThreshold: how far below alpha the combined p must be
            // before we consider the evidence "marginal" rather than "clear rejection".
            // 5 * alpha gives a comfortable margin; exposed here for easy tuning.
            double adaptiveThreshold = 5.0 * alpha;
            boolean marginalEvidence = r1.combinedP() < adaptiveThreshold;

            if (moreAvailable && (tooFewCols || marginalEvidence)) {
                List<Integer> zColsFull = new ArrayList<>();
                zColsFull.addAll(P2full);
                zColsFull.addAll(T2);

                OrientationResult r2 = ginConditionHolds(zColsFull, yCols);
                System.out.printf("  P7 test (expanded): candidate=%s  GIN=%b  p=%.4f  |Z|=%d |Y|=%d%n",
                        origP.getName(), r2.independent(), r2.combinedP(),
                        zColsFull.size(), yCols.size());
                return r2.independent();
            }

            return false;
        }

        /**
         * Proposition 8 edge pruning (Xie et al. 2024).
         *
         * <p>For each ordered pair (Lp, Lq) in the cluster with a directed edge
         * Lp->Lq, checks whether that edge is made redundant by intermediate
         * latents LS lying on all paths from Lp to Lq. Removes the edge if the
         * rank of the combined covariance block is at most |LC_external union LS|.
         */
        private void pruneRedundantEdges(List<Node> cluster) {
            for (int i = 0; i < cluster.size(); i++) {
                for (int j = 0; j < cluster.size(); j++) {
                    if (i == j) continue;
                    Node origP = cluster.get(i);
                    Node origQ = cluster.get(j);
                    if (!hasDirectedEdgeBetweenClusters(origP, origQ)) continue;

                    List<Node> LS = intermediateLatents(origP, origQ, cluster);
                    if (LS.isEmpty()) continue;

                    // Collect external confounders (common parents outside cluster).
                    List<Node> LC = externalParents(cluster);

                    if (proposition8RankTest(origP, origQ, LS, LC)) {
                        removeDirectedEdgesBetweenClusters(origP, origQ);
                        System.out.println("  Proposition 8 removed edge: "
                                + origP.getName() + " -> " + origQ.getName());
                    }
                }
            }
        }

        /**
         * Tests the Proposition 8 rank condition.
         *
         * <p>Constructs the combined column set {P1, Q1, T1, T2, S} and checks
         * whether the estimated rank of its covariance is at most |LC| + |LS|.
         */
        private boolean proposition8RankTest(Node origP, Node origQ,
                                             List<Node> LS, List<Node> LC) {
            List<Integer> P1 = firstN(originalToIndicatorCols.get(origP),
                    rankByOriginal.get(origP));
            List<Integer> Q1 = firstN(originalToIndicatorCols.get(origQ),
                    rankByOriginal.get(origQ));

            List<Integer> T1 = new ArrayList<>();
            List<Integer> T2 = new ArrayList<>();
            for (Node lt : LC) {
                List<Integer> tCols = originalToIndicatorCols.get(lt);
                if (tCols == null || tCols.isEmpty()) continue;
                int half = tCols.size() / 2;
                T1.addAll(tCols.subList(0, half));
                T2.addAll(tCols.subList(half, tCols.size()));
            }

            List<Integer> S = new ArrayList<>();
            for (Node ls : LS) {
                List<Integer> sCols = originalToIndicatorCols.get(ls);
                if (sCols != null && !sCols.isEmpty()) S.add(sCols.get(0));
            }

            List<Integer> allCols = new ArrayList<>();
            allCols.addAll(P1);
            allCols.addAll(Q1);
            allCols.addAll(T1);
            allCols.addAll(T2);
            allCols.addAll(S);

            if (allCols.isEmpty()) return false;

            Matrix sub = submatrix(dataSet, allCols);
            Matrix cov = crossCovariance(sub, sub);

            int estimatedRank = estimateRank(cov.getSimpleMatrix(), allCols.size());
            int targetRank = LC.size() + LS.size();
            return estimatedRank <= targetRank;
        }

        // ==================================================================
        // Core GIN test (shared by Stage 4)
        // ==================================================================

        /**
         * Tests whether the GIN condition holds for the ordered pair (Z, Y),
         * where Z and Y are specified by column index lists into dataSet.
         *
         * <p>Computes the left null space of Sigma_{Y,Z}, forms the residual
         * Omega * Y, and tests pairwise independence of each residual row
         * against each column of Z using HSIC. Combines p-values via Fisher's
         * method.
         *
         * @return an {@link OrientationResult} carrying both the boolean verdict
         *         ({@code combinedP > alpha}) and the raw combined p-value, so
         *         callers can implement adaptive fallback strategies.
         */
        private OrientationResult ginConditionHolds(List<Integer> zCols, List<Integer> yCols)
                throws InterruptedException {

            Matrix Ydata = submatrix(dataSet, yCols);   // n x |Y|
            Matrix Zdata = submatrix(dataSet, zCols);   // n x |Z|

            // Sigma_{Y,Z}: |Y| x |Z|
            Matrix SigmaYZ = crossCovariance(Ydata, Zdata);
            Matrix Omega = leftNullSpace(SigmaYZ);      // nullDim x |Y|

            if (Omega.getNumRows() == 0) return new OrientationResult(false, 0.0);

            // residual = Omega * Y^T,  shape: nullDim x n
            Matrix residual = Omega.times(Ydata.transpose());

            final double EPSILON = 1e-15;
            double fisherStat = 0.0;
            int df = 0;

            for (int row = 0; row < residual.getNumRows(); row++) {
                double[] res = residual.row(row).toArray();
                for (int col = 0; col < zCols.size(); col++) {
                    double[] zCol = Zdata.col(col).toArray();
                    double p = hsic.computePValue(res, zCol);
                    p = Math.max(p, EPSILON);
                    fisherStat += -2.0 * Math.log(p);
                    df += 2;
                }
            }

            double combinedP = 1.0 - chiSquaredCdf(fisherStat, df);
            return new OrientationResult(combinedP > alpha, combinedP);
        }

        // ==================================================================
        // Stage 5 helpers: ICA-based intra-cluster ordering
        // ==================================================================

        /**
         * Recovers a causal order over r ICA sources via pairwise LiNGAM.
         * For each pair (i,j): regress i on j and vice versa, test residual
         * independence with HSIC. Build a DAG; topological sort gives the order.
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
                    boolean iToJ = pij > alpha;

                    double[] res_i_on_j = residualOLS(si, sj);
                    double pji = hsic.computePValue(res_i_on_j, sj);
                    boolean jToI = pji > alpha;

                    System.out.printf("  LiNGAM i=%d j=%d pij=%.4f pji=%.4f%n",
                            i, j, pij, pji);

                    if (iToJ && !jToI) adj[i][j] = true;
                    if (jToI && !iToJ) adj[j][i] = true;
                }
            }

            return topologicalSort(adj, r);
        }

        /**
         * OLS residual of y regressed on x.
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
            double denom = sumX2 - n * meanX * meanX;
            double beta = (denom == 0.0) ? 0.0 : (sumXY - n * meanX * meanY) / denom;
            double intercept = meanY - beta * meanX;
            double[] residuals = new double[n];
            for (int i = 0; i < n; i++) residuals[i] = y[i] - (intercept + beta * x[i]);
            return residuals;
        }

        /**
         * Topological sort of the pairwise LiNGAM adjacency matrix.
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

            if (index < r) {
                System.out.println("WARNING: topologicalSort cycle detected ("
                        + index + " of " + r + " placed). Falling back to identity order.");
                for (int k = 0; k < r; k++) result[k] = k;
            }
            return result;
        }

        // ==================================================================
        // Linear algebra helpers
        // ==================================================================

        /**
         * Returns the left null space of M as a matrix whose rows span the
         * orthogonal complement of M's row space. Rank is estimated from
         * singular values using a relative threshold.
         */
        private Matrix leftNullSpace(Matrix M) {
            SimpleSVD<SimpleMatrix> svd = M.getSimpleMatrix().svd(false);
            SimpleMatrix U = svd.getU();
            SimpleMatrix W = svd.getW();

            int m = Math.min(W.numRows(), W.numCols());
            double maxSV = 0.0;
            for (int i = 0; i < m; i++) maxSV = Math.max(maxSV, W.get(i, i));
            double threshold = maxSV * Math.max(M.getNumRows(), M.getNumColumns()) * 1e-8;

            int estimatedRank = 0;
            for (int i = 0; i < m; i++)
                if (W.get(i, i) > threshold) estimatedRank++;

            if (U.getNumCols() <= estimatedRank)
                return new Matrix(0, M.getNumRows());

            int nullDim = U.getNumCols() - estimatedRank;
            double[][] result = new double[nullDim][M.getNumRows()];
            for (int col = 0; col < nullDim; col++)
                for (int row = 0; row < M.getNumRows(); row++)
                    result[col][row] = U.get(row, estimatedRank + col);

            return new Matrix(result);
        }

        /**
         * Estimates the numerical rank of a square matrix from its SVD.
         */
        private int estimateRank(SimpleMatrix M, int dim) {
            SimpleSVD<SimpleMatrix> svd = M.svd(false);
            int m = Math.min(svd.getW().numRows(), svd.getW().numCols());
            double maxSV = 0.0;
            for (int i = 0; i < m; i++) maxSV = Math.max(maxSV, svd.getW().get(i, i));
            double threshold = maxSV * dim * 1e-8;
            int rank = 0;
            for (int i = 0; i < m; i++)
                if (svd.getW().get(i, i) > threshold) rank++;
            return rank;
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

        /**
         * Sample cross-covariance of X and Y (both n x p matrices).
         * Returns X^T * Y / (n-1), shape p_x x p_y.
         */
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

        // ==================================================================
        // Statistical helpers
        // ==================================================================

        private double chiSquaredCdf(double x, int df) {
            if (x <= 0.0) return 0.0;
            return regularizedGammaP(df / 2.0, x / 2.0);
        }

        private double regularizedGammaP(double a, double x) {
            if (x <= 0.0) return 0.0;
            if (x < a + 1.0) {
                double ap = a, sum = 1.0 / a, del = sum;
                for (int i = 0; i < 200; i++) {
                    ap += 1.0;
                    del *= x / ap;
                    sum += del;
                    if (Math.abs(del) < Math.abs(sum) * 1e-12) break;
                }
                return sum * Math.exp(-x + a * Math.log(x) - logGamma(a));
            } else {
                double fpmin = 1e-300, b = x + 1.0 - a, c = 1.0 / fpmin,
                        d = 1.0 / b, h = d;
                for (int i = 1; i <= 200; i++) {
                    double an = -i * (i - a);
                    b += 2.0;
                    d = an * d + b;
                    if (Math.abs(d) < fpmin) d = fpmin;
                    c = b + an / c;
                    if (Math.abs(c) < fpmin) c = fpmin;
                    d = 1.0 / d;
                    h *= d * c;
                    if (Math.abs(d * c - 1.0) < 1e-12) break;
                }
                return 1.0 - Math.exp(-x + a * Math.log(x) - logGamma(a)) * h;
            }
        }

        private double logGamma(double x) {
            double[] c = {76.18009172947146, -86.50532032941677, 24.01409824083091,
                    -1.231739572450155, 0.001208650973866179, -5.395239384953e-6};
            double y = x, tmp = x + 5.5;
            tmp -= (x + 0.5) * Math.log(tmp);
            double ser = 1.000000000190015;
            for (double ci : c) ser += ci / ++y;
            return -tmp + Math.log(2.5066282746310005 * ser / x);
        }

        // ==================================================================
        // Graph query / mutation helpers
        // ==================================================================

        private boolean hasUndirectedEdgeBetweenClusters(Node origX, Node origY) {
            for (Node lx : originalToExpanded.get(origX))
                for (Node ly : originalToExpanded.get(origY))
                    if (graph.containsEdge(Edges.undirectedEdge(lx, ly))) return true;
            return false;
        }

        private boolean hasDirectedEdgeBetweenClusters(Node origX, Node origY) {
            for (Node lx : originalToExpanded.get(origX))
                for (Node ly : originalToExpanded.get(origY))
                    if (graph.containsEdge(Edges.directedEdge(lx, ly))) return true;
            return false;
        }

        private void removeUndirectedEdgesBetweenClusters(Node origX, Node origY) {
            for (Node lx : originalToExpanded.get(origX))
                for (Node ly : originalToExpanded.get(origY)) {
                    Edge e = Edges.undirectedEdge(lx, ly);
                    if (graph.containsEdge(e)) graph.removeEdge(e);
                }
        }

        private void removeDirectedEdgesBetweenClusters(Node origX, Node origY) {
            for (Node lx : originalToExpanded.get(origX))
                for (Node ly : originalToExpanded.get(origY)) {
                    Edge e = Edges.directedEdge(lx, ly);
                    if (graph.containsEdge(e)) graph.removeEdge(e);
                }
        }

        /**
         * Adds directed edges from every copy of origX to every copy of origY.
         */
        private void expandEdge(Node origX, Node origY) {
            for (Node lx : originalToExpanded.get(origX))
                for (Node ly : originalToExpanded.get(origY))
                    if (!graph.containsEdge(Edges.directedEdge(lx, ly)))
                        graph.addDirectedEdge(lx, ly);
        }

        /**
         * Returns latents in {@code cluster} that lie strictly between origP
         * and origQ in the current directed graph (descendants of origP that
         * are also ancestors of origQ within the cluster).
         */
        private List<Node> intermediateLatents(Node origP, Node origQ,
                                               List<Node> cluster) {
            List<Node> result = new ArrayList<>();
            for (Node n : cluster) {
                if (n == origP || n == origQ) continue;
                if (hasDirectedEdgeBetweenClusters(origP, n)
                        && hasDirectedEdgeBetweenClusters(n, origQ))
                    result.add(n);
            }
            return result;
        }

        /**
         * Returns original latents outside {@code cluster} that are parents
         * of any expanded copy of any cluster member.
         */
        private List<Node> externalParents(List<Node> cluster) {
            List<Node> LC = new ArrayList<>();
            for (Node n : cluster) {
                for (Node copy : originalToExpanded.get(n)) {
                    for (Node parentCopy : graph.getParents(copy)) {
                        Node origParent = nodeMap.get(parentCopy);
                        if (origParent != null
                                && !cluster.contains(origParent)
                                && !LC.contains(origParent)) {
                            LC.add(origParent);
                        }
                    }
                }
            }
            return LC;
        }

        // ==================================================================
        // Union-Find helpers (for findImpureClusters)
        // ==================================================================

        private Node find(Map<Node, Node> parent, Node n) {
            while (parent.get(n) != n) {
                parent.put(n, parent.get(parent.get(n)));  // path compression
                n = parent.get(n);
            }
            return n;
        }

        // ==================================================================
        // Miscellaneous helpers
        // ==================================================================

        /**
         * Returns the first n elements of cols (or all if cols.size() < n).
         */
        private List<Integer> firstN(List<Integer> cols, int n) {
            if (cols == null) return new ArrayList<>();
            return new ArrayList<>(cols.subList(0, Math.min(n, cols.size())));
        }

        private String clusterNames(List<Node> nodes) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < nodes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(nodes.get(i).getName());
            }
            return sb.append("]").toString();
        }

        // ==================================================================
        // Map builders (called once in constructor)
        // ==================================================================

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

                // All copies share the same measured children; use the first copy.
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

        // ==================================================================
        // Result record (unused externally but kept for symmetry)
        // ==================================================================

        record OrientationResult(boolean independent, double combinedP) {
        }
    }
}
