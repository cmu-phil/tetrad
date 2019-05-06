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
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.TimeLagGraph;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.model.TimeLagGraphWrapper;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.util.CopyLayoutAction;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.ImageUtils;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;
import edu.cmu.tetradapp.workbench.LayoutUtils;
import edu.cmu.tetradapp.workbench.TimeLagGraphWorkbench;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.SpinnerModel;
import javax.swing.SpinnerNumberModel;
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
public final class TimeLagGraphEditor extends JPanel
        implements GraphEditable, LayoutEditable, IndTestProducer {

    private static final long serialVersionUID = -2425361202348129265L;

    private TimeLagGraphWorkbench workbench;
    private final LayoutEditable layoutEditable;
    private CopyLayoutAction copyLayoutAction;

    private final JScrollPane graphEditorScroll = new JScrollPane();

    private final EdgeTypeTable edgeTypeTable;

    //===========================CONSTRUCTOR========================//
    public TimeLagGraphEditor(TimeLagGraphWrapper timeLagGraphWrapper) {
        setLayout(new BorderLayout());
        this.layoutEditable = this;
        this.edgeTypeTable = new EdgeTypeTable();

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
            String[] events = {"graph", "edgeAdded", "edgeRemoved"};

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

//         Graph menu at the very top of the window
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

        edgeTypeTable.setPreferredSize(new Dimension(820, 150));

        // Use JSplitPane to allow resize the bottom box - Zhou
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new PaddingPanel(topBox), new PaddingPanel(edgeTypeTable));
        splitPane.setDividerLocation((int) (splitPane.getPreferredSize().getHeight() - 150));

        // Add to parent container
        add(menuBar, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);

        edgeTypeTable.update(graph);

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
        edgeTypeTable.update(graph);

        validate();
    }

    private JMenuBar createGraphMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new GraphFileMenu(this, getWorkbench());
        JMenu editMenu = createEditMenu();
//        JMenu graphMenu = createGraphMenu();

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
//        menuBar.add(graphMenu);
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
//        edit.addSeparator();
        JMenuItem configuration = new JMenuItem("Configuration...");
        edit.add(configuration);

        configuration.addActionListener(e -> {
            final TimeLagGraph graph = (TimeLagGraph) getLayoutEditable().getGraph();

            class ConfigurationEditor extends JPanel {

                private static final long serialVersionUID = 1878934284195587094L;

                private int maxLag;
                private int numInitialLags;

                public ConfigurationEditor(final TimeLagGraph graph) {
                    maxLag = graph.getMaxLag();
                    numInitialLags = graph.getNumInitialLags();

                    final SpinnerModel maxLagSpinnerModel = new SpinnerNumberModel(graph.getMaxLag(), 0, 300, 1);
                    JSpinner maxLagSpinner = new JSpinner(maxLagSpinnerModel);

                    maxLagSpinner.addChangeListener(e -> {
                        JSpinner spinner = (JSpinner) e.getSource();
                        SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                        int value = (Integer) model.getValue();
                        setMaxLag(value);
                    });

                    final SpinnerModel initialLagsSpinnerModel = new SpinnerNumberModel(graph.getNumInitialLags(), 1, 300, 1);
                    JSpinner initialLagsSpinner = new JSpinner(initialLagsSpinnerModel);

                    initialLagsSpinner.addChangeListener(e -> {
                        JSpinner spinner = (JSpinner) e.getSource();
                        SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                        int value = (Integer) model.getValue();
                        setNumInitialLags(value);
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
                @Override
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
        });

        return edit;
    }

    @Override
    public IndependenceTest getIndependenceTest() {
        Graph graph = getWorkbench().getGraph();
        EdgeListGraph listGraph = new EdgeListGraph(graph);
        return new IndTestDSep(listGraph);
    }

    private LayoutEditable getLayoutEditable() {
        return layoutEditable;
    }

    private CopyLayoutAction getCopyLayoutAction() {
        return copyLayoutAction;
    }

}
