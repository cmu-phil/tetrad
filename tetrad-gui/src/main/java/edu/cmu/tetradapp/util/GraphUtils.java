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
    public static Graph makeRandomGraph(final Graph graph, final Parameters parameters) {
        final int newGraphNumEdges = parameters.getInt("newGraphNumEdges", 3);
        final boolean connected = parameters.getBoolean("randomGraphConnected", false);
        final boolean addCycles = parameters.getBoolean("randomGraphAddCycles", false);
        final boolean graphRandomFoward = parameters.getBoolean("graphRandomFoward", true);
        final int newGraphNumMeasuredNodes = parameters.getInt("newGraphNumMeasuredNodes", 5);
        final int newGraphNumLatents = parameters.getInt("newGraphNumLatents", 0);
        final boolean graphUniformlySelected = parameters.getBoolean("graphUniformlySelected", true);
        final int randomGraphMaxIndegree = parameters.getInt("randomGraphMaxIndegree", 3);
        final int randomGraphMaxOutdegree = parameters.getInt("randomGraphMaxOutdegree", 1);
        final boolean randomGraphConnected = parameters.getBoolean("randomGraphConnected", connected);
        final int randomGraphMaxDegree = parameters.getInt("randomGraphMaxDegree", 6);
        final boolean graphChooseFixed = parameters.getBoolean("graphChooseFixed", false);
        final int numStructuralNodes = parameters.getInt("numStructuralNodes", 3);
        final int maxStructuralEdges = parameters.getInt("numStructuralEdges", 3);
        final int measurementModelDegree = parameters.getInt("measurementModelDegree", 3);
        final int numLatentMeasuredImpureParents = parameters.getInt("latentMeasuredImpureParents", 0);
        final int numMeasuredMeasuredImpureParents = parameters.getInt("measuredMeasuredImpureParents", 0);
        final int numMeasuredMeasuredImpureAssociations = parameters.getInt("measuredMeasuredImpureAssociations", 0);
        final double alpha = parameters.getDouble("scaleFreeAlpha", 0.2);
        final double beta = parameters.getDouble("scaleFreeBeta", 0.6);
        final double deltaIn = parameters.getDouble("scaleFreeDeltaIn", 0.2);
        final double deltaOut = parameters.getDouble("scaleFreeDeltaOut", 0.2);
        final int numFactors = parameters.getInt("randomMimNumFactors", 1);

        final String type = parameters.getString("randomGraphType", "ScaleFree");

        if (type.equals("Uniform")) {
            return GraphUtils.makeRandomDag(graph,
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
            return GraphUtils.makeRandomMim(numFactors, numStructuralNodes, maxStructuralEdges, measurementModelDegree,
                    numLatentMeasuredImpureParents, numMeasuredMeasuredImpureParents,
                    numMeasuredMeasuredImpureAssociations);
        } else if (type.equals("ScaleFree")) {
            return GraphUtils.makeRandomScaleFree(newGraphNumMeasuredNodes,
                    newGraphNumLatents, alpha, beta, deltaIn, deltaOut);
        }

        throw new IllegalStateException("Unrecognized graph type: " + type);
    }

    private static Graph makeRandomDag(final Graph _graph, final int newGraphNumMeasuredNodes,
                                       final int newGraphNumLatents,
                                       final int newGraphNumEdges, final int randomGraphMaxDegree,
                                       final int randomGraphMaxIndegree,
                                       final int randomGraphMaxOutdegree,
                                       final boolean graphRandomFoward,
                                       final boolean graphUniformlySelected,
                                       final boolean randomGraphConnected,
                                       final boolean graphChooseFixed,
                                       final boolean addCycles, final Parameters parameters) {
        Graph graph = null;


        final int numNodes = newGraphNumMeasuredNodes + newGraphNumLatents;
        int numTrials = 0;

        while (graph == null && ++numTrials < 100) {


            final List<Node> nodes = new ArrayList<>();

            for (int i = 0; i < numNodes; i++) {
                nodes.add(new GraphNode("X" + (i + 1)));
            }

            if (graphRandomFoward) {
                graph = edu.cmu.tetrad.graph.GraphUtils.randomGraphRandomForwardEdges(nodes, newGraphNumLatents,
                        newGraphNumEdges, randomGraphMaxDegree, randomGraphMaxIndegree, randomGraphMaxOutdegree,
                        false, true);
                edu.cmu.tetrad.graph.GraphUtils.arrangeBySourceGraph(graph, _graph);
                final HashMap<String, PointXy> layout = edu.cmu.tetrad.graph.GraphUtils.grabLayout(nodes);
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
                    final HashMap<String, PointXy> layout = edu.cmu.tetrad.graph.GraphUtils.grabLayout(nodes);
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
                            final HashMap<String, PointXy> layout = edu.cmu.tetrad.graph.GraphUtils.grabLayout(nodes);
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

            final int randomGraphMinNumCycles = parameters.getInt("randomGraphMinNumCycles", 0);
            edu.cmu.tetrad.graph.GraphUtils.addTwoCycles(graph, randomGraphMinNumCycles);
        }

        if (graph == null) {
            throw new NullPointerException("Could not find a graph that meets those constraints.");
        }

        return graph;
    }

    private static Graph makeRandomMim(final int numFactors, final int numStructuralNodes, final int maxStructuralEdges, final int measurementModelDegree,
                                       final int numLatentMeasuredImpureParents, final int numMeasuredMeasuredImpureParents,
                                       final int numMeasuredMeasuredImpureAssociations) {

        final Graph graph;

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
        } else {
            throw new IllegalArgumentException("Can only make random MIMs for 1 or 2 factors, " +
                    "sorry dude.");
        }

        return graph;
    }

    private static Graph makeRandomScaleFree(final int numNodes, final int numLatents, final double alpha,
                                             final double beta, final double deltaIn, final double deltaOut) {
        final Graph graph = edu.cmu.tetrad.graph.GraphUtils.scaleFreeGraph(numNodes, numLatents,
                alpha, beta, deltaIn, deltaOut);
        return graph;
    }

    // Returns true if a path consisting of undirected and directed edges toward 'to' exists of
    // length at most 'bound' except for an edge from->to itself. Cycle checker in other words.
    public static boolean existsSemiDirectedPathExcept(final Node from, final Node to, final int bound, final Graph graph) {
        final Queue<Node> Q = new LinkedList<>();
        final Set<Node> V = new HashSet<>();
        Q.offer(from);
        V.add(from);
        Node e = null;
        int distance = 0;

        while (!Q.isEmpty()) {
            final Node t = Q.remove();
//            if (t == to) {
//                return true;
//            }

            if (e == t) {
                e = null;
                distance++;
                if (distance > (bound == -1 ? 1000 : bound)) return false;
            }

            for (final Node u : graph.getAdjacentNodes(t)) {
                final Edge edge = graph.getEdge(t, u);
                final Node c = edu.cmu.tetrad.graph.GraphUtils.traverseSemiDirected(t, edge);
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
    public static Node traverseSemiDirected(final Node node, final Edge edge) {
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
