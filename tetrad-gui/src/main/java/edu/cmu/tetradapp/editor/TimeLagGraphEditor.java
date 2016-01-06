///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.PointXy;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.model.TimeLagGraphWrapper;
import edu.cmu.tetradapp.util.CopyLayoutAction;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;


/**
 * Displays a workbench editing workbench area together with a toolbench for
 * editing tetrad-style graphs.
 *
 * @author Aaron Powers
 * @author Joseph Ramsey
 */
public final class TimeLagGraphEditor extends JPanel
        implements GraphEditable, LayoutEditable, IndTestProducer {
    private final TimeLagGraphWorkbench workbench;
    private TimeLagGraphWrapper graphWrapper;
    private LayoutEditable layoutEditable;
    private CopyLayoutAction copyLayoutAction;

    //===========================PUBLIC METHODS========================//

    public TimeLagGraphEditor(TimeLagGraphWrapper graphWrapper) {
        this((TimeLagGraph) graphWrapper.getGraph());
        this.graphWrapper = graphWrapper;
        this.layoutEditable = this;

        getWorkbench().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("graph".equals(evt.getPropertyName())) {
                    getGraphWrapper().setGraph((TimeLagGraph) evt.getNewValue());
                } else if ("modelChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });
    }

    //===========================PRIVATE METHODS======================//

    /**
     * Constructs a new GraphEditor for the given EdgeListGraph.
     */
    public TimeLagGraphEditor(TimeLagGraph graph) {
        setLayout(new BorderLayout());

        this.workbench = new TimeLagGraphWorkbench(graph);
        DagGraphToolbar toolbar = new DagGraphToolbar(getWorkbench());
        JMenuBar menuBar = createGraphMenuBar();
        JScrollPane scroll = new JScrollPane(getWorkbench());
        scroll.setPreferredSize(new Dimension(450, 450));

        add(scroll, BorderLayout.CENTER);
        add(toolbar, BorderLayout.WEST);
        add(menuBar, BorderLayout.NORTH);

        JLabel label = new JLabel("Double click variable to change name.");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        Box b = Box.createHorizontalBox();
        b.add(Box.createHorizontalStrut(2));
        b.add(label);
        b.add(Box.createHorizontalGlue());
        b.setBorder(new MatteBorder(0, 0, 1, 0, Color.GRAY));

        add(b, BorderLayout.SOUTH);

        this.getWorkbench().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();

                if ("graph".equals(propertyName)) {
                    TimeLagGraph _graph = (TimeLagGraph) evt.getNewValue();

                    if (getWorkbench() != null) {
                        getGraphWrapper().setGraph(_graph);
                    }
                }
            }
        });
    }

    /**
     * Sets the name of this editor.
     */
    public final void setName(String name) {
        String oldName = getName();
        super.setName(name);
        firePropertyChange("name", oldName, getName());
    }

    /**
     * @return a list of all the SessionNodeWrappers (TetradNodes) and
     * SessionNodeEdges that are model components for the respective
     * SessionNodes and SessionEdges selected in the workbench. Note that the
     * workbench, not the SessionEditorNodes themselves, keeps track of the
     * selection.
     */
    public List getSelectedModelComponents() {
        List<Component> selectedComponents =
                getWorkbench().getSelectedComponents();
        List<TetradSerializable> selectedModelComponents =
                new ArrayList<TetradSerializable>();

        for (Iterator<Component> it =
             selectedComponents.iterator(); it.hasNext(); ) {
            Object comp = it.next();

            if (comp instanceof DisplayNode) {
                selectedModelComponents.add(
                        ((DisplayNode) comp).getModelNode());
            } else if (comp instanceof DisplayEdge) {
                selectedModelComponents.add(
                        ((DisplayEdge) comp).getModelEdge());
            }
        }

        return selectedModelComponents;
    }

    /**
     * Pastes list of session elements into the workbench.
     */
    public void pasteSubsession(List sessionElements, Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        for (int i = 0; i < sessionElements.size(); i++) {

            Object o = sessionElements.get(i);

            if (o instanceof GraphNode) {
                Node modelNode = (Node) o;
                getWorkbench().selectNode(modelNode);
            }
        }

        getWorkbench().selectConnectingEdges();
    }

    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    public Graph getGraph() {
        return getWorkbench().getGraph();
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return getWorkbench().getModelEdgesToDisplay();
    }

    public Map getModelNodesToDisplay() {
        return getWorkbench().getModelNodesToDisplay();
    }

    public void setGraph(Graph graph) {
        getWorkbench().setGraph(graph);
    }

    public IKnowledge getKnowledge() {
        return null;
    }

    public Graph getSourceGraph() {
        return getWorkbench().getGraph();
    }

    public void layoutByGraph(Graph graph) {
        getWorkbench().layoutByGraph(graph);
    }

    public void layoutByKnowledge() {
        // Does nothing.
    }

    public Rectangle getVisibleRect() {
        return getWorkbench().getVisibleRect();
    }

    private TimeLagGraphWrapper getGraphWrapper() {
        return graphWrapper;
    }

    //===========================PRIVATE METHODS========================//

    private JMenuBar createGraphMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new GraphFileMenu(this, getWorkbench());
        JMenu editMenu = createEditMenu();
        JMenu graphMenu = createGraphMenu();

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(graphMenu);
        menuBar.add(new LayoutMenu(this));

        return menuBar;
    }

//    /**
//     * Creates the "file" menu, which allows the user to load, save, and post
//     * workbench models.
//     *
//     * @return this menu.
//     */
//    private JMenu createFileMenu() {
//        JMenu file = new JMenu("File");
//
//        file.add(new LoadGraph(this, "Load Graph..."));
//        file.add(new SaveGraph(this, "Save Graph..."));
////        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
//        file.add(new SaveComponentImage(getWorkbench(), "Save Graph Image..."));
//
//        return file;
//    }

    /**
     * Creates the "file" menu, which allows the user to load, save, and post
     * workbench models.
     *
     * @return this menu.
     */
    private JMenu createEditMenu() {
        JMenu edit = new JMenu("Edit");

//        JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
//        JMenuItem paste = new JMenuItem(new PasteSubgraphAction(this));
//
//        copy.setAccelerator(
//                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
//        paste.setAccelerator(
//                KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));
//
//        edit.add(copy);
//        edit.add(paste);

        edit.addSeparator();

        JMenuItem configuration = new JMenuItem("Configuration...");
        edit.add(configuration);

        configuration.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final TimeLagGraph graph = (TimeLagGraph) getLayoutEditable().getGraph();


                class ConfigurationEditor extends JPanel {
                    private int maxLag;
                    private int numInitialLags;

                    public ConfigurationEditor(final TimeLagGraph graph) {
                        maxLag = graph.getMaxLag();
                        numInitialLags = graph.getNumInitialLags();

                        final SpinnerModel maxLagSpinnerModel = new SpinnerNumberModel(graph.getMaxLag(), 0, 300, 1);
                        JSpinner maxLagSpinner = new JSpinner(maxLagSpinnerModel);

                        maxLagSpinner.addChangeListener(new ChangeListener() {
                            public void stateChanged(ChangeEvent e) {
                                JSpinner spinner = (JSpinner) e.getSource();
                                SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                                int value = (Integer) model.getValue();
                                setMaxLag(value);
                            }
                        });

                        final SpinnerModel initialLagsSpinnerModel = new SpinnerNumberModel(graph.getNumInitialLags(), 1, 300, 1);
                        JSpinner initialLagsSpinner = new JSpinner(initialLagsSpinnerModel);

                        initialLagsSpinner.addChangeListener(new ChangeListener() {
                            public void stateChanged(ChangeEvent e) {
                                JSpinner spinner = (JSpinner) e.getSource();
                                SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                                int value = (Integer) model.getValue();
                                setNumInitialLags(value);
                            }
                        });

                        setLayout(new BorderLayout());

                        Box box = Box.createVerticalBox();

                        Box b1 = Box.createHorizontalBox();
                        b1.add(new JLabel("Time lag graph configuration:"));
                        b1.add(Box.createHorizontalGlue());
                        box.add(b1);

                        Box b2 = Box.createHorizontalBox();
                        b2.add(new JLabel("Maximum Lag = "));
                        b2.add(Box.createHorizontalGlue());
                        b2.add(maxLagSpinner);
                        box.add(b2);

                        Box b3 = Box.createHorizontalBox();
                        b3.add(new JLabel("# Initial Lags = "));
                        b3.add(Box.createHorizontalGlue());
                        b3.add(initialLagsSpinner);
                        box.add(b3);

                        box.setBorder(new EmptyBorder(10, 10, 10, 10));

                        add(box, BorderLayout.CENTER);

                    }

                    public int getMaxLag() {
                        return maxLag;
                    }

                    public void setMaxLag(int maxLag) {
                        this.maxLag = maxLag;
                    }

                    public int getNumInitialLags() {
                        return numInitialLags;
                    }

                    public void setNumInitialLags(int numInitialLags) {
                        this.numInitialLags = numInitialLags;
                    }
                }

                final ConfigurationEditor editor = new ConfigurationEditor((TimeLagGraph) getGraph());


                EditorWindow editorWindow =
                        new EditorWindow(editor, "Configuration...", "Save", true, TimeLagGraphEditor.this);

                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                editorWindow.setVisible(true);


                editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                    public void internalFrameClosed(InternalFrameEvent e) {
                        EditorWindow window = (EditorWindow) e.getSource();

                        if (window.isCanceled()) {
                            return;
                        }

                        graph.setMaxLag(editor.getMaxLag());
                        graph.setNumInitialLags(editor.getNumInitialLags());

                        LayoutUtils.lastLayout(getLayoutEditable());

                    }
                });
            }
        });


        return edit;
    }

    private JMenu createGraphMenu() {
        JMenu graph = new JMenu("Graph");

        graph.add(new GraphPropertiesAction(getWorkbench()));
        graph.add(new PathsAction(getWorkbench()));
//        graph.add(new DirectedPathsAction(getWorkbench()));
//        graph.add(new TreksAction(getWorkbench()));
//        graph.add(new AllPathsAction(getWorkbench()));
//        graph.add(new NeighborhoodsAction(getWorkbench()));
        graph.addSeparator();

        JMenuItem correlateExogenous =
                new JMenuItem("Correlate Exogenous Variables");
        JMenuItem uncorrelateExogenous =
                new JMenuItem("Uncorrelate Exogenous Variables");
        graph.add(correlateExogenous);
        graph.add(uncorrelateExogenous);
        graph.addSeparator();

        correlateExogenous.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                correlateExogenousVariables();
                getWorkbench().invalidate();
                getWorkbench().repaint();
            }
        });

        uncorrelateExogenous.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uncorrelationExogenousVariables();
                getWorkbench().invalidate();
                getWorkbench().repaint();
            }
        });

        JMenuItem randomGraph = new JMenuItem("Random Graph");
        graph.add(randomGraph);

        randomGraph.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                RandomGraphEditor editor = new RandomGraphEditor(workbench.getGraph(), true);

                int ret = JOptionPane.showConfirmDialog(
                        TimeLagGraphEditor.this, editor,
                        "Edit Random DAG Parameters",
                        JOptionPane.PLAIN_MESSAGE);

                if (ret == JOptionPane.OK_OPTION) {
                    Graph graph = null;
                    Graph dag = new Dag();
                    int numTrials = 0;

                    while (graph == null && ++numTrials < 100) {

                        if (editor.isRandomForward()) {
                            dag = GraphUtils.randomGraphRandomForwardEdges(getGraph().getNodes(), editor.getNumLatents(), editor.getMaxEdges(), 30, 15, 15, false);
                            GraphUtils.arrangeBySourceGraph(dag, getWorkbench().getGraph());
                            HashMap<String, PointXy> layout = GraphUtils.grabLayout(workbench.getGraph().getNodes());
                            GraphUtils.arrangeByLayout(dag, layout);
                        } else if (editor.isUniformlySelected()) {
                            if (getGraph().getNumNodes() == editor.getNumNodes()) {
                                HashMap<String, PointXy> layout = GraphUtils.grabLayout(workbench.getGraph().getNodes());

                                dag = GraphUtils.randomGraph(getGraph().getNodes(), editor.getNumLatents(), editor.getMaxEdges(), editor.getMaxDegree(), editor.getMaxIndegree(), editor.getMaxOutdegree(), editor.isConnected());
                                GraphUtils.arrangeBySourceGraph(dag, getWorkbench().getGraph());

                                GraphUtils.arrangeByLayout(dag, layout);
                            } else {
                                List<Node> nodes = new ArrayList<Node>();

                                for (int i = 0; i < editor.getNumNodes(); i++) {
                                    nodes.add(new ContinuousVariable("X" + (i + 1)));
                                }

                                dag = GraphUtils.randomGraph(nodes, editor.getNumLatents(), editor.getMaxEdges(),
                                        editor.getMaxDegree(), editor.getMaxIndegree(), editor.getMaxOutdegree(), editor.isConnected());
                            }
                        } else {
                            do {
                                if (getGraph().getNumNodes() == editor.getNumNodes()) {
                                    HashMap<String, PointXy> layout = GraphUtils.grabLayout(workbench.getGraph().getNodes());

                                    dag = GraphUtils.randomDag(getGraph().getNodes(),
                                            editor.getNumLatents(), editor.getMaxEdges(),
                                            30, 15, 15, editor.isConnected()
                                    );


                                    GraphUtils.arrangeByLayout(dag, layout);
                                } else {
                                    List<Node> nodes = new ArrayList<Node>();

                                    for (int i = 0; i < editor.getNumNodes(); i++) {
                                        nodes.add(new ContinuousVariable("X" + (i + 1)));
                                    }

                                    dag = GraphUtils.randomGraph(nodes, editor.getNumLatents(), editor.getMaxEdges(),
                                            30, 15, 15, editor.isConnected());
                                }
                            } while (dag.getNumEdges() < editor.getMaxEdges());
                        }

                        boolean addCycles = editor.isAddCycles();

                        if (addCycles) {
                            int minNumCycles = editor.getMinNumCycles();
                            int minCycleLength = editor.getMinCycleLength();

//                            graph = DataGraphUtils.addCycles2(dag, minNumCycles, minCycleLength);

                            graph = GraphUtils.cyclicGraph2(editor.getNumNodes(), editor.getMaxEdges());
                            GraphUtils.addTwoCycles(graph, editor.getMinNumCycles());
                        } else {
                            graph = new EdgeListGraph(dag);
                        }
                    }

                    if (graph == null) {
                        JOptionPane.showMessageDialog(TimeLagGraphEditor.this,
                                "Could not find a graph that fits those constrains.");
                        getWorkbench().setGraph(new EdgeListGraph(dag));
                    } else {
                        getWorkbench().setGraph(graph);
                    }

//                    getWorkbench().setGraph(new EdgeListGraph(dag));
//                    getWorkbench().setGraph(graph);
                }
            }
        });

        JMenuItem randomIndicatorModel =
                new JMenuItem("Random Multiple Indicator Model");
        graph.add(randomIndicatorModel);

        randomIndicatorModel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                RandomMimParamsEditor editor = new RandomMimParamsEditor();

                int ret = JOptionPane.showConfirmDialog(
                        JOptionUtils.centeringComp(), editor,
                        "Edit Random MIM Parameters",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);

                if (ret == JOptionPane.OK_OPTION) {
                    int numFactors = Preferences.userRoot().getInt(
                            "randomMimNumFactors", 1);
                    int numStructuralNodes = Preferences.userRoot().getInt(
                            "numStructuralNodes", 3);
                    int maxStructuralEdges = Preferences.userRoot().getInt(
                            "numStructuralEdges", 3);
                    int measurementModelDegree = Preferences.userRoot().getInt(
                            "measurementModelDegree", 3);
                    int numLatentMeasuredImpureParents = Preferences.userRoot()
                            .getInt("latentMeasuredImpureParents", 0);
                    int numMeasuredMeasuredImpureParents =
                            Preferences.userRoot()
                                    .getInt("measuredMeasuredImpureParents", 0);
                    int numMeasuredMeasuredImpureAssociations =
                            Preferences.userRoot()
                                    .getInt("measuredMeasuredImpureAssociations",
                                            0);

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

                    getWorkbench().setGraph(graph);
                }
            }
        });


        graph.addSeparator();
        graph.add(new JMenuItem(new SelectBidirectedAction(getWorkbench())));
        graph.add(new JMenuItem(new SelectUndirectedAction(getWorkbench())));

//        graph.addSeparator();
//        IndependenceFactsAction action = new IndependenceFactsAction(
//                JOptionUtils.centeringComp(), this, "D Separation Facts...");
//        graph.add(action);

        return graph;
    }

    private void correlateExogenousVariables() {
        Graph graph = getWorkbench().getGraph();

        if (graph instanceof Dag) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Cannot add bidirected edges to DAG's.");
            return;
        }

        List<Node> nodes = graph.getNodes();

        List<Node> exoNodes = new LinkedList<Node>();

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
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

                for (int k = 0; k < edges.size(); k++) {
                    Edge edge = edges.get(k);
                    if (Edges.isBidirectedEdge(edge)) {
                        continue loop;
                    }
                }

                graph.addBidirectedEdge(node1, node2);
            }
        }
    }

    private void uncorrelationExogenousVariables() {
        Graph graph = getWorkbench().getGraph();

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

    public IndependenceTest getIndependenceTest() {
        Graph graph = getWorkbench().getGraph();
        EdgeListGraph listGraph = new EdgeListGraph(graph);
        return new IndTestDSep(listGraph);
    }

//    private JMenu getLayoutMenu() {
//        JMenu Layout = new JMenu("Layout");
//        this.layoutEditable = this;
//
//        JMenuItem topToBottom = new JMenuItem("Rectangular array");
//        Layout.add(topToBottom);
//
//        topToBottom.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                LayoutUtils.topToBottomLayout(getLayoutEditable());
//
//                // Copy the laid out graph to the clipboard.
//                getCopyLayoutAction().actionPerformed(null);
//            }
//        });
//
//        JMenuItem likeLag0 = new JMenuItem("Copy lag 0");
//        Layout.add(likeLag0);
//
//        likeLag0.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                LayoutUtils.copyLag0LayoutTopToBottom(getLayoutEditable());
//
//                // Copy the laid out graph to the clipboard.
//                getCopyLayoutAction().actionPerformed(null);
//            }
//        });
//
//
//        JMenuItem circleLayout = new JMenuItem("Circle");
//        Layout.add(circleLayout);
//
//        circleLayout.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                LayoutUtils.circleLayout(getLayoutEditable());
//
//                // Copy the laid out graph to the clipboard.
//                getCopyLayoutAction().actionPerformed(null);
//            }
//
//        });
//
//        if (getLayoutEditable().getKnowledge() != null) {
//            JMenuItem knowledgeTiersLayout = new JMenuItem("Knowledge Tiers");
//            Layout.add(knowledgeTiersLayout);
//
//            knowledgeTiersLayout.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    LayoutUtils.knowledgeLayout(getLayoutEditable());
//
//                    // Copy the laid out graph to the clipboard.
//                    getCopyLayoutAction().actionPerformed(null);
//                }
//            });
//        }
//
//        if (getLayoutEditable().getSourceGraph() != null) {
//            JMenuItem lastResultLayout = new JMenuItem("Source Graph");
//            add(lastResultLayout);
//
//            lastResultLayout.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    LayoutUtils.sourceGraphLayout(getLayoutEditable());
//
//                    // Copy the laid out graph to the clipboard.
//                    getCopyLayoutAction().actionPerformed(null);
//                }
//            });
//        }
//
//        JMenuItem layeredDrawing = new JMenuItem("Layered Drawing");
//        Layout.add(layeredDrawing);
//
//        layeredDrawing.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                LayoutEditable layoutEditable = getLayoutEditable();
//                LayoutUtils.layeredDrawingLayout(layoutEditable);
//
//                // Copy the laid out graph to the clipboard.
//                getCopyLayoutAction().actionPerformed(null);
//            }
//        });
//
//
//        JMenuItem fruchtermanReingold = new JMenuItem("Fruchterman-Reingold");
//        Layout.add(fruchtermanReingold);
//
//        fruchtermanReingold.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                Graph graph = getLayoutEditable().getGraph();
//
//                for (Node node : new ArrayList<Node>(graph.getNodes())) {
//                    if (node.getNodeType() == NodeType.ERROR) {
//                        graph.removeNode(node);
//                    }
//                }
//
//                DataGraphUtils.fruchtermanReingoldLayout(graph);
//                getLayoutEditable().layoutByGraph(graph);
//
//            }
//        });
//
//        JMenuItem kamadaKawai = new JMenuItem("Kamada-Kawai");
//        Layout.add(kamadaKawai);
//
//        kamadaKawai.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                final LayoutEditable layoutEditable = getLayoutEditable();
//                LayoutUtils.kamadaKawaiLayout(layoutEditable);
//
//                // Copy the laid out graph to the clipboard.
//                getCopyLayoutAction().actionPerformed(null);
//            }
//        });
//
//        Layout.addSeparator();
//
//        copyLayoutAction = new CopyLayoutAction(getLayoutEditable());
//        Layout.add(getCopyLayoutAction());
//        Layout.add(new PasteLayoutAction(getLayoutEditable()));
//
//        return Layout;
//    }


    private LayoutEditable getLayoutEditable() {
        return layoutEditable;
    }

    private CopyLayoutAction getCopyLayoutAction() {
        return copyLayoutAction;
    }
}


