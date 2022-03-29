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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.GraphSelectionWrapper;
import edu.cmu.tetradapp.ui.DualListPanel;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.*;

/**
 * Lets the user select a subgraph of a possible large graph and display it.
 *
 * @author jdramsey
 * @author Zhou Yuan
 */
public class GraphSelectionEditor extends JPanel implements GraphEditable, TripleClassifier {

    private static final long serialVersionUID = 2754618060275627122L;

    private final GraphEditorOptionsPanel graphEditorOptionsPanel;
    private JPanel workbenchScrollsPanel = new JPanel();
    private final JComboBox<GraphSelectionWrapper.Type> graphTypeCombo = new JComboBox<>();
    ;

    private final HelpSet helpSet;

    /**
     * Holds the graphs.
     */
    private final GraphSelectionWrapper wrapper;
    private final JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
    private final List<GraphWorkbench> workbenches = new ArrayList<>();
    private GraphPropertiesAction graphAction;
    private TriplesAction triplesAction;
    private Map<String, List<Integer>> layoutGraph;
    private int prevSelected = 0;

    /**
     * Constructs a graph selection editor.
     *
     * @param wrapper
     * @throws NullPointerException if <code>wrapper</code> is null.
     */
    public GraphSelectionEditor(final GraphSelectionWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException("The regression wrapper is required.");
        }

        this.wrapper = wrapper;

        if (this.layoutGraph == null) {
            this.layoutGraph = new HashMap<>();
        }

        // Initialize helpSet - Zhou
        final String helpHS = "/resources/javahelp/TetradHelp.hs";

        try {
            final URL url = this.getClass().getResource(helpHS);
            this.helpSet = new HelpSet(null, url);
        } catch (final Exception ee) {
            System.out.println("HelpSet " + ee.getMessage());
            System.out.println("HelpSet " + helpHS + " not found");
            throw new IllegalArgumentException();
        }

        setLayout(new BorderLayout());


        // Must before calling setSelectedGraphType()
        this.graphEditorOptionsPanel = new GraphEditorOptionsPanel(wrapper);

        // Select the graph type if graph wrapper has the type info
        setSelectedGraphType(wrapper.getType());

        // Graph panel on right
        this.workbenchScrollsPanel = workbenchScrollsPanel(wrapper);

        // splitPane contains subgraph setting options on left and graph on right
        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(new PaddingPanel(this.graphEditorOptionsPanel));
        splitPane.setRightComponent(new PaddingPanel(this.workbenchScrollsPanel));
        splitPane.setDividerLocation(383);

        // Bottom panel contains "Graph It" button and info on edge types
        final JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());

        // "Graph It" button
        final JButton executeButton = new JButton("Graph It!");

        executeButton.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {
                        final GraphWorkbench workbench = getWorkbench();
                        final List<DisplayNode> displayNodes = workbench.getSelectedNodes();
                        final List<Node> newSelected = new ArrayList<>();
                        for (final DisplayNode node : displayNodes) {
                            newSelected.add(node.getModelNode());
                        }

                        if (!newSelected.isEmpty()) {
                            GraphSelectionEditor.this.graphEditorOptionsPanel.setSelected(newSelected);
                        }

                        tabbedPaneGraphs(wrapper);
                    }
                };
            }
        });

        this.workbenchScrollsPanel.validate();

        // Add to buttonPanel
        buttonPanel.add(executeButton);

        // Info button added by Zhou to show edge types
        final JLabel infoLabel = new JLabel("More information on graph edge types and colorings");
        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Info button added by Zhou to show edge types
        final JButton infoBtn = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        infoBtn.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Clock info button to show edge types instructions - Zhou
        infoBtn.addActionListener(e -> {
            this.helpSet.setHomeID("graph_edge_types");
            final HelpBroker broker = this.helpSet.createHelpBroker();
            final ActionListener listener = new CSH.DisplayHelpFromSource(broker);
            listener.actionPerformed(e);
        });

        // Add to buttonPanel
        buttonPanel.add(infoLabel);
        buttonPanel.add(infoBtn);

        // Add top level componments to container
        add(createTopMenuBar(), BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        this.graphEditorOptionsPanel.reset();
    }

    // Top menu bar, contains "Save As" and "Graph"
    private JMenuBar createTopMenuBar() {
        final JMenuBar menuBar = new JMenuBar();

        // Add the save options - Zhou
        final JMenu saveMenu = createSaveMenu(this);
        menuBar.add(saveMenu);

        // Add the graph options
        final JMenu graphMenu = createGraphMenu();
        menuBar.add(graphMenu);

        return menuBar;
    }

    // Graph type selection
    private void graphTypeSelection() {
        for (final GraphSelectionWrapper.Type type : GraphSelectionWrapper.Type.values()) {
            this.graphTypeCombo.addItem(type);
        }

        this.graphTypeCombo.setSelectedItem(this.wrapper.getType());

        this.graphTypeCombo.addActionListener(e -> {
            final GraphSelectionWrapper.Type selectedItem = (GraphSelectionWrapper.Type) this.graphTypeCombo.getSelectedItem();
            this.wrapper.setType(selectedItem);
            setSelectedGraphType(selectedItem);
        });
    }

    private void setSelectedGraphType(final GraphSelectionWrapper.Type type) {
        if (type == GraphSelectionWrapper.Type.Subgraph) {
            this.graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Adjacents) {
            this.graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Adjacents_of_Adjacents) {
            this.graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Adjacents_of_Adjacents_of_Adjacents) {
            this.graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Pag_Y_Structures) {
            this.graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Markov_Blankets) {
            this.graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Y_Structures) {
            this.graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Pag_Y_Structures) {
            this.graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Treks) {
            this.graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Trek_Edges) {
            this.graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Paths) {
            this.graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Path_Edges) {
            this.graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Directed_Paths) {
            this.graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Directed_Path_Edges) {
            this.graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Indegree) {
            this.graphEditorOptionsPanel.setNLabel("Indegree");
        }

        if (type == GraphSelectionWrapper.Type.Out_Degree) {
            this.graphEditorOptionsPanel.setNLabel("Outdegree");
        }

        if (type == GraphSelectionWrapper.Type.Degree) {
            this.graphEditorOptionsPanel.setNLabel("");
        }
    }

    // Create scroll pane for each graph
    private JPanel workbenchScrollsPanel(final GraphSelectionWrapper wrapper) {
        //tabbedPane.removeAll();

        final List<JScrollPane> workbenchScrolls = new ArrayList<>();

        //workbenchScrolls.clear();

        final List<Graph> graphs = wrapper.getGraphs();

        for (int i = 0; i < graphs.size(); i++) {
            final GraphWorkbench workbench = new GraphWorkbench();
            this.workbenches.add(workbench);

            workbench.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(final PropertyChangeEvent evt) {
                    if ("modelChanged".equals(evt.getPropertyName())) {
                        firePropertyChange("modelChanged", null, null);
                    }
                }
            });

            final JScrollPane workbenchScroll = new JScrollPane(workbench);
            workbenchScroll.setPreferredSize(new Dimension(520, 560));

            workbenchScrolls.add(workbenchScroll);
        }

//        wrapper.setSelectionGraphs(selectionGraphs);

        for (int i = 0; i < workbenchScrolls.size(); i++) {
            this.tabbedPane.add("" + (i + 1), workbenchScrolls.get(i));
        }

        this.tabbedPane.addChangeListener(e -> {
            if (e.getSource() instanceof JTabbedPane) {
                final JTabbedPane panel = (JTabbedPane) e.getSource();
                int selectedIndex = panel.getSelectedIndex();
                selectedIndex = selectedIndex == -1 ? 0 : selectedIndex;
                this.graphAction.setGraph(wrapper.getGraphs().get(selectedIndex), getWorkbench());
                this.triplesAction.setGraph(wrapper.getGraphs().get(selectedIndex), getWorkbench());
            }
        });

        // Show graph in each tabbed pane
        tabbedPaneGraphs(wrapper);

        // Make the tabbedPane auto resize - Zhou
        this.workbenchScrollsPanel.setLayout(new BorderLayout());
        this.workbenchScrollsPanel.add(this.tabbedPane, BorderLayout.CENTER);

        this.workbenchScrollsPanel.validate();

        return this.workbenchScrollsPanel;
    }


    private void tabbedPaneGraphs(final GraphSelectionWrapper wrapper) {
        wrapper.calculateSelection();

//        if (wrapper.getSelectedVariables() == null || wrapper.getSelectedVariables().isEmpty()) {
//            first500Variables(wrapper);
//        }

        for (int i = 0; i < this.tabbedPane.getTabCount(); i++) {
            final Graph selection = wrapper.getSelectionGraph(i);

//            if (selection.getNumNodes() > 500) {
//                throw new IllegalArgumentException("That is too many nodes for me to display ("
//                        + selection.getNumNodes() + ") I can only go up to 500 nodes.\n"
//                        + "Try a smaller selection.");
//            }

            if (!this.layoutGraph.isEmpty()) {
                for (final Node node : selection.getNodes()) {
                    final List<Integer> center = this.layoutGraph.get(node.getName());

                    if (center != null) {
                        node.setCenter(center.get(0), center.get(1));
                    }
                }
            } else {
//                GraphUtils.circleLayout(selection, 200, 200, 150);
//                GraphUtils.fruchtermanReingoldLayout(selection);
            }

            final GraphWorkbench workbench = getWorkbench(i);
            workbench.setGraph(selection);
            final List<Node> selected = wrapper.getSelectedVariables();

            for (final Node node : selected) {
                if (wrapper.getHighlightInEditor().contains(node)
                        && workbench.getGraph().containsNode(node)) {
                    workbench.selectNode(node);
                }
            }
        }
    }

    /**
     * File save menu - Zhou
     *
     * @param editable
     * @param comp
     * @return
     */
    private JMenu createSaveMenu(final GraphEditable editable) {
        final JMenu save = new JMenu("Save As");

        save.add(new SaveGraph(editable, "Graph XML...", SaveGraph.Type.xml));
        save.add(new SaveGraph(editable, "Graph Text...", SaveGraph.Type.text));
        save.add(new SaveGraph(editable, "Graph Json...", SaveGraph.Type.json));
        save.add(new SaveGraph(editable, "R...", SaveGraph.Type.r));
        save.add(new SaveGraph(editable, "Dot...", SaveGraph.Type.dot));

        return save;
    }

    private JMenu createGraphMenu() {
        final JMenu graph = new JMenu("Graph");

        this.graphAction = new GraphPropertiesAction(this.wrapper.getGraphs().get(0), getWorkbench());
        graph.add(this.graphAction);
        graph.add(new PathsAction(getWorkbench()));
//        graph.add(new DirectedPathsAction(getWorkbench()));
//        graph.add(new TreksAction(getWorkbench()));
//        graph.add(new AllPathsAction(getWorkbench()));
//        graph.add(new NeighborhoodsAction(getWorkbench()));
        this.triplesAction = new TriplesAction(this.wrapper.getGraphs().get(0), getWorkbench());
        graph.add(this.triplesAction);

        return graph;
    }

    /**
     * Sets the name of this editor.
     */
    @Override
    public void setName(final String name) {
        final String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    @Override
    public List getSelectedModelComponents() {
        final List<Component> selectedComponents
                = getWorkbench().getSelectedComponents();
        final List<TetradSerializable> selectedModelComponents
                = new ArrayList<>();

        for (final Component comp : selectedComponents) {
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

    @Override
    public void pasteSubsession(final List sessionElements, final Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        for (final Object o : sessionElements) {
            if (o instanceof GraphNode) {
                final Node modelNode = (Node) o;
                getWorkbench().selectNode(modelNode);
            }
        }

        getWorkbench().selectConnectingEdges();

    }

    @Override
    public GraphWorkbench getWorkbench() {
        int selectedIndex = this.tabbedPane.getSelectedIndex();
        if (selectedIndex == -1) {
            selectedIndex = 0;
        }

        return getWorkbench(selectedIndex);

//        Graph graph = workbenches.get(selectedIndex).getGraph();
//
//        for (Node node : graph.getNodes()) {
//            List<Integer> center = layoutGraph.get(node.getName());
//
//            if (center != null) {
//                node.setCenter(center.get(0), center.get(1));
//            }
//        }
//
//        return workbenches.get(selectedIndex);
    }

    public GraphWorkbench getWorkbench(final int selectionIndex) {
        final Graph layout = this.workbenches.get(this.prevSelected).getGraph();
        setLayoutGraph(layout);

        int selectedIndex = this.tabbedPane.getSelectedIndex();
        if (selectedIndex == -1) {
            selectedIndex = 0;
        }

        final Graph graph = this.workbenches.get(selectedIndex).getGraph();

        for (final Node node : graph.getNodes()) {
            final List<Integer> center = this.layoutGraph.get(node.getName());

            if (center != null) {
                node.setCenter(center.get(0), center.get(1));
            }
        }

        this.prevSelected = selectedIndex;

        return this.workbenches.get(selectionIndex);
    }

    public void saveLayout() {
        int selectedIndex = this.tabbedPane.getSelectedIndex();
        if (selectedIndex == -1) {
            selectedIndex = 0;
        }
        final Graph layout = this.wrapper.getSelectionGraph(selectedIndex);
        setLayoutGraph(layout);
    }

    @Override
    public Graph getGraph() {
        int selectedIndex = this.tabbedPane.getSelectedIndex();
        if (selectedIndex == -1) {
            selectedIndex = 0;
        }
        return this.wrapper.getSelectionGraph(selectedIndex);
    }

    @Override
    public void setGraph(final Graph graph) {
        this.wrapper.setGraphs(Collections.singletonList(graph));
        this.graphEditorOptionsPanel.reset();
        getWorkbench().setGraph(graph);
    }

    public void replace(List<Graph> graphs) {
        for (final Graph graph : graphs) {
            for (final Node node : graph.getNodes()) {
                final List<Integer> center = this.layoutGraph.get(node.getName());

                if (center != null) {
                    node.setCenter(center.get(0), center.get(1));
                }
            }
        }
        if (graphs == null) {
            graphs = new ArrayList<>();
        }
        this.wrapper.setGraphs(graphs);
        workbenchScrollsPanel(this.wrapper);
        tabbedPaneGraphs(this.wrapper);
        this.graphEditorOptionsPanel.reset();
    }

    private void setLayoutGraph(final Graph graph) {
        this.layoutGraph.clear();

        for (final Node node : graph.getNodes()) {
            final List<Integer> center = new ArrayList<>();
            center.add(node.getCenterX());
            center.add(node.getCenterY());
            this.layoutGraph.put(node.getName(), center);
        }
    }

    /**
     * Allows one to drop/drap variables from a source list to a response area
     * and a selected list. Also lets one specify an alpha level.
     *
     * @author Tyler Gibson
     * @author Kevin V. Bui (kvb2@pitt.edu)
     */
    public class GraphEditorOptionsPanel extends JPanel {

        private static final long serialVersionUID = -991342933507624509L;

        // Stores the length of a path or the degree of a node, depending.
        private final JLabel nLabel;

        // Selected if one wants to know that the length of a path is equal to n, etc.
        private final JRadioButton equals;

        // Selected if one wants to know that the length of a path is at most n.
        private final JRadioButton atMost;

        // Selected if one wants to know that the length of a path is at most n.
        private final JRadioButton atLeast;
        private final IntTextField nField;

        //The list of source variables.
        private final JList<Node> sourceList;

        // The list of selected.
        private final JList<Node> selectedList;

        private final DualListPanel dualListPanel;

        // The font to render fields in.
        private final Font _font = new Font("Dialog", Font.PLAIN, 12);

        private final GraphSelectionWrapper graphSelectionWrapper;

        /**
         * Constructs the editor given the <code>Parameters</code> and the
         * <code>DataModel</code> that should be used.
         *
         * @param graphSelectionWrapper
         */
        public GraphEditorOptionsPanel(final GraphSelectionWrapper graphSelectionWrapper) {
            if (graphSelectionWrapper == null) {
                throw new NullPointerException("Graph wrapper must not be null");
            }

            this.nLabel = new JLabel("Degree");
            this.equals = new JRadioButton("Equals");
            this.atMost = new JRadioButton("At Most");
            this.atLeast = new JRadioButton("At Least");
            this.nField = new IntTextField(GraphSelectionEditor.this.wrapper.getN(), 2);
            this.graphSelectionWrapper = graphSelectionWrapper;
            this.dualListPanel = new DualListPanel();
            this.sourceList = this.dualListPanel.getSourceList();
            this.selectedList = this.dualListPanel.getSelectedList();

            initList(this.sourceList);
            initList(this.selectedList);

            this.dualListPanel.getMoveToSource().addActionListener((e) -> {
                for (final GraphWorkbench workbench : GraphSelectionEditor.this.workbenches) {
                    workbench.deselectAll();
                }

                final VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
                final VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();

                final List<Node> selected = getSelected(getSelectedList());
                selectedModel.removeAll(selected);
                sourceModel.addAll(selected);
                GraphSelectionEditor.this.wrapper.setSelectedVariables(getSelected());
                getSelectedList().setSelectedIndices(new int[0]);
                getSourceList().setSelectedIndices(new int[0]);
            });
            this.dualListPanel.getMoveToselector().addActionListener((e) -> {
                for (final GraphWorkbench workbench : GraphSelectionEditor.this.workbenches) {
                    workbench.deselectAll();
                }

                final VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
                final VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();

                final List<Node> selected = getSelected(getSourceList());
                sourceModel.removeAll(selected);
                selectedModel.addAll(selected);

                getSelectedList().setSelectedIndices(new int[0]);
                getSourceList().setSelectedIndices(new int[0]);
                GraphSelectionEditor.this.wrapper.setSelectedVariables(getSelected());
            });

            final VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
            final VariableListModel variableModel = (VariableListModel) getSourceList().getModel();

            // if selected are already set use'em.
            final List<Node> selectedNodes = GraphSelectionEditor.this.wrapper.getSelectedVariables();
            final List<Node> initVars = new ArrayList<>(GraphSelectionEditor.this.wrapper.getVariables());
            initVars.removeAll(selectedNodes);
            variableModel.addAll(initVars);
            selectedModel.addAll(selectedNodes);

            // deal with drag and drop
            new DropTarget(getSourceList(), DnDConstants.ACTION_MOVE, new TargetListener(), true);
            new DropTarget(getSelectedList(), DnDConstants.ACTION_MOVE, new TargetListener(), true);

            DragSource dragSource = DragSource.getDefaultDragSource();
            dragSource.createDefaultDragGestureRecognizer(getSourceList(), DnDConstants.ACTION_MOVE, new SourceListener());
            dragSource = DragSource.getDefaultDragSource();
            dragSource.createDefaultDragGestureRecognizer(getSelectedList(), DnDConstants.ACTION_MOVE, new SourceListener());

            final ButtonGroup group2 = new ButtonGroup();

            group2.add(this.equals);
            group2.add(this.atMost);
            group2.add(this.atLeast);

            if (GraphSelectionEditor.this.wrapper.getNType().equals(GraphSelectionWrapper.nType.equals.toString())) {
                this.equals.setSelected(true);
            } else if (GraphSelectionEditor.this.wrapper.getNType().equals(GraphSelectionWrapper.nType.atMost.toString())) {
                this.atMost.setSelected(true);
            } else {
                this.atLeast.setSelected(true);
            }

            this.equals.addActionListener((e) -> {
                GraphSelectionEditor.this.wrapper.setNType(GraphSelectionWrapper.nType.equals);
            });
            this.atMost.addActionListener((e) -> {
                GraphSelectionEditor.this.wrapper.setNType(GraphSelectionWrapper.nType.atMost);
            });
            this.atLeast.addActionListener((e) -> {
                GraphSelectionEditor.this.wrapper.setNType(GraphSelectionWrapper.nType.atLeast);
            });
            this.nField.setFilter((value, oldValue) -> {
                try {
                    GraphSelectionEditor.this.wrapper.setN(value);
                    return value;
                } catch (final Exception e) {
                    return oldValue;
                }
            });

            initGUI();
        }

        private void initGUI() {
            setLayout(new BorderLayout());

            final Box box = Box.createHorizontalBox();
            box.add(new JLabel("Please select variables:"));
            box.add(Box.createGlue());

            final Box northComp = Box.createVerticalBox();
            northComp.add(box);
            northComp.add(Box.createVerticalStrut(10));

            final Box varManupBox = Box.createHorizontalBox();
            varManupBox.add(new VariableManipulationPanel(createSortButton(), createTextButton()));
            varManupBox.add(Box.createGlue());

            final Box graphTypeBox = Box.createHorizontalBox();

            graphTypeSelection();

            graphTypeBox.add(new GraphTypePanel(this.atMost, this.equals, this.nField, this.nLabel, GraphSelectionEditor.this.graphTypeCombo));
            graphTypeBox.add(Box.createGlue());

            final Box southComp = Box.createVerticalBox();
            southComp.add(Box.createVerticalStrut(20));
            southComp.add(varManupBox);
            southComp.add(Box.createVerticalStrut(20));
            southComp.add(graphTypeBox);

            final JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.add(northComp, BorderLayout.NORTH);
            mainPanel.add(this.dualListPanel, BorderLayout.CENTER);
            mainPanel.add(southComp, BorderLayout.SOUTH);

            add(mainPanel, BorderLayout.CENTER);
        }

        private class GraphTypePanel extends JPanel {

            private static final long serialVersionUID = -1341212423361787517L;

            private final JRadioButton atMost;
            private final JRadioButton equals;
            private final IntTextField nField;
            private final JLabel nLabel;
            private final ButtonGroup pathLengthBtnGrp;
            private final JLabel selectGraphTypeLbl;
            private final JComboBox<GraphSelectionWrapper.Type> graphTypeCombo;

            public GraphTypePanel(final JRadioButton atMost, final JRadioButton equals, final IntTextField nField, final JLabel nLabel, final JComboBox<GraphSelectionWrapper.Type> graphTypeCombo) {
                this.atMost = atMost;
                this.equals = equals;
                this.nField = nField;
                this.nLabel = nLabel;
                this.pathLengthBtnGrp = new ButtonGroup();
                this.selectGraphTypeLbl = new JLabel("Please select a graph type:");
                this.graphTypeCombo = graphTypeCombo;

                initComponents();
            }

            private void initComponents() {
                this.pathLengthBtnGrp.add(this.equals);
                this.pathLengthBtnGrp.add(this.atMost);
                this.pathLengthBtnGrp.add(GraphEditorOptionsPanel.this.atLeast);

                final GroupLayout layout = new GroupLayout(this);
                this.setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(this.selectGraphTypeLbl)
                                .addComponent(this.graphTypeCombo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(this.nLabel)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(this.equals)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(this.atMost)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(GraphEditorOptionsPanel.this.atLeast)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(this.nField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                );
                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(this.selectGraphTypeLbl)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(this.graphTypeCombo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                .addComponent(this.nLabel)
                                                .addComponent(this.equals)
                                                .addComponent(this.atMost)
                                                .addComponent(GraphEditorOptionsPanel.this.atLeast)
                                                .addComponent(this.nField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)))
                );
            }

        }

        private class VariableManipulationPanel extends JPanel {

            private static final long serialVersionUID = 4538277448583296121L;

            private final JButton sortBnt;
            private final JButton textInputBnt;

            public VariableManipulationPanel(final JButton sortBnt, final JButton textInputBnt) {
                this.sortBnt = sortBnt;
                this.textInputBnt = textInputBnt;

                initComponents();
            }

            private void initComponents() {
                final GroupLayout layout = new GroupLayout(this);
                this.setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(this.sortBnt)
                                        .addGap(30, 30, 30)
                                        .addComponent(this.textInputBnt))
                );

                layout.linkSize(SwingConstants.HORIZONTAL, new java.awt.Component[]{this.sortBnt, this.textInputBnt});

                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(this.sortBnt)
                                        .addComponent(this.textInputBnt))
                );
            }

        }

        private JButton createSortButton() {
            final JButton sort = new JButton("Sort Variables");
            sort.setFont(sort.getFont().deriveFont(11f));
            sort.setMargin(new Insets(3, 3, 3, 3));
            sort.addActionListener((e) -> {
                final VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
                final VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();
                selectedModel.sort();
                sourceModel.sort();
            });

            return sort;
        }

        private JButton createTextButton() {
            final GraphSelectionTextInputAction action
                    = new GraphSelectionTextInputAction(GraphEditorOptionsPanel.this,
                    GraphSelectionEditor.this.wrapper, this.sourceList, this.selectedList);
            final JButton sort = new JButton(action);
            sort.setFont(sort.getFont().deriveFont(11f));
            sort.setMargin(new Insets(3, 3, 3, 3));

            return sort;
        }

        private void initList(final JList<Node> list) {
            list.setModel(new VariableListModel());
            list.setFont(getFONT());
            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            list.setVisibleRowCount(8);
        }

        public Font getFONT() {
            return this._font;
        }

        public void setNLabel(final String label) {
            if (label.equals("")) {
                this.nLabel.setEnabled(false);
                this.equals.setEnabled(false);
                this.atMost.setEnabled(false);
                this.atLeast.setEnabled(false);
                this.nField.setEnabled(false);
            } else {
                this.nLabel.setEnabled(true);
                this.equals.setEnabled(true);
                this.atMost.setEnabled(true);
                this.atLeast.setEnabled(true);
                this.nField.setEnabled(true);
                this.nLabel.setText(label);
            }
        }

        private List<Node> getSelected() {
            final ListModel<Node> model = getSelectedList().getModel();
            final List<Node> selected = new ArrayList<>(model.getSize());
            for (int i = 0; i < model.getSize(); i++) {
                final Node node = model.getElementAt(i);
                selected.add(node);
            }

            return selected;
        }

        private List<Node> getSelected(final JList<Node> list) {
            final List<Node> selected = list.getSelectedValuesList();
            final List<Node> selectedList = new ArrayList<>(selected == null ? 0 : selected.size());
            if (selected != null) {
                for (final Object o : selected) {
                    selectedList.add((Node) o);
                }
            }
            return selectedList;
        }

        public void setSelected(final List<Node> selected) {
            final VariableListModel selectedModel = (VariableListModel) this.selectedList.getModel();
            final VariableListModel sourceModel = (VariableListModel) this.sourceList.getModel();
            final List<Node> oldSelected = GraphSelectionEditor.this.wrapper.getSelectedVariables();
            selectedModel.removeAll(oldSelected);
            sourceModel.addAll(oldSelected);
            selectedModel.addAll(selected);
            sourceModel.removeAll(selected);
            GraphSelectionEditor.this.wrapper.setSelectedVariables(selected);
            this.selectedList.setSelectedIndices(new int[0]);
            this.sourceList.setSelectedIndices(new int[0]);
        }

        public void reset() {
            final VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
            final VariableListModel variableModel = (VariableListModel) getSourceList().getModel();
            final List<Node> variableNames = GraphSelectionEditor.this.wrapper.getVariables();

            // if regressors are already set use'em.
            selectedModel.removeAll();
            variableModel.removeAll();
            variableModel.addAll(variableNames);
            variableModel.removeAll(GraphSelectionEditor.this.wrapper.getSelectedVariables());
            selectedModel.addAll(GraphSelectionEditor.this.wrapper.getSelectedVariables());

            getSelectedList().setSelectedIndices(new int[0]);
            getSourceList().setSelectedIndices(new int[0]);
        }

        private JList<Node> getSourceList() {
            return this.sourceList;
        }

        private JList<Node> getSelectedList() {
            return this.selectedList;
        }

        //========================== Inner classes (a lot of'em) =========================================//
        private class TargetListener extends DropTargetAdapter {

            @Override
            public void drop(final DropTargetDropEvent dtde) {
                final Transferable t = dtde.getTransferable();
                final Component comp = dtde.getDropTargetContext().getComponent();

                if (comp instanceof JList || comp instanceof JTextField) {
                    try {
                        for (final GraphWorkbench workbench : GraphSelectionEditor.this.workbenches) {
                            workbench.deselectAll();
                        }

                        // if response, remove everything first
                        final JList<Node> list = (JList<Node>) comp;

                        final VariableListModel model = (VariableListModel) list.getModel();
                        final ListTransferable listTransferable = new ListTransferable(new ArrayList<>());
                        final List<Node> transferData = (List<Node>) t.getTransferData(listTransferable.getDataFlavor());

                        final List<Node> elements = new ArrayList<>();

                        for (int i = 0; i < model.getSize(); i++) {
                            elements.add((Node) model.getElementAt(i));
                        }

                        elements.retainAll(transferData);

                        if (!elements.isEmpty()) {
                            dtde.getDropTargetContext().dropComplete(false);
                            return;
                        }

                        for (final Node c : transferData) {
                            model.removeFirst(c);
                        }

                        model.addAll(transferData);

                        GraphSelectionEditor.this.wrapper.setSelectedVariables(getSelected());
                        dtde.getDropTargetContext().dropComplete(true);

                    } catch (final Exception exception) {
                        dtde.rejectDrop();
                        exception.printStackTrace(System.err);
                    }
                } else {
                    dtde.rejectDrop();
                }
            }
        }

        /**
         * A source/gesture listener for the JLists
         */
        private class SourceListener extends DragSourceAdapter implements DragGestureListener {

            @Override
            public void dragDropEnd(final DragSourceDropEvent evt) {
                if (evt.getDropSuccess()) {
                    final Component comp = evt.getDragSourceContext().getComponent();
                    final Transferable t = evt.getDragSourceContext().getTransferable();
                    if (t instanceof ListTransferable) {
                        try {
                            //noinspection unchecked
                            final List<Node> o = (List<Node>) t.getTransferData(new ListTransferable(new ArrayList()).getDataFlavor());
                            if (comp instanceof JList) {
                                final JList<Node> list = (JList<Node>) comp;
                                final VariableListModel model = (VariableListModel) list.getModel();
                                for (final Node c : o) {
                                    model.removeFirst(c);
                                }
                            } else {
                                final JTextField pane = (JTextField) comp;
                                pane.setText(null);
                            }

                            GraphSelectionEditor.this.wrapper.setSelectedVariables(getSelected());
                        } catch (final Exception exception) {
                            exception.printStackTrace(System.err);
                        }
                    }
                }
            }

            @Override
            public void dragGestureRecognized(final DragGestureEvent dge) {
                final Component comp = dge.getComponent();
                List<Node> selected = null;
                if (comp instanceof JList) {
                    final JList<Node> list = (JList<Node>) comp;
                    selected = list.getSelectedValuesList();
                }
                if (selected != null) {
                    final List<Node> nodes = new ArrayList<>();
                    nodes.addAll(selected);
                    final ListTransferable t = new ListTransferable(nodes);
                    dge.startDrag(DragSource.DefaultMoveDrop, t, this);
                }
            }
        }

        /**
         * A basic model for the list (needed an addAll feature, which the
         * detault model didn't have)
         */
        public class VariableListModel extends AbstractListModel {

            private static final long serialVersionUID = 8014422476634156667L;

            private final List<Node> delegate = new ArrayList<>();

            @Override
            public int getSize() {
                return this.delegate.size();
            }

            @Override
            public Object getElementAt(final int index) {
                return this.delegate.get(index);
            }

            public void remove(final Node element) {
                final int index = this.delegate.indexOf(element);
                if (0 <= index) {
                    this.delegate.remove(index);
                    this.fireIntervalRemoved(this, index, index);
                }
            }

            public void add(final Node element) {
                this.delegate.add(element);
                this.fireIntervalAdded(this, this.delegate.size(), this.delegate.size());
            }

            public void removeFirst(final Node element) {
                this.delegate.remove(element);
                this.fireContentsChanged(this, 0, this.delegate.size());
            }

            public void removeAll(final List<? extends Node> elements) {
                this.delegate.removeAll(elements);
                this.fireContentsChanged(this, 0, this.delegate.size());
            }

            public void addAll(final List<? extends Node> elements) {
                this.delegate.addAll(elements);
                this.fireContentsChanged(this, 0, this.delegate.size());
            }

            public void removeAll() {
                this.delegate.clear();
                this.fireContentsChanged(this, 0, 0);
            }

            public void sort() {
                Collections.sort(this.delegate);
                this.fireContentsChanged(this, 0, this.delegate.size());
            }
        }

        private DataFlavor getListDataFlavor() {
            try {
                return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + "; class=java.lang.Object",
                        "Local Variable List");
            } catch (final Exception exception) {
                exception.printStackTrace(System.err);
                return null;
            }
        }

        /**
         * A basic transferable.
         */
        private class ListTransferable implements Transferable {

            private final DataFlavor FLAVOR = getListDataFlavor();

            private final List<Node> nodes;

            public DataFlavor getDataFlavor() {
                return this.FLAVOR;
            }

            public ListTransferable(final List<Node> nodes) {
                if (nodes == null) {
                    throw new NullPointerException();
                }
                this.nodes = nodes;
            }

            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{this.FLAVOR};
            }

            @Override
            public boolean isDataFlavorSupported(final DataFlavor flavor) {
                return flavor == this.FLAVOR;
            }

            @Override
            public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException, IOException {
                return this.nodes;
            }
        }

    }

    /**
     * Puts up a panel showing some graph properties, e.g., number of nodes and
     * edges in the graph, etc.
     *
     * @author Joseph Ramsey jdramsey@andrew.cmu.edu
     */
    public static class GraphSelectionTextInputAction extends AbstractAction implements ClipboardOwner {

        private static final long serialVersionUID = 8126264917739434042L;

        private final GraphSelectionWrapper wrapper;
        private final JList<Node> sourceList;
        private final JList<Node> selectedList;
        private final JComponent component;
        private JTextArea textArea;

        /**
         * Creates a new copy subsession action for the given LayoutEditable and
         * clipboard.
         *
         * @param component
         * @param wrapper
         * @param sourceList
         * @param selectedList
         */
        public GraphSelectionTextInputAction(final JComponent component, final GraphSelectionWrapper wrapper,
                                             final JList<Node> sourceList, final JList<Node> selectedList) {
            super("Text Input...");
            this.component = component;
            this.wrapper = wrapper;
            this.sourceList = sourceList;
            this.selectedList = selectedList;
        }

        /**
         * Copies a parentally closed selection of session nodes in the
         * frontmost session editor to the clipboard.
         *
         * @param e
         */
        @Override
        public void actionPerformed(final ActionEvent actionEvent) {
            final Box b = Box.createVerticalBox();

            this.textArea = new JTextArea();

            this.textArea.setText(this.wrapper.getDialogText());

            final JScrollPane scroll = new JScrollPane(this.textArea);
            scroll.setPreferredSize(new Dimension(150, 300));
            scroll.setBorder(new CompoundBorder(new LineBorder(Color.GRAY, 1), new EmptyBorder(5, 5, 5, 5)));

            final Box b2 = Box.createVerticalBox();
            b2.add(Box.createVerticalStrut(10));
            b2.add(new JLabel("Please enter a list of variable names you'd like to have"));
            b2.add(new JLabel("selected, one per line. Those that exist in the graph will"));
            b2.add(new JLabel("be selected; the rest will be disgarded. You can paste a"));
            b2.add(new JLabel("list in from the clipboard or type it. This list will be"));
            b2.add(new JLabel("saved."));
            b2.add(Box.createVerticalStrut(10));
            b2.add(scroll);
            this.textArea.setCaretPosition(0);
            b.add(b2);

            final JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.add(b);

            final EditorWindow window = new EditorWindow(panel,
                    "Input Variable Names as Text", "Select", false, this.component);
            DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
            window.setVisible(true);

            window.addActionListener(e -> {
                this.wrapper.setDialogText(this.textArea.getText());
                final GraphEditorOptionsPanel.VariableListModel selectedModel = (GraphEditorOptionsPanel.VariableListModel) this.selectedList.getModel();
                final GraphEditorOptionsPanel.VariableListModel sourceModel = (GraphEditorOptionsPanel.VariableListModel) this.sourceList.getModel();
                final List<Node> oldSelected = this.wrapper.getSelectedVariables();
                selectedModel.removeAll(oldSelected);
                sourceModel.addAll(oldSelected);
                final List<Node> newSelected = selectedVars();
                selectedModel.addAll(newSelected);
                sourceModel.removeAll(newSelected);
                this.wrapper.setSelectedVariables(newSelected);
                this.selectedList.setSelectedIndices(new int[0]);
                this.sourceList.setSelectedIndices(new int[0]);
            });
        }

        public List<Node> selectedVars() {
            final List<Node> nodes = new ArrayList<>();

            try {
                final String text = this.textArea.getText();

                final BufferedReader r = new BufferedReader(new CharArrayReader(text.toCharArray()));
                String line = null;

                while ((line = r.readLine()) != null) {
                    final Node node = this.wrapper.getOriginalGraph().getNode(line);

                    if (node != null) {
                        nodes.add(node);
                    }
                }

                return nodes;
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Required by the AbstractAction interface; does nothing.
         */
        @Override
        public void lostOwnership(final Clipboard clipboard, final Transferable contents) {
        }
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     * <code>getTriplesList</code>
     */
    @Override
    public List<String> getTriplesClassificationTypes() {
        final List<String> names = new ArrayList<>();
        names.add("Underlines");
        names.add("Dotted Underlines");
        return names;
    }

    /**
     * @return the list of triples corresponding to
     * <code>getTripleClassificationNames</code> for the given node.
     */
    @Override
    public List<List<Triple>> getTriplesLists(final Node node) {
        final List<List<Triple>> triplesList = new ArrayList<>();
        final Graph graph = getGraph();
        triplesList.add(GraphUtils.getUnderlinedTriplesFromGraph(node, graph));
        triplesList.add(GraphUtils.getDottedUnderlinedTriplesFromGraph(node, graph));
        return triplesList;
    }

}