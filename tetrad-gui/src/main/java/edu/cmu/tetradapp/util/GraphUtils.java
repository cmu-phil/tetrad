package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.DataGraphUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.PointXy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Created by jdramsey on 12/8/15.
 *
 * @author Joseph Ramsey
 */
public class GraphUtils {
    public static Graph makeRandomGraph(Graph graph) {
        int newGraphNumEdges = Preferences.userRoot().getInt("newGraphNumEdges", 3);
        boolean connected = Preferences.userRoot().getBoolean("randomGraphConnected", false);
        boolean addCycles = Preferences.userRoot().getBoolean("randomGraphAddCycles", false);
        boolean graphRandomFoward = Preferences.userRoot().getBoolean("graphRandomFoward", true);
        int newGraphNumMeasuredNodes = Preferences.userRoot().getInt("newGraphNumMeasuredNodes", 5);
        int newGraphNumLatents = Preferences.userRoot().getInt("newGraphNumLatents", 0);
        boolean graphUniformlySelected = Preferences.userRoot().getBoolean("graphUniformlySelected", true);
        int randomGraphMaxIndegree = Preferences.userRoot().getInt("randomGraphMaxIndegree", 3);
        int randomGraphMaxOutdegree = Preferences.userRoot().getInt("randomGraphMaxOutdegree", 1);
        boolean randomGraphConnected = Preferences.userRoot().getBoolean("randomGraphConnected", connected);
        int randomGraphMaxDegree = Preferences.userRoot().getInt("randomGraphMaxDegree", 6);
        boolean graphChooseFixed = Preferences.userRoot().getBoolean("graphChooseFixed", false);
        int numStructuralNodes = Preferences.userRoot().getInt("numStructuralNodes", 3);
        int maxStructuralEdges = Preferences.userRoot().getInt("numStructuralEdges", 3);
        int measurementModelDegree = Preferences.userRoot().getInt("measurementModelDegree", 3);
        int numLatentMeasuredImpureParents = Preferences.userRoot().getInt("latentMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureParents = Preferences.userRoot().getInt("measuredMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureAssociations = Preferences.userRoot().getInt("measuredMeasuredImpureAssociations", 0);
        double alpha = Preferences.userRoot().getDouble("scaleFreeAlpha", 0.2);
        double beta = Preferences.userRoot().getDouble("scaleFreeBeta", 0.6);
        double deltaIn = Preferences.userRoot().getDouble("scaleFreeDeltaIn", 0.2);
        double deltaOut = Preferences.userRoot().getDouble("scaleFreeDeltaOut", 0.2);
        int numFactors = Preferences.userRoot().getInt("randomMimNumFactors", 1);

        final String type = Preferences.userRoot().get("randomGraphType", "Uniform");

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
                    addCycles);
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

    public static Graph makeRandomDag(Graph _graph, int newGraphNumMeasuredNodes,
                                      int newGraphNumLatents,
                                      int newGraphNumEdges, int randomGraphMaxDegree,
                                      int randomGraphMaxIndegree,
                                      int randomGraphMaxOutdegree,
                                      boolean graphRandomFoward,
                                      boolean graphUniformlySelected,
                                      boolean randomGraphConnected,
                                      boolean graphChooseFixed,
                                      boolean addCycles) {
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
                        false);
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
                graph = edu.cmu.tetrad.graph.GraphUtils.cyclicGraph2(numNodes, newGraphNumEdges);
            } else {
                graph = new EdgeListGraph(graph);
            }

            int randomGraphMinNumCycles = Preferences.userRoot().getInt("randomGraphMinNumCycles", 0);
            edu.cmu.tetrad.graph.GraphUtils.addTwoCycles(graph, randomGraphMinNumCycles);
        }

        if (graph == null) {
            throw new NullPointerException("Could not find a graph that meets those constraints.");
        }

        return graph;
    }

    public static Graph makeRandomMim(int numFactors, int numStructuralNodes, int maxStructuralEdges, int measurementModelDegree,
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

    public static Graph makeRandomScaleFree(int numNodes, int numLatents, double alpha,
                                            double beta, double deltaIn, double deltaOut) {
        Graph graph = edu.cmu.tetrad.graph.GraphUtils.scaleFreeGraph(numNodes, numLatents,
                alpha, beta, deltaIn, deltaOut);
        return graph;
    }
}
