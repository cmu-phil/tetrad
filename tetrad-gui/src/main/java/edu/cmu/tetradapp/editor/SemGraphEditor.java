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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.session.DelegatesEditing;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.model.SemGraphWrapper;
import edu.cmu.tetradapp.util.BootstrapTable;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.*;
import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
public final class SemGraphEditor extends JPanel
        implements GraphEditable, LayoutEditable, DelegatesEditing, IndTestProducer {

    private static final long serialVersionUID = 6837233499169689575L;

    private GraphWorkbench workbench;
    private SemGraphWrapper semGraphWrapper;
    private JMenuItem errorTerms;
    private Parameters parameters;

    private JScrollPane graphEditorScroll = new JScrollPane();
    private Box tablePaneBox;
    
    //===========================CONSTRUCTOR========================//
    public SemGraphEditor(final SemGraphWrapper semGraphWrapper) {
        if (semGraphWrapper == null) {
            throw new NullPointerException();
        }

        setLayout(new BorderLayout());
        this.semGraphWrapper = semGraphWrapper;
        this.parameters = semGraphWrapper.getParameters();

        initUI(semGraphWrapper);
    }

    //===========================PUBLIC METHODS======================//
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
        List<Component> selectedComponents = getWorkbench().getSelectedComponents();
        List<TetradSerializable> selectedModelComponents = new ArrayList<>();

        for (Object comp : selectedComponents) {
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

    @Override
    public JComponent getEditDelegate() {
        return getWorkbench();
    }

    @Override
    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    @Override
    public Graph getGraph() {
        return workbench.getGraph();
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return workbench.getModelEdgesToDisplay();
    }

    @Override
    public Map getModelNodesToDisplay() {
        return workbench.getModelNodesToDisplay();
    }

    @Override
    public void setGraph(Graph graph) {
        try {
            SemGraph semGraph = new SemGraph(graph);
            workbench.setGraph(semGraph);
        } catch (Exception e) {
            throw new RuntimeException("Not a SEM graph.", e);
        }
    }

    private SemGraphWrapper getSemGraphWrapper() {
        return semGraphWrapper;
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
        ((SemGraph) graph).setShowErrorTerms(false);
        getWorkbench().layoutByGraph(graph);
        ((SemGraph) graph).resetErrorPositions();
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
    private void initUI(SemGraphWrapper semGraphWrapper) {
        Graph graph = semGraphWrapper.getGraph();
        
        workbench = new GraphWorkbench(graph);
        
        workbench.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            String propertyName = evt.getPropertyName();
            
            // Update the bootstrap table if there's changes to the edges or node renaming
            String[] events = { "graph", "edgeAdded", "edgeRemoved" };
            
            if (Arrays.asList(events).contains(propertyName)) {
                if (getWorkbench() != null) {
                    Graph targetGraph = (Graph) getWorkbench().getGraph();
                    
                    // Update the semGraphWrapper
                    semGraphWrapper.setGraph(targetGraph);
                    // Also need to update the UI
                    updateBootstrapTable(targetGraph);
                }
            } else if ("modelChanged".equals(propertyName)) {
                firePropertyChange("modelChanged", null, null);
            }
        });
        
        // Graph menu at the very top of the window
        JMenuBar menuBar = createGraphMenuBar();
        
        // Add the model selection to top if multiple models
        modelSelectin(semGraphWrapper);
        
        // topBox Left side toolbar
        SemGraphToolbar graphToolbar = new SemGraphToolbar(getWorkbench());
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
     * Updates the graph in workbench when changing graph model
     * 
     * @param graph 
     */
    private void updateGraphWorkbench(Graph graph) {
        workbench = new GraphWorkbench(graph);
        graphEditorScroll.setViewportView(workbench);
        
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
    
    /**
     * Creates the UI component for choosing from multiple graph models
     * 
     * @param semGraphWrapper 
     */
    private void modelSelectin(SemGraphWrapper semGraphWrapper) {
        int numModels = semGraphWrapper.getNumModels();

        if (numModels > 1) {
            List<Integer> models = new ArrayList<>();
            for (int i = 0; i < numModels; i++) {
                models.add(i + 1);
            }
            
            final JComboBox<Integer> comboBox = new JComboBox(models.toArray());

            // Remember the selected model on reopen
            comboBox.setSelectedIndex(semGraphWrapper.getModelIndex());
            
            comboBox.addActionListener((ActionEvent e) -> {
                semGraphWrapper.setModelIndex(comboBox.getSelectedIndex());
                
                // Update the graph workbench
                updateGraphWorkbench(semGraphWrapper.getGraph());
                
                // Update the bootstrap table
                updateBootstrapTable(semGraphWrapper.getGraph());
            });

            // Put together
            Box modelSelectionBox = Box.createHorizontalBox();
            modelSelectionBox.add(new JLabel("Using model "));
            modelSelectionBox.add(comboBox);
            modelSelectionBox.add(new JLabel(" from "));
            modelSelectionBox.add(new JLabel(semGraphWrapper.getModelSourceName()));
            modelSelectionBox.add(Box.createHorizontalStrut(20));
            modelSelectionBox.add(Box.createHorizontalGlue());

            // Add to upper right
            add(modelSelectionBox, BorderLayout.EAST);
        }
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

        JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
        JMenuItem paste = new JMenuItem(new PasteSubgraphAction(this));

        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        paste.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));

        edit.add(copy);
        edit.add(paste);

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

        errorTerms = new JMenuItem();

        if (getSemGraph().isShowErrorTerms()) {
            errorTerms.setText("Hide Error Terms");
        } else {
            errorTerms.setText("Show Error Terms");
        }

        errorTerms.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JMenuItem menuItem = (JMenuItem) e.getSource();

                if ("Hide Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Show Error Terms");
                    getSemGraph().setShowErrorTerms(false);
                } else if ("Show Error Terms".equals(menuItem.getText())) {
                    menuItem.setText("Hide Error Terms");
                    getSemGraph().setShowErrorTerms(true);
                }
            }
        });

        graph.add(errorTerms);
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
                correlationExogenousVariables();
                getWorkbench().invalidate();
                getWorkbench().repaint();
            }
        });

        uncorrelateExogenous.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uncorrelateExogenousVariables();
                getWorkbench().invalidate();
                getWorkbench().repaint();
            }
        });

        JMenuItem randomGraph = new JMenuItem("Random Graph");
        graph.add(randomGraph);

        randomGraph.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final GraphParamsEditor editor = new GraphParamsEditor();
                editor.setParams(parameters);

                EditorWindow editorWindow = new EditorWindow(editor, "Edit Random Graph Parameters",
                        "Done", false, SemGraphEditor.this);

                DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                editorWindow.pack();
                editorWindow.setVisible(true);

                editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                    public void internalFrameClosed(InternalFrameEvent e1) {
                        EditorWindow window = (EditorWindow) e1.getSource();

                        if (window.isCanceled()) {
                            return;
                        }

                        RandomUtil.getInstance().setSeed(new Date().getTime());
                        Graph graph1 = edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph(), parameters);

                        boolean addCycles = parameters.getBoolean("randomAddCycles", false);

                        if (addCycles) {
                            int newGraphNumMeasuredNodes = parameters.getInt("newGraphNumMeasuredNodes", 10);
                            int newGraphNumEdges = parameters.getInt("newGraphNumEdges", 10);
                            graph1 = GraphUtils.cyclicGraph2(newGraphNumMeasuredNodes, newGraphNumEdges, 6);
                        }

                        getWorkbench().setGraph(graph1);
                    }
                });
            }
        });

        graph.addSeparator();
        graph.add(new JMenuItem(new SelectBidirectedAction(getWorkbench())));
//        graph.add(new JMenuItem(new SelectUndirectedAction(getWorkbench())));

//        graph.addSeparator();
//        IndependenceFactsAction action = new IndependenceFactsAction(
//                JOptionUtils.centeringComp(), this, "D Separation Facts...");
//        graph.add(action);
        return graph;
    }

    private SemGraph getSemGraph() {
        return (SemGraph) semGraphWrapper.getGraph();
    }

    private void correlationExogenousVariables() {
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

    private void uncorrelateExogenousVariables() {
        Graph graph = getWorkbench().getGraph();

        Set<Edge> edges = graph.getEdges();

        for (Edge edge : edges) {
            if (Edges.isBidirectedEdge(edge)) {
                try {
                    graph.removeEdge(edge);
                } catch (Exception e) {
                }
            }
        }
    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return new IndTestDSep(workbench.getGraph());
    }

    @Override
    public void pasteSubsession(List sessionElements, java.awt.Point upperLeft) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}