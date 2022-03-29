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
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
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
 * Edits some algorithm to search for Markov blanket CPDAGs.
 *
 * @author Joseph Ramsey
 */
public class IonSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable {

    //    private JTabbedPane tabbedPane;
    private IonDisplay ionDisplay;

    //=========================CONSTRUCTORS============================//

    public IonSearchEditor(final IonRunner runner) {
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

    public void layoutByGraph(final Graph graph) {
        getWorkbench().layoutByGraph(graph);
    }

    public void layoutByKnowledge() {
        final GraphWorkbench resultWorkbench = getWorkbench();
        final Graph graph = resultWorkbench.getGraph();
        final IKnowledge knowledge = (IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2());
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
    protected void setup(final String resultLabel) {
        setLayout(new BorderLayout());
        add(getToolbar(), BorderLayout.WEST);
        //JTabbedPane tabbedPane = new JTabbedPane();
//        tabbedPane = new JTabbedPane();
        this.ionDisplay = ionDisplay();
//        tabbedPane.add("Result", lingDisplay);

        /*if (getAlgorithmRunner().getSelectedDataModel() instanceof DataSet) {
            tabbedPane.add("Model Statistics", modelStatsText);
            tabbedPane.add("DAG in CPDAG", dagWorkbench);
        }*/

        add(this.ionDisplay, BorderLayout.CENTER);
//        add(tabbedPane, BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
    }

    private IonDisplay ionDisplay() {
        final Graph resultGraph = resultGraph();
        final List<Graph> storedGraphs = arrangeGraphs();
        final IonDisplay display = new IonDisplay(storedGraphs, (IonRunner) getAlgorithmRunner());

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
            public void mouseExited(final MouseEvent e) {
                storeLatestWorkbenchGraph();
            }
        });

        display.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent propertyChangeEvent) {
                firePropertyChange(propertyChangeEvent.getPropertyName(), null, null);
            }
        });


        return display;
    }

    private List<Graph> arrangeGraphs() {
        final IonRunner runner = (IonRunner) getAlgorithmRunner();

        List<Graph> storedGraphs = runner.getStoredGraphs();
        if (storedGraphs == null) storedGraphs = new ArrayList<>();

        for (final Graph storedGraph : storedGraphs) {
            GraphUtils.circleLayout(storedGraph, 200, 200, 150);
        }

        return storedGraphs;
    }

    private Graph resultGraph() {
        Graph resultGraph = getAlgorithmRunner().getGraph();

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
        final Window owner = (Window) getTopLevelAncestor();

        final WatchedProcess process = new WatchedProcess(owner) {
            public void watch() {
                getExecuteButton().setEnabled(false);
                setErrorMessage(null);

                if (!IonSearchEditor.this.knowledgeMessageShown) {
                    final Parameters searchParams = getAlgorithmRunner().getParams();

                    if (searchParams != null) {
                        final IKnowledge knowledge = (IKnowledge) searchParams.get("knowledge", new Knowledge2());
                        if (!knowledge.isEmpty()) {
                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(),
                                    "Using previously set knowledge. (To edit, use " +
                                            "the Knowledge menu.)");
                            IonSearchEditor.this.knowledgeMessageShown = true;
                        }
                    }
                }

                try {
                    storeLatestWorkbenchGraph();
                    getAlgorithmRunner().execute();
                    final IonRunner runner = (IonRunner) getAlgorithmRunner();
                    arrangeGraphs();
                    IonSearchEditor.this.ionDisplay.resetGraphs(runner.getStoredGraphs());
                } catch (final Exception e) {
                    final CharArrayWriter writer1 = new CharArrayWriter();
                    final PrintWriter writer2 = new PrintWriter(writer1);
                    e.printStackTrace(writer2);
                    final String message = writer1.toString();
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
                final Graph resultGraph = resultGraph();

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

        final Thread watcher = new Thread() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(300);

                        if (!process.isAlive()) {
                            getExecuteButton().setEnabled(true);
                            return;
                        }
                    } catch (final InterruptedException e) {
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
        final JPanel toolbar = new JPanel();

        getExecuteButton().setText("Execute*");
        getExecuteButton().addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                execute();
            }
        });

        final Box b1 = Box.createVerticalBox();

        final Parameters params = getAlgorithmRunner().getParams();

        final JCheckBox pruneByAdjacenciesBox = new JCheckBox("Prune by Adjacencies");
        pruneByAdjacenciesBox.setSelected(params.getBoolean("pruneByAdjacencies", true));

        final JCheckBox pruneByPathLengthBox = new JCheckBox("Prune by Path Length");
        pruneByPathLengthBox.setSelected(params.getBoolean("pruneByPathLength", true));

        pruneByAdjacenciesBox.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final JCheckBox checkBox = (JCheckBox) e.getSource();
                params.set("pruneByAdjacencies", checkBox.isSelected());
            }
        });

        pruneByPathLengthBox.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final JCheckBox checkBox = (JCheckBox) e.getSource();
                params.set("pruneByPathLength", checkBox.isSelected());
            }
        });

        final Box paramsPanel = Box.createVerticalBox();
        paramsPanel.setBorder(new TitledBorder("Parameters"));

        final Box b3a = Box.createHorizontalBox();
        b3a.add(pruneByAdjacenciesBox);
        b3a.add(Box.createHorizontalGlue());
        paramsPanel.add(b3a);

        final Box b3b = Box.createHorizontalBox();
        b3b.add(pruneByPathLengthBox);
        b3b.add(Box.createHorizontalGlue());
        paramsPanel.add(b3b);

        b1.add(paramsPanel);
        b1.add(Box.createVerticalStrut(10));

        final Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(getExecuteButton());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        if (getAlgorithmRunner().getDataModel() instanceof DataSet) {
            final Box b3 = Box.createHorizontalBox();
            b3.add(Box.createGlue());
            b1.add(b3);
        }

        if (getAlgorithmRunner().getParams() instanceof Parameters) {
            b1.add(Box.createVerticalStrut(5));
            final Box hBox = Box.createHorizontalBox();
            hBox.add(Box.createHorizontalGlue());
            b1.add(hBox);
            b1.add(Box.createVerticalStrut(5));
        }

        final Box b4 = Box.createHorizontalBox();
        final JLabel label = new JLabel("<html>" + "*Please note that some" +
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

    protected void addSpecialMenus(final JMenuBar menuBar) {
        if (!(getAlgorithmRunner() instanceof IGesRunner)) {
            final JMenu test = new JMenu("Independence");
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

        final JMenu graph = new JMenu("Graph");
        final JMenuItem showDags = new JMenuItem("Show DAGs in forbid_latent_common_causes");
//        JMenuItem meekOrient = new JMenuItem("Meek Orientation");
        final JMenuItem dagInCPDAG = new JMenuItem("Choose DAG in forbid_latent_common_causes");
        final JMenuItem gesOrient = new JMenuItem("Global Score-based Reorientation");
        final JMenuItem nextGraph = new JMenuItem("Next Graph");
        final JMenuItem previousGraph = new JMenuItem("Previous Graph");

//        graph.add(new LayoutMenu(this));
        graph.add(new GraphPropertiesAction(getWorkbench()));
        graph.add(new PathsAction(getWorkbench()));
//        graph.add(new DirectedPathsAction(getWorkbench()));
//        graph.add(new TreksAction(getWorkbench()));
//        graph.add(new AllPathsAction(getWorkbench()));
//        graph.add(new NeighborhoodsAction(getWorkbench()));
        graph.add(new TriplesAction(getWorkbench().getGraph(), getAlgorithmRunner()));
        graph.addSeparator();

//        graph.add(meekOrient);
        graph.add(dagInCPDAG);
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
            public void actionPerformed(final ActionEvent e) {
                final Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {

                        // Needs to be a CPDAG search; this isn't checked
                        // before running the algorithm because of allowable
                        // "slop"--e.g. bidirected edges.
                        final AlgorithmRunner runner = getAlgorithmRunner();
                        final Graph graph = runner.getGraph();


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
                        final CPDAGDisplay display = new CPDAGDisplay(graph);
                        final GraphWorkbench workbench = getWorkbench();

                        final EditorWindow editorWindow =
                                new EditorWindow(display, "Independence Facts",
                                        "Close", false, workbench);
                        DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                        editorWindow.setVisible(true);
//                        }
                    }
                };
            }
        });

//        meekOrient.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                ImpliedOrientation rules = getAlgorithmRunner().getMeekRules();
//                rules.setKnowledge((IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2()));
//                rules.orientImplied(getGraph());
//                getGraphHistory().add(getGraph());
//                getWorkbench().setGraph(getGraph());
//                firePropertyChange("modelChanged", null, null);
//            }
//        });

        dagInCPDAG.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final Graph graph = new EdgeListGraph(getGraph());

                // Removing bidirected edges from the CPDAG before selecting a DAG.                                   4
                for (final Edge edge : graph.getEdges()) {
                    if (Edges.isBidirectedEdge(edge)) {
                        graph.removeEdge(edge);
                    }
                }

                final Graph dag = SearchGraphUtils.dagFromCPDAG(graph);

                getGraphHistory().add(dag);
                getWorkbench().setGraph(dag);

                ((AbstractAlgorithmRunner) getAlgorithmRunner()).setResultGraph(dag);
                firePropertyChange("modelChanged", null, null);
            }
        });

        gesOrient.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final DataModel dataModel = getAlgorithmRunner().getDataModel();

                final Graph graph = SearchGraphUtils.reorient(getGraph(), dataModel, getKnowledge());

                getGraphHistory().add(graph);
                getWorkbench().setGraph(graph);
                firePropertyChange("modelChanged", null, null);
            }

        });

        nextGraph.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final Graph next = getGraphHistory().next();
                getWorkbench().setGraph(next);
                ((AbstractAlgorithmRunner) getAlgorithmRunner()).setResultGraph(next);
                firePropertyChange("modelChanged", null, null);
            }
        });

        previousGraph.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final Graph previous = getGraphHistory().previous();
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
        final Parameters params = getAlgorithmRunner().getParams();
        return (List<String>) params.get("varNames", null);
    }

    public IndTestType getTestType() {
        return super.getTestType();
    }

    public void setTestType(final IndTestType testType) {
        super.setTestType(testType);
    }

    public IKnowledge getKnowledge() {
        final Parameters searchParams = getAlgorithmRunner().getParams();

        if (searchParams == null) {
            return null;
        }

        return (IKnowledge) searchParams.get("knowledge", new Knowledge2());
    }

    public void setKnowledge(final IKnowledge knowledge) {
        final Parameters searchParams = getAlgorithmRunner().getParams();

        if (searchParams != null) {
            searchParams.set("knowledge", knowledge);
        }
    }

    //================================PRIVATE METHODS====================//

    protected void doDefaultArrangement(final Graph resultGraph) {
        if (getKnowledge() != null && getKnowledge().isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(resultGraph,
                    getKnowledge());
        } else {
            GraphUtils.arrangeBySourceGraph(resultGraph,
                    getLatestWorkbenchGraph());
        }
    }
}


