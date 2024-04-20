package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.DataGraphUtils;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.PointXy;
import edu.cmu.tetradapp.editor.*;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by jdramsey on 12/8/15.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraphUtils {
    /**
     * <p>makeRandomGraph.</p>
     *
     * @param graph      a {@link edu.cmu.tetrad.graph.Graph} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
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

    public static @NotNull JMenu getCheckGraphMenu(GraphWorkbench workbench) {
        JMenu checkGraph = new JMenu("Check Graph Type");
        JMenuItem checkGraphForDag = new JMenuItem(new CheckGraphForDagAction(workbench));
        JMenuItem checkGraphForCpdag = new JMenuItem(new CheckGraphForCpdagAction(workbench));
        JMenuItem checkGraphForMpdag = new JMenuItem(new CheckGraphForMpdagAction(workbench));
        JMenuItem checkGraphForMag = new JMenuItem(new CheckGraphForMagAction(workbench));
        JMenuItem checkGraphForPag = new JMenuItem(new CheckGraphForPagAction(workbench));
        JMenuItem checkGraphForMpag = new JMenuItem(new CheckGraphForMpagAction(workbench));

        checkGraph.add(checkGraphForDag);
        checkGraph.add(checkGraphForCpdag);
        checkGraph.add(checkGraphForMpdag);
        checkGraph.add(checkGraphForMag);
        checkGraph.add(checkGraphForPag);
        checkGraph.add(checkGraphForMpag);
        return checkGraph;
    }

    public static @NotNull JMenu getHighlightMenu(GraphWorkbench workbench) {
        JMenu highlightMenu = new JMenu("Highlight Edges");
        highlightMenu.add(new SelectDirectedAction(workbench));
        highlightMenu.add(new SelectBidirectedAction(workbench));
        highlightMenu.add(new SelectUndirectedAction(workbench));
        highlightMenu.add(new SelectTrianglesAction(workbench));
        highlightMenu.add(new SelectLatentsAction(workbench));
        highlightMenu.add(new SelectEdgesInCycles(workbench));
        return highlightMenu;
    }

    /**
     * Breaks down a given reason into multiple lines with a maximum number of columns.
     *
     * @param reason     the reason to be broken down
     * @param maxColumns the maximum number of columns in a line
     * @return a string with the reason broken down into multiple lines
     */
    public static String breakDown(String reason, int maxColumns) {
        StringBuilder buf1 = new StringBuilder();
        StringBuilder buf2 = new StringBuilder();

        String[] tokens = reason.split(" ");

        for (String token : tokens) {
            if (buf1.length() + token.length() > maxColumns) {
                buf2.append(buf1);
                buf2.append("\n");
                buf1 = new StringBuilder();
                buf1.append(token);
            } else {
                buf1.append(" ").append(token);
            }
        }

        if (!buf1.isEmpty()) {
            buf2.append(buf1);
        }

        return buf2.toString().trim();
    }

    /**
     * Adds graph manipulation items to the given graph menu.
     *
     * @param graph the graph menu to add the items to.
     */
    public static void addGraphManipItems(JMenu graph, GraphWorkbench workbench) {
        JMenu applyFinalRules = new JMenu("Apply final rules");
        JMenuItem runMeekRules = new JMenuItem(new ApplyMeekRules(workbench));
        JMenuItem runFinalFciRules = new JMenuItem(new ApplyFinalFciRules(workbench));
        applyFinalRules.add(runMeekRules);
        applyFinalRules.add(runFinalFciRules);
        graph.add(applyFinalRules);

        JMenu revertGraph = new JMenu("Revert Graph");
        JMenuItem revertToCpdag = new JMenuItem(new RevertToCpdag(workbench));
        JMenuItem revertToPag = new JMenuItem(new RevertToPag(workbench));
        JMenuItem undoLast = new JMenuItem(new UndoLastAction(workbench));
        JMenuItem redoLast = new JMenuItem(new RedoLastAction(workbench));
        JMenuItem setToOriginal = new JMenuItem(new SetToOriginalAction(workbench));
        revertGraph.add(undoLast);
        revertGraph.add(redoLast);
        revertGraph.add(setToOriginal);
        revertGraph.add(revertToCpdag);
        revertGraph.add(revertToPag);
        graph.add(revertGraph);

        runMeekRules.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK));
        revertToCpdag.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
        runFinalFciRules.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        revertToPag.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));
        undoLast.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        redoLast.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        setToOriginal.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
    }
}
