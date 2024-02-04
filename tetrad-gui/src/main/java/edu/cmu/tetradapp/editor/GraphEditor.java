///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;

import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.List;
import java.util.*;

/**
 * Displays a workbench editing workbench area together with a toolbench for editing tetrad-style graphs.
 *
 * @author Aaron Powers
 * @author josephramsey
 * @author Zhou Yuan 8/22/2018
 */
public final class GraphEditor extends JPanel implements GraphEditable, LayoutEditable, IndTestProducer {

    private static final long serialVersionUID = 5123725895449927539L;

    private static final Set<String> EVENTS = new HashSet<>(Arrays.asList(
            "graph",
            "edgeAdded",
            "edgeRemoved",
            "nodeRemoved"
    ));
    private final Parameters parameters;
    private final JScrollPane graphEditorScroll = new JScrollPane();
    private final EdgeTypeTable edgeTypeTable;
    private GraphWorkbench workbench;
    /**
     * Flag to indicate if interventional variables are in the graph - Zhou
     */
    private boolean hasInterventional;

    private boolean enableEditing = true;

    //===========================CONSTRUCTOR========================//
    public GraphEditor(GraphWrapper graphWrapper) {
        // Check if this graph has interventional nodes - Zhou
        boolean result = graphWrapper.getGraph().getNodes().stream()
                .anyMatch(e -> (e.getNodeVariableType() == NodeVariableType.INTERVENTION_STATUS || e.getNodeVariableType() == NodeVariableType.INTERVENTION_VALUE));
        setHasInterventional(result);

        setLayout(new BorderLayout());

        this.parameters = graphWrapper.getParameters();
        this.edgeTypeTable = new EdgeTypeTable();

        initUI(graphWrapper);
    }

    //===========================PUBLIC METHODS======================//

    /**
     * Sets the name of this editor.
     */
    @Override
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        firePropertyChange("name", oldName, getName());
    }

    /**
     * @return a list of all the SessionNodeWrappers (TetradNodes) and SessionNodeEdges that are model components for
     * the respective SessionNodes and SessionEdges selected in the workbench. Note that the workbench, not the
     * SessionEditorNodes themselves, keeps track of the selection.
     */
    @Override
    public List getSelectedModelComponents() {
        List<Component> selectedComponents
                = getWorkbench().getSelectedComponents();
        List<TetradSerializable> selectedModelComponents
                = new ArrayList<>();

        for (Component comp : selectedComponents) {
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
    public void pasteSubsession(List<Object> sessionElements, Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        sessionElements.forEach(o -> {
            if (o instanceof GraphNode) {
                Node modelNode = (Node) o;
                getWorkbench().selectNode(modelNode);
            }
        });

        getWorkbench().selectConnectingEdges();
    }

    @Override
    public GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    @Override
    public Graph getGraph() {
        return getWorkbench().getGraph();
    }

    @Override
    public void setGraph(Graph graph) {
        getWorkbench().setGraph(graph);
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
    public Knowledge getKnowledge() {
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

    //===========================PRIVATE METHODS========================//
    private void initUI(GraphWrapper graphWrapper) {
        Graph graph = graphWrapper.getGraph();

        this.workbench = new GraphWorkbench(graph);
        this.workbench.enableEditing(this.enableEditing);

        this.workbench.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            String propertyName = evt.getPropertyName();
            if (GraphEditor.EVENTS.contains(propertyName)) {
                if (getWorkbench() != null) {
                    Graph targetGraph = getWorkbench().getGraph();

                    // Update the graphWrapper
                    graphWrapper.setGraph(targetGraph);
                    // Also need to update the UI
//                    updateBootstrapTable(targetGraph);
                }
            } else if ("modelChanged".equals(propertyName)) {
                firePropertyChange("modelChanged", null, null);
            }
        });

        // Graph menu at the very top of the window
        JMenuBar menuBar = createGraphMenuBar();

        // Add the model selection to top if multiple models
        modelSelection(graphWrapper);

        // topBox Left side toolbar
        GraphToolbar graphToolbar = new GraphToolbar(getWorkbench());
        graphToolbar.setMaximumSize(new Dimension(140, 450));

        // topBox right side graph editor
        this.graphEditorScroll.setPreferredSize(new Dimension(760, 450));
        this.graphEditorScroll.setViewportView(this.workbench);

        // topBox contains the topGraphBox and the instructionBox underneath
        Box topBox = Box.createVerticalBox();
        topBox.setPreferredSize(new Dimension(820, 400));

        // topGraphBox contains the vertical graph toolbar and graph editor
        Box topGraphBox = Box.createHorizontalBox();
        topGraphBox.add(graphToolbar);
        topGraphBox.add(this.graphEditorScroll);

        // Instruction with info button
        Box instructionBox = Box.createHorizontalBox();
        instructionBox.setMaximumSize(new Dimension(820, 40));

        JLabel label = new JLabel("Double click variable/node rectangle to change name. More information on graph edge types and colorings");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Info button added by Zhou to show edge types
        JButton infoBtn = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        infoBtn.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Clock info button to show edge types instructions - Zhou
        infoBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Initialize helpSet
                final String helpHS = "/docs/javahelp/TetradHelp.hs";

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

        this.edgeTypeTable.setPreferredSize(new Dimension(820, 150));

//        //Use JSplitPane to allow resize the bottom box - Zhou
//        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new PaddingPanel(topBox), new PaddingPanel(edgeTypeTable));
//        splitPane.setDividerLocation((int) (splitPane.getPreferredSize().getHeight() - 150));

        // Switching to tabbed pane because of resizing problems with the split pane... jdramsey 2021.08.25
        JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.RIGHT);
        tabbedPane.addTab("Graph", new PaddingPanel(topBox));
        tabbedPane.addTab("Edges", this.edgeTypeTable);

        // Add to parent container
        add(menuBar, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        this.edgeTypeTable.update(graph);

        // Performs relayout.
        // It means invalid content is asked for all the sizes and
        // all the subcomponents' sizes are set to proper values by LayoutManager.
        validate();
    }

    /**
     * Updates the graph in workbench when changing graph model
     */
    private void updateGraphWorkbench(Graph graph) {
        this.workbench = new GraphWorkbench(graph);
        this.workbench.enableEditing(this.enableEditing);
        this.graphEditorScroll.setViewportView(this.workbench);

        validate();
    }

    /**
     * Updates bootstrap table on adding/removing edges or graph changes
     */
    private void updateBootstrapTable(Graph graph) {
        this.edgeTypeTable.update(graph);

        validate();
    }

    /**
     * Creates the UI component for choosing from multiple graph models
     */
    private void modelSelection(GraphWrapper graphWrapper) {
        int numModels = graphWrapper.getNumModels();

        if (numModels > 1) {
            List<Integer> models = new ArrayList<>();
            for (int i = 0; i < numModels; i++) {
                models.add(i + 1);
            }

            JComboBox<Integer> comboBox = new JComboBox(models.toArray());

            // Remember the selected model on reopen
            comboBox.setSelectedIndex(graphWrapper.getModelIndex());

            comboBox.addActionListener((ActionEvent e) -> {
                graphWrapper.setModelIndex(comboBox.getSelectedIndex());

                // Update the graph workbench
                updateGraphWorkbench(graphWrapper.getGraph());

                // Update the bootstrap table
//                updateBootstrapTable(graphWrapper.getGraph());
            });

            // Put together
            Box modelSelectionBox = Box.createHorizontalBox();
            modelSelectionBox.add(new JLabel("Using model "));
            modelSelectionBox.add(comboBox);
            modelSelectionBox.add(new JLabel(" from "));
            modelSelectionBox.add(new JLabel(graphWrapper.getModelSourceName()));
            modelSelectionBox.add(Box.createHorizontalStrut(20));
            modelSelectionBox.add(Box.createHorizontalGlue());

            // Add to upper right
            add(modelSelectionBox, BorderLayout.EAST);
        }
    }

    public boolean isEnableEditing() {
        return this.enableEditing;
    }

    public void enableEditing(boolean enableEditing) {
        this.enableEditing = enableEditing;
        if (this.workbench != null) {
            this.workbench.enableEditing(enableEditing);
        }
    }

    private JMenuBar createGraphMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new GraphFileMenu(this, getWorkbench(), false);
        JMenu editMenu = createEditMenu();
        JMenu graphMenu = createGraphMenu();

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(graphMenu);
        menuBar.add(new LayoutMenu(this));

        return menuBar;
    }

    JMenuBar createGraphMenuBarNoEditing() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(new SaveComponentImage(this.workbench, "Save Graph Image..."));

        menuBar.add(file);

        JMenu graph = new JMenu("Graph");

        graph.add(new GraphPropertiesAction(this.workbench));
        graph.add(new PathsAction(this.workbench));
        graph.add(new UnderliningsAction(this.workbench));

        graph.add(new JMenuItem(new SelectDirectedAction(this.workbench)));
        graph.add(new JMenuItem(new SelectBidirectedAction(this.workbench)));
        graph.add(new JMenuItem(new SelectUndirectedAction(this.workbench)));
        graph.add(new JMenuItem(new SelectTrianglesAction(this.workbench)));
        graph.add(new JMenuItem(new SelectLatentsAction(this.workbench)));
//        graph.addSeparator();
        graph.add(new PagColorer(getWorkbench()));

        menuBar.add(graph);

        return menuBar;
    }


    /**
     * Creates the "file" menu, which allows the user to load, save, and post workbench models.
     *
     * @return this menu.
     */
    private JMenu createEditMenu() {
        JMenu edit = new JMenu("Edit");

        JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
        JMenuItem paste = new JMenuItem(new PasteSubgraphAction(this));

        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        paste.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));

        edit.add(copy);
        edit.add(paste);

        return edit;
    }

    private JMenu createGraphMenu() {
        JMenu graph = new JMenu("Graph");

        JMenuItem randomGraph = new JMenuItem("Random Graph");
        graph.add(randomGraph);

        graph.addSeparator();

        graph.add(new GraphPropertiesAction(getWorkbench()));
        graph.add(new PathsAction(getWorkbench()));
        graph.add(new UnderliningsAction(getWorkbench()));

        graph.addSeparator();

        JMenuItem correlateExogenous = new JMenuItem("Correlate Exogenous Variables");
        JMenuItem uncorrelateExogenous = new JMenuItem("Uncorrelate Exogenous Variables");
        graph.add(correlateExogenous);
        graph.add(uncorrelateExogenous);
        graph.addSeparator();

        correlateExogenous.addActionListener(e -> {
            correlateExogenousVariables();
            getWorkbench().invalidate();
            getWorkbench().repaint();
        });

        uncorrelateExogenous.addActionListener(e -> {
            uncorrelationExogenousVariables();
            getWorkbench().invalidate();
            getWorkbench().repaint();
        });


        randomGraph.addActionListener(e -> {
            GraphParamsEditor editor = new GraphParamsEditor();
            editor.setParams(this.parameters);

            EditorWindow editorWindow = new EditorWindow(editor, "Edit Random Graph Parameters",
                    "Done", true, this);

            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
            editorWindow.pack();
            editorWindow.setVisible(true);

            editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                @Override
                public void internalFrameClosed(InternalFrameEvent e1) {
                    EditorWindow window = (EditorWindow) e1.getSource();

                    if (window.isCanceled()) {
                        return;
                    }

//                    RandomUtil.getInstance().setSeed(new Date().getTime());
                    Graph graph1 = edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph(), GraphEditor.this.parameters);

                    boolean addCycles = GraphEditor.this.parameters.getBoolean("randomAddCycles", false);

                    if (addCycles) {
                        int newGraphNumMeasuredNodes = GraphEditor.this.parameters.getInt("newGraphNumMeasuredNodes", 10);
                        int newGraphNumEdges = GraphEditor.this.parameters.getInt("newGraphNumEdges", 10);
                        graph1 = RandomGraph.randomCyclicGraph2(newGraphNumMeasuredNodes, newGraphNumEdges, 8);
                    }

                    getWorkbench().setGraph(graph1);
                }
            });
        });

        graph.add(new JMenuItem(new SelectDirectedAction(getWorkbench())));
        graph.add(new JMenuItem(new SelectBidirectedAction(getWorkbench())));
        graph.add(new JMenuItem(new SelectUndirectedAction(getWorkbench())));
        graph.add(new JMenuItem(new SelectTrianglesAction(getWorkbench())));
        graph.add(new JMenuItem(new SelectLatentsAction(getWorkbench())));
        graph.add(new PagColorer(getWorkbench()));

        // Only show these menu options for graph that has interventional nodes - Zhou
        if (isHasInterventional()) {
            graph.add(new JMenuItem(new SelectInterventionalAction(getWorkbench())));
            graph.add(new JMenuItem(new HideShowInterventionalAction(getWorkbench())));
        }

        graph.addSeparator();
        graph.add(new JMenuItem(new HideShowNoConnectionNodesAction(getWorkbench())));

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
        return new MsepTest(listGraph);
    }

    public boolean isHasInterventional() {
        return this.hasInterventional;
    }

    public void setHasInterventional(boolean hasInterventional) {
        this.hasInterventional = hasInterventional;
    }

}
