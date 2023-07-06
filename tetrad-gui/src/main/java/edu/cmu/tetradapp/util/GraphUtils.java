package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.DataGraphUtils;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.PointXy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by jdramsey on 12/8/15.
 *
 * @author josephramsey
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

        String type = parameters.getString("randomGraphType", "ScaleFree");

        switch (type) {
            case "Uniform":
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
            case "Mim":
                return GraphUtils.makeRandomMim(numFactors, numStructuralNodes, maxStructuralEdges, measurementModelDegree,
                        numLatentMeasuredImpureParents, numMeasuredMeasuredImpureParents,
                        numMeasuredMeasuredImpureAssociations);
            case "ScaleFree":
                return GraphUtils.makeRandomScaleFree(newGraphNumMeasuredNodes,
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

        while (graph == null) {

            List<Node> nodes = new ArrayList<>();

            for (int i = 0; i < numNodes; i++) {
                nodes.add(new GraphNode("X" + (i + 1)));
            }

            if (graphRandomFoward) {
                graph = RandomGraph.randomGraphRandomForwardEdges(nodes, newGraphNumLatents,
                        newGraphNumEdges, randomGraphMaxDegree, randomGraphMaxIndegree, randomGraphMaxOutdegree,
                        randomGraphConnected, true);
                LayoutUtil.arrangeBySourceGraph(graph, _graph);
                HashMap<String, PointXy> layout = GraphSaveLoadUtils.grabLayout(nodes);
                LayoutUtil.arrangeByLayout(graph, layout);
            } else {
                if (graphUniformlySelected) {

                    graph = RandomGraph.randomGraphUniform(nodes,
                            newGraphNumLatents,
                            newGraphNumEdges,
                            randomGraphMaxDegree,
                            randomGraphMaxIndegree,
                            randomGraphMaxOutdegree,
                            randomGraphConnected, 50000);
                    LayoutUtil.arrangeBySourceGraph(graph, _graph);
                    HashMap<String, PointXy> layout = GraphSaveLoadUtils.grabLayout(nodes);
                    LayoutUtil.arrangeByLayout(graph, layout);
                } else {
                    if (graphChooseFixed) {
                        do {
                            graph = RandomGraph.randomGraph(nodes,
                                    newGraphNumLatents,
                                    newGraphNumEdges,
                                    randomGraphMaxDegree,
                                    randomGraphMaxIndegree,
                                    randomGraphMaxOutdegree,
                                    randomGraphConnected);
                            LayoutUtil.arrangeBySourceGraph(graph, _graph);
                            HashMap<String, PointXy> layout = GraphSaveLoadUtils.grabLayout(nodes);
                            LayoutUtil.arrangeByLayout(graph, layout);
                        } while (graph.getNumEdges() < newGraphNumEdges);
                    }
                }
            }

            if (addCycles) {
                graph = RandomGraph.randomCyclicGraph2(numNodes, newGraphNumEdges, 8);
            } else {
                graph = new EdgeListGraph(graph);
            }

            int randomGraphMinNumCycles = parameters.getInt("randomGraphMinNumCycles", 0);
            RandomGraph.addTwoCycles(graph, randomGraphMinNumCycles);
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
        } else {
            throw new IllegalArgumentException("Can only make random MIMs for 1 or 2 factors, " +
                    "sorry dude.");
        }

        return graph;
    }

    private static Graph makeRandomScaleFree(int numNodes, int numLatents, double alpha,
                                             double beta, double deltaIn, double deltaOut) {
        return RandomGraph.randomScaleFreeGraph(numNodes, numLatents,
                alpha, beta, deltaIn, deltaOut);
    }

}
