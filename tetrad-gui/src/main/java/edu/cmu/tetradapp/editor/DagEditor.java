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
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.DagWrapper;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.util.BootstrapTable;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
public final class DagEditor extends JPanel
        implements GraphEditable, LayoutEditable, DelegatesEditing, IndTestProducer {

    private static final long serialVersionUID = -6082746735835257666L;

    private GraphWorkbench workbench;
    private DagWrapper dagWrapper;
    private Parameters parameters;
    
    private JScrollPane graphEditorScroll = new JScrollPane();
    private Box tablePaneBox;

    public DagEditor(DagWrapper dagWrapper) {
        setLayout(new BorderLayout());
        this.dagWrapper = dagWrapper;
        this.parameters = dagWrapper.getParameters();

        
        initUI(dagWrapper);

        getWorkbench().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();

                // Update the bootstrap table if there's changes to the edges or node renaming
                String[] events = { "graph", "edgeAdded", "edgeRemoved" };
                
                if (Arrays.asList(events).contains(propertyName)) {
                    Graph graph = (Graph) getWorkbench().getGraph();

                    if (getWorkbench() != null && dagWrapper != null) {
                        // Update the graphWrapper
                        dagWrapper.setGraph(graph);
                        // Also need to update the UI
                        updateBootstrapTable(graph);
                    }
                } else if ("modelChanged".equals(propertyName)) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });
    }

    private void initUI(DagWrapper dagWrapper) {
        Graph graph = dagWrapper.getGraph();
        
        workbench = new GraphWorkbench(graph);
        
        // Graph menu at the very top of the window
        JMenuBar menuBar = createGraphMenuBar();
        
        // Add the model selection to top if multiple models
        modelSelectin(dagWrapper);
        
        // topBox Left side toolbar
        DagGraphToolbar graphToolbar = new DagGraphToolbar(getWorkbench());
        
        // topBox right side graph editor
        
        graphEditorScroll.setPreferredSize(new Dimension(750, 450));
        graphEditorScroll.setViewportView(workbench);

        // topBox contains the topGraphBox and the instructionBox underneath
        Box topBox = Box.createVerticalBox();
        
        // topGraphBox contains the vertical graph toolbar and graph editor
        Box topGraphBox = Box.createHorizontalBox();
        topGraphBox.add(graphToolbar);
        topGraphBox.add(graphEditorScroll);

        // Instruction with info button 
        Box instructionBox = Box.createHorizontalBox();
        
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
        add(splitPane, BorderLayout.SOUTH);
        
        // Performs relayout. 
        // It means invalid content is asked for all the sizes and 
        // all the subcomponents' sizes are set to proper values by LayoutManager.
        validate();
    }
    
    private void updateGraphWorkbench(Graph graph) {
        workbench = new GraphWorkbench(graph);
        graphEditorScroll.setViewportView(workbench);
        
        validate();
    }
    
    private void updateBootstrapTable(Graph graph) {
        tablePaneBox.removeAll();
        JScrollPane tablePane = BootstrapTable.renderBootstrapTable(graph);
        tablePaneBox.add(tablePane);
        
        validate();
    }
    
    private void modelSelectin(DagWrapper dagWrapper) {
        int numModels = dagWrapper.getNumModels();

        if (numModels > 1) {
            final JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < numModels; i++) {
                comp.addItem(i + 1);
            }

            comp.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    dagWrapper.setModelIndex(((Integer) comp.getSelectedItem()).intValue() - 1);
                    
                    // Update the graph workbench
                    updateGraphWorkbench(dagWrapper.getGraph());
  
                    // Update the bootstrap table
                    updateBootstrapTable(dagWrapper.getGraph());
                }
            });

            comp.setMaximumSize(comp.getPreferredSize());

            Box b = Box.createHorizontalBox();
            b.add(new JLabel("Using model "));
            b.add(comp);
            b.add(new JLabel(" from "));
            b.add(new JLabel(dagWrapper.getModelSourceName()));
            b.add(Box.createHorizontalGlue());

            add(b, BorderLayout.EAST);
        }
    }
    

    /**
     * Sets the name of this editor.
     */
    public final void setName(String name) {
        String oldName = getName();
        super.setName(name);
        firePropertyChange("name", oldName, getName());
    }

    public JComponent getEditDelegate() {
        return getWorkbench();
    }

    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    /**
     * Returns a list of all the SessionNodeWrappers (TetradNodes) and
     * SessionNodeEdges that are model components for the respective
     * SessionNodes and SessionEdges selected in the workbench. Note that the
     * workbench, not the SessionEditorNodes themselves, keeps track of the
     * selection.
     *
     * @return the set of selected model nodes.
     */
    public List getSelectedModelComponents() {
        List<Component> selectedComponents
                = getWorkbench().getSelectedComponents();
        List<TetradSerializable> selectedModelComponents
                = new ArrayList<>();

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

        for (Object sessionElement : sessionElements) {
            if (sessionElement instanceof GraphNode) {
                Node modelNode = (Node) sessionElement;
                getWorkbench().selectNode(modelNode);
            }
        }

        getWorkbench().selectConnectingEdges();
    }

    public Graph getGraph() {
        return workbench.getGraph();
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return workbench.getModelEdgesToDisplay();
    }

    public Map getModelNodesToDisplay() {
        return workbench.getModelNodesToDisplay();
    }

    public void setGraph(Graph graph) {
        try {
            Dag dag = new Dag(graph);
            workbench.setGraph(dag);
        } catch (Exception e) {
            throw new RuntimeException("Not a DAG", e);
        }
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

    private DagWrapper getDagWrapper() {
        return dagWrapper;
    }

    private JMenuBar createGraphMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new GraphFileMenu(this, getWorkbench());
//        JMenu fileMenu = createFileMenu();
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

        JMenuItem randomGraph = new JMenuItem("Random Graph");
        graph.add(randomGraph);

        randomGraph.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final GraphParamsEditor editor = new GraphParamsEditor();
                editor.setParams(parameters);

                EditorWindow editorWindow = new EditorWindow(editor, "Edit Random Graph Parameters",
                        "Done", false, DagEditor.this);

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
                            graph1 = GraphUtils.cyclicGraph2(newGraphNumMeasuredNodes, newGraphNumEdges, 8);
                        }

                        getWorkbench().setGraph(graph1);
                    }
                });
            }
        });

        return graph;
    }

    public IndependenceTest getIndependenceTest() {
        return new IndTestDSep(workbench.getGraph());
    }
}
