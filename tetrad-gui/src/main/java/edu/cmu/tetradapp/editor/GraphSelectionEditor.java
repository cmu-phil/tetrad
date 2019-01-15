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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.graph.TripleClassifier;
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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.GroupLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.LayoutStyle;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

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
    private final JComboBox<GraphSelectionWrapper.Type> graphTypeCombo = new JComboBox<>();;

    private final HelpSet helpSet;
 
    /**
     * Holds the graphs.
     */
    private GraphSelectionWrapper wrapper;
    private JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.LEFT);
    private List<GraphWorkbench> workbenches = new ArrayList<>();
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
    public GraphSelectionEditor(GraphSelectionWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException("The regression wrapper is required.");
        }
 
        this.wrapper = wrapper;

        if (layoutGraph == null) {
            layoutGraph = new HashMap<>();
        }

        // Initialize helpSet - Zhou
        String helpHS = "/resources/javahelp/TetradHelp.hs";

        try {
            URL url = this.getClass().getResource(helpHS);
            this.helpSet = new HelpSet(null, url);
        } catch (Exception ee) {
            System.out.println("HelpSet " + ee.getMessage());
            System.out.println("HelpSet " + helpHS + " not found");
            throw new IllegalArgumentException();
        }

        setLayout(new BorderLayout());

       
        // Must before calling setSelectedGraphType()
        graphEditorOptionsPanel = new GraphEditorOptionsPanel(wrapper);

        // Select the graph type if graph wrapper has the type info
        setSelectedGraphType(wrapper.getType());
        
        // Graph panel on right
        workbenchScrollsPanel = workbenchScrollsPanel(wrapper);

        // splitPane contains subgraph setting options on left and graph on right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(new PaddingPanel(graphEditorOptionsPanel));
        splitPane.setRightComponent(new PaddingPanel(workbenchScrollsPanel));
        splitPane.setDividerLocation(383);

        // Bottom panel contains "Graph It" button and info on edge types
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout());

        // "Graph It" button
        JButton executeButton = new JButton("Graph It!");

        executeButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {
                        GraphWorkbench workbench = getWorkbench();
                        List<DisplayNode> displayNodes = workbench.getSelectedNodes();
                        List<Node> newSelected = new ArrayList<>();
                        for (DisplayNode node : displayNodes) {
                            newSelected.add(node.getModelNode());
                        }

                        if (!newSelected.isEmpty()) {
                            graphEditorOptionsPanel.setSelected(newSelected);
                        }

                        tabbedPaneGraphs(wrapper);
                    }
                };
            }
        });

        workbenchScrollsPanel.validate();
        
        // Add to buttonPanel
        buttonPanel.add(executeButton);
        
        // Info button added by Zhou to show edge types
        JLabel infoLabel = new JLabel("More information on graph edge types");
        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Info button added by Zhou to show edge types
        JButton infoBtn = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        infoBtn.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Clock info button to show edge types instructions - Zhou
        infoBtn.addActionListener(e -> {
            helpSet.setHomeID("graph_edge_types");
            HelpBroker broker = helpSet.createHelpBroker();
            ActionListener listener = new CSH.DisplayHelpFromSource(broker);
            listener.actionPerformed(e);
        });
        
        // Add to buttonPanel
        buttonPanel.add(infoLabel);
        buttonPanel.add(infoBtn);

        // Add top level componments to container
        add(createTopMenuBar(), BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        graphEditorOptionsPanel.reset();
    }

    // Top menu bar, contains "Save As" and "Graph"
    private JMenuBar createTopMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        // Add the save options - Zhou
        JMenu saveMenu = createSaveMenu(this);
        menuBar.add(saveMenu);
        
        // Add the graph options
        JMenu graphMenu = createGraphMenu();
        menuBar.add(graphMenu);
        
        return menuBar;
    }
    
    // Graph type selection
    private void graphTypeSelection() {
        for (GraphSelectionWrapper.Type type : GraphSelectionWrapper.Type.values()) {
            graphTypeCombo.addItem(type);
        }

        graphTypeCombo.setSelectedItem(wrapper.getType());

        graphTypeCombo.addActionListener(e -> {
            GraphSelectionWrapper.Type selectedItem = (GraphSelectionWrapper.Type) graphTypeCombo.getSelectedItem();
            wrapper.setType(selectedItem);
            setSelectedGraphType(selectedItem);
        });
    }
    
    private void setSelectedGraphType(GraphSelectionWrapper.Type type) {
        if (type == GraphSelectionWrapper.Type.Subgraph) {
            graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Adjacents) {
            graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Adjacents_of_Adjacents) {
            graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Adjacents_of_Adjacents_of_Adjacents) {
            graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Pag_Y_Structures) {
            graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Markov_Blankets) {
            graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Y_Structures) {
            graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Pag_Y_Structures) {
            graphEditorOptionsPanel.setNLabel("");
        }

        if (type == GraphSelectionWrapper.Type.Treks) {
            graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Trek_Edges) {
            graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Paths) {
            graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Path_Edges) {
            graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Directed_Paths) {
            graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Directed_Path_Edges) {
            graphEditorOptionsPanel.setNLabel("Path Length");
        }

        if (type == GraphSelectionWrapper.Type.Indegree) {
            graphEditorOptionsPanel.setNLabel("Indegree");
        }

        if (type == GraphSelectionWrapper.Type.Out_Degree) {
            graphEditorOptionsPanel.setNLabel("Outdegree");
        }

        if (type == GraphSelectionWrapper.Type.Degree) {
            graphEditorOptionsPanel.setNLabel("");
        }
    }

    // Create scroll pane for each graph
    private JPanel workbenchScrollsPanel(GraphSelectionWrapper wrapper) {
        //tabbedPane.removeAll();

        List<JScrollPane> workbenchScrolls = new ArrayList<>();

        //workbenchScrolls.clear();
        
        List<Graph> graphs = wrapper.getGraphs();

        for (int i = 0; i < graphs.size(); i++) {
            GraphWorkbench workbench = new GraphWorkbench();
            workbenches.add(workbench);

            workbench.addPropertyChangeListener(new PropertyChangeListener() {
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("modelChanged".equals(evt.getPropertyName())) {
                        firePropertyChange("modelChanged", null, null);
                    }
                }
            });

            JScrollPane workbenchScroll = new JScrollPane(workbench);
            workbenchScroll.setPreferredSize(new Dimension(520, 560));

            workbenchScrolls.add(workbenchScroll);
        }

//        wrapper.setSelectionGraphs(selectionGraphs);

        for (int i = 0; i < workbenchScrolls.size(); i++) {
            tabbedPane.add("" + (i + 1), workbenchScrolls.get(i));
        }

        tabbedPane.addChangeListener(e -> {
            if (e.getSource() instanceof JTabbedPane) {
                JTabbedPane panel = (JTabbedPane) e.getSource();
                int selectedIndex = panel.getSelectedIndex();
                selectedIndex = selectedIndex == -1 ? 0 : selectedIndex;
                graphAction.setGraph(wrapper.getGraphs().get(selectedIndex), getWorkbench());
                triplesAction.setGraph(wrapper.getGraphs().get(selectedIndex), getWorkbench());
            }
        });
        
        // Show graph in each tabbed pane
        tabbedPaneGraphs(wrapper);
        
        // Make the tabbedPane auto resize - Zhou
        workbenchScrollsPanel.setLayout(new BorderLayout());
        workbenchScrollsPanel.add(tabbedPane, BorderLayout.CENTER);
        
        workbenchScrollsPanel.validate();
        
        return workbenchScrollsPanel;
    }


    private void tabbedPaneGraphs(GraphSelectionWrapper wrapper) {
        wrapper.calculateSelection();

//        if (wrapper.getSelectedVariables() == null || wrapper.getSelectedVariables().isEmpty()) {
//            first500Variables(wrapper);
//        }

        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            Graph selection = wrapper.getSelectionGraph(i);

//            if (selection.getNumNodes() > 500) {
//                throw new IllegalArgumentException("That is too many nodes for me to display ("
//                        + selection.getNumNodes() + ") I can only go up to 500 nodes.\n"
//                        + "Try a smaller selection.");
//            }

            if (!layoutGraph.isEmpty()) {
                for (Node node : selection.getNodes()) {
                    List<Integer> center = layoutGraph.get(node.getName());

                    if (center != null) {
                        node.setCenter(center.get(0), center.get(1));
                    }
                }
            } else {
//                GraphUtils.circleLayout(selection, 200, 200, 150);
//                GraphUtils.fruchtermanReingoldLayout(selection);
            }

            GraphWorkbench workbench = getWorkbench(i);
            workbench.setGraph(selection);
            List<Node> selected = wrapper.getSelectedVariables();

            for (Node node : selected) {
                if (wrapper.getHighlightInEditor().contains(node)
                        && workbench.getGraph().containsNode(node)) {
                    workbench.selectNode(node);
                }
            }
        }
    }

    /**
     * File save menu - Zhou
     * @param editable
     * @param comp
     * @return 
     */
    private JMenu createSaveMenu(GraphEditable editable) { 
        JMenu save = new JMenu("Save As");

        save.add(new SaveGraph(editable, "Graph XML...", SaveGraph.Type.xml));
        save.add(new SaveGraph(editable, "Graph Text...", SaveGraph.Type.text));
        save.add(new SaveGraph(editable, "Graph Json...", SaveGraph.Type.json));
        save.add(new SaveGraph(editable, "R...", SaveGraph.Type.r));
        save.add(new SaveGraph(editable, "Dot...", SaveGraph.Type.dot));

        return save;
    }

    private JMenu createGraphMenu() {
        JMenu graph = new JMenu("Graph");

        graphAction = new GraphPropertiesAction(wrapper.getGraphs().get(0), getWorkbench());
        graph.add(graphAction);
        graph.add(new PathsAction(getWorkbench()));
//        graph.add(new DirectedPathsAction(getWorkbench()));
//        graph.add(new TreksAction(getWorkbench()));
//        graph.add(new AllPathsAction(getWorkbench()));
//        graph.add(new NeighborhoodsAction(getWorkbench()));
        triplesAction = new TriplesAction(wrapper.getGraphs().get(0), getWorkbench());
        graph.add(triplesAction);

        return graph;
    }

    /**
     * Sets the name of this editor.
     */
    @Override
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

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

    @Override
    public void pasteSubsession(List sessionElements, Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        for (Object o : sessionElements) {
            if (o instanceof GraphNode) {
                Node modelNode = (Node) o;
                getWorkbench().selectNode(modelNode);
            }
        }

        getWorkbench().selectConnectingEdges();

    }

    @Override
    public GraphWorkbench getWorkbench() {
        int selectedIndex = tabbedPane.getSelectedIndex();
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

    public GraphWorkbench getWorkbench(int selectionIndex) {
        Graph layout = workbenches.get(prevSelected).getGraph();
        setLayoutGraph(layout);

        int selectedIndex = tabbedPane.getSelectedIndex();
        if (selectedIndex == -1) {
            selectedIndex = 0;
        }

        Graph graph = workbenches.get(selectedIndex).getGraph();

        for (Node node : graph.getNodes()) {
            List<Integer> center = layoutGraph.get(node.getName());

            if (center != null) {
                node.setCenter(center.get(0), center.get(1));
            }
        }

        prevSelected = selectedIndex;

        return workbenches.get(selectionIndex);
    }

    public void saveLayout() {
        int selectedIndex = tabbedPane.getSelectedIndex();
        if (selectedIndex == -1) {
            selectedIndex = 0;
        }
        Graph layout = wrapper.getSelectionGraph(selectedIndex);
        setLayoutGraph(layout);
    }

    @Override
    public Graph getGraph() {
        int selectedIndex = tabbedPane.getSelectedIndex();
        if (selectedIndex == -1) {
            selectedIndex = 0;
        }
        return wrapper.getSelectionGraph(selectedIndex);
    }

    @Override
    public void setGraph(Graph graph) {
        wrapper.setGraphs(Collections.singletonList(graph));
        graphEditorOptionsPanel.reset();
        getWorkbench().setGraph(graph);
    }

    public void replace(List<Graph> graphs) {
        for (Graph graph : graphs) {
            for (Node node : graph.getNodes()) {
                List<Integer> center = layoutGraph.get(node.getName());

                if (center != null) {
                    node.setCenter(center.get(0), center.get(1));
                }
            }
        }
        if (graphs == null) {
            graphs = new ArrayList<>();
        }
        wrapper.setGraphs(graphs);
        workbenchScrollsPanel(wrapper);
        tabbedPaneGraphs(wrapper);
        graphEditorOptionsPanel.reset();
    }

    private void setLayoutGraph(Graph graph) {
        layoutGraph.clear();

        for (Node node : graph.getNodes()) {
            List<Integer> center = new ArrayList<>();
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
        private JList<Node> sourceList;

        // The list of selected.
        private JList<Node> selectedList;

        private DualListPanel dualListPanel;

        // The font to render fields in.
        private final Font _font = new Font("Dialog", Font.PLAIN, 12);

        private final GraphSelectionWrapper graphSelectionWrapper;

        /**
         * Constructs the editor given the <code>Parameters</code> and the
         * <code>DataModel</code> that should be used.
         *
         * @param graphSelectionWrapper
         */
        public GraphEditorOptionsPanel(GraphSelectionWrapper graphSelectionWrapper) {
            if (graphSelectionWrapper == null) {
                throw new NullPointerException("Graph wrapper must not be null");
            }

            this.nLabel = new JLabel("Degree");
            this.equals = new JRadioButton("Equals");
            this.atMost = new JRadioButton("At Most");
            this.atLeast = new JRadioButton("At Least");
            this.nField = new IntTextField(wrapper.getN(), 2);
            this.graphSelectionWrapper = graphSelectionWrapper;
            this.dualListPanel = new DualListPanel();
            this.sourceList = dualListPanel.getSourceList();
            this.selectedList = dualListPanel.getSelectedList();

            initList(sourceList);
            initList(selectedList);

            dualListPanel.getMoveToSource().addActionListener((e) -> {
                for (GraphWorkbench workbench : workbenches) {
                    workbench.deselectAll();
                }

                VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
                VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();

                List<Node> selected = getSelected(getSelectedList());
                selectedModel.removeAll(selected);
                sourceModel.addAll(selected);
                wrapper.setSelectedVariables(getSelected());
                getSelectedList().setSelectedIndices(new int[0]);
                getSourceList().setSelectedIndices(new int[0]);
            });
            dualListPanel.getMoveToselector().addActionListener((e) -> {
                for (GraphWorkbench workbench : workbenches) {
                    workbench.deselectAll();
                }

                VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
                VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();

                List<Node> selected = getSelected(getSourceList());
                sourceModel.removeAll(selected);
                selectedModel.addAll(selected);

                getSelectedList().setSelectedIndices(new int[0]);
                getSourceList().setSelectedIndices(new int[0]);
                wrapper.setSelectedVariables(getSelected());
            });

            VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
            VariableListModel variableModel = (VariableListModel) getSourceList().getModel();

            // if selected are already set use'em.
            List<Node> selectedNodes = wrapper.getSelectedVariables();
            List<Node> initVars = new ArrayList<>(wrapper.getVariables());
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

            ButtonGroup group2 = new ButtonGroup();

            group2.add(equals);
            group2.add(atMost);
            group2.add(atLeast);

            if (wrapper.getNType().equals(GraphSelectionWrapper.nType.equals.toString())) {
                equals.setSelected(true);
            } else if (wrapper.getNType().equals(GraphSelectionWrapper.nType.atMost.toString())) {
                atMost.setSelected(true);
            } else {
                atLeast.setSelected(true);
            }

            equals.addActionListener((e) -> {
                wrapper.setNType(GraphSelectionWrapper.nType.equals);
            });
            atMost.addActionListener((e) -> {
                wrapper.setNType(GraphSelectionWrapper.nType.atMost);
            });
            atLeast.addActionListener((e) -> {
                wrapper.setNType(GraphSelectionWrapper.nType.atLeast);
            });
            nField.setFilter((value, oldValue) -> {
                try {
                    wrapper.setN(value);
                    return value;
                } catch (Exception e) {
                    return oldValue;
                }
            });

            initGUI();
        }

        private void initGUI() {
            setLayout(new BorderLayout());

            Box box = Box.createHorizontalBox();
            box.add(new JLabel("Please select variables:"));
            box.add(Box.createGlue());

            Box northComp = Box.createVerticalBox();
            northComp.add(box);
            northComp.add(Box.createVerticalStrut(10));

            Box varManupBox = Box.createHorizontalBox();
            varManupBox.add(new VariableManipulationPanel(createSortButton(), createTextButton()));
            varManupBox.add(Box.createGlue());

            Box graphTypeBox = Box.createHorizontalBox();
            
            graphTypeSelection();
            
            graphTypeBox.add(new GraphTypePanel(atMost, equals, nField, nLabel, graphTypeCombo));
            graphTypeBox.add(Box.createGlue());

            Box southComp = Box.createVerticalBox();
            southComp.add(Box.createVerticalStrut(20));
            southComp.add(varManupBox);
            southComp.add(Box.createVerticalStrut(20));
            southComp.add(graphTypeBox);

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.add(northComp, BorderLayout.NORTH);
            mainPanel.add(dualListPanel, BorderLayout.CENTER);
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

            public GraphTypePanel(JRadioButton atMost, JRadioButton equals, IntTextField nField, JLabel nLabel, JComboBox<GraphSelectionWrapper.Type> graphTypeCombo) {
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
                pathLengthBtnGrp.add(equals);
                pathLengthBtnGrp.add(atMost);
                pathLengthBtnGrp.add(atLeast);

                GroupLayout layout = new GroupLayout(this);
                this.setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addComponent(selectGraphTypeLbl)
                                .addComponent(graphTypeCombo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(nLabel)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(equals)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(atMost)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(atLeast)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(nField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE))
                );
                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(selectGraphTypeLbl)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(graphTypeCombo, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                .addComponent(nLabel)
                                                .addComponent(equals)
                                                .addComponent(atMost)
                                                .addComponent(atLeast)
                                                .addComponent(nField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)))
                );
            }

        }

        private class VariableManipulationPanel extends JPanel {

            private static final long serialVersionUID = 4538277448583296121L;

            private final JButton sortBnt;
            private final JButton textInputBnt;

            public VariableManipulationPanel(JButton sortBnt, JButton textInputBnt) {
                this.sortBnt = sortBnt;
                this.textInputBnt = textInputBnt;

                initComponents();
            }

            private void initComponents() {
                GroupLayout layout = new GroupLayout(this);
                this.setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(sortBnt)
                                        .addGap(30, 30, 30)
                                        .addComponent(textInputBnt))
                );

                layout.linkSize(SwingConstants.HORIZONTAL, new java.awt.Component[]{sortBnt, textInputBnt});

                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(sortBnt)
                                        .addComponent(textInputBnt))
                );
            }

        }

        private JButton createSortButton() {
            JButton sort = new JButton("Sort Variables");
            sort.setFont(sort.getFont().deriveFont(11f));
            sort.setMargin(new Insets(3, 3, 3, 3));
            sort.addActionListener((e) -> {
                VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
                VariableListModel sourceModel = (VariableListModel) getSourceList().getModel();
                selectedModel.sort();
                sourceModel.sort();
            });

            return sort;
        }

        private JButton createTextButton() {
            GraphSelectionTextInputAction action
                    = new GraphSelectionTextInputAction(GraphEditorOptionsPanel.this,
                            wrapper, sourceList, selectedList);
            JButton sort = new JButton(action);
            sort.setFont(sort.getFont().deriveFont(11f));
            sort.setMargin(new Insets(3, 3, 3, 3));

            return sort;
        }

        private void initList(JList<Node> list) {
            list.setModel(new VariableListModel());
            list.setFont(getFONT());
            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            list.setVisibleRowCount(8);
        }

        public Font getFONT() {
            return _font;
        }

        public void setNLabel(String label) {
            if (label.equals("")) {
                nLabel.setEnabled(false);
                equals.setEnabled(false);
                atMost.setEnabled(false);
                atLeast.setEnabled(false);
                nField.setEnabled(false);
            } else {
                nLabel.setEnabled(true);
                equals.setEnabled(true);
                atMost.setEnabled(true);
                atLeast.setEnabled(true);
                nField.setEnabled(true);
                nLabel.setText(label);
            }
        }

        private List<Node> getSelected() {
            ListModel<Node> model = getSelectedList().getModel();
            List<Node> selected = new ArrayList<>(model.getSize());
            for (int i = 0; i < model.getSize(); i++) {
                Node node = model.getElementAt(i);
                selected.add(node);
            }

            return selected;
        }

        private List<Node> getSelected(JList<Node> list) {
            List<Node> selected = list.getSelectedValuesList();
            List<Node> selectedList = new ArrayList<>(selected == null ? 0 : selected.size());
            if (selected != null) {
                for (Object o : selected) {
                    selectedList.add((Node) o);
                }
            }
            return selectedList;
        }

        public void setSelected(List<Node> selected) {
            VariableListModel selectedModel = (VariableListModel) selectedList.getModel();
            VariableListModel sourceModel = (VariableListModel) sourceList.getModel();
            List<Node> oldSelected = wrapper.getSelectedVariables();
            selectedModel.removeAll(oldSelected);
            sourceModel.addAll(oldSelected);
            selectedModel.addAll(selected);
            sourceModel.removeAll(selected);
            wrapper.setSelectedVariables(selected);
            selectedList.setSelectedIndices(new int[0]);
            sourceList.setSelectedIndices(new int[0]);
        }

        public void reset() {
            VariableListModel selectedModel = (VariableListModel) getSelectedList().getModel();
            VariableListModel variableModel = (VariableListModel) getSourceList().getModel();
            List<Node> variableNames = wrapper.getVariables();

            // if regressors are already set use'em.
            selectedModel.removeAll();
            variableModel.removeAll();
            variableModel.addAll(variableNames);
            variableModel.removeAll(wrapper.getSelectedVariables());
            selectedModel.addAll(wrapper.getSelectedVariables());

            getSelectedList().setSelectedIndices(new int[0]);
            getSourceList().setSelectedIndices(new int[0]);
        }

        private JList<Node> getSourceList() {
            return sourceList;
        }

        private JList<Node> getSelectedList() {
            return selectedList;
        }

        //========================== Inner classes (a lot of'em) =========================================//
        private class TargetListener extends DropTargetAdapter {

            @Override
            public void drop(DropTargetDropEvent dtde) {
                Transferable t = dtde.getTransferable();
                Component comp = dtde.getDropTargetContext().getComponent();

                if (comp instanceof JList || comp instanceof JTextField) {
                    try {
                        for (GraphWorkbench workbench : workbenches) {
                            workbench.deselectAll();
                        }

                        // if response, remove everything first
                        JList<Node> list = (JList<Node>) comp;

                        VariableListModel model = (VariableListModel) list.getModel();
                        ListTransferable listTransferable = new ListTransferable(new ArrayList<>());
                        List<Node> transferData = (List<Node>) t.getTransferData(listTransferable.getDataFlavor());

                        List<Node> elements = new ArrayList<>();

                        for (int i = 0; i < model.getSize(); i++) {
                            elements.add((Node) model.getElementAt(i));
                        }

                        elements.retainAll(transferData);

                        if (!elements.isEmpty()) {
                            dtde.getDropTargetContext().dropComplete(false);
                            return;
                        }

                        for (Node c : transferData) {
                            model.removeFirst(c);
                        }

                        model.addAll(transferData);

                        wrapper.setSelectedVariables(getSelected());
                        dtde.getDropTargetContext().dropComplete(true);

                    } catch (Exception exception) {
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
            public void dragDropEnd(DragSourceDropEvent evt) {
                if (evt.getDropSuccess()) {
                    Component comp = evt.getDragSourceContext().getComponent();
                    Transferable t = evt.getDragSourceContext().getTransferable();
                    if (t instanceof ListTransferable) {
                        try {
                            //noinspection unchecked
                            List<Node> o = (List<Node>) t.getTransferData(new ListTransferable(new ArrayList()).getDataFlavor());
                            if (comp instanceof JList) {
                                JList<Node> list = (JList<Node>) comp;
                                VariableListModel model = (VariableListModel) list.getModel();
                                for (Node c : o) {
                                    model.removeFirst(c);
                                }
                            } else {
                                JTextField pane = (JTextField) comp;
                                pane.setText(null);
                            }

                            wrapper.setSelectedVariables(getSelected());
                        } catch (Exception exception) {
                            exception.printStackTrace(System.err);
                        }
                    }
                }
            }

            @Override
            public void dragGestureRecognized(DragGestureEvent dge) {
                Component comp = dge.getComponent();
                List<Node> selected = null;
                if (comp instanceof JList) {
                    JList<Node> list = (JList<Node>) comp;
                    selected = list.getSelectedValuesList();
                }
                if (selected != null) {
                    List<Node> nodes = new ArrayList<>();
                    nodes.addAll(selected);
                    ListTransferable t = new ListTransferable(nodes);
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
            public Object getElementAt(int index) {
                return this.delegate.get(index);
            }

            public void remove(Node element) {
                int index = this.delegate.indexOf(element);
                if (0 <= index) {
                    this.delegate.remove(index);
                    this.fireIntervalRemoved(this, index, index);
                }
            }

            public void add(Node element) {
                this.delegate.add(element);
                this.fireIntervalAdded(this, this.delegate.size(), this.delegate.size());
            }

            public void removeFirst(Node element) {
                this.delegate.remove(element);
                this.fireContentsChanged(this, 0, this.delegate.size());
            }

            public void removeAll(List<? extends Node> elements) {
                this.delegate.removeAll(elements);
                this.fireContentsChanged(this, 0, this.delegate.size());
            }

            public void addAll(List<? extends Node> elements) {
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
            } catch (Exception exception) {
                exception.printStackTrace(System.err);
                return null;
            }
        }

        /**
         * A basic transferable.
         */
        private class ListTransferable implements Transferable {

            private final DataFlavor FLAVOR = getListDataFlavor();

            private List<Node> nodes;

            public DataFlavor getDataFlavor() {
                return FLAVOR;
            }

            public ListTransferable(List<Node> nodes) {
                if (nodes == null) {
                    throw new NullPointerException();
                }
                this.nodes = nodes;
            }

            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{FLAVOR};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return flavor == FLAVOR;
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
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
        public GraphSelectionTextInputAction(JComponent component, GraphSelectionWrapper wrapper,
                JList<Node> sourceList, JList<Node> selectedList) {
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
        public void actionPerformed(ActionEvent actionEvent) {
            Box b = Box.createVerticalBox();

            textArea = new JTextArea();

            textArea.setText(wrapper.getDialogText());

            JScrollPane scroll = new JScrollPane(textArea);
            scroll.setPreferredSize(new Dimension(150, 300));
            scroll.setBorder(new CompoundBorder(new LineBorder(Color.GRAY, 1), new EmptyBorder(5, 5, 5, 5)));

            Box b2 = Box.createVerticalBox();
            b2.add(Box.createVerticalStrut(10));
            b2.add(new JLabel("Please enter a list of variable names you'd like to have"));
            b2.add(new JLabel("selected, one per line. Those that exist in the graph will"));
            b2.add(new JLabel("be selected; the rest will be disgarded. You can paste a"));
            b2.add(new JLabel("list in from the clipboard or type it. This list will be"));
            b2.add(new JLabel("saved."));
            b2.add(Box.createVerticalStrut(10));
            b2.add(scroll);
            textArea.setCaretPosition(0);
            b.add(b2);

            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.add(b);

            EditorWindow window = new EditorWindow(panel,
                    "Input Variable Names as Text", "Select", false, component);
            DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
            window.setVisible(true);

            window.addActionListener(e -> {
                wrapper.setDialogText(textArea.getText());
                GraphEditorOptionsPanel.VariableListModel selectedModel = (GraphEditorOptionsPanel.VariableListModel) selectedList.getModel();
                GraphEditorOptionsPanel.VariableListModel sourceModel = (GraphEditorOptionsPanel.VariableListModel) sourceList.getModel();
                List<Node> oldSelected = wrapper.getSelectedVariables();
                selectedModel.removeAll(oldSelected);
                sourceModel.addAll(oldSelected);
                List<Node> newSelected = selectedVars();
                selectedModel.addAll(newSelected);
                sourceModel.removeAll(newSelected);
                wrapper.setSelectedVariables(newSelected);
                selectedList.setSelectedIndices(new int[0]);
                sourceList.setSelectedIndices(new int[0]);
            });
        }

        public List<Node> selectedVars() {
            List<Node> nodes = new ArrayList<>();

            try {
                String text = textArea.getText();

                BufferedReader r = new BufferedReader(new CharArrayReader(text.toCharArray()));
                String line = null;

                while ((line = r.readLine()) != null) {
                    Node node = wrapper.getOriginalGraph().getNode(line);

                    if (node != null) {
                        nodes.add(node);
                    }
                }

                return nodes;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Required by the AbstractAction interface; does nothing.
         */
        @Override
        public void lostOwnership(Clipboard clipboard, Transferable contents) {
        }
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     * <code>getTriplesList</code>
     */
    @Override
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
        names.add("Underlines");
        names.add("Dotted Underlines");
        return names;
    }

    /**
     * @return the list of triples corresponding to
     * <code>getTripleClassificationNames</code> for the given node.
     */
    @Override
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        Graph graph = getGraph();
        triplesList.add(GraphUtils.getUnderlinedTriplesFromGraph(node, graph));
        triplesList.add(GraphUtils.getDottedUnderlinedTriplesFromGraph(node, graph));
        return triplesList;
    }

}