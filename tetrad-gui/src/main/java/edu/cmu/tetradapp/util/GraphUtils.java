package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.DataGraphUtils;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.PointXy;

import java.util.*;

/**
 * Created by jdramsey on 12/8/15.
 *
 * @author Joseph Ramsey
 */
public class GraphUtils {
    public static Graph makeRandomGraph(Graph graph, Parameters parameters) {
        int newGraphNumEdges = parameters.getInt("newGraphNumEdges", 3);
        boolean connected = parameters.getBoolean("randomGraphConnected", false);
        boolean addCycles = parameters.getBoolean("randomGraphAddCycles", false);
        boolean graphRandomFoward = parameters.getBoolean("graphRandomFoward", true);
        int newGraphNumMeasuredNodes = parameters.getInt("newGraphNumMeasuredNodes", 5);
        int newGraphNumLatents = parameters.getInt("newGraphNumLatents", 0);
        boolean graphUniformlySelected = parameters.getBoolean("graphUniformlySelected", true);
        int randomGraphMaxIndegree = parameters.getInt("randomGraphMaxIndegree", 3);
        int randomGraphMaxOutdegree = parameters.getInt("randomGraphMaxOutdegree", 1);
        boolean randomGraphConnected = parameters.getBoolean("randomGraphConnected", connected);
        int randomGraphMaxDegree = parameters.getInt("randomGraphMaxDegree", 6);
        boolean graphChooseFixed = parameters.getBoolean("graphChooseFixed", false);
        int numStructuralNodes = parameters.getInt("numStructuralNodes", 3);
        int maxStructuralEdges = parameters.getInt("numStructuralEdges", 3);
        int measurementModelDegree = parameters.getInt("measurementModelDegree", 3);
        int numLatentMeasuredImpureParents = parameters.getInt("latentMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureParents = parameters.getInt("measuredMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureAssociations = parameters.getInt("measuredMeasuredImpureAssociations", 0);
        double alpha = parameters.getDouble("scaleFreeAlpha", 0.2);
        double beta = parameters.getDouble("scaleFreeBeta", 0.6);
        double deltaIn = parameters.getDouble("scaleFreeDeltaIn", 0.2);
        double deltaOut = parameters.getDouble("scaleFreeDeltaOut", 0.2);
        int numFactors = parameters.getInt("randomMimNumFactors", 1);

        final String type = parameters.getString("randomGraphType", "ScaleFree");

        if (type.equals("Uniform")) {
            return makeRandomDag(graph,
                    newGraphNumMeasuredNodes,
                    newGraphNumLatents,
                    newGraphNumEdges,
                    randomGraphMaxDegree,
                    randomGraphMaxIndegree,
                    randomGraphMaxOutdegree,
                    graphRandomFoward,
                    graphUniformlySelected,
                    randomGraphConnected,
                    graphChooseFixed,
                    addCycles, parameters);
        } else if (type.equals("Mim")) {
            return makeRandomMim(numFactors, numStructuralNodes, maxStructuralEdges, measurementModelDegree,
                    numLatentMeasuredImpureParents, numMeasuredMeasuredImpureParents,
                    numMeasuredMeasuredImpureAssociations);
        } else if (type.equals("ScaleFree")) {
            return makeRandomScaleFree(newGraphNumMeasuredNodes,
                    newGraphNumLatents, alpha, beta, deltaIn, deltaOut);
        }

        throw new IllegalStateException("Unrecognized graph type: " + type);
    }

    private static Graph makeRandomDag(Graph _graph, int newGraphNumMeasuredNodes,
                                       int newGraphNumLatents,
                                       int newGraphNumEdges, int randomGraphMaxDegree,
                                       int randomGraphMaxIndegree,
                                       int randomGraphMaxOutdegree,
                                       boolean graphRandomFoward,
                                       boolean graphUniformlySelected,
                                       boolean randomGraphConnected,
                                       boolean graphChooseFixed,
                                       boolean addCycles, Parameters parameters) {
        Graph graph = null;


        int numNodes = newGraphNumMeasuredNodes + newGraphNumLatents;
        int numTrials = 0;

        while (graph == null && ++numTrials < 100) {


            List<Node> nodes = new ArrayList<>();

            for (int i = 0; i < numNodes; i++) {
                nodes.add(new GraphNode("X" + (i + 1)));
            }

            if (graphRandomFoward) {
                graph = edu.cmu.tetrad.graph.GraphUtils.randomGraphRandomForwardEdges(nodes, newGraphNumLatents,
                        newGraphNumEdges, randomGraphMaxDegree, randomGraphMaxIndegree, randomGraphMaxOutdegree,
                        false, true);
                edu.cmu.tetrad.graph.GraphUtils.arrangeBySourceGraph(graph, _graph);
                HashMap<String, PointXy> layout = edu.cmu.tetrad.graph.GraphUtils.grabLayout(nodes);
                edu.cmu.tetrad.graph.GraphUtils.arrangeByLayout(graph, layout);
            } else {
                if (graphUniformlySelected) {

                    graph = edu.cmu.tetrad.graph.GraphUtils.randomGraphUniform(nodes,
                            newGraphNumLatents,
                            newGraphNumEdges,
                            randomGraphMaxDegree,
                            randomGraphMaxIndegree,
                            randomGraphMaxOutdegree,
                            randomGraphConnected);
                    edu.cmu.tetrad.graph.GraphUtils.arrangeBySourceGraph(graph, _graph);
                    HashMap<String, PointXy> layout = edu.cmu.tetrad.graph.GraphUtils.grabLayout(nodes);
                    edu.cmu.tetrad.graph.GraphUtils.arrangeByLayout(graph, layout);
                } else {
                    if (graphChooseFixed) {
                        do {
                            graph = edu.cmu.tetrad.graph.GraphUtils.randomGraph(nodes,
                                    newGraphNumLatents,
                                    newGraphNumEdges,
                                    randomGraphMaxDegree,
                                    randomGraphMaxIndegree,
                                    randomGraphMaxOutdegree,
                                    randomGraphConnected);
                            edu.cmu.tetrad.graph.GraphUtils.arrangeBySourceGraph(graph, _graph);
                            HashMap<String, PointXy> layout = edu.cmu.tetrad.graph.GraphUtils.grabLayout(nodes);
                            edu.cmu.tetrad.graph.GraphUtils.arrangeByLayout(graph, layout);
                        } while (graph.getNumEdges() < newGraphNumEdges);
                    }
                }
            }

            if (addCycles) {
                graph = edu.cmu.tetrad.graph.GraphUtils.cyclicGraph2(numNodes, newGraphNumEdges, 8);
            } else {
                graph = new EdgeListGraph(graph);
            }

            int randomGraphMinNumCycles = parameters.getInt("randomGraphMinNumCycles", 0);
            edu.cmu.tetrad.graph.GraphUtils.addTwoCycles(graph, randomGraphMinNumCycles);
        }

        if (graph == null) {
            throw new NullPointerException("Could not find a graph that meets those constraints.");
        }

        return graph;
    }

    private static Graph makeRandomMim(int numFactors, int numStructuralNodes, int maxStructuralEdges, int measurementModelDegree,
                                       int numLatentMeasuredImpureParents, int numMeasuredMeasuredImpureParents,
                                       int numMeasuredMeasuredImpureAssociations) {

        Graph graph;

        if (numFactors == 1) {
            graph = DataGraphUtils.randomSingleFactorModel(numStructuralNodes,
                    maxStructuralEdges, measurementModelDegree,
                    numLatentMeasuredImpureParents,
                    numMeasuredMeasuredImpureParents,
                    numMeasuredMeasuredImpureAssociations);
        } else if (numFactors == 2) {
            graph = DataGraphUtils.randomBifactorModel(numStructuralNodes,
                    maxStructuralEdges, measurementModelDegree,
                    numLatentMeasuredImpureParents,
                    numMeasuredMeasuredImpureParents,
                    numMeasuredMeasuredImpureAssociations);
        }
        else {
            throw new  IllegalArgumentException("Can only make random MIMs for 1 or 2 factors, " +
                    "sorry dude.");
        }

        return graph;
    }

    private static Graph makeRandomScaleFree(int numNodes, int numLatents, double alpha,
                                             double beta, double deltaIn, double deltaOut) {
        Graph graph = edu.cmu.tetrad.graph.GraphUtils.scaleFreeGraph(numNodes, numLatents,
                alpha, beta, deltaIn, deltaOut);
        return graph;
    }

    // Returns true if a path consisting of undirected and directed edges toward 'to' exists of
    // length at most 'bound' except for an edge from->to itself. Cycle checker in other words.
    public static boolean existsSemiDirectedPathExcept(Node from, Node to, int bound, Graph graph) {
        Queue<Node> Q = new LinkedList<>();
        Set<Node> V = new HashSet<>();
        Q.offer(from);
        V.add(from);
        Node e = null;
        int distance = 0;

        while (!Q.isEmpty()) {
            Node t = Q.remove();
//            if (t == to) {
//                return true;
//            }

            if (e == t) {
                e = null;
                distance++;
                if (distance > (bound == -1 ? 1000 : bound)) return false;
            }

            for (Node u : graph.getAdjacentNodes(t)) {
                Edge edge = graph.getEdge(t, u);
                Node c = edu.cmu.tetrad.graph.GraphUtils.traverseSemiDirected(t, edge);
                if (c == null) continue;

                if (t == from && c == to) {
                    continue;
                }

                if (c == to) {
                    return true;
                }

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);

                    if (e == null) {
                        e = u;
                    }
                }
            }
        }

        return false;
    }

    // Used to find semidirected paths for cycle checking.
    public static Node traverseSemiDirected(Node node, Edge edge) {
        if (node == edge.getNode1()) {
            if (edge.getEndpoint1() == Endpoint.TAIL || edge.getEndpoint1() == Endpoint.CIRCLE) {
                return edge.getNode2();
            }
        } else if (node == edge.getNode2()) {
            if (edge.getEndpoint2() == Endpoint.TAIL || edge.getEndpoint2() == Endpoint.CIRCLE) {
                return edge.getNode1();
            }
        }
        return null;
    }
}
