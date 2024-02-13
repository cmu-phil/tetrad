package edu.pitt.dbmi.algo.resampling;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.*;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by mahdi on 1/16/17.
 * <p>
 * Updated: Chirayu Kong Wongchokprasitti, PhD on 9/13/2018
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GeneralResamplingTest {

    private final GeneralResamplingSearch resamplingSearch;
    private final ResamplingEdgeEnsemble edgeEnsemble;
    private ScoreWrapper scoreWrapper;
    private IndependenceWrapper independenceWrapper;
    private PrintStream out = System.out;
    private Parameters parameters;
    private Algorithm algorithm;
    private MultiDataSetAlgorithm multiDataSetAlgorithm;
    private List<Graph> graphs = new ArrayList<>();
    private boolean verbose;
    /**
     * Specification of forbidden and required edges.
     */
    private Knowledge knowledge = new Knowledge();
    /**
     * An initial graph to start from.
     */
    private Graph externalGraph;
    /**
     * The number of threads to use for bootstrapping. Must be at least 1. Default is 1.
     */
    private int numBootstrapThreads = 1;

    /**
     * Constructor.
     *
     * @param data                      the data set.
     * @param algorithm                 the algorithm.
     * @param numberResampling          the number of resampling.
     * @param percentResamplingSize     the percent resampling size.
     * @param resamplingWithReplacement whether resampling with replacement.
     * @param edgeEnsemble              the edge ensemble.
     * @param addOriginalDataset        whether to add the original dataset.
     * @param bootstrappingNumThreads   the number of threads to use for bootstrapping.
     */
    public GeneralResamplingTest(
            DataSet data,
            Algorithm algorithm,
            int numberResampling,
            double percentResamplingSize,
            boolean resamplingWithReplacement,
            int edgeEnsemble,
            boolean addOriginalDataset,
            int bootstrappingNumThreads) {
        this.algorithm = algorithm;
        this.resamplingSearch = new GeneralResamplingSearch(data, numberResampling);
        this.resamplingSearch.setPercentResampleSize(percentResamplingSize);
        this.resamplingSearch.setResamplingWithReplacement(resamplingWithReplacement);
        this.resamplingSearch.setAddOriginalDataset(addOriginalDataset);
        this.numBootstrapThreads = bootstrappingNumThreads;

        switch (edgeEnsemble) {
            case 1:
                this.edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                break;
            case 2:
                this.edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                break;
            case 3:
                this.edgeEnsemble = ResamplingEdgeEnsemble.Majority;
                break;
            case 4:
                this.edgeEnsemble = ResamplingEdgeEnsemble.Threshold;
                break;
            default:
                throw new IllegalArgumentException("Expecting 1, 2, 3 or 4.");
        }
    }

    /**
     * Constructor.
     *
     * @param dataSets                  the data sets.
     * @param multiDataSetAlgorithm     the multi data set algorithm.
     * @param numberResampling          the number of resampling.
     * @param percentResamplingSize     the percent resampling size.
     * @param resamplingWithReplacement whether resampling with replacement.
     * @param edgeEnsemble              the edge ensemble.
     * @param addOriginalDataset        whether to add the original dataset.
     * @param bootstrappingNumThreads   the number of threads to use for bootstrapping.
     */
    public GeneralResamplingTest(
            List<DataSet> dataSets,
            MultiDataSetAlgorithm multiDataSetAlgorithm,
            int numberResampling,
            double percentResamplingSize,
            boolean resamplingWithReplacement,
            int edgeEnsemble,
            boolean addOriginalDataset, int bootstrappingNumThreads) {
        this.multiDataSetAlgorithm = multiDataSetAlgorithm;
        this.resamplingSearch = new GeneralResamplingSearch(dataSets, numberResampling);
        this.resamplingSearch.setPercentResampleSize(percentResamplingSize);
        this.resamplingSearch.setResamplingWithReplacement(resamplingWithReplacement);
        this.resamplingSearch.setAddOriginalDataset(addOriginalDataset);
        this.numBootstrapThreads = bootstrappingNumThreads;

        switch (edgeEnsemble) {
            case 1:
                this.edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                break;
            case 2:
                this.edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                break;
            case 3:
                this.edgeEnsemble = ResamplingEdgeEnsemble.Majority;
                break;
            case 4:
                this.edgeEnsemble = ResamplingEdgeEnsemble.Threshold;
                break;
            default:
                throw new IllegalArgumentException("Expecting 1, 2, 3, or 4.");
        }
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
     * Sets the output stream that output (except for log output) should be sent to. By default System.out.
     *
     * @param out the output stream that output (except for log output) should be sent to.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

    /**
     * Sets the parameters.
     *
     * @param parameters the parameters.
     */
    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
        Object obj = parameters.get(Params.PRINT_STREAM);
        if (obj instanceof PrintStream) {
            setOut((PrintStream) obj);
        }
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the knowledge object, specifying forbidden and required edges.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the initial graph.
     *
     * @param externalGraph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    /**
     * Runs the resampling test.
     *
     * @return the graph.
     */
    public Graph search() {
        long start, stop;

        start = MillisecondTimes.timeMillis();

        if (this.algorithm != null) {
            this.resamplingSearch.setAlgorithm(this.algorithm);
        } else {
            this.resamplingSearch.setMultiDataSetAlgorithm(this.multiDataSetAlgorithm);
        }
        this.resamplingSearch.setNumBootstrapThreads(this.numBootstrapThreads);
        this.resamplingSearch.setVerbose(this.verbose);
        this.resamplingSearch.setParameters(this.parameters);
        this.resamplingSearch.setScoreWrapper(scoreWrapper);

        if (!this.knowledge.isEmpty()) {
            this.resamplingSearch.setKnowledge(this.knowledge);
        }

        if (this.verbose) {
            if (this.algorithm != null) {
                this.out.println("Resampling on the " + this.algorithm.getDescription());
            } else if (this.multiDataSetAlgorithm != null) {
                this.out.println("Resampling on the " + this.multiDataSetAlgorithm.getDescription());
            }
        }

        this.graphs = this.resamplingSearch.search();
        int numNoGraphs = this.resamplingSearch.getNumRunsReturningNullGraph();

        TetradLogger.getInstance().forceLogMessage(
                "Bootstrapping: Number of searches that didn't return a graph = " + numNoGraphs);

        if (this.verbose) {
            this.out.println("Resampling number is : " + this.graphs.size());
        }
        stop = MillisecondTimes.timeMillis();
        if (this.verbose) {
            this.out.println("Processing time of total resamplings : " + (stop - start) / 1000.0 + " sec");
        }

        start = MillisecondTimes.timeMillis();
        Graph graph = GraphSampling.createGraphWithHighProbabilityEdges(this.graphs);
        stop = MillisecondTimes.timeMillis();

//        if (this.verbose) {
        TetradLogger.getInstance().forceLogMessage("Final Resampling Search Result:");
        TetradLogger.getInstance().forceLogMessage(GraphUtils.graphToText(graph, false));
        TetradLogger.getInstance().forceLogMessage("probDistribution finished in " + (stop - start) + " ms");
//        }

        return graph;
    }

    /**
     * Sets the score wrapper.
     *
     * @param scoreWrapper the score wrapper.
     */
    public void setScoreWrapper(ScoreWrapper scoreWrapper) {
        this.scoreWrapper = scoreWrapper;
    }

    /**
     * Sets the independence wrapper.
     *
     * @param independenceWrapper the independence wrapper.
     */
    public void setIndTestWrapper(IndependenceWrapper independenceWrapper) {
        this.independenceWrapper = independenceWrapper;
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
     * Sets the number of threads to use for bootstrapping. Must be at least 1. Default is 1. Note that this is the
     * number of threads to use for bootstrapping, not the number of threads to use for the search itself. The n umber
     * of threads to use for each search is determined by the algorithm being used.
     *
     * @param numBootstrapThreads the number of threads to use for bootstrapping.
     */
    public void setNumBootstrapThreads(int numBootstrapThreads) {
        this.numBootstrapThreads = numBootstrapThreads;
    }
}
