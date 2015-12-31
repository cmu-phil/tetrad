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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.session.DelegatesEditing;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.PointXy;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.IndTestProducer;
import edu.cmu.tetradapp.model.SemGraphWrapper;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;

import javax.swing.*;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Displays a workbench editing workbench area together with a toolbench for
 * editing tetrad-style graphs.
 *
 * @author Aaron Powers
 * @author Joseph Ramsey
 */
public final class SemGraphEditor extends JPanel
        implements GraphEditable, LayoutEditable, DelegatesEditing, IndTestProducer {
    private final GraphWorkbench workbench;
    private SemGraphWrapper semGraphWrapper;
    private JMenuItem errorTerms;

    //===========================PUBLIC METHODS========================//

    public SemGraphEditor(SemGraphWrapper semGraphWrapper) {
        if (semGraphWrapper == null) {
            throw new NullPointerException();
        }

        this.semGraphWrapper = semGraphWrapper;
        this.workbench = new GraphWorkbench(semGraphWrapper.getGraph());

        setLayout(new BorderLayout());

        SemGraphToolbar toolbar = new SemGraphToolbar(getWorkbench());
        JMenuBar menuBar = createMenuBar();
        JScrollPane scroll = new JScrollPane(getWorkbench());
        scroll.setPreferredSize(new Dimension(450, 450));

        add(scroll, BorderLayout.CENTER);
        add(toolbar, BorderLayout.WEST);
        add(menuBar, BorderLayout.NORTH);

        JLabel label = new JLabel("Double click variable to change name.");
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        Box b = Box.createHorizontalBox();
        b.add(Box.createHorizontalStrut(2));
        b.add(label);
        b.add(Box.createHorizontalGlue());
        b.setBorder(new MatteBorder(0, 0, 1, 0, Color.GRAY));

        add(b, BorderLayout.SOUTH);

        this.workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();

                if ("graph".equals(propertyName)) {
                    SemGraph _graph = (SemGraph) evt.getNewValue();

                    if (getWorkbench() != null) {
                        getSemGraphWrapper().setSemGraph(_graph);
                    }
                }
            }
        });

        this.workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("graph".equals(evt.getPropertyName())) {
                    getSemGraphWrapper().setSemGraph(
                            (SemGraph) evt.getNewValue());
                }
            }
        });


        this.workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();

                if ("modelChanged".equals(propertyName)) {
                    firePropertyChange("modelChanged", evt.getOldValue(),
                            evt.getNewValue());
                }
            }
        });
    }

    //===========================PRIVATE METHODS======================//

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
        List<Component> selectedComponents = getWorkbench().getSelectedComponents();
        List<TetradSerializable> selectedModelComponents = new ArrayList<TetradSerializable>();

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


    public JComponent getEditDelegate() {
        return getWorkbench();
    }

    public GraphWorkbench getWorkbench() {
        return workbench;
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
            SemGraph semGraph = new SemGraph(graph);
            workbench.setGraph(semGraph);
        } catch (Exception e) {
            throw new RuntimeException("Not a SEM graph.", e);
        }
    }

    private SemGraphWrapper getSemGraphWrapper() {
        return semGraphWrapper;
    }

    public IKnowledge getKnowledge() {
        return null;
    }

    public Graph getSourceGraph() {
        return getWorkbench().getGraph();
    }

    public void layoutByGraph(Graph graph) {
        ((SemGraph) graph).setShowErrorTerms(false);
        getWorkbench().layoutByGraph(graph);
        ((SemGraph) graph).resetErrorPositions();
    }

    public void layoutByKnowledge() {
        // Does nothing.
    }

    public Rectangle getVisibleRect() {
        return getWorkbench().getVisibleRect();
    }

    //===========================PRIVATE METHODS========================//

    private JMenuBar createMenuBar() {
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
//        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
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

        JMenuItem correlateExogenous =
                new JMenuItem("Correlate Exogenous Variables");
        JMenuItem uncorrelateExogenous =
                new JMenuItem("Uncorrelate Exogenous Variables");
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
                RandomGraphEditor editor = new RandomGraphEditor(workbench.getGraph(), true);

                int ret = JOptionPane.showConfirmDialog(
                        SemGraphEditor.this, editor,
                        "Edit Random DAG Parameters",
                        JOptionPane.PLAIN_MESSAGE);

                if (ret == JOptionPane.OK_OPTION) {
                    Graph graph = null;
                    Graph dag = new EdgeListGraph();
                    int numTrials = 0;

                    while (graph == null && ++numTrials < 100) {

                        if (editor.isRandomForward()) {
                            dag = GraphUtils.randomGraphRandomForwardEdges(getGraph().getNodes(), editor.getNumLatents(), editor.getMaxEdges(), 30, 15, 15, false);
                            GraphUtils.arrangeBySourceGraph(dag, getWorkbench().getGraph());
                            HashMap<String, PointXy> layout = GraphUtils.grabLayout(workbench.getGraph().getNodes());
                            GraphUtils.arrangeByLayout(dag, layout);
                        } else if (editor.isUniformlySelected()) {
                            if (getGraph().getNumNodes() == editor.getNumNodes()) {
                                HashMap<String, PointXy> layout = GraphUtils.grabLayout(workbench.getGraph().getNodes());

                                dag = GraphUtils.randomGraph(getGraph().getNodes(), editor.getNumLatents(), editor.getMaxEdges(), editor.getMaxDegree(), editor.getMaxIndegree(), editor.getMaxOutdegree(), editor.isConnected());
                                GraphUtils.arrangeBySourceGraph(dag, getWorkbench().getGraph());

                                GraphUtils.arrangeByLayout(dag, layout);
                            } else {
                                List<Node> nodes = new ArrayList<Node>();

                                for (int i = 0; i < editor.getNumNodes(); i++) {
                                    nodes.add(new ContinuousVariable("X" + (i + 1)));
                                }

                                dag = GraphUtils.randomGraph(nodes, editor.getNumLatents(), editor.getMaxEdges(),
                                        editor.getMaxDegree(), editor.getMaxIndegree(), editor.getMaxOutdegree(), editor.isConnected());
                            }
                        } else if (editor.isChooseFixed()) {
                            do {
                                if (getGraph().getNumNodes() == editor.getNumNodes()) {
                                    HashMap<String, PointXy> layout = GraphUtils.grabLayout(workbench.getGraph().getNodes());

                                    dag = GraphUtils.randomGraph(getGraph().getNodes(), editor.getNumLatents(), editor.getMaxEdges(), 30, 15, 15, editor.isConnected());


                                    GraphUtils.arrangeByLayout(dag, layout);
                                } else {
                                    List<Node> nodes = new ArrayList<Node>();

                                    for (int i = 0; i < editor.getNumNodes(); i++) {
                                        nodes.add(new ContinuousVariable("X" + (i + 1)));
                                    }

                                    dag = GraphUtils.randomGraph(nodes, editor.getNumLatents(), editor.getMaxEdges(),
                                            30, 15, 15, editor.isConnected());
                                }
                            } while (dag.getNumEdges() < editor.getMaxEdges());
                        }

                        boolean addCycles = editor.isAddCycles();

                        if (addCycles) {
                            int minCycleLength = editor.getMinCycleLength();
                            int minNumCycles = editor.getMinNumCycles();

//                            graph = DataGraphUtils.addCycles2(dag, minNumCycles, minCycleLength);

                            graph = GraphUtils.cyclicGraph2(editor.getNumNodes(), editor.getMaxEdges());
                        } else {
                            graph = new EdgeListGraph(dag);
                        }
                    }

                    int minNumCycles = editor.getMinNumCycles();
                    GraphUtils.addTwoCycles(graph, minNumCycles);

                    if (graph == null) {
                        JOptionPane.showMessageDialog(SemGraphEditor.this,
                                "Could not find a graph that fits those constrains.");
                        getWorkbench().setGraph(new SemGraph(dag));
                    } else {
                        getWorkbench().setGraph(new SemGraph(graph));
                    }

//                    getWorkbench().setGraph(new EdgeListGraph(dag));
//                    getWorkbench().setGraph(graph);
                }
            }
        });


        JMenuItem randomIndicatorModel =
                new JMenuItem("Random Multiple Indicator Model");
        graph.add(randomIndicatorModel);

        randomIndicatorModel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                RandomMimParamsEditor editor = new RandomMimParamsEditor();

                int ret = JOptionPane.showConfirmDialog(
                        JOptionUtils.centeringComp(), editor,
                        "Edit Random MIM Parameters",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.PLAIN_MESSAGE);

                if (ret == JOptionPane.OK_OPTION) {
                    int numFactors = Preferences.userRoot().getInt(
                            "randomMimNumFactors", 1);
                    int numStructuralNodes = Preferences.userRoot().getInt(
                            "numStructuralNodes", 3);
                    int maxStructuralEdges = Preferences.userRoot().getInt(
                            "numStructuralEdges", 3);
                    int measurementModelDegree = Preferences.userRoot().getInt(
                            "measurementModelDegree", 3);
                    int numLatentMeasuredImpureParents = Preferences.userRoot()
                            .getInt("latentMeasuredImpureParents", 0);
                    int numMeasuredMeasuredImpureParents =
                            Preferences.userRoot()
                                    .getInt("measuredMeasuredImpureParents", 0);
                    int numMeasuredMeasuredImpureAssociations =
                            Preferences.userRoot()
                                    .getInt("measuredMeasuredImpureAssociations",
                                            0);

                    Graph graph;

                    if (numFactors == 1) {
                        graph = DataGraphUtils.randomSingleFactorModel(numStructuralNodes,
                                maxStructuralEdges, measurementModelDegree,
                                numLatentMeasuredImpureParents,
                                numMeasuredMeasuredImpureParents,
                                numMeasuredMeasuredImpureAssociations);
                    } else if (numFactors == 2) {
                        graph = DataGraphUtils.randomBifactorModel(numStructuralNodes,
                                maxStructuralEdges, measurementModelDegree,
                                numLatentMeasuredImpureParents,
                                numMeasuredMeasuredImpureParents,
                                numMeasuredMeasuredImpureAssociations);
                    } else {
                        throw new IllegalArgumentException("Can only make random MIMs for 1 or 2 factors, " +
                                "sorry dude.");
                    }

                    SemGraph semGraph = new SemGraph(graph);
                    semGraph.setShowErrorTerms(false);

                    workbench.setGraph(semGraph);
                    getSemGraphWrapper().setSemGraph(semGraph);
                    errorTerms.setText("Show Error Terms");
                }
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

        List<Node> exoNodes = new LinkedList<Node>();

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

    public IndependenceTest getIndependenceTest() {
        return new IndTestDSep(workbench.getGraph());
    }
}





