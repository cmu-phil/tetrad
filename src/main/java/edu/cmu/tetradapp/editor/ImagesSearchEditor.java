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
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.*;
import edu.cmu.tetradapp.util.*;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Edits some algorithms to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class ImagesSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable, Indexable, DoNotScroll {

    private JTextArea modelStatsText;
    private JTextArea logBayesFactorsScroll;
    private JTextArea bootstrapEdgeCountsScroll;
    private JTabbedPane tabbedPane;
    private boolean alreadyLaidOut = false;
    private GesDisplay gesDisplay;
    private ImagesIndTestParamsEditor paramsEditor;

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given GesRunner.
     */
    public ImagesSearchEditor(GesRunner runner) {
        super(runner, "Result Pattern");
//        getWorkbench().setGraph(runner.getTopGraphs().get(runner.getTopGraphs().size() - 1).getGraph());
    }

    public ImagesSearchEditor(ImagesRunner runner) {
        super(runner, "Result Pattern");
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


    public void setIndex(int index) {
        ((Indexable) getAlgorithmRunner()).setIndex(index);
        removeStatsTabs();
        firePropertyChange("modelChanged", null, null);
    }

    public int getIndex() {
        return ((Indexable) getAlgorithmRunner()).getIndex();
    }

    public void execute() {
        Window owner = (Window) getTopLevelAncestor();

        final WatchedProcess process = new WatchedProcess(owner) {
            public void watch() {
                getExecuteButton().setEnabled(false);
                setErrorMessage(null);

                if (!knowledgeMessageShown) {
                    IKnowledge knowledge = getAlgorithmRunner().getParams().getKnowledge();
                    if (!knowledge.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                getWorkbench(),
                                "Using previously set knowledge. (To edit, use " +
                                        "the Knowledge menu.)");
                        knowledgeMessageShown = true;
                    }
                }

                try {
                    storeLatestWorkbenchGraph();
                    getAlgorithmRunner().execute();
                    IGesRunner runner = (IGesRunner) getAlgorithmRunner();
                    arrangeGraphs();
                    gesDisplay.resetGraphs(runner.getTopGraphs());
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
//                getWorkbench().setGraph(gesDisplay.getTopGraphs().get(gesDisplay.getTopGraphs().size() - 1).getGraph());
                getGraphHistory().clear();
                getGraphHistory().add(resultGraph);
                getWorkbench().repaint();

                // For Mimbuild, e.g., that need to do a second stage.
                firePropertyChange("algorithmFinished", null, null);
                getExecuteButton().setEnabled(true);
                firePropertyChange("modelChanged", null, null);

                doPostExecutionSteps();
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
                    } catch (InterruptedException e) {
                        getExecuteButton().setEnabled(true);
                        return;
                    }
                }
            }
        };

        watcher.start();
    }

    public List<String> getVarNames() {
        SearchParams params = getAlgorithmRunner().getParams();
        return params.getVarNames();
    }

    public void setKnowledge(IKnowledge knowledge) {
        getAlgorithmRunner().getParams().setKnowledge(knowledge);
    }

    public IKnowledge getKnowledge() {
        return getAlgorithmRunner().getParams().getKnowledge();
    }

    //==========================PROTECTED METHODS============================//


    protected void doDefaultArrangement(Graph resultGraph) {
        if (getLatestWorkbenchGraph() != null) {   //(alreadyLaidOut) {
            GraphUtils.arrangeBySourceGraph(resultGraph,
                    getLatestWorkbenchGraph());
        } else if (getKnowledge().isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(resultGraph,
                    getKnowledge());
//            alreadyLaidOut = true;
        } else {
            GraphUtils.circleLayout(resultGraph, 200, 200, 150);
//            alreadyLaidOut = true;
        }
    }

    /**
     * Sets up the editor, does the layout, and so on.
     */
    protected void setup(String resultLabel) {
        setLayout(new BorderLayout());
        add(getToolbar(), BorderLayout.WEST);

        tabbedPane = new JTabbedPane();
        tabbedPane.add("Pattern", gesDisplay());

        add(tabbedPane, BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
    }

    /**
     * Construct the toolbar panel.
     */
    protected JPanel getToolbar() {
        JPanel toolbar = new JPanel();

        getExecuteButton().setText("Execute*");
        getExecuteButton().addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                removeStatsTabs();
                execute();
            }
        });

        JButton statsButton = new JButton("Calc Stats");
        statsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Window owner = (Window) getTopLevelAncestor();

                new WatchedProcess(owner) {
                    public void watch() {
                        calcStats();
                    }
                };
            }
        });


        Box b1 = Box.createVerticalBox();
        b1.add(getParamsPanel());
        b1.add(Box.createVerticalStrut(10));

        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(getExecuteButton());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        if (!(getAlgorithmRunner().getDataModel() instanceof ICovarianceMatrix)) {
            Box b3 = Box.createHorizontalBox();
            b3.add(Box.createGlue());
            b3.add(statsButton);
            b1.add(b3);
        }

//        if (getAlgorithmRunner().getParams() instanceof MeekSearchParams) {
//            MeekSearchParams params = (MeekSearchParams) getAlgorithmRunner().getParams();
//            JCheckBox preventCycles = new JCheckBox("Aggressively Prevent Cycles");
//            preventCycles.setHorizontalTextPosition(AbstractButton.RIGHT);
//            preventCycles.setSelected(params.isAggressivelyPreventCycles());
//
//            preventCycles.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    JCheckBox box = (JCheckBox) e.getSource();
//                    MeekSearchParams params = (MeekSearchParams) getAlgorithmRunner().getParams();
//                    params.setAggressivelyPreventCycles(box.isSelected());
//                }
//            });
//
//            b1.add(Box.createVerticalStrut(5));
//            Box hBox = Box.createHorizontalBox();
//            hBox.add(Box.createHorizontalGlue());
//            hBox.add(preventCycles);
//            b1.add(hBox);
//            b1.add(Box.createVerticalStrut(5));
//        }

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
        GesIndTestParams gesIndTestParams = (GesIndTestParams) getAlgorithmRunner().getParams().getIndTestParams();
        this.paramsEditor.setPenaltyDiscount(gesIndTestParams.getPenaltyDiscount());

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
        final JMenuItem dagInPattern = new JMenuItem("Choose DAG in Pattern");
        JMenuItem gesOrient = new JMenuItem("Global Score-based Reorientation");
        JMenuItem nextGraph = new JMenuItem("Next Graph");
        JMenuItem previousGraph = new JMenuItem("Previous Graph");

        graph.add(new GraphPropertiesAction(getWorkbench()));
        graph.add(new PathsAction(getWorkbench()));
//        graph.add(new DirectedPathsAction(getWorkbench()));
//        graph.add(new TreksAction(getWorkbench()));
//        graph.add(new AllPathsAction(getWorkbench()));
//        graph.add(new NeighborhoodsAction(getWorkbench()));
//        graph.add(new TriplesAction(getWorkbench(), getAlgorithmRunner()));
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

                        if (runner instanceof ImagesRunner) {
                            GraphScorer scorer = ((ImagesRunner) runner).getGraphScorer();
                            Graph _graph = ((ImagesRunner) runner).getTopGraphs().get(getIndex()).getGraph();

                            ScoredGraphsDisplay display = new ScoredGraphsDisplay(_graph, scorer);
                            GraphWorkbench workbench = getWorkbench();

                            EditorWindow editorWindow =
                                    new EditorWindow(display, "Independence Facts",
                                            "Close", false, workbench);
                            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                            editorWindow.setVisible(true);
                        } else {
                            PatternDisplay display = new PatternDisplay(graph);
                            GraphWorkbench workbench = getWorkbench();

                            EditorWindow editorWindow =
                                    new EditorWindow(display, "Independence Facts",
                                            "Close", false, workbench);
                            DesktopController.getInstance().addEditorWindow(editorWindow, JLayeredPane.PALETTE_LAYER);
                            editorWindow.setVisible(true);
                        }
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

        menuBar.add(new LayoutMenu(this));
    }

    //==============================PRIVATE METHODS=============================//

    private GesDisplay gesDisplay() {
        Graph resultGraph = resultGraph();
        List<ScoredGraph> topGraphs = arrangeGraphs();
        GesDisplay display = new GesDisplay(resultGraph, topGraphs, this);
        this.gesDisplay = display;

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

        return display;
    }

    // TODO Fix this.
    private List<ScoredGraph> arrangeGraphs() {
        IGesRunner runner = (IGesRunner) getAlgorithmRunner();

        List<ScoredGraph> topGraphs = runner.getTopGraphs();

        if (topGraphs == null) topGraphs = new ArrayList<ScoredGraph>();

        Graph latestWorkbenchGraph = runner.getParams().getSourceGraph();
        Graph sourceGraph = runner.getSourceGraph();

        boolean arrangedAll = false;

        for (int i = 0; i < topGraphs.size(); i++) {
            arrangedAll = GraphUtils.arrangeBySourceGraph(topGraphs.get(i).getGraph(),
                    latestWorkbenchGraph);
        }

        if (!arrangedAll) {
            for (ScoredGraph topGraph : topGraphs) {
                arrangedAll = GraphUtils.arrangeBySourceGraph(topGraph.getGraph(), sourceGraph);
            }
        }

        if (!arrangedAll) {
            for (ScoredGraph topGraph : topGraphs) {
                GraphUtils.circleLayout(topGraph.getGraph(), 200, 200, 150);
            }
        }

        return topGraphs;
    }

    private Graph resultGraph() {
        Graph resultGraph = getAlgorithmRunner().getResultGraph();

        if (resultGraph == null) {
            resultGraph = new EdgeListGraph();
        }

        return resultGraph;
    }


    private void calcStats() {
        IGesRunner runner = (IGesRunner) getAlgorithmRunner();

        if (runner.getTopGraphs().isEmpty()) {
            throw new IllegalArgumentException("No patterns were recorded. Please adjust the number of " +
                    "patterns to store.");
        }

        Graph resultGraph = runner.getTopGraphs().get(runner.getIndex()).getGraph();

        if (!(getAlgorithmRunner().getDataModel() instanceof ICovarianceMatrix)) {

            //resultGraph may be the output of a PC search.
            //Such graphs sometimes contain doubly directed edges.
            //We converte such edges to directed edges here.
            //For the time being an orientation is arbitrarily selected.
            Set<Edge> allEdges = resultGraph.getEdges();

            for (Edge edge : allEdges) {
                if (edge.getEndpoint1() == Endpoint.ARROW &&
                        edge.getEndpoint2() == Endpoint.ARROW) {
                    //Option 1 orient it from node1 to node2
                    resultGraph.setEndpoint(edge.getNode1(),
                            edge.getNode2(), Endpoint.ARROW);

                    //Option 2 remove such edges:
                    resultGraph.removeEdge(edge);
                }
            }

            Pattern pattern = new Pattern(resultGraph);
            Graph dag = SearchGraphUtils.dagFromPattern(pattern);

//            DataSet dataSet = (DataSet) getAlgorithmRunner().getDataModel();
//            String report;
//
//            if (dataSet.isContinuous()) {
//                report = reportIfContinuous(dag, dataSet);
//            } else if (dataSet.isDiscrete()) {
//                report = reportIfDiscrete(dag, dataSet);
//            } else {
//                throw new IllegalArgumentException("");
//            }

            String bayesFactorsReport = ((ImagesRunner) getAlgorithmRunner()).getBayesFactorsReport(dag);
            String bootstrapEdgeCountsReport = ((ImagesRunner) getAlgorithmRunner()).getBootstrapEdgeCountsReport(25);

            JScrollPane dagWorkbenchScroll = dagWorkbenchScroll(dag);

            modelStatsText = new JTextArea();
            logBayesFactorsScroll = new JTextArea();
            bootstrapEdgeCountsScroll = new JTextArea();

            modelStatsText.setLineWrap(true);
            modelStatsText.setWrapStyleWord(true);
//            modelStatsText.setText(report);

            logBayesFactorsScroll.setLineWrap(true);
            logBayesFactorsScroll.setWrapStyleWord(true);
            logBayesFactorsScroll.setText(bayesFactorsReport);

            bootstrapEdgeCountsScroll.setLineWrap(true);
            bootstrapEdgeCountsScroll.setWrapStyleWord(true);
            bootstrapEdgeCountsScroll.setText(bootstrapEdgeCountsReport);

            JPanel bootstrapPanel = new JPanel();
            bootstrapPanel.setLayout(new BorderLayout());
            bootstrapPanel.add(bootstrapEdgeCountsScroll, BorderLayout.CENTER);

            Box b = Box.createHorizontalBox();
            b.add(new JLabel("# Bootstraps = "));

            final IntTextField numBootstraps = new IntTextField(25, 8);

            numBootstraps.setFilter(new IntTextField.Filter() {
                public int filter(int value, int oldValue) {
                    if (value < 0) return oldValue;
                    else return value;
                }
            });

            b.add(numBootstraps);

            JButton goButton = new JButton("Go!");

            goButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent actionEvent) {
                    Window owner = (Window) getTopLevelAncestor();

                    new WatchedProcess(owner) {
                        public void watch() {
                            int n = numBootstraps.getValue();
                            String bootstrapEdgeCountsReport = ((ImagesRunner) getAlgorithmRunner()).getBootstrapEdgeCountsReport(n);
                            bootstrapEdgeCountsScroll.setText(bootstrapEdgeCountsReport);
                        }
                    };
                }
            });

            b.add(Box.createHorizontalGlue());
            b.add(goButton);

            bootstrapPanel.add(b, BorderLayout.NORTH);

            removeStatsTabs();
            tabbedPane.addTab("DAG in pattern", dagWorkbenchScroll);
//            tabbedPane.addTab("DAG Model Statistics", new JScrollPane(modelStatsText));
            tabbedPane.addTab("Log Bayes Factors", new JScrollPane(logBayesFactorsScroll));
            tabbedPane.addTab("Edge Bootstraps", new JScrollPane(bootstrapPanel));
        }
    }

    private void removeStatsTabs() {
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            String name = tabbedPane.getTitleAt(i);

            if (name.equals("DAG Model Statistics")) {
                tabbedPane.removeTabAt(i);
            } else if (name.equals("DAG in pattern")) {
                tabbedPane.removeTabAt(i);
            } else if (name.equals("Log Bayes Factors")) {
                tabbedPane.removeTabAt(i);
            } else if (name.equals("Edge Bootstraps")) {
                tabbedPane.removeTabAt(i);
            }
        }
    }

    public Graph getSourceGraph() {
        Graph sourceGraph = getWorkbench().getGraph();

        if (sourceGraph == null) {
            sourceGraph = getAlgorithmRunner().getSourceGraph();
        }
        return sourceGraph;
    }

    private void addCovMatrixTestMenuItems(JMenu test) {
        IndTestType testType = getTestType();
        if (testType != IndTestType.FISHER_Z
//                &&
//                testType != IndTestType.CORRELATION_T
                ) {
            setTestType(IndTestType.FISHER_Z);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem fishersZ = new JCheckBoxMenuItem("Fisher's Z");
        group.add(fishersZ);
        test.add(fishersZ);

        JCheckBoxMenuItem tTest = new JCheckBoxMenuItem("Cramer's T");
        group.add(tTest);
        test.add(tTest);

        testType = getTestType();

        if (testType == IndTestType.FISHER_Z) {
            fishersZ.setSelected(true);
        }
//        else if (testType == IndTestType.CORRELATION_T) {
//            tTest.setSelected(true);
//        }

        fishersZ.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setTestType(IndTestType.FISHER_Z);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Fisher's Z.");
            }
        });

//        tTest.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                setTestType(IndTestType.CORRELATION_T);
//                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
//                        "Using Cramer's T.");
//            }
//        });
    }

    public void setTestType(IndTestType testType) {
        super.setTestType(testType);
    }

    public IndTestType getTestType() {
        return super.getTestType();
    }

    private JPanel getParamsPanel() {
        JPanel paramsPanel = new JPanel();

        Box b2 = Box.createVerticalBox();

        JComponent indTestParamBox = getIndTestParamBox();
        if (indTestParamBox != null) {
            b2.add(indTestParamBox);
        }

        paramsPanel.add(b2);
        paramsPanel.setBorder(new TitledBorder("Parameters"));
        return paramsPanel;
    }

    private JComponent getIndTestParamBox() {
        SearchParams params = getAlgorithmRunner().getParams();
        IndTestParams indTestParams = params.getIndTestParams();
        return getIndTestParamBox(indTestParams);
    }

    /**
     * Factory to return the correct param editor for independence test params.
     * This will go in a little box in the search editor.
     */
    private JComponent getIndTestParamBox(IndTestParams indTestParams) {
        if (indTestParams == null) {
            throw new NullPointerException();
        }

        GesIndTestParams params = (GesIndTestParams) indTestParams;
        ImagesIndTestParamsEditor paramsEditor = new ImagesIndTestParamsEditor(params, false);

        this.paramsEditor = paramsEditor;

        return paramsEditor;
    }

    private JScrollPane dagWorkbenchScroll(Graph dag) {

        GraphWorkbench dagWorkbench = new GraphWorkbench(dag);
        dagWorkbench.setAllowDoubleClickActions(false);
        dagWorkbench.setAllowNodeEdgeSelection(true);
        JScrollPane dagWorkbenchScroll = new JScrollPane(dagWorkbench);
        dagWorkbenchScroll.setPreferredSize(new Dimension(450, 450));

        dagWorkbench.addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) {
                storeLatestWorkbenchGraph();
            }
        });

        return dagWorkbenchScroll;
    }

    private static class ImagesIndTestParamsEditor extends JComponent {

        private GesIndTestParams params;
        private DoubleTextField cellPriorField, structurePriorField;
        private JButton defaultStructurePrior;
        private JButton uniformStructurePrior;
        private boolean discreteData;
        private IntTextField numPatternsToSave;
        private DoubleTextField penaltyDiscount;
        private JCheckBox firstNontriangular;
        private DoubleTextField minJump;

        public ImagesIndTestParamsEditor(GesIndTestParams simulator, boolean discreteData) {
            this.params = simulator;
            this.discreteData = discreteData;

            NumberFormat nf = new DecimalFormat("0.0####");

            if (this.discreteData) {
                this.cellPriorField = new DoubleTextField(
                        getGesIndTestParams().getSamplePrior(), 5, nf);

                this.cellPriorField.setFilter(new DoubleTextField.Filter() {
                    public double filter(double value, double oldValue) {
                        try {
                            getGesIndTestParams().setSamplePrior(value);
                            return value;
                        } catch (IllegalArgumentException e) {
                            return oldValue;
                        }
                    }
                });

                this.structurePriorField = new DoubleTextField(
                        getGesIndTestParams().getStructurePrior(), 5, nf);
                this.structurePriorField.setFilter(new DoubleTextField.Filter() {
                    public double filter(double value, double oldValue) {
                        try {
                            getGesIndTestParams().setStructurePrior(value);
                            return value;
                        } catch (IllegalArgumentException e) {
                            return oldValue;
                        }
                    }
                });

                this.defaultStructurePrior =
                        new JButton("Default structure prior = 0.05");
                Font font = new Font("Dialog", Font.BOLD, 10);
                this.defaultStructurePrior.setFont(font);
                this.defaultStructurePrior.setBorder(null);
                this.defaultStructurePrior.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        structurePriorField.setValue(0.05);
                    }
                });

                this.uniformStructurePrior =
                        new JButton("Uniform structure prior = 1.0");
                this.uniformStructurePrior.setFont(font);
                this.uniformStructurePrior.setBorder(null);
                this.uniformStructurePrior.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        structurePriorField.setValue(1.0);
                    }
                });
            } else {
                penaltyDiscount = new DoubleTextField(
                        getGesIndTestParams().getPenaltyDiscount(), 5, nf);
                penaltyDiscount.setFilter(new DoubleTextField.Filter() {
                    public double filter(double value, double oldValue) {
                        try {
                            getGesIndTestParams().setPenaltyDiscount(value);
                            return value;
                        } catch (IllegalArgumentException e) {
                            return oldValue;
                        }
                    }
                });
            }

            this.numPatternsToSave = new IntTextField(
                    getGesIndTestParams().getNumPatternsToSave(), 5);
            this.numPatternsToSave.setFilter(new IntTextField.Filter() {
                public int filter(int value, int oldValue) {
                    try {
                        getGesIndTestParams().setNumPatternsToSave(value);
                        return value;
                    } catch (IllegalArgumentException e) {
                        return oldValue;
                    }
                }
            });


//            if (!discreteData) {
//                this.fCutoffP = new DoubleTextField(
//                        getGesIndTestParams().getfCutoffP(), 5, nf);
//                this.fCutoffP.setFilter(new DoubleTextField.Filter() {
//                    public double filter(double value, double oldValue) {
//                        try {
//                            getGesIndTestParams().setMinimumJump(value);
//                            return value;
//                        }
//                        catch (IllegalArgumentException e) {
//                            return oldValue;
//                        }
//                    }
//                });
//
//                this.useFCutoff = new JCheckBox();
//                this.useFCutoff.setSelected(getGesIndTestParams().isUseMinimumJump());
//                this.useFCutoff.addActionListener(new ActionListener() {
//                    public void actionPerformed(ActionEvent actionEvent) {
//                        JCheckBox box = (JCheckBox) actionEvent.getSource();
//                        getGesIndTestParams().setUseFCutoff(box.isSelected());
//                    }
//                });
//            }

            if (!discreteData) {
                this.minJump = new DoubleTextField(
                        getGesIndTestParams().getMinJump(), 5, nf);
                this.minJump.setFilter(new DoubleTextField.Filter() {
                    public double filter(double value, double oldValue) {
                        try {
                            getGesIndTestParams().setMinJump(value);
                            return value;
                        } catch (IllegalArgumentException e) {
                            return oldValue;
                        }
                    }
                });
            }

            firstNontriangular = new JCheckBox("Find first nontriangular");
            firstNontriangular = new JCheckBox();
            firstNontriangular.setSelected(getGesIndTestParams().isFirstNontriangular());
            firstNontriangular.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent actionEvent) {
                    JCheckBox source = (JCheckBox) actionEvent.getSource();
                    firstNontriangular.setSelected(source.isSelected());
                    getGesIndTestParams().setFirstNontriangular(source.isSelected());
                }
            });

            buildGui();
        }

        private void buildGui() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            if (discreteData) {
                Box b0 = Box.createHorizontalBox();
                b0.add(new JLabel("BDeu:"));
                b0.add(Box.createHorizontalGlue());
                add(b0);
                add(Box.createVerticalStrut(5));

                Box b2 = Box.createHorizontalBox();
                b2.add(Box.createHorizontalStrut(5));
                b2.add(new JLabel("Sample prior:"));
                b2.add(Box.createHorizontalGlue());
                b2.add(this.cellPriorField);
                add(b2);
                add(Box.createVerticalStrut(5));

                Box b3 = Box.createHorizontalBox();
                b3.add(Box.createHorizontalStrut(5));
                b3.add(new JLabel("Structure prior:"));
                b3.add(Box.createHorizontalGlue());
                b3.add(this.structurePriorField);
                add(b3);

                Box b4 = Box.createHorizontalBox();
                b4.add(Box.createHorizontalGlue());
                b4.add(this.defaultStructurePrior);
                add(b4);

                Box b5 = Box.createHorizontalBox();
                b5.add(Box.createHorizontalGlue());
                b5.add(this.uniformStructurePrior);
                add(b5);
                add(Box.createVerticalStrut(10));

//            Box b6 = Box.createHorizontalBox();
//            b6.add(new JLabel("Continuous (SEM Score):"));
//            b6.add(Box.createHorizontalGlue());
//            add(b6);
//            add(Box.createVerticalStrut(5));
            } else {
                Box b7 = Box.createHorizontalBox();
                b7.add(new JLabel("Penalty Discount"));
                b7.add(Box.createHorizontalGlue());
                b7.add(penaltyDiscount);
                add(b7);

//                Box b7a = Box.createHorizontalBox();
//                b7a.add(this.useFCutoff);
//                b7a.add(new JLabel("Use F Cutoff, p ="));
//                b7a.add(Box.createHorizontalGlue());
//                b7a.add(this.fCutoffP);
//                add(b7a);

//                Box b7a = Box.createHorizontalBox();
//                b7a.add(new JLabel("Min jump = "));
//                b7a.add(Box.createHorizontalGlue());
//                b7a.add(this.minJump);
//                add(b7a);


//            Box b1 = Box.createHorizontalBox();
//            b1.add(new JLabel("No parameters to set"));
//            add(b1);
                add(Box.createHorizontalGlue());

                Box b8 = Box.createHorizontalBox();
                b8.add(new JLabel("Num Patterns to Save"));
                b8.add(Box.createHorizontalGlue());
                b8.add(this.numPatternsToSave);
                add(b8);

                Box b9 = Box.createHorizontalBox();
                b9.add(new JLabel("Find first nontriangular: "));
                b9.add(firstNontriangular);
                add(b9);
            }
        }

        private GesIndTestParams getGesIndTestParams() {
            return params;
        }

        public void setPenaltyDiscount(double penaltyDiscount) {
            System.out.println("**** " + penaltyDiscount);
            this.penaltyDiscount.setValue(penaltyDiscount);
        }
    }

}


