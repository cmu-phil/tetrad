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
import edu.cmu.tetrad.util.PointXy;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.DagWrapper;
import edu.cmu.tetradapp.model.IndTestProducer;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays a workbench editing workbench area together with a toolbench for
 * editing tetrad-style graphs.
 *
 * @author Aaron Powers
 * @author Joseph Ramsey
 */
public final class DagEditor extends JPanel
        implements GraphEditable, LayoutEditable, DelegatesEditing, IndTestProducer {
    private final GraphWorkbench workbench;
    private DagWrapper dagWrapper;

    public DagEditor(DagWrapper graphWrapper) {
        this(graphWrapper.getDag());
        this.dagWrapper = graphWrapper;
    }

    public DagEditor(Dag dag) {
        setPreferredSize(new Dimension(550, 450));
        setLayout(new BorderLayout());

        this.workbench = new GraphWorkbench(dag);

        this.workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("graph".equals(evt.getPropertyName())) {
                    if (getDagWrapper() != null) {
                        getDagWrapper().setDag((Dag) evt.getNewValue());
                    }
                }
            }
        });

        DagGraphToolbar toolbar = new DagGraphToolbar(getWorkbench());
        JMenuBar menuBar = createGraphMenuBar();

        add(new JScrollPane(getWorkbench()), BorderLayout.CENTER);
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

                if ("dag".equals(propertyName)) {
                    Dag _graph = (Dag) evt.getNewValue();

                    if (getWorkbench() != null) {
                        getDagWrapper().setDag(_graph);
                    }
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
     * @return a list of all the SessionNodeWrappers (TetradNodes) and
     * SessionNodeEdges that are model components for the respective
     * SessionNodes and SessionEdges selected in the workbench. Note that the
     * workbench, not the SessionEditorNodes themselves, keeps track of the
     * selection.
     *
     * @return the set of selected model nodes.
     */
    public List getSelectedModelComponents() {
        List<Component> selectedComponents =
                getWorkbench().getSelectedComponents();
        List<TetradSerializable> selectedModelComponents =
                new ArrayList<TetradSerializable>();

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

        // TODO Add Cut and Delete.
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

    private JMenu createGraphMenu() {
        JMenu graph = new JMenu("Graph");

        graph.add(new GraphPropertiesAction(getWorkbench()));
        graph.add(new PathsAction(getWorkbench()));
//        graph.add(new DirectedPathsAction(getWorkbench()));
//        graph.add(new TreksAction(getWorkbench()));
//        graph.add(new AllPathsAction(getWorkbench()));
//        graph.add(new NeighborhoodsAction(getWorkbench()));

        JMenuItem randomDag = new JMenuItem("Random DAG");
        graph.add(randomDag);

        randomDag.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                RandomGraphEditor editor = new RandomGraphEditor(workbench.getGraph(), false);

                int ret = JOptionPane.showConfirmDialog(
                        DagEditor.this, editor,
                        "Edit Random DAG Parameters",
                        JOptionPane.PLAIN_MESSAGE);

                if (ret == JOptionPane.OK_OPTION) {
                    Graph dag = new EdgeListGraph();
                    int numTrials = 0;

                    if (editor.isRandomForward()) {
                        dag = GraphUtils.randomGraphRandomForwardEdges(getGraph().getNodes(), editor.getNumLatents(),
                                editor.getMaxEdges());
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
                            dag = GraphUtils.randomGraph(editor.getNumNodes(),
                                    editor.getNumLatents(), editor.getMaxEdges(),
                                    editor.getMaxDegree(), editor.getMaxIndegree(),
                                    editor.getMaxOutdegree(), editor.isConnected());
                        }
                    } else if (editor.isChooseFixed()) {
                        do {
                            if (getGraph().getNumNodes() == editor.getNumNodes()) {
                                HashMap<String, PointXy> layout = GraphUtils.grabLayout(workbench.getGraph().getNodes());

                                dag = GraphUtils.randomGraph(getGraph().getNodes(), editor.getNumLatents(), editor.getMaxEdges(), 30, 15, 15, editor.isConnected());


                                GraphUtils.arrangeByLayout(dag, layout);
                            } else {
                                dag = GraphUtils.randomGraph(editor.getNumNodes(),
                                        editor.getNumLatents(), editor.getMaxEdges(),
                                        30, 15, 15, editor.isConnected()
                                );
                            }
                        } while (dag.getNumEdges() < editor.getMaxEdges());
                    }

                    getWorkbench().setGraph(dag);
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });

//        graph.addSeparator();
//        graph.add(new JMenuItem(new SelectBidirectedAction(getWorkbench())));
//        graph.add(new JMenuItem(new SelectUndirectedAction(getWorkbench())));

//        graph.addSeparator();
//        IndependenceFactsAction action = new IndependenceFactsAction(
//                JOptionUtils.centeringComp(), this, "D Separation Facts...");
//        graph.add(action);

        return graph;
    }

    public IndependenceTest getIndependenceTest() {
        return new IndTestDSep(workbench.getGraph());
    }
}





