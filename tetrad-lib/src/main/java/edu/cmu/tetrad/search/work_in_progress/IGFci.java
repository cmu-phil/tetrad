package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Instance-specific FGES-FCI given in Fattaneh Jabbari's dissertation (Pages 144-147)
 *
 * @author Fattaneh
 */
public final class IGFci implements IGraphSearch {

    // The PAG being constructed.
    private Graph graph;

    // The background knowledge.
    private Knowledge knowledge = new Knowledge();

    // The conditional independence test.
    private IndependenceTest independenceTest;

    // Flag for complete rule set, true if should use complete rule set, false otherwise.
    private boolean completeRuleSetUsed = false;

    // The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
    private int maxPathLength = -1;

    // The maxDegree for the fast adjacency search.
    private int maxDegree = -1;

    // The logger to use.
    private TetradLogger logger = TetradLogger.getInstance();

    // True iff verbose output should be printed.
    private boolean verbose = false;

    // The covariance matrix beign searched over. Assumes continuous data.
    ICovarianceMatrix covarianceMatrix;

    // The sample size.
    int sampleSize;

    // The print stream that output is directed to.
    private PrintStream out = System.out;

    // True iff one-edge faithfulness is assumed. Speed up the algorithm for very large searches. By default false.
    private boolean faithfulnessAssumed = true;

    // The instance-specific score.
    private ISScore score;

    // The population-wide graph
    private Graph populationGraph;

    private SepsetProducer sepsets;
    private long elapsedTime;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs an instance of IGFci with the provided independence test and score.
     *
     * @param test the IndependenceTest instance to be used; must not be null.
     * @param score the ISScore instance to be used; must not be null.
     * @throws NullPointerException if the provided score is null.
     */
    public IGFci(IndependenceTest test, ISScore score) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.sampleSize = score.getSampleSize();
        this.score = score;
        this.independenceTest = test;
    }

    /**
     * Constructs an instance of IGFci with the provided independence test,
     * score, and population graph.
     *
     * @param test the IndependenceTest instance to be used; must not be null.
     * @param score the ISScore instance to be used; must not be null.
     * @param populationGraph the Graph representing the population.
     * @throws NullPointerException if the provided score is null.
     */
    public IGFci(IndependenceTest test, ISScore score, Graph populationGraph) {
        if (score == null) {
            throw new NullPointerException();
        }
        this.sampleSize = score.getSampleSize();
        this.score = score;
        this.independenceTest = test;
        this.populationGraph = populationGraph;
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Executes the FCI algorithm using the provided independence test, score, and population graph,
     * and returns the resulting graph with edges oriented according to the algorithm's rules.
     * The algorithm first constructs an initial graph, prunes it based on separation sets,
     * and further refines it using multiple phases of orientation rules.
     * The time taken to complete the search is recorded and stored in the elapsedTime field.
     *
     * @return the final oriented graph obtained after applying the FCI algorithm.
     */
    public Graph search() throws InterruptedException {
        long time1 = System.currentTimeMillis();

        List<Node> nodes = getIndependenceTest().getVariables();

        logger.log("Starting FCI algorithm.");
        logger.log("Independence test = " + getIndependenceTest() + ".");

        this.graph = new EdgeListGraph(nodes);

        ISFges fges = new ISFges(score);
        fges.setPopulationGraph(this.populationGraph);
        fges.setInitialGraph(this.populationGraph);


        graph = fges.search();
        Graph fgesGraph = new EdgeListGraph(graph);
        sepsets = new SepsetsGreedy(fgesGraph, independenceTest, -1);

        for (Node b : nodes) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            List<Node> adjacentNodes = fgesGraph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isAdjacentTo(a, c) && fgesGraph.isAdjacentTo(a, c)) {
                    if (sepsets.getSepset(a, c, -1, null) != null) {
                        graph.removeEdge(a, c);
                    }
                }
            }
        }

        modifiedR0(fgesGraph);

        R0R4Strategy r0r4 = new R0R4StrategyTestBased(independenceTest);

        FciOrient fciOrient = new FciOrient(r0r4);
        fciOrient.setVerbose(verbose);
        fciOrient.setKnowledge(getKnowledge());
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxDiscriminatingPathLength(maxPathLength);
        fciOrient.finalOrientation(graph);

        GraphUtils.replaceNodes(graph, independenceTest.getVariables());

        long time2 = System.currentTimeMillis();

        elapsedTime = time2 - time1;

        return graph;
    }

    /**
     * Sets the maximum degree for the graph.
     *
     * @param maxDegree the maximum degree, where -1 indicates unlimited. Must be -1 or a non-negative integer.
     * @throws IllegalArgumentException if maxDegree is less than -1.
     */
    public void setMaxDegree(int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + maxDegree);
        }

        this.maxDegree = maxDegree;
    }

    /**
     * Retrieves the maximum degree for the graph.
     *
     * @return the maximum degree, or -1 if it is unlimited.
     */
    public int getMaxDegree() {
        return maxDegree;
    }

    // Due to Spirtes.

    /**
     * Modifies the given FGES graph based on the FCI algorithm rules, reorienting edges and
     * potentially identifying and orienting definite colliders.
     *
     * @param fgesGraph the FGES Graph to be processed; must not be null.
     * @throws InterruptedException if the search is interrupted.
     */
    public void modifiedR0(Graph fgesGraph) throws InterruptedException {
        graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(knowledge, graph, graph.getNodes());

        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (fgesGraph.isDefCollider(a, b, c)) {
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                } else if (fgesGraph.isAdjacentTo(a, c) && !graph.isAdjacentTo(a, c)) {
                    Set<Node> sepset = sepsets.getSepset(a, c, -1, null);

                    if (sepset != null && !sepset.contains(b)) {
                        graph.setEndpoint(a, b, Endpoint.ARROW);
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                    }
                }
            }
        }
    }

    /**
     * Returns the knowledge used in the IGFci algorithm.
     *
     * @return the Knowledge object currently used by the IGFci algorithm.
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * Sets the knowledge for the IGFci algorithm.
     *
     * @param knowledge the Knowledge object to be set; must not be null.
     * @throws NullPointerException if the provided knowledge is null.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * Checks if the complete rule set is being used in the IGFci algorithm.
     *
     * @return true if the complete rule set is used, false otherwise.
     */
    public boolean isCompleteRuleSetUsed() {
        return completeRuleSetUsed;
    }

    /**
     * Sets whether the complete rule set should be used in the IGFci algorithm.
     *
     * @param completeRuleSetUsed true if the complete rule set is to be used, false otherwise
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Retrieves the maximum length of any path, or -1 if unlimited.
     *
     * @return the maximum length of any path, or -1 if unlimited.
     */
    public int getMaxPathLength() {
        return maxPathLength;
    }

    /**
     * Sets the maximum length for any path in the graph. The value must be -1 to indicate
     * unlimited length or a non-negative integer to specify the maximum path length.
     *
     * @param maxPathLength the maximum path length to be set, where -1 indicates unlimited.
     *                      Must be -1 or a non-negative integer.
     * @throws IllegalArgumentException if maxPathLength is less than -1.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * Checks if verbose output is enabled.
     *
     * @return true if verbose output is enabled, false otherwise.
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose true if verbose output should be printed, false otherwise
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Retrieves the current independence test object being used.
     *
     * @return the IndependenceTest object currently set for this instance.
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    /**
     * Retrieves the current covariance matrix object used by the IGFci algorithm.
     *
     * @return the ICovarianceMatrix object currently set for this instance.
     */
    public ICovarianceMatrix getCovMatrix() {
        return covarianceMatrix;
    }

    /**
     * Retrieves the current covariance matrix object used by the IGFci algorithm.
     *
     * @return the ICovarianceMatrix object currently set for this instance.
     */
    public ICovarianceMatrix getCovarianceMatrix() {
        return covarianceMatrix;
    }

    /**
     * Sets the covariance matrix to be used in the IGFci algorithm.
     *
     * @param covarianceMatrix the ICovarianceMatrix object to be set; must not be null.
     */
    public void setCovarianceMatrix(ICovarianceMatrix covarianceMatrix) {
        this.covarianceMatrix = covarianceMatrix;
    }

    /**
     * Retrieves the current PrintStream used for output by the IGFci algorithm.
     *
     * @return the PrintStream object currently set for this instance.
     */
    public PrintStream getOut() {
        return out;
    }

    /**
     * Sets the PrintStream object used for output by the IGFci instance.
     *
     * @param out the PrintStream object to be used for output; must not be null.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Sets the independence test for the IGFci algorithm.
     *
     * @param independenceTest the IndependenceTest object to be set; must not be null.
     */
    public void setIndependenceTest(IndependenceTest independenceTest) {
        this.independenceTest = independenceTest;
    }

    /**
     * Sets whether faithfulness is assumed in the IGFci algorithm.
     *
     * @param faithfulnessAssumed true if faithfulness is assumed, false otherwise
     */
    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }

    //===========================================PRIVATE METHODS=======================================//
    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(Knowledge knowledge, Graph graph, List<Node> variables) {
        logger.log("Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it = knowledge.forbiddenEdgesIterator(); it.hasNext();) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);
            logger.log(LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it = knowledge.requiredEdgesIterator(); it.hasNext();) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            logger.log(LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        logger.log("Finishing BK Orientation.");
    }

}
