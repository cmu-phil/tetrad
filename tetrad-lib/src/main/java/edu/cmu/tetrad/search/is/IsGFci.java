package edu.cmu.tetrad.search.is;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.FciOrient;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.R0R4Strategy;
import edu.cmu.tetrad.search.utils.R0R4StrategyTestBased;
import edu.cmu.tetrad.search.utils.SepsetProducer;
import edu.cmu.tetrad.search.utils.SepsetsGreedy;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;

/**
 * Instance-specific FGES→FCI wrapper ("IS-GFCI").
 * <p>
 * 1) Runs {@link IsFges} to get an instance-specific backbone graph.
 * 2) Builds targeted sepsets constrained by that backbone.
 * 3) Prunes adjacencies and orients with FCI-style rules into a PAG.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Interrupt-friendly loops (check {@link Thread#currentThread()}).</li>
 *   <li>Guards against missing edges and null variables during BK orientation.</li>
 *   <li>Propagates core settings (knowledge, maxDegree, verbosity, faithfulness) to {@link IsFges}.</li>
 * </ul>
 */
public final class IsGFci implements IGraphSearch {

    // ----------------------- Required collaborators -----------------------
    private final Score populationScore;        // population score (for IsFges)
    private final IndependenceTest independenceTest; // CI test used by the FCI phase
    private final IsScore isScore;              // instance-specific score for IsFges

    // ----------------------- Tunables -----------------------
    private Knowledge knowledge = new Knowledge();
    private boolean completeRuleSetUsed = false;
    private int maxPathLength = -1;     // -1 = unlimited
    private int maxDegree = -1;         // -1 = unlimited
    private boolean faithfulnessAssumed = true; // as in legacy
    private boolean verbose = false;
    private PrintStream out = System.out;

    // ----------------------- Optional context -----------------------
    private Graph populationGraph;      // optional initializer/backbone for IsFges
    private ICovarianceMatrix covarianceMatrix; // kept for API parity (not used here)

    // ----------------------- Results -----------------------
    private Graph graph;                // final PAG
    private SepsetProducer sepsets;     // sepset producer constrained by IsFges adjacencies
    private long elapsedTimeMs;

    // ----------------------- Ctor -----------------------

    /**
     * Constructs an instance of IsGFci with a specified independence test, scoring function, and population score.
     *
     * @param test the independence test used to evaluate conditional independence relationships
     * @param score the scoring function used to assign scores to the graph structures
     * @param populationScore the population score associated with the graph structures
     * @throws NullPointerException if any of the provided parameters (test, score, or populationScore) is null
     */
    public IsGFci(final IndependenceTest test, final IsScore score, final Score populationScore) {
        if (test == null) throw new NullPointerException("IndependenceTest is null");
        if (score == null) throw new NullPointerException("IsScore is null");
        if (populationScore == null) throw new NullPointerException("populationScore is null");
        this.independenceTest = test;
        this.isScore = score;
        this.populationScore = populationScore;
    }

    // ----------------------- IGraphSearch -----------------------

    /**
     * Executes IS-FGES, targeted-pruning, and FCI orientations to produce an instance-specific PAG.
     */
    @Override
    public Graph search() throws InterruptedException {
        final long t0 = System.currentTimeMillis();
        final TetradLogger logger = TetradLogger.getInstance();
        logger.log("Starting IS-GFCI (IS-FGES → FCI)...");

        final List<Node> nodes = independenceTest.getVariables();
        this.graph = new EdgeListGraph(nodes);

        // 1) Instance-specific FGES
        IsFges fges = new IsFges(isScore, populationScore);
        fges.setKnowledge(knowledge);
        fges.setVerbose(verbose);
        fges.setOut(out);
        fges.setFaithfulnessAssumed(faithfulnessAssumed);
        if (maxDegree >= 0) fges.setMaxDegree(maxDegree);
        if (populationGraph != null) {
            fges.setPopulationGraph(populationGraph);
            fges.setInitialGraph(populationGraph);
        }
        Graph fgesGraph = fges.search(); // may throw InterruptedException
        this.graph = new EdgeListGraph(fgesGraph);

        // 2) Targeted sepsets constrained by FGES adjacencies
        this.sepsets = new SepsetsGreedy(fgesGraph, independenceTest, maxDegree);

        // 3) Triangle-based prune using sepsets (remove a–c when a sepset exists)
        trianglePrune(fgesGraph, nodes);

        // 4) Modified R0 pass: respect FGES-definite colliders, complete via tests
        modifiedR0(fgesGraph);

        // 5) FCI orientation (R0/R4 and rest)
        R0R4Strategy r0r4 = new R0R4StrategyTestBased(independenceTest);
        FciOrient fciOrient = new FciOrient(r0r4);
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(knowledge);
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(maxPathLength);
        fciOrient.finalOrientation(graph);

        // Keep node identities aligned with the test’s variables
        GraphUtils.replaceNodes(graph, nodes);

        this.elapsedTimeMs = System.currentTimeMillis() - t0;
        logger.log("IS-GFCI finished in " + elapsedTimeMs + " ms.");
        return graph;
    }

    // ----------------------- Phases -----------------------

    /**
     * Triangle-based prune: for every b, if a–c exists and sepset(a,c) != null, remove a–c.
     */
    private void trianglePrune(Graph fgesGraph, List<Node> nodes) throws InterruptedException {
        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) return;
            final List<Node> adjB = fgesGraph.getAdjacentNodes(b);
            if (adjB.size() < 2) continue;

            ChoiceGenerator cg = new ChoiceGenerator(adjB.size(), 2);
            int[] comb;
            while ((comb = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) return;
                Node a = adjB.get(comb[0]);
                Node c = adjB.get(comb[1]);
                if (!graph.isAdjacentTo(a, c)) continue;

                Set<Node> s = sepsets.getSepset(a, c, maxDegree, null);
                if (s != null) {
                    graph.removeEdge(a, c);
                }
            }
        }
    }

    /**
     * Modifies the orientation of edges in the given graph based on a set of rules and conditions
     * that respect definite colliders, adjacency, and separation sets derived from FGES analysis.
     * This method iterates over the nodes of the graph, examines their adjacencies, and applies
     * orientation logic to enforce consistency with the FGES graph.
     *
     * @param fgesGraph The FGES graph that provides the definite collider and adjacency structure
     *                  used to guide orientation decisions.
     * @throws InterruptedException If the thread running this method is interrupted during execution.
     */
    public void modifiedR0(Graph fgesGraph) throws InterruptedException {
        graph.reorientAllWith(Endpoint.CIRCLE);
        orientByKnowledge(knowledge, graph, graph.getNodes());

        final List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) return;
            final List<Node> adjB = graph.getAdjacentNodes(b);
            if (adjB.size() < 2) continue;

            ChoiceGenerator cg = new ChoiceGenerator(adjB.size(), 2);
            int[] comb;
            while ((comb = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) return;
                Node a = adjB.get(comb[0]);
                Node c = adjB.get(comb[1]);

                // 1) Respect FGES-definite collider at b
                if (fgesGraph.isDefCollider(a, b, c)) {
                    orientToCollider(a, b, c);
                    continue;
                }

                // 2) If FGES kept a–c AND graph currently has no a–c, test-based collider check
                if (fgesGraph.isAdjacentTo(a, c) && !graph.isAdjacentTo(a, c)) {
                    Set<Node> s = sepsets.getSepset(a, c, maxDegree, null);
                    if (s != null && !s.contains(b)) {
                        orientToCollider(a, b, c);
                    }
                }
            }
        }
    }

    private void orientToCollider(Node a, Node b, Node c) {
        Edge eab = graph.getEdge(a, b);
        Edge ecb = graph.getEdge(c, b);
        if (eab != null) graph.setEndpoint(a, b, Endpoint.ARROW);
        if (ecb != null) graph.setEndpoint(c, b, Endpoint.ARROW);
    }

    private void orientByKnowledge(Knowledge knowledge, Graph g, List<Node> vars) {
        final TetradLogger logger = TetradLogger.getInstance();
        logger.log("Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();
            Node from = GraphSearchUtils.translate(edge.getFrom(), vars);
            Node to = GraphSearchUtils.translate(edge.getTo(), vars);
            if (from == null || to == null) continue;
            Edge e = g.getEdge(from, to);
            if (e == null) continue;
            // Orient to *-> from
            g.setEndpoint(to, from, Endpoint.ARROW);
            g.setEndpoint(from, to, Endpoint.CIRCLE);
            logger.log(LogUtilsSearch.edgeOrientedMsg("Knowledge", g.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();
            Node from = GraphSearchUtils.translate(edge.getFrom(), vars);
            Node to = GraphSearchUtils.translate(edge.getTo(), vars);
            if (from == null || to == null) continue;
            Edge e = g.getEdge(from, to);
            if (e == null) continue;
            g.setEndpoint(to, from, Endpoint.TAIL);
            g.setEndpoint(from, to, Endpoint.ARROW);
            logger.log(LogUtilsSearch.edgeOrientedMsg("Knowledge", g.getEdge(from, to)));
        }

        logger.log("Finishing BK Orientation.");
    }

    // ----------------------- Accessors -----------------------

    /**
     * Returns the elapsed time in milliseconds for the execution or process
     * associated with this instance.
     *
     * @return the elapsed time in milliseconds as a long value
     */
    public long getElapsedTimeMillis() { return elapsedTimeMs; }

    /**
     * Retrieves the maximum degree for the graph search procedure. The maximum degree
     * limits the number of edges any single node in the graph can have. A value of -1
     * indicates that there is no restriction on the maximum degree.
     *
     * @return the maximum degree as an integer; a value of -1 indicates no restriction
     */
    public int getMaxDegree() { return maxDegree; }

    /**
     * Sets the maximum degree for the graph search procedure. The maximum degree
     * places a limit on the number of edges any single node in the graph can have.
     * If set to -1, no restriction is applied to the maximum degree.
     *
     * @param maxDegree the maximum degree to be set; must be -1 (indicating no restriction)
     *                  or a non-negative integer (0 or greater)
     * @throws IllegalArgumentException if the maxDegree is less than -1
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) throw new IllegalArgumentException("maxDegree must be -1 or >= 0");
        this.maxDegree = maxDegree;
    }

    /**
     * Retrieves the Knowledge object associated with this instance.
     *
     * @return the Knowledge object representing the knowledge structure
     */
    public Knowledge getKnowledge() { return knowledge; }

    /**
     * Sets the knowledge structure for the instance.
     *
     * @param knowledge the Knowledge object to be set; must not be null
     * @throws NullPointerException if the provided knowledge is null
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException("knowledge");
        this.knowledge = knowledge;
    }

    /**
     * Indicates whether the complete rule set is used in the instance.
     *
     * @return true if the complete rule set is used; false otherwise
     */
    public boolean isCompleteRuleSetUsed() { return completeRuleSetUsed; }

    /**
     * Sets whether the complete rule set is used in the instance.
     *
     * @param completeRuleSetUsed a boolean value indicating whether the complete rule set
     *                            is enabled. If true, the complete rule set will be utilized;
     *                            otherwise, it will not.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) { this.completeRuleSetUsed = completeRuleSetUsed; }

    /**
     * Retrieves the maximum path length for the graph search procedure.
     * The maximum path length determines the longest allowable path that the
     * search algorithm will consider. A value of -1 indicates no restriction
     * on the path length.
     *
     * @return the maximum path length as an integer; a value of -1 indicates
     * no restriction on path length
     */
    public int getMaxPathLength() { return maxPathLength; }

    /**
     * Sets the maximum path length for the graph search procedure.
     * The maximum path length determines the longest allowable path considered
     * during the search. If set to -1, it indicates no restriction on the path length.
     *
     * @param maxPathLength the maximum path length to be set; must be -*/
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) throw new IllegalArgumentException("maxPathLength must be -1 or >= 0");
        this.maxPathLength = maxPathLength;
    }

    /**
     * Indicates whether verbose mode is enabled for logging or detailed output.
     *
     * @return true if verbose mode is enabled, providing detailed logging or output;
     *         false if verbose mode is disabled, resulting in minimal or suppressed output.
     */
    public boolean isVerbose() { return verbose; }

    /**
     * Sets whether verbose mode is enabled for logging or detailed output.
     *
     * @param verbose a boolean value indicating whether verbose mode is enabled.
     *                If true, detailed logging or output will be provided; if false,
     *                output will be minimal or suppressed.
     */
    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    /**
     * Retrieves the independence test associated with the current instance.
     *
     * @return the IndependenceTest object representing the independence test
     */
    public IndependenceTest getIndependenceTest() { return independenceTest; }

    /**
     * Returns the covariance matrix associated with this instance.
     *
     * @return the ICovarianceMatrix object representing the covariance matrix
     */
    public ICovarianceMatrix getCovarianceMatrix() { return covarianceMatrix; }

    /**
     * Sets the covariance matrix for the instance.
     *
     * @param covarianceMatrix the covariance matrix to be set; must not be null
     */
    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) { this.covarianceMatrix = covarianceMatrix; }

    /**
     * Returns the output stream used by the instance.
     *
     * @return the PrintStream object representing the output stream
     */
    public PrintStream getOut() { return out; }

    /**
     * Sets the output stream for the instance.
     *
     * @param out the PrintStream to be used as the output stream; must not be null
     */
    public void setOut(PrintStream out) { this.out = out; }

    /**
     * Sets whether faithfulness is assumed in the graph search procedure.
     *
     * @param faithfulnessAssumed a boolean value indicating whether the assumption of faithfulness is applied.
     *                            If true, the search enforces the faithfulness condition; otherwise, it does not.
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) { this.faithfulnessAssumed = faithfulnessAssumed; }

    /**
     * Sets the population graph used in the instance.
     *
     * @param populationGraph the population graph to be set; should not be null
     */
    public void setPopulationGraph(Graph populationGraph) { this.populationGraph = populationGraph; }

    // ----------------------- Utilities kept from legacy -----------------------

    /** Align columns of {@code instance} to match {@code train}'s variable order and label sets. */
    private static DataSet alignColumnsByName(DataSet instance, DataSet train) {
        final List<Node> trainVars = train.getVariables();
        final int p = trainVars.size();
        final int[] cols = new int[p];

        for (int i = 0; i < p; i++) {
            final String name = trainVars.get(i).getName();
            Node instVar = instance.getVariable(name);
            if (instVar == null) {
                throw new IllegalArgumentException("Instance dataset is missing variable: " + name);
            }
            cols[i] = instance.getColumn(instVar);
        }

        DataSet reordered = instance.subsetColumns(cols);

        // For discrete variables, ensure category label alignment
        for (int j = 0; j < p; j++) {
            Node tVar = trainVars.get(j);
            Node iVar = reordered.getVariable(j);
            if (tVar instanceof edu.cmu.tetrad.data.DiscreteVariable tv &&
                iVar instanceof edu.cmu.tetrad.data.DiscreteVariable iv) {

                List<String> tLabels = tv.getCategories();
                List<String> iLabels = iv.getCategories();
                if (tLabels.equals(iLabels)) continue;

                if (!(new HashSet<>(tLabels).equals(new HashSet<>(iLabels)))) {
                    throw new IllegalArgumentException(
                            "Discrete categories differ for variable '" + tv.getName() +
                            "'. Train labels=" + tLabels + ", Instance labels=" + iLabels);
                }

                int K = iLabels.size();
                int[] remap = new int[K];
                Map<String, Integer> trainIndexByLabel = new HashMap<>();
                for (int k = 0; k < K; k++) trainIndexByLabel.put(tLabels.get(k), k);
                for (int k = 0; k < K; k++) remap[k] = trainIndexByLabel.get(iLabels.get(k));

                for (int r = 0; r < reordered.getNumRows(); r++) {
                    int val = reordered.getInt(r, j);
                    if (val == -99) continue; // keep missing sentinel if used
                    if (val < 0 || val >= K) {
                        throw new IllegalArgumentException("Out-of-range category at row " + r + ", var " + tv.getName());
                    }
                    reordered.setInt(r, j, remap[val]);
                }

                reordered.setVariable(j, tv); // carry train labels/order
            }
        }

        return reordered;
    }

    /** Convenience: return a single-row test case aligned to {@code train}. */
    @SuppressWarnings("unused")
    private static DataSet alignedSingleRow(DataSet instanceFull, DataSet train, int rowIndex) {
        if (rowIndex < 0 || rowIndex >= instanceFull.getNumRows()) {
            throw new IllegalArgumentException("Requested instance row " + rowIndex +
                                               " out of range [0, " + (instanceFull.getNumRows() - 1) + "].");
        }
        DataSet aligned = alignColumnsByName(instanceFull, train);
        return aligned.subsetRows(new int[]{rowIndex});
    }
}
