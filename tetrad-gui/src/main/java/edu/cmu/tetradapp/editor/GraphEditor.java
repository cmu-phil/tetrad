/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.GraphUtils;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;

import javax.swing.*;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.io.Serial;
import java.util.*;
import java.util.List;

/**
 * Displays a workbench editing workbench area together with a toolbench for editing tetrad-style graphs.
 *
 * @author Aaron Powers
 * @author josephramsey
 * @author Zhou Yuan 8/22/2018
 * @version $Id: $Id
 */
public final class GraphEditor extends JPanel implements GraphEditable, LayoutEditable, IndTestProducer {

    @Serial
    private static final long serialVersionUID = 5123725895449927539L;

    private static final Set<String> EVENTS = new HashSet<>(Arrays.asList(
            "graph",
            "edgeAdded",
            "edgeRemoved",
            "nodeRemoved"
    ));

    /**
     * The parameters for the graph.
     */
    private final Parameters parameters;

    /**
     * The scroll pane for the graph editor.
     */
    private final JScrollPane graphEditorScroll = new JScrollPane();

    /**
     * The table for the edge types.
     */
    private final EdgeTypeTable edgeTypeTable;

    /**
     * The workbench for the graph.
     */
    private GraphWorkbench workbench;

    /**
     * Flag to indicate if interventional variables are in the graph - Zhou
     */
    private boolean hasInterventional;

    /**
     * Flag to indicate if editing is enabled.
     */
    private boolean enableEditing = true;

    //===========================CONSTRUCTOR========================//

    /**
     * <p>Constructor for GraphEditor.</p>
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     */
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
     * {@inheritDoc}
     * <p>
     * Sets the name of this editor.
     */
    @Override
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        firePropertyChange("name", oldName, getName());
    }

    /**
     * {@inheritDoc}
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
     * {@inheritDoc}
     * <p>
     * Pastes list of session elements into the workbench.
     */
    @Override
    public void pasteSubsession(List<Object> sessionElements, Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        sessionElements.forEach(o -> {
            if (o instanceof GraphNode modelNode) {
                getWorkbench().selectNode(modelNode);
            }
        });

        getWorkbench().selectConnectingEdges();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getGraph() {
        return getWorkbench().getGraph();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGraph(Graph graph) {
        getWorkbench().setGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map getModelEdgesToDisplay() {
        return getWorkbench().getModelEdgesToDisplay();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map getModelNodesToDisplay() {
        return getWorkbench().getModelNodesToDisplay();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Knowledge getKnowledge() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getSourceGraph() {
        return getWorkbench().getGraph();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void layoutByGraph(Graph graph) {
        getWorkbench().layoutByGraph(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void layoutByKnowledge() {
        // Does nothing.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Rectangle getVisibleRect() {
        return getWorkbench().getVisibleRect();
    }

    //===========================PRIVATE METHODS========================//
    private void initUI(GraphWrapper graphWrapper) {
        Graph graph = graphWrapper.getGraph();

        this.workbench = new GraphWorkbench(graph);
        this.workbench.setEnableEditing(this.enableEditing);

        this.workbench.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            String propertyName = evt.getPropertyName();
            if (GraphEditor.EVENTS.contains(propertyName)) {
                if (getWorkbench() != null) {
                    Graph targetGraph = getWorkbench().getGraph();

                    SwingUtilities.invokeLater(() -> {
                        graphWrapper.setGraph(targetGraph);
                        // Also need to update the UI
//                    updateBootstrapTable(targetGraph);
                    });
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
        this.graphEditorScroll.setPreferredSize(new Dimension(500, 500));
        this.graphEditorScroll.setViewportView(this.workbench);

        // topBox contains the topGraphBox and the instructionBox underneath
        Box topBox = Box.createVerticalBox();
        topBox.setPreferredSize(new Dimension(450, 400));

        // topGraphBox contains the vertical graph toolbar and graph editor
        Box topGraphBox = Box.createHorizontalBox();
        topGraphBox.add(graphToolbar);
        topGraphBox.add(this.graphEditorScroll);

        // Instruction with info button
        Box instructionBox = Box.createHorizontalBox();
        instructionBox.setMaximumSize(new Dimension(450, 40));

        JLabel label = new JLabel("Double click variable/node rectangle to change name.");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
//
        // Info button added by Zhou to show edge types
//        JButton infoBtn = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
//        infoBtn.setBorder(new EmptyBorder(0, 0, 0, 0));
//
//        // Clock info button to show edge types instructions - Zhou
//        infoBtn.addActionListener(new ActionListener() {
//            @Override
//            public void actionPerformed(ActionEvent e) {
//                // Initialize helpSet
//                final String helpHS = "/docs/javahelp/TetradHelp.hs";
//
//                try {
//                    URL url = this.getClass().getResource(helpHS);
//                    HelpSet helpSet = new HelpSet(null, url);
//
//                    helpSet.setHomeID("graph_edge_types");
//                    HelpBroker broker = helpSet.createHelpBroker();
//                    ActionListener listener = new CSH.DisplayHelpFromSource(broker);
//                    listener.actionPerformed(e);
//                } catch (Exception ee) {
//                    System.out.println("HelpSet " + ee.getMessage());
//                    System.out.println("HelpSet " + helpHS + " not found");
//                    throw new IllegalArgumentException();
//                }
//            }
//        });
//
        instructionBox.add(label);
//        instructionBox.add(Box.createHorizontalStrut(2));
//        instructionBox.add(infoBtn);

        // Add to topBox
        topBox.add(topGraphBox);
        topBox.add(instructionBox);

        this.edgeTypeTable.setPreferredSize(new Dimension(500, 150));

//        //Use JSplitPane to allow resize the bottom box - Zhou
//        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new PaddingPanel(topBox), new PaddingPanel(edgeTypeTable));
//        splitPane.setDividerLocation((int) (splitPane.getPreferredSize().getHeight() - 150));


        // Switching to tabbed pane because of resizing problems with the split pane... jdramsey 2021.08.25
        JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.RIGHT);
        tabbedPane.addTab("Graph", new PaddingPanel(topBox));

        Box edgeTableBox = Box.createVerticalBox();
        edgeTableBox.add(this.edgeTypeTable);
        edgeTableBox.add(new JLabel("Rows can be copy/pasted into Excel or text file"));

        tabbedPane.addTab("Edges", edgeTableBox);

        updateBootstrapTable(graph);
        this.edgeTypeTable.update(graph);

        tabbedPane.addChangeListener(e -> {
            if (tabbedPane.getSelectedIndex() == 1) { // "Edges" tab
                updateBootstrapTable(workbench.getGraph());
                this.edgeTypeTable.update(workbench.getGraph());
            }
        });

        // Add to parent container
        add(menuBar, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        // Performs relayout.
        // It means invalid content is asked for all the sizes and
        // all the subcomponents' sizes are set to proper values by LayoutManager.
        validate();
    }

    /**
     * Updates the graph in workbench when changing graph model
     */
    private void updateGraphWorkbench(Graph graph) {
        this.workbench.setGraph(graph);// = new GraphWorkbench(graph);
        this.workbench.setEnableEditing(this.enableEditing);
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
                updateBootstrapTable(graphWrapper.getGraph());
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

    /**
     * <p>isEnableEditing.</p>
     *
     * @return a boolean
     */
    public boolean isEnableEditing() {
        return this.enableEditing;
    }

    /**
     * <p>enableEditing.</p>
     *
     * @param enableEditing a boolean
     */
    public void setEnableEditing(boolean enableEditing) {
        this.enableEditing = enableEditing;
        if (this.workbench != null) {
            this.workbench.setEnableEditing(enableEditing);
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


    /**
     * Creates the "file" menu, which allows the user to load, save, and post workbench models.
     *
     * @return this menu.
     */
    private JMenu createEditMenu() {
        JMenu edit = new JMenu("Edit");

        JMenuItem cut = new JMenuItem(new CutSubgraphAction(this));
        JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
        JMenuItem paste = new JMenuItem(new PasteSubgraphAction(this));
        JMenuItem undoLast = new JMenuItem(new UndoLastAction(workbench));
        JMenuItem redoLast = new JMenuItem(new RedoLastAction(workbench));
        JMenuItem setToOriginal = new JMenuItem(new ResetGraph(workbench));

        cut.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK));
        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        paste.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
        undoLast.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        redoLast.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        setToOriginal.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));

        edit.add(cut);
        edit.add(copy);
        edit.add(paste);
        edit.addSeparator();

        edit.add(undoLast);
        edit.add(redoLast);
        edit.add(setToOriginal);

        return edit;
    }

    private JMenu createGraphMenu() {
        JMenu graph = new JMenu("Graph");

        JMenuItem randomGraph = new JMenuItem("Random Graph");
        graph.add(randomGraph);

        randomGraph.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.ALT_DOWN_MASK));

        graph.addSeparator();

        JMenuItem graphProperties = new JMenuItem(new GraphPropertiesAction(getWorkbench()));
        JMenuItem pathsAction = new JMenuItem(new PathsAction(getWorkbench(), parameters));
        graphProperties.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.ALT_DOWN_MASK));
        pathsAction.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.ALT_DOWN_MASK));

        graph.add(graphProperties);
        graph.add(pathsAction);
        graph.add(new UnderliningsAction(getWorkbench()));
        graph.addSeparator();

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

        graph.add(GraphUtils.getHighlightMenu(this.workbench));
        graph.add(GraphUtils.getCheckGraphMenu(this.workbench));
        GraphUtils.addGraphManipItems(graph, this.workbench);
        graph.addSeparator();
        graph.add(GraphUtils.addPagEdgeSpecializationsItems(this.workbench));

        // Only show these menu options for graph that has interventional nodes - Zhou
        if (isHasInterventional()) {
            graph.add(new JMenuItem(new SelectInterventionalAction(getWorkbench())));
            graph.add(new JMenuItem(new HideShowInterventionalAction(getWorkbench())));
        }

//         graph.add(new JMenuItem(new HideShowNoConnectionNodesAction(getWorkbench())));

        return graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getIndependenceTest() {
        Graph graph = getWorkbench().getGraph();
        EdgeListGraph listGraph = new EdgeListGraph(graph);
        return new MsepTest(listGraph);
    }

    /**
     * <p>isHasInterventional.</p>
     *
     * @return a boolean
     */
    public boolean isHasInterventional() {
        return this.hasInterventional;
    }

    /**
     * <p>Setter for the field <code>hasInterventional</code>.</p>
     *
     * @param hasInterventional a boolean
     */
    public void setHasInterventional(boolean hasInterventional) {
        this.hasInterventional = hasInterventional;
    }

}
