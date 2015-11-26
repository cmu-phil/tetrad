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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.ImpliedOrientation;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.PatternToDag;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.*;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Edits some algorithms to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class IonSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable {

//    private JTabbedPane tabbedPane;
    private IonDisplay ionDisplay;

    //=========================CONSTRUCTORS============================//

    public IonSearchEditor(IonRunner runner) {
        super(runner, "Result Graph");
    }

    //=============================== Public Methods ==================================//

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

    public void layoutByGraph(Graph graph) {
        getWorkbench().layoutByGraph(graph);
    }

    public void layoutByKnowledge() {
        GraphWorkbench resultWorkbench = getWorkbench();
        Graph graph = resultWorkbench.getGraph();
        IKnowledge knowledge = getAlgorithmRunner().getParams().getKnowledge();
        SearchGraphUtils.arrangeByKnowledgeTiers(graph, knowledge);
//        resultWorkbench.setGraph(graph);
    }

    public Rectangle getVisibleRect() {
        return getWorkbench().getVisibleRect();
    }

    //==========================PROTECTED METHODS============================//


    /**
     * Sets up the editor, does the layout, and so on.
     */
    protected void setup(String resultLabel) {
        setLayout(new BorderLayout());
        add(getToolbar(), BorderLayout.WEST);
        //JTabbedPane tabbedPane = new JTabbedPane();
//        tabbedPane = new JTabbedPane();
        ionDisplay = ionDisplay();
//        tabbedPane.add("Result", lingDisplay);

        /*if (getAlgorithmRunner().getSelectedDataModel() instanceof DataSet) {
            tabbedPane.add("Model Statistics", modelStatsText);
            tabbedPane.add("DAG in pattern", dagWorkbench);
        }*/

        add(ionDisplay, BorderLayout.CENTER);
//        add(tabbedPane, BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
    }

    private IonDisplay ionDisplay() {
        Graph resultGraph = resultGraph();
        List<Graph> storedGraphs = arrangeGraphs();
        IonDisplay display = new IonDisplay(storedGraphs, (IonRunner) getAlgorithmRunner());

        // Superfluous?
        getGraphHistory().clear();
        getGraphHistory().add(resultGraph);

        setWorkbench(display.getWorkbench());
        getWorkbench().setAllowDoubleClickActions(false);
        getWorkbench().setAllowNodeEdgeSelection(true);

        setWorkbenchScroll(new JScrollPane(display));
        getWorkbenchScroll().setPreferredSize(new Dimension(450, 450));
        getWorkbenchScroll().setBorder(new TitledBorder(""));

        getWorkbench().addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) {
                storeLatestWorkbenchGraph();
            }
        });

        display.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
                firePropertyChange(propertyChangeEvent.getPropertyName(), null, null);
            }
        });


        return display;
    }

    private List<Graph> arrangeGraphs() {
        IonRunner runner = (IonRunner) getAlgorithmRunner();

        List<Graph> storedGraphs = runner.getStoredGraphs();
        if (storedGraphs == null) storedGraphs = new ArrayList<Graph>();

        for (Graph storedGraph : storedGraphs) {
            GraphUtils.circleLayout(storedGraph, 200, 200, 150);
        }

        return storedGraphs;
    }

    private Graph resultGraph() {
        Graph resultGraph = getAlgorithmRunner().getResultGraph();

        if (resultGraph == null) {
            resultGraph = new EdgeListGraph();
        }

        return resultGraph;
    }


    /**
     * Executes the algorithm. The execution takes place inside a thread, so one
     * cannot count on a result graph having been found when the method
     */
    public void execute() {
        Window owner = (Window) getTopLevelAncestor();

        final WatchedProcess process = new WatchedProcess(owner) {
            public void watch() {
                getExecuteButton().setEnabled(false);
                setErrorMessage(null);

                if (!knowledgeMessageShown) {
                    SearchParams searchParams = getAlgorithmRunner().getParams();

                    if (searchParams != null) {
                        IKnowledge knowledge = searchParams.getKnowledge();
                        if (!knowledge.isEmpty()) {
                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(),
                                    "Using previously set knowledge. (To edit, use " +
                                            "the Knowledge menu.)");
                            knowledgeMessageShown = true;
                        }
                    }
                }

                try {
                    storeLatestWorkbenchGraph();
                    getAlgorithmRunner().execute();
                    IonRunner runner = (IonRunner) getAlgorithmRunner();
                    arrangeGraphs();
                    ionDisplay.resetGraphs(runner.getStoredGraphs());
                } catch (Exception e) {
                    CharArrayWriter writer1 = new CharArrayWriter();
                    PrintWriter writer2 = new PrintWriter(writer1);
                    e.printStackTrace(writer2);
                    String message = writer1.toString();
                    writer2.close();

                    e.printStackTrace(System.out);

                    TetradLogger.getInstance().error(message);

                    String messageString = e.getMessage();

                    if (e.getCause() != null) {
                        messageString = e.getCause().getMessage();
                    }

                    if (messageString == null) {
                        messageString = message;
                    }
                    setErrorMessage(messageString);

                    TetradLogger.getInstance().error("************Algorithm stopped!");

                    getExecuteButton().setEnabled(true);
                    throw new RuntimeException(e);
                }

                getWorkbenchScroll().setBorder(
                        new TitledBorder(getResultLabel()));
                Graph resultGraph = resultGraph();

                doDefaultArrangement(resultGraph);
                getWorkbench().setBackground(Color.WHITE);
                getWorkbench().setGraph(resultGraph);
                getGraphHistory().clear();
                getGraphHistory().add(resultGraph);
                getWorkbench().repaint();

                // For Mimbuild, e.g., that need to do a second stage.
                firePropertyChange("algorithmFinished", null, null);
                getExecuteButton().setEnabled(true);
                firePropertyChange("modelChanged", null, null);
            }
        };

        Thread watcher = new Thread() {
            public void run() {
                while (true) {
                    try {
                        sleep(300);

                        if (!process.isAlive()) {
                            getExecuteButton().setEnabled(true);
                            return;
                        }
                    }
                    catch (InterruptedException e) {
                        getExecuteButton().setEnabled(true);
                        return;
                    }
                }
            }
        };

        watcher.start();
    }


    /**
     * Construct the toolbar panel.
     */
    protected JPanel getToolbar() {
        JPanel toolbar = new JPanel();

        getExecuteButton().setText("Execute*");
        getExecuteButton().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                execute();
            }
        });

        Box b1 = Box.createVerticalBox();

        final IonParams params = (IonParams) getAlgorithmRunner().getParams();

        JCheckBox pruneByAdjacenciesBox = new JCheckBox("Prune by Adjacencies");
        pruneByAdjacenciesBox.setSelected(params.isPruneByAdjacencies());

        JCheckBox pruneByPathLengthBox = new JCheckBox("Prune by Path Length");
        pruneByPathLengthBox.setSelected(params.isPruneByPathLength());

        pruneByAdjacenciesBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                params.setPruneByAdjacencies(checkBox.isSelected());
            }
        });

        pruneByPathLengthBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                params.setPruneByPathLength(checkBox.isSelected());
            }
        });

        Box paramsPanel = Box.createVerticalBox();
        paramsPanel.setBorder(new TitledBorder("Parameters"));

        Box b3a = Box.createHorizontalBox();
        b3a.add(pruneByAdjacenciesBox);
        b3a.add(Box.createHorizontalGlue());
        paramsPanel.add(b3a);

        Box b3b = Box.createHorizontalBox();
        b3b.add(pruneByPathLengthBox);
        b3b.add(Box.createHorizontalGlue());
        paramsPanel.add(b3b);

        b1.add(paramsPanel);
        b1.add(Box.createVerticalStrut(10));

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(getExecuteButton());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        if (getAlgorithmRunner().getDataModel() instanceof DataSet) {
            Box b3 = Box.createHorizontalBox();
            b3.add(Box.createGlue());
            b1.add(b3);
        }

        if (getAlgorithmRunner().getParams() instanceof MeekSearchParams) {
            b1.add(Box.createVerticalStrut(5));
            Box hBox = Box.createHorizontalBox();
            hBox.add(Box.createHorizontalGlue());
            b1.add(hBox);
            b1.add(Box.createVerticalStrut(5));
        }

        Box b4 = Box.createHorizontalBox();
        JLabel label = new JLabel("<html>" + "*Please note that some" +
                "<br>searches may take a" + "<br>long time to complete." +
                "</html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setBorder(new TitledBorder(""));
        b4.add(label);

        b1.add(Box.createVerticalStrut(10));
        b1.add(b4);

        toolbar.add(b1);
        return toolbar;
    }


    protected void doPostExecutionSteps() {
        System.out.println("Post execution.");
    }

    protected void addSpecialMenus(JMenuBar menuBar) {
        if (!(getAlgorithmRunner() instanceof IGesRunner)) {
            JMenu test = new JMenu("Independence");
            menuBar.add(test);

            IndTestMenuItems.addIndependenceTestChoices(test, this);

//            test.addSeparator();
//
//            AlgorithmRunner algorithmRunner = getAlgorithmRunner();

//            if (algorithmRunner instanceof IndTestProducer) {
//                IndTestProducer p = (IndTestProducer) algorithmRunner;
//                IndependenceFactsAction action =
//                        new IndependenceFactsAction(this, p, "Independence Facts...");
//                test.add(action);
//            }
        }

        JMenu graph = new JMenu("Graph");
        JMenuItem showDags = new JMenuItem("Show DAGs in Pattern");
        JMenuItem meekOrient = new JMenuItem("Meek Orientation");
        JMenuItem dagInPattern = new JMenuItem("Choose DAG in Pattern");
        JMenuItem gesOrient = new JMenuItem("Global Score-based Reorientation");
        JMenuItem nextGraph = new JMenuItem("Next Graph");
        JMenuItem previousGraph = new JMenuItem("Previous Graph");

//        graph.add(new LayoutMenu(this));
        graph.add(new GraphPropertiesAction(getWorkbench()));
        graph.add(new PathsAction(getWorkbench()));
//        graph.add(new DirectedPathsAction(getWorkbench()));
//        graph.add(new TreksAction(getWorkbench()));
//        graph.add(new AllPathsAction(getWorkbench()));
//        graph.add(new NeighborhoodsAction(getWorkbench()));
        graph.add(new TriplesAction(getWorkbench(), getAlgorithmRunner()));
        graph.addSeparator();

        graph.add(meekOrient);
        graph.add(dagInPattern);
        graph.add(gesOrient);
        graph.addSeparator();

        graph.add(previousGraph);
        graph.add(nextGraph);
        graph.addSeparator();

        graph.add(showDags);

        graph.addSeparator();
        graph.add(new JMenuItem(new SelectBidirectedAction(getWorkbench())));
        graph.add(new JMenuItem(new SelectUndirectedAction(getWorkbench())));

        menuBar.add(graph);

        showDags.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {

                        // Needs to be a pattern search; this isn't checked
                        // before running the algorithm because of allowable
                        // "slop"--e.g. bidirected edges.
                        AlgorithmRunner runner = getAlgorithmRunner();
                        Graph graph = runner.getResultGraph();


                        if (graph == null) {
                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(),
                                    "No result gaph.");
                            return;
                        }

//                        if (runner instanceof ImagesRunner) {
//                            GraphScorer scorer = ((ImagesRunner) runner).getGraphScorer();
//                            Graph _graph = ((ImagesRunner) runner).getTopGraphs().get(getIndex()).getGraph();
//
//                            ScoredGraphsDisplay display = new ScoredGraphsDisplay(_graph, scorer);
//                            GraphWorkbench workbench = getWorkbench();
//
//                            EditorWindow editorWindow =
//                                    new EditorWindow(display, "Independence Facts",
//                                            "Close", false, workbench);
//                            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
//                            editorWindow.setVisible(true);
//                        }
//                        else {
                            PatternDisplay display = new PatternDisplay(graph);
                            GraphWorkbench workbench = getWorkbench();

                            EditorWindow editorWindow =
                                    new EditorWindow(display, "Independence Facts",
                                            "Close", false, workbench);
                            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                            editorWindow.setVisible(true);
//                        }
                    }
                };
            }
        });

        meekOrient.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ImpliedOrientation rules = getAlgorithmRunner().getMeekRules();
                rules.setKnowledge(getAlgorithmRunner().getParams().getKnowledge());
                rules.orientImplied(getGraph());
                getGraphHistory().add(getGraph());
                getWorkbench().setGraph(getGraph());
                firePropertyChange("modelChanged", null, null);
            }
        });

        dagInPattern.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Graph graph = new EdgeListGraph(getGraph());

                // Removing bidirected edges from the pattern before selecting a DAG.                                   4
                for (Edge edge : graph.getEdges()) {
                    if (Edges.isBidirectedEdge(edge)) {
                        graph.removeEdge(edge);
                    }
                }

                PatternToDag search = new PatternToDag(new Pattern(graph));
                Graph dag = search.patternToDagMeekRules();

                getGraphHistory().add(dag);
                getWorkbench().setGraph(dag);

                ((AbstractAlgorithmRunner) getAlgorithmRunner()).setResultGraph(dag);
                firePropertyChange("modelChanged", null, null);
            }
        });

        gesOrient.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                DataModel dataModel = getAlgorithmRunner().getDataModel();

                final Graph graph = SearchGraphUtils.reorient(getGraph(), dataModel, getKnowledge());

                getGraphHistory().add(graph);
                getWorkbench().setGraph(graph);
                firePropertyChange("modelChanged", null, null);
            }

        });

        nextGraph.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Graph next = getGraphHistory().next();
                getWorkbench().setGraph(next);
                ((AbstractAlgorithmRunner) getAlgorithmRunner()).setResultGraph(next);
                firePropertyChange("modelChanged", null, null);
            }
        });

        previousGraph.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Graph previous = getGraphHistory().previous();
                getWorkbench().setGraph(previous);
                ((AbstractAlgorithmRunner) getAlgorithmRunner()).setResultGraph(previous);
                firePropertyChange("modelChanged", null, null);
            }
        });

//        if (getAlgorithmRunner().supportsKnowledge()) {
//            menuBar.add(new Knowledge2Menu(this));
//        }

        menuBar.add(new LayoutMenu(this));
    }

    public Graph getSourceGraph() {
        Graph sourceGraph = getWorkbench().getGraph();

        if (sourceGraph == null) {
            sourceGraph = getAlgorithmRunner().getSourceGraph();
        }
        return sourceGraph;
    }

    public List<String> getVarNames() {
        SearchParams params = getAlgorithmRunner().getParams();
        return params.getVarNames();
    }

    public void setTestType(IndTestType testType) {
        super.setTestType(testType);
    }

    public IndTestType getTestType() {
        return super.getTestType();
    }

    public void setKnowledge(IKnowledge knowledge) {
        SearchParams searchParams = getAlgorithmRunner().getParams();

        if (searchParams != null) {
            searchParams.setKnowledge(knowledge);
        }
    }

    public IKnowledge getKnowledge() {
        SearchParams searchParams = getAlgorithmRunner().getParams();

        if (searchParams == null) {
            return null;
        }

        return searchParams.getKnowledge();
    }

    //================================PRIVATE METHODS====================//

    protected void doDefaultArrangement(Graph resultGraph) {
        if (getKnowledge() != null && getKnowledge().isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(resultGraph,
                    getKnowledge());
        } else {
            GraphUtils.arrangeBySourceGraph(resultGraph,
                    getLatestWorkbenchGraph());
        }
    }
}


