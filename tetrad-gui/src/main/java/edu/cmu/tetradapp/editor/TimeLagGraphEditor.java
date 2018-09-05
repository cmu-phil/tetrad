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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataGraphUtils;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.PointXy;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.model.TimeLagGraphWrapper;
import edu.cmu.tetradapp.util.BootstrapTable;
import edu.cmu.tetradapp.util.CopyLayoutAction;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.*;
import java.util.prefs.Preferences;
import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

/**
 * Displays a workbench editing workbench area together with a toolbench for
 * editing tetrad-style graphs.
 *
 * @author Aaron Powers
 * @author Joseph Ramsey
 * @author Zhou Yuan
 */
public final class TimeLagGraphEditor extends JPanel
        implements GraphEditable, LayoutEditable, IndTestProducer {

    private static final long serialVersionUID = -2425361202348129265L;

    private TimeLagGraphWorkbench workbench;
    private TimeLagGraphWrapper timeLagGraphWrapper;
    private LayoutEditable layoutEditable;
    private CopyLayoutAction copyLayoutAction;
    private Parameters parameters;
    
    private JScrollPane graphEditorScroll = new JScrollPane();
    private Box tablePaneBox;

    //===========================CONSTRUCTOR========================//
    public TimeLagGraphEditor(TimeLagGraphWrapper timeLagGraphWrapper) {
        setLayout(new BorderLayout());
        
        this.timeLagGraphWrapper = timeLagGraphWrapper;
        this.layoutEditable = this;
        this.parameters = timeLagGraphWrapper.getParameters();

        initUI(timeLagGraphWrapper);
    }

    
    //===========================PUBLIC METHODS========================//
    /**
     * Sets the name of this editor.
     */
    @Override
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
    @Override
    public List getSelectedModelComponents() {
        List<Component> selectedComponents
                = getWorkbench().getSelectedComponents();
        List<TetradSerializable> selectedModelComponents
                = new ArrayList<>();

        for (Iterator<Component> it
                = selectedComponents.iterator(); it.hasNext();) {
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
    @Override
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

    @Override
    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    @Override
    public Graph getGraph() {
        return getWorkbench().getGraph();
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return getWorkbench().getModelEdgesToDisplay();
    }

    @Override
    public Map getModelNodesToDisplay() {
        return getWorkbench().getModelNodesToDisplay();
    }

    @Override
    public void setGraph(Graph graph) {
        getWorkbench().setGraph(graph);
    }

    @Override
    public IKnowledge getKnowledge() {
        return null;
    }

    @Override
    public Graph getSourceGraph() {
        return getWorkbench().getGraph();
    }

    @Override
    public void layoutByGraph(Graph graph) {
        getWorkbench().layoutByGraph(graph);
    }

    @Override
    public void layoutByKnowledge() {
        // Does nothing.
    }

    @Override
    public Rectangle getVisibleRect() {
        return getWorkbench().getVisibleRect();
    }

    //===========================PRIVATE METHODS======================//
    private void initUI(TimeLagGraphWrapper timeLagGraphWrapper) {
        TimeLagGraph graph = (TimeLagGraph) timeLagGraphWrapper.getGraph();
        
        workbench = new TimeLagGraphWorkbench(graph);
        
        workbench.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            String propertyName = evt.getPropertyName();
            
            // Update the bootstrap table if there's changes to the edges or node renaming
            String[] events = { "graph", "edgeAdded", "edgeRemoved" };
            
            if (Arrays.asList(events).contains(propertyName)) {
                if (getWorkbench() != null) {
                    TimeLagGraph targetGraph = (TimeLagGraph) getWorkbench().getGraph();
                    
                    // Update the timeLagGraphWrapper
                    timeLagGraphWrapper.setGraph(targetGraph);
                    // Also need to update the UI
                    updateBootstrapTable(targetGraph);
                }
            } else if ("modelChanged".equals(propertyName)) {
                firePropertyChange("modelChanged", null, null);
            }
        });
        
        // Graph menu at the very top of the window
        JMenuBar menuBar = createGraphMenuBar();
   
        // topBox Left side toolbar
        DagGraphToolbar graphToolbar = new DagGraphToolbar(getWorkbench());
        graphToolbar.setMaximumSize(new Dimension(140, 450));
        
        // topBox right side graph editor
        graphEditorScroll.setPreferredSize(new Dimension(760, 450));
        graphEditorScroll.setViewportView(workbench);

        // topBox contains the topGraphBox and the instructionBox underneath
        Box topBox = Box.createVerticalBox();
        topBox.setPreferredSize(new Dimension(820, 400));
        
        // topGraphBox contains the vertical graph toolbar and graph editor
        Box topGraphBox = Box.createHorizontalBox();
        topGraphBox.add(graphToolbar);
        topGraphBox.add(graphEditorScroll);

        // Instruction with info button 
        Box instructionBox = Box.createHorizontalBox();
        instructionBox.setMaximumSize(new Dimension(820, 40));
        
        JLabel label = new JLabel("Double click variable/node rectangle to change name. More information on graph edge types");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Info button added by Zhou to show edge types
        JButton infoBtn = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        infoBtn.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Clock info button to show edge types instructions - Zhou
        infoBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Initialize helpSet
                String helpHS = "/resources/javahelp/TetradHelp.hs";

                try {
                    URL url = this.getClass().getResource(helpHS);
                    HelpSet helpSet = new HelpSet(null, url);

                    helpSet.setHomeID("graph_edge_types");
                    HelpBroker broker = helpSet.createHelpBroker();
                    ActionListener listener = new CSH.DisplayHelpFromSource(broker);
                    listener.actionPerformed(e);
                } catch (Exception ee) {
                    System.out.println("HelpSet " + ee.getMessage());
                    System.out.println("HelpSet " + helpHS + " not found");
                    throw new IllegalArgumentException();
                }
            }
        });

        instructionBox.add(label);
        instructionBox.add(Box.createHorizontalStrut(2));
        instructionBox.add(infoBtn);
        
        // Add to topBox
        topBox.add(topGraphBox);
        topBox.add(instructionBox);

        // bottomBox contains bootstrap table
        Box bottomBox = Box.createVerticalBox();
        bottomBox.setPreferredSize(new Dimension(750, 150));

        bottomBox.add(Box.createVerticalStrut(5));
        
        // Put the table title label in a box so it can be centered
        Box tableTitleBox = Box.createHorizontalBox();
        JLabel tableTitle = new JLabel("Edges and Edge Type Probabilities");
        tableTitleBox.add(tableTitle);
        
        bottomBox.add(tableTitleBox);
        
        bottomBox.add(Box.createVerticalStrut(5));
        
        // Table box contains the table pane
        tablePaneBox = Box.createHorizontalBox();
        JScrollPane tablePane = BootstrapTable.renderBootstrapTable(graph);
        tablePaneBox.add(tablePane);
        
        bottomBox.add(tablePaneBox);
        
        // Use JSplitPane to allow resize the bottom box - Zhou
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        
        // Set the top and bottom split panes
        splitPane.setTopComponent(topBox);
        splitPane.setBottomComponent(bottomBox);
        
        
        // Add to parent container
        add(menuBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        
        // Performs relayout. 
        // It means invalid content is asked for all the sizes and 
        // all the subcomponents' sizes are set to proper values by LayoutManager.
        validate();
    }
    
    /**
     * Updates bootstrap table on adding/removing edges or graph changes
     * 
     * @param graph 
     */
    private void updateBootstrapTable(Graph graph) {
        tablePaneBox.removeAll();
        JScrollPane tablePane = BootstrapTable.renderBootstrapTable(graph);
        tablePaneBox.add(tablePane);
        
        validate();
    }

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

//                        Box b3 = Box.createHorizontalBox();
//                        b3.add(new JLabel("# Initial Lags = "));
//                        b3.add(Box.createHorizontalGlue());
//                        b3.add(initialLagsSpinner);
//                        box.add(b3);

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

                EditorWindow editorWindow
                        = new EditorWindow(editor, "Configuration...", "Save", true, TimeLagGraphEditor.this);

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

        JMenuItem correlateExogenous
                = new JMenuItem("Correlate Exogenous Variables");
        JMenuItem uncorrelateExogenous
                = new JMenuItem("Uncorrelate Exogenous Variables");
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
                RandomGraphEditor editor = new RandomGraphEditor(workbench.getGraph(), true, parameters);

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
                            dag = GraphUtils.randomGraphRandomForwardEdges(getGraph().getNodes(), editor.getNumLatents(), editor.getMaxEdges(), 30, 15, 15, false, true);
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
                                List<Node> nodes = new ArrayList<>();

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
                                    List<Node> nodes = new ArrayList<>();

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
                            graph = GraphUtils.cyclicGraph2(editor.getNumNodes(), editor.getMaxEdges(), 8);
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

        JMenuItem randomIndicatorModel
                = new JMenuItem("Random Multiple Indicator Model");
        graph.add(randomIndicatorModel);

        randomIndicatorModel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                RandomMimParamsEditor editor = new RandomMimParamsEditor(parameters);

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
                    int numMeasuredMeasuredImpureParents
                            = Preferences.userRoot()
                            .getInt("measuredMeasuredImpureParents", 0);
                    int numMeasuredMeasuredImpureAssociations
                            = Preferences.userRoot()
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
                        throw new IllegalArgumentException("Can only make random MIMs for 1 or 2 factors, "
                                + "sorry dude.");
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

        List<Node> exoNodes = new LinkedList<>();

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

    @Override
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
