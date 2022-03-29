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
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.model.SemGraphWrapper;
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
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.net.URL;
import java.util.List;
import java.util.*;

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
    private final SemGraphWrapper semGraphWrapper;
    private JMenuItem errorTerms;
    private final Parameters parameters;

    private final JScrollPane graphEditorScroll = new JScrollPane();

    private final EdgeTypeTable edgeTypeTable;

    //===========================CONSTRUCTOR========================//
    public SemGraphEditor(final SemGraphWrapper semGraphWrapper) {
        if (semGraphWrapper == null) {
            throw new NullPointerException();
        }

        setLayout(new BorderLayout());
        this.semGraphWrapper = semGraphWrapper;
        this.parameters = semGraphWrapper.getParameters();
        this.edgeTypeTable = new EdgeTypeTable();

        initUI(semGraphWrapper);
    }

    //===========================PUBLIC METHODS======================//

    /**
     * Sets the name of this editor.
     */
    @Override
    public final void setName(final String name) {
        final String oldName = getName();
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
        final List<Component> selectedComponents = getWorkbench().getSelectedComponents();
        final List<TetradSerializable> selectedModelComponents = new ArrayList<>();

        selectedComponents.forEach(comp -> {
            if (comp instanceof DisplayNode) {
                selectedModelComponents.add(((DisplayNode) comp).getModelNode());
            } else if (comp instanceof DisplayEdge) {
                selectedModelComponents.add(((DisplayEdge) comp).getModelEdge());
            }
        });

        return selectedModelComponents;
    }

    /**
     * Pastes list of session elements into the workbench.
     */
    @Override
    public void pasteSubsession(final List sessionElements, final Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        for (int i = 0; i < sessionElements.size(); i++) {

            final Object o = sessionElements.get(i);

            if (o instanceof GraphNode) {
                final Node modelNode = (Node) o;
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
        return this.workbench;
    }

    @Override
    public Graph getGraph() {
        return this.workbench.getGraph();
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return this.workbench.getModelEdgesToDisplay();
    }

    @Override
    public Map getModelNodesToDisplay() {
        return this.workbench.getModelNodesToDisplay();
    }

    @Override
    public void setGraph(final Graph graph) {
        try {
            final SemGraph semGraph = new SemGraph(graph);
            this.workbench.setGraph(semGraph);
        } catch (final Exception e) {
            throw new RuntimeException("Not a SEM graph.", e);
        }
    }

    private SemGraphWrapper getSemGraphWrapper() {
        return this.semGraphWrapper;
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
    public void layoutByGraph(final Graph graph) {
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
    private void initUI(final SemGraphWrapper semGraphWrapper) {
        final Graph graph = semGraphWrapper.getGraph();

        this.workbench = new GraphWorkbench(graph);

        this.workbench.addPropertyChangeListener((PropertyChangeEvent evt) -> {
            final String propertyName = evt.getPropertyName();

            // Update the bootstrap table if there's changes to the edges or node renaming
            final String[] events = {"graph", "edgeAdded", "edgeRemoved"};

            if (Arrays.asList(events).contains(propertyName)) {
                if (getWorkbench() != null) {
                    final Graph targetGraph = (Graph) getWorkbench().getGraph();

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
        final JMenuBar menuBar = createGraphMenuBar();

        // Add the model selection to top if multiple models
        modelSelectin(semGraphWrapper);

        // topBox Left side toolbar
        final SemGraphToolbar graphToolbar = new SemGraphToolbar(getWorkbench());
        graphToolbar.setMaximumSize(new Dimension(140, 450));

        // topBox right side graph editor
        this.graphEditorScroll.setPreferredSize(new Dimension(760, 450));
        this.graphEditorScroll.setViewportView(this.workbench);

        // topBox contains the topGraphBox and the instructionBox underneath
        final Box topBox = Box.createVerticalBox();
        topBox.setPreferredSize(new Dimension(820, 400));

        // topGraphBox contains the vertical graph toolbar and graph editor
        final Box topGraphBox = Box.createHorizontalBox();
        topGraphBox.add(graphToolbar);
        topGraphBox.add(this.graphEditorScroll);

        // Instruction with info button
        final Box instructionBox = Box.createHorizontalBox();
        instructionBox.setMaximumSize(new Dimension(820, 40));

        final JLabel label = new JLabel("Double click variable/node rectangle to change name. More information on graph edge types and colorings");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Info button added by Zhou to show edge types
        final JButton infoBtn = new JButton(new ImageIcon(ImageUtils.getImage(this, "info.png")));
        infoBtn.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Clock info button to show edge types instructions - Zhou
        infoBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent e) {
                // Initialize helpSet
                final String helpHS = "/resources/javahelp/TetradHelp.hs";

                try {
                    final URL url = this.getClass().getResource(helpHS);
                    final HelpSet helpSet = new HelpSet(null, url);

                    helpSet.setHomeID("graph_edge_types");
                    final HelpBroker broker = helpSet.createHelpBroker();
                    final ActionListener listener = new CSH.DisplayHelpFromSource(broker);
                    listener.actionPerformed(e);
                } catch (final Exception ee) {
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

        // Use JSplitPane to allow resize the bottom box - Zhou
        final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new PaddingPanel(topBox), new PaddingPanel(this.edgeTypeTable));
        splitPane.setDividerLocation((int) (splitPane.getPreferredSize().getHeight() - 150));

        // Add to parent container
        add(menuBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        this.edgeTypeTable.update(graph);

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
    private void updateGraphWorkbench(final Graph graph) {
        this.workbench = new GraphWorkbench(graph);
        this.graphEditorScroll.setViewportView(this.workbench);

        validate();
    }

    /**
     * Updates bootstrap table on adding/removing edges or graph changes
     *
     * @param graph
     */
    private void updateBootstrapTable(final Graph graph) {
        this.edgeTypeTable.update(graph);

        validate();
    }

    /**
     * Creates the UI component for choosing from multiple graph models
     *
     * @param semGraphWrapper
     */
    private void modelSelectin(final SemGraphWrapper semGraphWrapper) {
        final int numModels = semGraphWrapper.getNumModels();

        if (numModels > 1) {
            final List<Integer> models = new ArrayList<>();
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
            final Box modelSelectionBox = Box.createHorizontalBox();
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
        final JMenuBar menuBar = new JMenuBar();

        final JMenu fileMenu = new GraphFileMenu(this, getWorkbench());
        final JMenu editMenu = createEditMenu();
        final JMenu graphMenu = createGraphMenu();

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
        final JMenu edit = new JMenu("Edit");

        final JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
        final JMenuItem paste = new JMenuItem(new PasteSubgraphAction(this));

        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        paste.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));

        edit.add(copy);
        edit.add(paste);

        return edit;
    }

    private JMenu createGraphMenu() {
        final JMenu graph = new JMenu("Graph");

        final JMenuItem randomGraph = new JMenuItem("Random Graph");
        graph.add(randomGraph);
        graph.addSeparator();

        graph.add(new GraphPropertiesAction(getWorkbench()));
        graph.add(new PathsAction(getWorkbench()));
//        graph.add(new DirectedPathsAction(getWorkbench()));
//        graph.add(new TreksAction(getWorkbench()));
//        graph.add(new AllPathsAction(getWorkbench()));
//        graph.add(new NeighborhoodsAction(getWorkbench()));
        graph.addSeparator();

        this.errorTerms = new JMenuItem();

        if (getSemGraph().isShowErrorTerms()) {
            this.errorTerms.setText("Hide Error Terms");
        } else {
            this.errorTerms.setText("Show Error Terms");
        }

        this.errorTerms.addActionListener(e -> {
            final JMenuItem menuItem = (JMenuItem) e.getSource();
            if ("Hide Error Terms".equals(menuItem.getText())) {
                menuItem.setText("Show Error Terms");
                getSemGraph().setShowErrorTerms(false);
            } else if ("Show Error Terms".equals(menuItem.getText())) {
                menuItem.setText("Hide Error Terms");
                getSemGraph().setShowErrorTerms(true);
            }
        });

        graph.add(this.errorTerms);
        graph.addSeparator();

        final JMenuItem correlateExogenous
                = new JMenuItem("Correlate Exogenous Variables");
        final JMenuItem uncorrelateExogenous
                = new JMenuItem("Uncorrelate Exogenous Variables");
        graph.add(correlateExogenous);
        graph.add(uncorrelateExogenous);
        graph.addSeparator();

        correlateExogenous.addActionListener(e -> {
            correlationExogenousVariables();
            getWorkbench().invalidate();
            getWorkbench().repaint();
        });

        uncorrelateExogenous.addActionListener(e -> {
            uncorrelateExogenousVariables();
            getWorkbench().invalidate();
            getWorkbench().repaint();
        });


        randomGraph.addActionListener(e -> {
            final GraphParamsEditor editor = new GraphParamsEditor();
            editor.setParams(this.parameters);

            final EditorWindow editorWindow = new EditorWindow(editor, "Edit Random Graph Parameters",
                    "Done", false, SemGraphEditor.this);

            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
            editorWindow.pack();
            editorWindow.setVisible(true);

            editorWindow.addInternalFrameListener(new InternalFrameAdapter() {
                @Override
                public void internalFrameClosed(final InternalFrameEvent e1) {
                    final EditorWindow window = (EditorWindow) e1.getSource();

                    if (window.isCanceled()) {
                        return;
                    }

                    RandomUtil.getInstance().setSeed(new Date().getTime());
                    Graph graph1 = edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph(), SemGraphEditor.this.parameters);

                    final boolean addCycles = SemGraphEditor.this.parameters.getBoolean("randomAddCycles", false);

                    if (addCycles) {
                        final int newGraphNumMeasuredNodes = SemGraphEditor.this.parameters.getInt("newGraphNumMeasuredNodes", 10);
                        final int newGraphNumEdges = SemGraphEditor.this.parameters.getInt("newGraphNumEdges", 10);
                        graph1 = GraphUtils.cyclicGraph2(newGraphNumMeasuredNodes, newGraphNumEdges, 6);
                    }

                    getWorkbench().setGraph(graph1);
                }
            });
        });

        graph.add(new JMenuItem(new SelectBidirectedAction(getWorkbench())));

        return graph;
    }

    private SemGraph getSemGraph() {
        return (SemGraph) this.semGraphWrapper.getGraph();
    }

    private void correlationExogenousVariables() {
        final Graph graph = getWorkbench().getGraph();

        if (graph instanceof Dag) {
            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "Cannot add bidirected edges to DAG's.");
            return;
        }

        final List<Node> nodes = graph.getNodes();

        final List<Node> exoNodes = new LinkedList<>();

        for (int i = 0; i < nodes.size(); i++) {
            final Node node = nodes.get(i);
            if (graph.isExogenous(node)) {
                exoNodes.add(node);
            }
        }

        for (int i = 0; i < exoNodes.size(); i++) {

            loop:
            for (int j = i + 1; j < exoNodes.size(); j++) {
                final Node node1 = exoNodes.get(i);
                final Node node2 = exoNodes.get(j);
                final List<Edge> edges = graph.getEdges(node1, node2);

                for (int k = 0; k < edges.size(); k++) {
                    final Edge edge = edges.get(k);
                    if (Edges.isBidirectedEdge(edge)) {
                        continue loop;
                    }
                }

                graph.addBidirectedEdge(node1, node2);
            }
        }
    }

    private void uncorrelateExogenousVariables() {
        final Graph graph = getWorkbench().getGraph();

        final Set<Edge> edges = graph.getEdges();

        for (final Edge edge : edges) {
            if (Edges.isBidirectedEdge(edge)) {
                try {
                    graph.removeEdge(edge);
                } catch (final Exception e) {
                }
            }
        }
    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return new IndTestDSep(this.workbench.getGraph());
    }

}
