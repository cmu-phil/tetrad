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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * Displays a workbench editing workbench area together with a toolbench for
 * editing tetrad-style graphs.
 *
 * @author Aaron Powers
 * @author Joseph Ramsey
 */
public final class DagEditor extends JPanel
        implements GraphEditable, LayoutEditable, DelegatesEditing, IndTestProducer {

    private GraphWorkbench workbench;
    private DagWrapper dagWrapper;
    private Parameters parameters;

    private final HelpSet helpSet;

    public DagEditor(DagWrapper graphWrapper) {
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
        this.dagWrapper = graphWrapper;
        this.parameters = graphWrapper.getParameters();

        editGraph(graphWrapper.getGraph());

        this.getWorkbench().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();

                if ("graph".equals(propertyName)) {
                    Graph _graph = (Graph) evt.getNewValue();

                    if (getWorkbench() != null && getDagWrapper() != null) {
                        getDagWrapper().setGraph(_graph);
                    }
                }
            }
        });

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
                    editGraph(dagWrapper.getGraph());
                    validate();
                }
            });

            comp.setMaximumSize(comp.getPreferredSize());

            Box b = Box.createHorizontalBox();
            b.add(new JLabel("Using model"));
            b.add(comp);
            b.add(new JLabel("from "));
            b.add(new JLabel(dagWrapper.getModelSourceName()));
            b.add(Box.createHorizontalGlue());

            add(b, BorderLayout.NORTH);
        }

        getWorkbench().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("graph".equals(evt.getPropertyName())) {
                    dagWrapper.setGraph((Graph) evt.getNewValue());
                } else if ("modelChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

        validate();
    }

    private void editGraph(Graph graph) {
        this.workbench = new GraphWorkbench(graph);
        
        JMenuBar menuBar = createGraphMenuBar();
        
        // topBox Left side toolbar
        DagGraphToolbar graphToolbar = new DagGraphToolbar(getWorkbench());

        // topBox right side graph editor
        JScrollPane graphEditorScroll = new JScrollPane();
        graphEditorScroll.setPreferredSize(new Dimension(750, 450));
        graphEditorScroll.setViewportView(getWorkbench());

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
                helpSet.setHomeID("graph_edge_types");
                HelpBroker broker = helpSet.createHelpBroker();
                ActionListener listener = new CSH.DisplayHelpFromSource(broker);
                listener.actionPerformed(e);
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
        
        // Bootstrap table view
        // Create object of table and table model
        JTable table = new JTable();
 
        DefaultTableModel tableModel = new DefaultTableModel();

        // Set model into the table object
        table.setModel(tableModel);
        
        // Sorting, enable sorting on all columns except the edge type column (index = 1)
        RowSorter<TableModel> sorter = new TableRowSorter<TableModel>(tableModel) {
            @Override
            public boolean isSortable(int column) {
                return column != 1;
            };
        };
        table.setRowSorter(sorter);

        // Headers
        List<String> columnNames = new LinkedList<>();
        
        // The very left headers: node1, edge type, node2
        columnNames.add(0, "Node1");
        columnNames.add(1, "Interaction");
        columnNames.add(2, "Node2");

        // Edge Type probabilities
        columnNames.add(3, "Ensemble");
        columnNames.add(4, "No edge");
        columnNames.add(5, "-->");
        columnNames.add(6, "<--");
        columnNames.add(7, "o->");
        columnNames.add(8, "<-o");
        columnNames.add(9, "o-o");
        columnNames.add(10, "<->");
        columnNames.add(11, "---");
        columnNames.add(12, "Additional information");
        
        // Table header
        tableModel.setColumnIdentifiers(columnNames.toArray());

        // Add new row to table
        graph.getEdges().forEach(e->{
            String edgeType = "";
            Endpoint endpoint1 = e.getEndpoint1();
            Endpoint endpoint2 = e.getEndpoint2();

            String endpoint1Str = "";
            if (endpoint1 == Endpoint.TAIL) {
                endpoint1Str = "-";
            } else if (endpoint1 == Endpoint.ARROW) {
                endpoint1Str = "<";
            } else if (endpoint1 == Endpoint.CIRCLE) {
                endpoint1Str = "o";
            }

            String endpoint2Str = "";
            if (endpoint2 == Endpoint.TAIL) {
                endpoint2Str = "-";
            } else if (endpoint2 == Endpoint.ARROW) {
                endpoint2Str = ">";
            } else if (endpoint2 == Endpoint.CIRCLE) {
                endpoint2Str = "o";
            }
            // Produce a string representation of the edge
            edgeType = endpoint1Str + "-" + endpoint2Str;
            
            addRow(tableModel, e.getNode1().getName(), e.getNode2().getName(), edgeType, e.getProperties(), e.getEdgeTypeProbabilities());
        });
        
        
        // To be able to see the header, we need to put the table in a JScrollPane
        JScrollPane tablePane = new JScrollPane(table);
        tablePane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        table.getParent().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                if (table.getPreferredSize().width < table.getParent().getWidth()) {
                    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
                } else {
                    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
                }
            }
        });
        
        bottomBox.add(tablePane);
        
        // Use JSplitPane to allow resize the bottom box - Zhou
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        
        // Set the top and bottom split panes
        splitPane.setTopComponent(topBox);
        splitPane.setBottomComponent(bottomBox);
        
        // Add to parent
        add(menuBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.SOUTH);
        
        validate();
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
    
        // Add a new row to bootstrap table
    private void addRow(DefaultTableModel tableModel, String node1, String node2, String edgeType, List<Edge.Property> properties, List<EdgeTypeProbability> edgeTypeProbabilities) {
        String[] row = new String[13];
        
        // node1
        row[0] = node1;
        
        // Edge interaction type with edge properties (dd, pd, nl, pl)
        if (!properties.isEmpty()) {
            row[1] = edgeType + " (" + properties.stream().map(e->e.name()).collect(Collectors.joining(",")) + ")";
        } else {
            row[1] = edgeType;
        }

        // node2
        row[2] = node2;
        
        // Ensemble, empty by default
        row[3] = "";
        
        for (EdgeTypeProbability edgeTypeProb : edgeTypeProbabilities) {
            String type = "";
            String probValue = String.format("%.4f", edgeTypeProb.getProbability());
            List<String> additionalInfoList = new LinkedList<>();
            
            switch (edgeTypeProb.getEdgeType()) {
                case nil: //"no edge"
                    row[4] = probValue;
                    break;
                case ta: //"-->";
                    type = "-->";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[5] = probValue;
                    break;
                case at: //"<--";
                    type = "<--";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[6] = probValue;
                    break;
                case ca: //"o->";
                    type = "o->";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[7] = probValue;
                    break;
                case ac: //"<-o";
                    type = "<-o";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[8] = probValue;
                    break;
                case cc: //"o-o";
                    type = "o-o";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[9] = probValue;
                    break;
                case aa: //"<->";
                    type = "<->";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[10] = probValue;
                    break;
                case tt: //"---";
                    type = "---";
                    if (edgeType.equals(type)) {
                        row[3] = probValue;
                    }
                    row[11] = probValue;
                    break;
                default:
                    break;
            }

            // Additional info
            if (!edgeTypeProb.getProperties().isEmpty()) {
                type = node1 + " " + type + " " + node2;
                for (Edge.Property property : edgeTypeProb.getProperties()) {
                    type = type + " " + property.name();
                }
                additionalInfoList.add("[" + type + "]: " + probValue);
                
                String additionalInfo = additionalInfoList.stream().collect(Collectors.joining("; "));
                row[12] = additionalInfo;
            }

        }

        tableModel.addRow(row);
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
