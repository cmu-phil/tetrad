package edu.pitt.dbmi.algo.resampling;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.GraphSampling;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mahdi on 1/16/17.
 * <p>
 * Updated: Chirayu Kong Wongchokprasitti, PhD on 9/13/2018
 *
 * @version $Id: $Id
 */
public class GeneralResamplingTest {

    /**
     * The resampling search.
     */
    private final GeneralResamplingSearch resamplingSearch;

    /**
     * The output stream that output (except for log output) should be sent to.
     */
    private PrintStream out = System.out;

    /**
     * The individual bootstrap result graphs.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * Whether verbose output should be produced.
     */
    private boolean verbose;
    private ScoreWrapper setScoreWrapper = null;

    /**
     * Constructor for single data set algorithms.
     *
     * @param data      the data set.
     * @param algorithm the algorithm.
     * @param knowledge the knowledge.
     * @param parameters the parameters.
     */
    public GeneralResamplingTest(DataSet data, Algorithm algorithm, Knowledge knowledge, Parameters parameters) {
        this.resamplingSearch = new GeneralResamplingSearch(data, algorithm);
        this.resamplingSearch.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
        this.resamplingSearch.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));
        this.resamplingSearch.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
        this.resamplingSearch.setNumberOfResamples(parameters.getInt(Params.NUMBER_RESAMPLING));
        this.resamplingSearch.setBootstrappingNumThreads(parameters.getInt(Params.BOOTSTRAPPING_NUM_THEADS));
        this.resamplingSearch.setKnowledge(knowledge);
        this.resamplingSearch.setParameters(parameters);
    }

    /**
     * Constructor for multiple data set algorithms.
     *
     * @param dataSets  the data sets.
     * @param multiDataSetAlgorithm the algorithm.
     * @param knowledge the knowledge.
     * @param parameters the parameters.
     */
    public GeneralResamplingTest(List<DataSet> dataSets, MultiDataSetAlgorithm multiDataSetAlgorithm,
                                 Knowledge knowledge, Parameters parameters) {
        this.resamplingSearch = new GeneralResamplingSearch(dataSets, multiDataSetAlgorithm);
        this.resamplingSearch.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
        this.resamplingSearch.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));
        this.resamplingSearch.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));
        this.resamplingSearch.setNumberOfResamples(parameters.getInt(Params.NUMBER_RESAMPLING));
        this.resamplingSearch.setBootstrappingNumThreads(parameters.getInt(Params.BOOTSTRAPPING_NUM_THEADS));
        this.resamplingSearch.setKnowledge(knowledge);
        this.resamplingSearch.setParameters(parameters);
    }

    /**
     * Constructor.
     *
     * @param truth    the true graph.
     * @param estimate the estimated graph.
     * @return an array of {@link int} objects
     */
    public static int[][] getAdjConfusionMatrix(Graph truth, Graph estimate) {
        Graph complete = new EdgeListGraph(estimate.getNodes());
        complete.fullyConnect(Endpoint.TAIL);
        List<Edge> edges = new ArrayList<>(complete.getEdges());
        int numEdges = edges.size();

        // Adjacency Confusion Matrix
        int[][] adjAr = new int[2][2];

        GeneralResamplingTest.countAdjConfMatrix(adjAr, edges, truth, estimate, 0, numEdges - 1);

        return adjAr;
    }

    private static void countAdjConfMatrix(int[][] adjAr, List<Edge> edges, Graph truth, Graph estimate, int start,
                                           int end) {
        if (start == end) {
            Edge edge = edges.get(start);
            Node n1 = truth.getNode(edge.getNode1().toString());
            Node n2 = truth.getNode(edge.getNode2().toString());
            Node nn1 = estimate.getNode(edge.getNode1().toString());
            Node nn2 = estimate.getNode(edge.getNode2().toString());

            // Adjacency Count
            int i = truth.getEdge(n1, n2) == null ? 0 : 1;
            int j = estimate.getEdge(nn1, nn2) == null ? 0 : 1;
            adjAr[i][j]++;

        } else if (start < end) {
            int mid = (start + end) / 2;
            GeneralResamplingTest.countAdjConfMatrix(adjAr, edges, truth, estimate, start, mid);
            GeneralResamplingTest.countAdjConfMatrix(adjAr, edges, truth, estimate, mid + 1, end);
        }
    }

    /**
     * Constructor.
     *
     * @param truth    the true graph.
     * @param estimate the estimated graph.
     * @return an array of {@link int} objects
     */
    public static int[][] getEdgeTypeConfusionMatrix(Graph truth, Graph estimate) {
        Graph complete = new EdgeListGraph(estimate.getNodes());
        complete.fullyConnect(Endpoint.TAIL);
        List<Edge> edges = new ArrayList<>(complete.getEdges());
        int numEdges = edges.size();

        // Edge Type Confusion Matrix
        int[][] edgeAr = new int[8][8];

        GeneralResamplingTest.countEdgeTypeConfMatrix(edgeAr, edges, truth, estimate, 0, numEdges - 1);

        return edgeAr;
    }

    private static void countEdgeTypeConfMatrix(int[][] edgeAr, List<Edge> edges, Graph truth, Graph estimate,
                                                int start, int end) {
        if (start == end) {
            Edge edge = edges.get(start);
            Node n1 = truth.getNode(edge.getNode1().toString());
            Node n2 = truth.getNode(edge.getNode2().toString());
            Node nn1 = estimate.getNode(edge.getNode1().toString());
            Node nn2 = estimate.getNode(edge.getNode2().toString());

            int i = 0, j = 0;

            Edge eTA = new Edge(n1, n2, Endpoint.TAIL, Endpoint.ARROW);
            if (truth.containsEdge(eTA)) {
                i = 1;
            }
            eTA = new Edge(nn1, nn2, Endpoint.TAIL, Endpoint.ARROW);
            if (estimate.containsEdge(eTA)) {
                j = 1;
            }
            Edge eAT = new Edge(n1, n2, Endpoint.ARROW, Endpoint.TAIL);
            if (truth.containsEdge(eAT)) {
                i = 2;
            }
            eAT = new Edge(nn1, nn2, Endpoint.ARROW, Endpoint.TAIL);
            if (estimate.containsEdge(eAT)) {
                j = 2;
            }
            Edge eCA = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.ARROW);
            if (truth.containsEdge(eCA)) {
                i = 3;
            }
            eCA = new Edge(nn1, nn2, Endpoint.CIRCLE, Endpoint.ARROW);
            if (estimate.containsEdge(eCA)) {
                j = 3;
            }
            Edge eAC = new Edge(n1, n2, Endpoint.ARROW, Endpoint.CIRCLE);
            if (truth.containsEdge(eAC)) {
                i = 4;
            }
            eAC = new Edge(nn1, nn2, Endpoint.ARROW, Endpoint.CIRCLE);
            if (estimate.containsEdge(eAC)) {
                j = 4;
            }
            Edge eCC = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.CIRCLE);
            if (truth.containsEdge(eCC)) {
                i = 5;
            }
            eCC = new Edge(nn1, nn2, Endpoint.CIRCLE, Endpoint.CIRCLE);
            if (estimate.containsEdge(eCC)) {
                j = 5;
            }
            Edge eAA = new Edge(n1, n2, Endpoint.ARROW, Endpoint.ARROW);
            if (truth.containsEdge(eAA)) {
                i = 6;
            }
            eAA = new Edge(nn1, nn2, Endpoint.ARROW, Endpoint.ARROW);
            if (estimate.containsEdge(eAA)) {
                j = 6;
            }
            Edge eTT = new Edge(n1, n2, Endpoint.TAIL, Endpoint.TAIL);
            if (truth.containsEdge(eTT)) {
                i = 7;
            }
            eTT = new Edge(nn1, nn2, Endpoint.TAIL, Endpoint.TAIL);
            if (estimate.containsEdge(eTT)) {
                j = 7;
            }

            edgeAr[i][j]++;

        } else if (start < end) {
            int mid = (start + end) / 2;
            GeneralResamplingTest.countEdgeTypeConfMatrix(edgeAr, edges, truth, estimate, start, mid);
            GeneralResamplingTest.countEdgeTypeConfMatrix(edgeAr, edges, truth, estimate, mid + 1, end);
        }
    }

    /**
     * Sets whether verbose output should be produced.
     *
     * @param verbose whether verbose output should be produced.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * <p>Getter for the field <code>out</code>.</p>
     *
     * @return the output stream that output (except for log output) should be sent to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent to. By default, System.out.
     *
     * @param out the output stream that output (except for log output) should be sent to.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Runs the resampling test.
     *
     * @return the graph.
     */
    public Graph search() {
        this.resamplingSearch.setScoreWrapper(setScoreWrapper);
        this.graphs = this.resamplingSearch.search();
        int numNoGraphs = this.resamplingSearch.getNumRunsReturningNullGraph();

        TetradLogger.getInstance().forceLogMessage(
                "Bootstrapping: Number of searches that didn't return a graph = " + numNoGraphs);

        if (this.verbose) {
            this.out.println("Resampling number is : " + this.graphs.size());
        }

        Graph graph = GraphSampling.createGraphWithHighProbabilityEdges(this.graphs);

        TetradLogger.getInstance().forceLogMessage("Final Resampling Search Result:");
        TetradLogger.getInstance().forceLogMessage(GraphUtils.graphToText(graph, false));

        return graph;
    }

    /**
     * Returns the individual bootstrap result graphs.
     *
     * @return A list of these Graphs.
     */
    public List<Graph> getGraphs() {
        return graphs == null ? new ArrayList<>() : new ArrayList<>(this.graphs);
    }

    /**
     * Sets the score wrapper if not null.
     *
     * @param score The wrapper
     * @see edu.pitt.dbmi.algo.resampling.task.GeneralResamplingSearchRunnable
     */
    public void setScoreWrapper(ScoreWrapper score) {
        this.setScoreWrapper = score;
    }
}
