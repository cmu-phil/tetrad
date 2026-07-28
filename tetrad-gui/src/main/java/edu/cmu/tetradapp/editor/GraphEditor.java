///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

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
public final class GraphEditor extends JPanel implements GraphEditable, LayoutEditable, IndTestProducer,
    DoNotScroll {

    @Serial
    private static final long serialVersionUID = 5123725895449927539L;

    private static final Set<String> EVENTS = new HashSet<>(Arrays.asList(
            "graph",
            "edgeAdded",
            "edgeRemoved",
            "nodeRemoved",
            "nodeRenamed"
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
     * A private JTextArea used for text-based input or output within the GraphEditor component.
     * This field provides a space for displaying or editing textual content
     * related to the graph editing functionalities of the GraphEditor class.
     */
    private JTextArea ta;

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
    /** Tracks which tab we're leaving, so edits to the Text pane can be committed on exit. */
    private int prevTabIndex = 0;
    /** Reentrancy guard: set while we programmatically force the selected tab back to Text. */
    private boolean handlingTabChange = false;
    /** Canonical text shown when the Text tab was last entered; the change-detection baseline. */
    private String taBaseline = "";
    /** The model that now owns the undo/redo history. */
    private GraphWrapper graphWrapper;
    /** Set while applying an undo/redo so the change listener doesn't re-record it. */
    private boolean suppressUndoRecording = false;

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

        setPreferredSize(new Dimension(827, 620));

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

    private EdgeTypeTable getEdgeTypeTable() {
        return this.edgeTypeTable;
    }

    private JTextArea getTa() {
        return this.ta;
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
        this.graphWrapper = graphWrapper;
        Graph graph = graphWrapper.getGraph();

        this.workbench = new GraphWorkbench(graph);
        this.workbench.setEnableEditing(this.enableEditing);

        this.workbench.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            String propertyName = evt.getPropertyName();
            if (GraphEditor.EVENTS.contains(propertyName)) {
                if (getWorkbench() != null) {
                    Graph targetGraph = getWorkbench().getGraph();

                    if (!suppressUndoRecording && this.graphWrapper != null) {
                        this.graphWrapper.recordGraphState(targetGraph);
                    }

                    SwingUtilities.invokeLater(() -> {
                        graphWrapper.setGraph(new EdgeListGraph(targetGraph));
                        firePropertyChange("modelChanged", null, null);
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

        // Left side toolbar
        GraphToolbar graphToolbar = new GraphToolbar(getWorkbench());
        graphToolbar.setMaximumSize(new Dimension(140, Integer.MAX_VALUE));
        graphToolbar.setAlignmentY(Component.TOP_ALIGNMENT);

        // Right side scroll pane — no fixed preferred size, let it fill
        this.graphEditorScroll.setMinimumSize(new Dimension(200, 200));
        this.graphEditorScroll.setAlignmentY(Component.TOP_ALIGNMENT);
        this.graphEditorScroll.setViewportView(this.workbench);

        // topGraphBox: toolbar on left, scroll pane fills the rest
        Box topGraphBox = Box.createHorizontalBox();
        topGraphBox.add(graphToolbar);
        topGraphBox.add(this.graphEditorScroll);

        // Instruction label underneath the graph
        Box instructionBox = Box.createHorizontalBox();
        instructionBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel label = new JLabel("Double click variable/node rectangle to change name.");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        instructionBox.add(label);

        // topBox: graph on top, instruction label on bottom — no fixed preferred size
        Box topBox = Box.createVerticalBox();
        topBox.add(topGraphBox);
        topBox.add(instructionBox);

        // Edge type table
        this.edgeTypeTable.setPreferredSize(new Dimension(500, 150));

        // Tabbed pane
        JTabbedPane tabbedPane = new JTabbedPane(SwingConstants.RIGHT);
        tabbedPane.addTab("Graph", new PaddingPanel(topBox));

        Box edgeTableBox = Box.createVerticalBox();
        edgeTableBox.add(this.edgeTypeTable);
        edgeTableBox.add(new JLabel("Rows can be copy/pasted into Excel or text file"));
        tabbedPane.addTab("Edges", edgeTableBox);

        ta = new JTextArea(String.valueOf(graph));
        ta.setEditable(true);
        ta.setCaretPosition(0);
        this.taBaseline = String.valueOf(graph);
        JScrollPane textScroll = new JScrollPane(ta);

        JButton applyTextButton = new JButton("Apply Text as Graph");
        applyTextButton.addActionListener(ev -> applyTextGraph());
        Box textButtonBox = Box.createHorizontalBox();
        textButtonBox.add(applyTextButton);
        textButtonBox.add(Box.createHorizontalGlue());

        Box textBox = Box.createVerticalBox();
        textBox.add(textScroll);
        textBox.add(textButtonBox);
        tabbedPane.addTab("Text", textBox);

        updateBootstrapTable(graph);
        this.edgeTypeTable.update(graph);

        tabbedPane.addChangeListener(e -> {
            if (handlingTabChange) return;

            int newIndex = tabbedPane.getSelectedIndex();

            // Leaving the Text tab: try to commit any edits before moving on.
            if (prevTabIndex == 2 && newIndex != 2) {
                boolean okToLeave = commitTextIfChanged();
                if (!okToLeave) {
                    // Invalid text, user chose to keep editing: snap back to Text.
                    handlingTabChange = true;
                    tabbedPane.setSelectedIndex(2);
                    handlingTabChange = false;
                    return;   // prevTabIndex stays 2
                }
            }

            if (newIndex == 1) {
                updateBootstrapTable(workbench.getGraph());
                this.edgeTypeTable.update(workbench.getGraph());
            }

            if (newIndex == 2) {
                // Entering Text: refresh from the live graph and reset the baseline.
                ta.setText(String.valueOf(workbench.getGraph()));
                ta.setCaretPosition(0);
                this.taBaseline = ta.getText();
            }

            prevTabIndex = newIndex;
        });

        // Add to parent container
        add(menuBar, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);

        if (!graphWrapper.hasUndoHistory()) {
            graphWrapper.recordGraphState(graph);
        }

        validate();
    }

    /**
     * If the Text pane has been edited, parse it and (with confirmation) apply it to the
     * workbench. Returns true if it is safe to leave the Text tab (nothing changed, change
     * applied, or change discarded); false if the text is invalid and the user chose to
     * keep editing, in which case the caller should remain on the Text tab.
     */
    private boolean commitTextIfChanged() {
        if (ta.getText().equals(this.taBaseline)) {
            return true;   // untouched — no prompt
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "The graph text has been edited. Apply it as the new graph?\n"
                        + "(No discards your changes.)",
                "Apply graph changes",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            if (applyTextGraph()) {
                return true;          // applied
            }
            return false;             // parse failed — stay on Text to fix it
        } else if (choice == JOptionPane.NO_OPTION) {
            ta.setText(this.taBaseline);   // discard
            ta.setCaretPosition(0);
            return true;
        } else {
            return false;             // Cancel — stay on Text, text intact
        }
    }

    /**
     * Parses the Text pane and, on confirmation, applies it to the workbench. Returns
     * true iff a graph was successfully parsed and applied; false on parse failure or
     * user cancel. On parse failure shows the error and leaves the user's text intact.
     */
    private boolean applyTextGraph() {
        String text = ta.getText();

        Graph parsed;
        try {
            parsed = GraphSaveLoadUtils.readerToGraphTxt(new java.io.StringReader(text));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(
                    this,
                    "The text isn't a valid graph:\n" + ex.getMessage(),
                    "Invalid graph text",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        LayoutUtil.defaultLayout(parsed);
        workbench.setGraph(parsed);
        this.edgeTypeTable.update(parsed);
        this.taBaseline = String.valueOf(parsed);
        ta.setText(this.taBaseline);
        ta.setCaretPosition(0);
        return true;
    }

    /**
     * Updates the graph in workbench when changing graph model
     */
    private void updateGraphWorkbench(Graph graph) {
        suppressUndoRecording = true;
        try {
            this.workbench.setGraph(graph);
            this.workbench.setEnableEditing(this.enableEditing);
            this.graphEditorScroll.setViewportView(this.workbench);
            this.edgeTypeTable.update(graph);
            this.ta.setText(String.valueOf(graph));
        } finally {
            suppressUndoRecording = false;
        }
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
            Integer[] models = new Integer[numModels];
            for (int i = 0; i < numModels; i++) {
                models[i] = i + 1;
            }

            JComboBox<Integer> comboBox = new JComboBox<>(models);

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
        JMenuItem undoLast = new JMenuItem(new AbstractAction("Undo") {
            @Override public void actionPerformed(ActionEvent e) { doUndo(); }
        });
        JMenuItem redoLast = new JMenuItem(new AbstractAction("Redo") {
            @Override public void actionPerformed(ActionEvent e) { doRedo(); }
        });
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

    private void doUndo() {
        if (!graphWrapper.canUndo()) { Toolkit.getDefaultToolkit().beep(); return; }
        refreshAfterUndoRedo(graphWrapper.undo());
    }

    private void doRedo() {
        if (!graphWrapper.canRedo()) { Toolkit.getDefaultToolkit().beep(); return; }
        refreshAfterUndoRedo(graphWrapper.redo());
    }

    private void refreshAfterUndoRedo(Graph g) {
        if (g == null) return;
        suppressUndoRecording = true;
        try {
            workbench.setGraph(g);
            edgeTypeTable.update(g);
            if (ta != null) {
                ta.setText(String.valueOf(g));
                taBaseline = ta.getText();
            }
        } finally {
            suppressUndoRecording = false;
        }
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
                    getEdgeTypeTable().update(graph1);
                    getTa().setText(String.valueOf(graph1));
                }
            });
        });

        graph.add(GraphUtils.getHighlightMenu(this.workbench));
        graph.add(GraphUtils.getCheckGraphMenu(this.workbench));
        GraphUtils.addGraphManipItems(graph, this.workbench);
        graph.add(new CheckMSeparationFacts(this.workbench));
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

