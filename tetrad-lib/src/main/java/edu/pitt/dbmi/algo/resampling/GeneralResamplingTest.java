package edu.pitt.dbmi.algo.resampling;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.util.*;

/**
 * Created by mahdi on 1/16/17.
 * <p>
 * Updated: Chirayu Kong Wongchokprasitti, PhD on 9/13/2018
 */
public class GeneralResamplingTest {
    private final GeneralResamplingSearch resamplingSearch;
    private final ResamplingEdgeEnsemble edgeEnsemble;
    private PrintStream out = System.out;
    private Parameters parameters;
    private Algorithm algorithm;
    private MultiDataSetAlgorithm multiDataSetAlgorithm;
    private List<Graph> graphs;
    private boolean verbose;
    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();
    /**
     * An initial graph to start from.
     */
    private Graph externalGraph;
    private int numNoGraphs = 0;

    public GeneralResamplingTest(
            DataSet data,
            Algorithm algorithm,
            int numberResampling,
            double percentResamplingSize,
            boolean resamplingWithReplacement, int edgeEnsemble, boolean addOriginalDataset) {
        this.algorithm = algorithm;
        this.resamplingSearch = new GeneralResamplingSearch(data, numberResampling);
        this.resamplingSearch.setPercentResampleSize(percentResamplingSize);
        this.resamplingSearch.setResamplingWithReplacement(resamplingWithReplacement);
        this.resamplingSearch.setAddOriginalDataset(addOriginalDataset);

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
            default:
                throw new IllegalArgumentException("Expecting 1, 2, or 3.");
        }
    }

    public GeneralResamplingTest(
            List<DataSet> dataSets, MultiDataSetAlgorithm multiDataSetAlgorithm,
            int numberResampling,
            double percentResamplingSize,
            boolean resamplingWithReplacement,
            int edgeEnsemble, boolean addOriginalDataset) {
        this.multiDataSetAlgorithm = multiDataSetAlgorithm;
        this.resamplingSearch = new GeneralResamplingSearch(dataSets, numberResampling);
        this.resamplingSearch.setPercentResampleSize(percentResamplingSize);
        this.resamplingSearch.setResamplingWithReplacement(resamplingWithReplacement);
        this.resamplingSearch.setAddOriginalDataset(addOriginalDataset);

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
            default:
                throw new IllegalArgumentException("Expecting 1, 2, or 3.");
        }
    }

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
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @return the output stream that output (except for log output) should be
     * sent to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent
     * to. By default System.out.
     */
    public void setOut(PrintStream out) {
        this.out = out;
    }

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
    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null)
            throw new NullPointerException();
        this.knowledge = knowledge;
    }

    /**
     * Sets the initial graph.
     */
    public void setExternalGraph(Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    public void setSeed(long seed) {
        RandomUtil.getInstance().setSeed(seed);
    }

    public Graph search() {
        long start, stop;

        start = System.currentTimeMillis();

        if (this.algorithm != null) {
            this.resamplingSearch.setAlgorithm(this.algorithm);
        } else {
            this.resamplingSearch.setMultiDataSetAlgorithm(this.multiDataSetAlgorithm);
        }
        boolean runParallel = true;
        this.resamplingSearch.setRunParallel(runParallel);
        this.resamplingSearch.setVerbose(this.verbose);
        this.resamplingSearch.setParameters(this.parameters);

        if (!this.knowledge.isEmpty()) {
            this.resamplingSearch.setKnowledge(this.knowledge);
        }

        if (this.externalGraph != null) {
            this.resamplingSearch.setExternalGraph(this.externalGraph);
        }

        if (this.verbose) {
            if (this.algorithm != null) {
                this.out.println("Resampling on the " + this.algorithm.getDescription());
            } else if (this.multiDataSetAlgorithm != null) {
                this.out.println("Resampling on the " + this.multiDataSetAlgorithm.getDescription());
            }
        }

        this.graphs = this.resamplingSearch.search();
        this.numNoGraphs = this.resamplingSearch.getNumNograph();

        TetradLogger.getInstance().forceLogMessage(
                "Bootstrappiung: Number of searches that didn't return a graph = " + this.numNoGraphs);

        if (this.verbose) {
            this.out.println("Resampling number is : " + this.graphs.size());
        }
        stop = System.currentTimeMillis();
        if (this.verbose) {
            this.out.println("Processing time of total resamplings : " + (stop - start) / 1000.0 + " sec");
        }

        start = System.currentTimeMillis();
        Graph graph = generateSamplingGraph();
        stop = System.currentTimeMillis();
        if (this.verbose) {
            this.out.println("Final Resampling Search Result:");
            this.out.println(GraphUtils.graphToText(graph));
            this.out.println();
            this.out.println("probDistribution finished in " + (stop - start) + " ms");
        }

        return graph;
    }

    private Graph generateSamplingGraph() {
        Graph _graph = null;
        if (this.verbose) {
            this.out.println("Graphs: " + this.graphs.size());
            this.out.println("Ensemble: " + this.edgeEnsemble);
            this.out.println();
        }

        if (this.graphs == null || this.graphs.size() == 0) return new EdgeListGraph();

        int i = 1;

        for (Graph g : this.graphs) {
            if (g != null) {
                if (_graph == null) {
                    _graph = g;
                }
                if (this.verbose) {
                    this.out.println("Resampling Search Result (" + i + "):");
                    this.out.println(GraphUtils.graphToText(g));
                    this.out.println();
                    i++;
                }
            }
        }

        if (_graph == null) return new EdgeListGraph();

        // Sort nodes by its name for fixing the edge orientation
        List<Node> nodes = _graph.getNodes();
        Collections.sort(nodes);

        Graph complete = new EdgeListGraph(nodes);
        complete.fullyConnect(Endpoint.TAIL);

        Graph graph = new EdgeListGraph(nodes);

        for (Edge e : complete.getEdges()) {
            Node n1 = e.getNode1();
            Node n2 = e.getNode2();

            // Test new probability method
            List<EdgeTypeProbability> edgeTypeProbabilities = getProbabilities(n1, n2);
            EdgeTypeProbability chosen_edge_type = null;
            double max_edge_prob = 0;
            double no_edge_prob = 0;
            if (edgeTypeProbabilities != null) {
                for (EdgeTypeProbability etp : edgeTypeProbabilities) {
                    EdgeType edgeType = etp.getEdgeType();
                    double prob = etp.getProbability();
                    if (edgeType != EdgeType.nil) {

                        if (prob > max_edge_prob) {
                            chosen_edge_type = etp;
                            max_edge_prob = prob;
                        }
                    } else {
                        no_edge_prob = prob;
                    }
                }
            }

            if (chosen_edge_type != null) {
                Edge edge = null;
                EdgeType edgeType = chosen_edge_type.getEdgeType();
                switch (edgeType) {
                    case ta:
                        edge = new Edge(n1, n2, Endpoint.TAIL, Endpoint.ARROW);
                        break;
                    case at:
                        edge = new Edge(n1, n2, Endpoint.ARROW, Endpoint.TAIL);
                        break;
                    case ca:
                        edge = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.ARROW);
                        break;
                    case ac:
                        edge = new Edge(n1, n2, Endpoint.ARROW, Endpoint.CIRCLE);
                        break;
                    case cc:
                        edge = new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.CIRCLE);
                        break;
                    case aa:
                        edge = new Edge(n1, n2, Endpoint.ARROW, Endpoint.ARROW);
                        break;
                    case tt:
                        edge = new Edge(n1, n2, Endpoint.TAIL, Endpoint.TAIL);
                        break;
                    default:
                        // Do nothing
                }

                for (Edge.Property property : chosen_edge_type.getProperties()) {
                    if (edge != null) {
                        edge.addProperty(property);
                    }
                }

                switch (this.edgeEnsemble) {
                    case Highest:
                        if (no_edge_prob > max_edge_prob) {
                            edge = null;
                        }
                        break;
                    case Majority:
                        if (no_edge_prob > max_edge_prob || max_edge_prob < .5) {
                            edge = null;
                        }
                        break;
                    default:
                        // Do nothing
                }

                if (edge != null) {
                    List<EdgeTypeProbability> probs = getProbabilities(edge.getNode1(), edge.getNode2());

                    for (EdgeTypeProbability etp : probs) {
                        edge.addEdgeTypeProbability(etp);
                    }

                    graph.addEdge(edge);
                }
            }

        }

        return graph;
    }

    private List<EdgeTypeProbability> getProbabilities(Node node1, Node node2) {
        Map<Edge, Integer> edgeDist = new HashMap<>();

        int no_edge_num = 0;

        for (Graph g : this.graphs) {
            if (g == null) continue;

            Edge e = g.getEdge(node1, node2);

            if (e != null) {
                Integer num_edge = edgeDist.get(e);

                if (num_edge == null) {
                    num_edge = 0;
                }

                num_edge = num_edge + 1;
                edgeDist.put(e, num_edge);
            } else {
                no_edge_num++;
            }
        }
        int n = graphs.size();
        // Normalization
        List<EdgeTypeProbability> edgeTypeProbabilities = edgeDist.size() == 0 ? null : new ArrayList<>();

        for (Edge edge : edgeDist.keySet()) {
            int edge_num = edgeDist.get(edge);
            double probability = (double) edge_num / n;

            Endpoint end1 = edge.getProximalEndpoint(node1);
            Endpoint end2 = edge.getProximalEndpoint(node2);

            EdgeType edgeType = EdgeType.nil;

            if (end1 == Endpoint.TAIL && end2 == Endpoint.ARROW) {
                edgeType = EdgeType.ta;
            }
            if (end1 == Endpoint.ARROW && end2 == Endpoint.TAIL) {
                edgeType = EdgeType.at;
            }
            if (end1 == Endpoint.CIRCLE && end2 == Endpoint.ARROW) {
                edgeType = EdgeType.ca;
            }
            if (end1 == Endpoint.ARROW && end2 == Endpoint.CIRCLE) {
                edgeType = EdgeType.ac;
            }
            if (end1 == Endpoint.CIRCLE && end2 == Endpoint.CIRCLE) {
                edgeType = EdgeType.cc;
            }
            if (end1 == Endpoint.ARROW && end2 == Endpoint.ARROW) {
                edgeType = EdgeType.aa;
            }
            if (end1 == Endpoint.TAIL && end2 == Endpoint.TAIL) {
                edgeType = EdgeType.tt;
            }

            EdgeTypeProbability etp = new EdgeTypeProbability(edgeType, probability);

            edgeTypeProbabilities.add(etp);
        }

        if (no_edge_num < n && edgeTypeProbabilities != null) {
            edgeTypeProbabilities.add(new EdgeTypeProbability(EdgeType.nil, (double) no_edge_num / n));
        }

        return edgeTypeProbabilities;
    }

    public int getNumNoGraphs() {
        return numNoGraphs;
    }
}