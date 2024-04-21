package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.DataGraphUtils;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.PointXy;
import edu.cmu.tetradapp.editor.*;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.*;
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
        JMenuItem checkGraphForDag = new JMenuItem(new CheckGraphFoDagAction(workbench));
        JMenuItem checkGraphForCpdag = new JMenuItem(new CheckGraphForCpdagAction(workbench));
        JMenuItem checkGraphForMpdag = new JMenuItem(new CheckGraphForMpdagAction(workbench));
        JMenuItem checkGraphForMag = new JMenuItem(new CheckGraphForMagAction(workbench));
        JMenuItem checkGraphForPag = new JMenuItem(new CheckGraphForPagAction(workbench));
//        JMenuItem checkGraphForMpag = new JMenuItem(new CheckGraphForMpagAction(workbench));

        checkGraph.add(checkGraphForDag);
        checkGraph.add(checkGraphForCpdag);
        checkGraph.add(checkGraphForMpdag);
        checkGraph.add(checkGraphForMag);
        checkGraph.add(checkGraphForPag);
//        checkGraph.add(checkGraphForMpag);
        return checkGraph;
    }

    public static @NotNull JMenu getHighlightMenu(GraphWorkbench workbench) {
        JMenu highlightMenu = new JMenu("Highlight Edges");
        highlightMenu.add(new SelectDirectedAction(workbench));
        highlightMenu.add(new SelectBidirectedAction(workbench));
        highlightMenu.add(new SelectUndirectedAction(workbench));
        highlightMenu.add(new SelectTrianglesAction(workbench));
        highlightMenu.add(new SelectLatentsAction(workbench));
        highlightMenu.add(new SelectEdgesInAlmostCyclicPaths(workbench));
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

        JMenu transformGraph = new JMenu("Manipulate Graph");
        JMenuItem undoLast = new JMenuItem(new UndoLastAction(workbench));
        JMenuItem redoLast = new JMenuItem(new RedoLastAction(workbench));
        JMenuItem setToOriginal = new JMenuItem(new SetToOriginalAction(workbench));
        JMenuItem runMeekRules = new JMenuItem(new ApplyMeekRules(workbench));
        JMenuItem runFinalFciRules = new JMenuItem(new ApplyFinalFciRules(workbench));
        JMenuItem revertToCpdag = new JMenuItem(new RevertToCpdag(workbench));
        JMenuItem revertToPag = new JMenuItem(new RevertToPag(workbench));
        JMenuItem randomDagInCpdag = new JMenuItem(new PickRandomDagInCpdagAction(workbench));
        JMenuItem zhangMagInPag = new JMenuItem(new PickZhangMagInPagAction(workbench));
        JMenuItem correlateExogenous = new JMenuItem("Correlate Exogenous Variables");
        JMenuItem uncorrelateExogenous = new JMenuItem("Uncorrelate Exogenous Variables");

        correlateExogenous.addActionListener(e -> {
            correlateExogenousVariables(workbench);
            workbench.invalidate();
            workbench.repaint();
        });

        uncorrelateExogenous.addActionListener(e -> {
            uncorrelateExogenousVariables(workbench);
            workbench.invalidate();
            workbench.repaint();
        });
        transformGraph.add(undoLast);
        transformGraph.add(redoLast);
        transformGraph.add(setToOriginal);
        transformGraph.add(runMeekRules);
        transformGraph.add(runFinalFciRules);
        transformGraph.add(revertToCpdag);
        transformGraph.add(revertToPag);
        transformGraph.add(randomDagInCpdag);
        transformGraph.add(zhangMagInPag);
        transformGraph.add(correlateExogenous);
        transformGraph.add(uncorrelateExogenous);
        graph.add(transformGraph);

        runMeekRules.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK));
        revertToCpdag.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK));
        runFinalFciRules.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK));
        revertToPag.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.ALT_DOWN_MASK));
        undoLast.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        redoLast.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        setToOriginal.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.ALT_DOWN_MASK));
        randomDagInCpdag.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.ALT_DOWN_MASK));
        zhangMagInPag.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.ALT_DOWN_MASK));
    }

    private static void correlateExogenousVariables(GraphWorkbench workbench) {
        Graph graph = workbench.getGraph();

        if (graph instanceof Dag) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Cannot add bidirected edges to DAG's.");
            return;
        }

        List<Node> nodes = graph.getNodes();

        List<Node> exoNodes = new LinkedList<>();

        for (Node node : nodes) {
            if (graph.isExogenous(node)) {
                exoNodes.add(node);
            }
        }

        for (int i = 0; i < exoNodes.size(); i++) {

            loop:
            for (int j = i + 1; j < exoNodes.size(); j++) {
                Node node1 = exoNodes.get(i);
                Node node2 = exoNodes.get(j);
                List<Edge> edges = graph.getEdges(node1, node2);

                for (Edge edge : edges) {
                    if (Edges.isBidirectedEdge(edge)) {
                        continue loop;
                    }
                }

                graph.addBidirectedEdge(node1, node2);
            }
        }
    }

    private static void uncorrelateExogenousVariables(GraphWorkbench workbench) {
        Graph graph = workbench.getGraph();

        Set<Edge> edges = graph.getEdges();

        for (Edge edge : edges) {
            if (Edges.isBidirectedEdge(edge)) {
                try {
                    graph.removeEdge(edge);
                } catch (Exception e) {
                    // Ignore.
                }
            }
        }
    }

    public static @NotNull JMenu addPagColoringItems(GraphWorkbench workbench) {
        JMenu pagColoring = new JMenu("PAG Coloring");
        pagColoring.add(new PagColorer(workbench));
        pagColoring.add(new PagEdgeTypeInstructions());
        return pagColoring;
    }

    /**
     * Returns the JScrollPane containing the given component, or null if no such JScrollPane exists.
     *
     * @param component the component to search for a containing JScrollPane
     * @return the JScrollPane containing the given component, or null if no such JScrollPane exists
     */
    public static JScrollPane getContainingScrollPane(Component component) {
        while (component != null && !(component instanceof JScrollPane)) {
            component = component.getParent();
        }
        return (JScrollPane) component;
    }
}
