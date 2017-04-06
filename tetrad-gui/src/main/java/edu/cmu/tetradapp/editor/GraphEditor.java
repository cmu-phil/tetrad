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
import edu.cmu.tetrad.search.MeekRules;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.*;
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
import java.util.*;
import java.util.List;
import javax.help.CSH;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.InternalFrameAdapter;
import javax.swing.event.InternalFrameEvent;

/**
 * Displays a workbench editing workbench area together with a toolbench for
 * editing tetrad-style graphs.
 *
 * @author Aaron Powers
 * @author Joseph Ramsey
 */
public final class GraphEditor extends JPanel
        implements GraphEditable, LayoutEditable, IndTestProducer {

    private GraphWorkbench workbench;
    private GraphSettable graphEditable;
    private Parameters parameters;

    private final HelpSet helpSet;

    //===========================PUBLIC METHODS========================//
    public GraphEditor(GraphSettable graphWrapper) {
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
        this.graphEditable = graphWrapper;
        this.parameters = graphWrapper.getParameters();

        editGraph(graphWrapper.getGraph());

        this.getWorkbench().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();

                if ("graph".equals(propertyName)) {
                    Graph _graph = (Graph) evt.getNewValue();

                    if (getWorkbench() != null && getGraphWrapper() != null) {
                        getGraphWrapper().setGraph(_graph);
                    }
                }
            }
        });

        int numModels = graphEditable.getNumModels();

        if (numModels > 1) {
            final JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < numModels; i++) {
                comp.addItem(i + 1);
            }

            comp.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    graphEditable.setModelIndex(((Integer) comp.getSelectedItem()).intValue() - 1);
                    editGraph(graphEditable.getGraph());
                    validate();
                }
            });

            comp.setMaximumSize(comp.getPreferredSize());

            Box b = Box.createHorizontalBox();
            b.add(new JLabel("Using model"));
            b.add(comp);
            b.add(new JLabel("from "));
            b.add(new JLabel(graphEditable.getModelSourceName()));
            b.add(Box.createHorizontalGlue());

            add(b, BorderLayout.NORTH);
        }

        getWorkbench().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("graph".equals(evt.getPropertyName())) {
                    getGraphWrapper().setGraph((Graph) evt.getNewValue());
                } else if ("modelChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

        validate();

    }

//    public GraphEditor(DagInPatternWrapper wrapper) {
//        this(wrapper.getGraph());
//    }
//
//    public GraphEditor(CompletedPatternWrapper wrapper) {
//        this(wrapper.getGraph());
//    }
    //===========================PRIVATE METHODS======================//
    private void editGraph(Graph graph) {
        this.workbench = new GraphWorkbench(graph);
        GraphToolbar toolbar = new GraphToolbar(getWorkbench());
        JMenuBar menuBar = createGraphMenuBar();
        JScrollPane scroll = new JScrollPane();
        scroll.setPreferredSize(new Dimension(450, 450));
        scroll.setViewportView(getWorkbench());

        add(scroll, BorderLayout.CENTER);
        add(toolbar, BorderLayout.WEST);
        add(menuBar, BorderLayout.NORTH);

        JLabel label = new JLabel("Double click variable to change name. More information on graph edge types");
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

        Box b = Box.createHorizontalBox();
        b.add(Box.createHorizontalStrut(2));
        b.add(label);
        b.add(infoBtn);
        b.add(Box.createHorizontalGlue());
        b.setBorder(new MatteBorder(0, 0, 1, 0, Color.GRAY));

        add(b, BorderLayout.SOUTH);
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

    /**
     * @return a list of all the SessionNodeWrappers (TetradNodes) and
     * SessionNodeEdges that are model components for the respective
     * SessionNodes and SessionEdges selected in the workbench. Note that the
     * workbench, not the SessionEditorNodes themselves, keeps track of the
     * selection.
     */
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

    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    public Graph getGraph() {
        return getWorkbench().getGraph();
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return getWorkbench().getModelEdgesToDisplay();
    }

    public Map getModelNodesToDisplay() {
        return getWorkbench().getModelNodesToDisplay();
    }

    public void setGraph(Graph graph) {
        getWorkbench().setGraph(graph);
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

    private GraphSettable getGraphWrapper() {
        return graphEditable;
    }

    //===========================PRIVATE METHODS========================//
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

//    /**
//     * Creates the "file" menu, which allows the user to load, save, and post
//     * workbench models.
//     *
//     * @return this menu.
//     */
//    private JMenu createFileMenu() {
//        JMenu file = new JMenu("File");
//
//        file.add(new LoadGraph(this, "Load Graph..."));
//        file.add(new SaveGraph(this, "Save Graph..."));
////        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
//        file.add(new SaveComponentImage(getWorkbench(), "Save Graph Image..."));
//
//        return file;
//    }
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

        graph.addSeparator();

        JMenuItem correlateExogenous = new JMenuItem("Correlate Exogenous Variables");
        JMenuItem uncorrelateExogenous = new JMenuItem("Uncorrelate Exogenous Variables");
        graph.add(correlateExogenous);
        graph.add(uncorrelateExogenous);
        graph.addSeparator();

        correlateExogenous.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                correlateExogenousVariables();
                getWorkbench().invalidate();
                getWorkbench().repaint();
            }
        });

        uncorrelateExogenous.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                uncorrelationExogenousVariables();
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
                        "Done", false, GraphEditor.this);

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

        graph.addSeparator();
        JMenuItem meekOrient = new JMenuItem("Meek Orientation");
        graph.add(meekOrient);

        meekOrient.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                MeekRules rules = new MeekRules();
                rules.orientImplied(getGraph());
                getWorkbench().setGraph(getGraph());
                firePropertyChange("modelChanged", null, null);
            }
        }
        );

        graph.addSeparator();
        graph.add(new JMenuItem(new SelectBidirectedAction(getWorkbench()
        )));
        graph.add(new JMenuItem(new SelectUndirectedAction(getWorkbench()
        )));
        graph.add(new JMenuItem(new SelectLatentsAction(getWorkbench()
        )));

//        graph.addSeparator();
//        IndependenceFactsAction action = new IndependenceFactsAction(
//                JOptionUtils.centeringComp(), this, "D Separation Facts...");
//        graph.add(action);
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

    public IndependenceTest getIndependenceTest() {
        Graph graph = getWorkbench().getGraph();
        EdgeListGraph listGraph = new EdgeListGraph(graph);
        return new IndTestDSep(listGraph);
    }
}
