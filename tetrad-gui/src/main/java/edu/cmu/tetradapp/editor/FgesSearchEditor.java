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

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.BayesProperties;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.*;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.IntTextField;
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
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class FgesSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable, Indexable, DoNotScroll {

    //    private JTextArea bootstrapEdgeCountsScroll;
    private JTabbedPane tabbedPane;
    private boolean alreadyLaidOut = false;
    private FgesDisplay gesDisplay;
    private FgesIndTestParamsEditor paramsEditor;

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given FgesRunner.
     */
    public FgesSearchEditor(FgesRunner runner) {
        super(runner, "Result forbid_latent_common_causes");
    }

    public FgesSearchEditor(WFgesRunner runner) {
        super(runner, "Result forbid_latent_common_causes");
    }

    public FgesSearchEditor(FgesMbRunner runner) {
        super(runner, "Result forbid_latent_common_causes");
    }

    public FgesSearchEditor(ImagesRunner runner) {
        super(runner, "Result forbid_latent_common_causes");
    }

    public FgesSearchEditor(TsFgesRunner runner) {
        super(runner, "Result forbid_latent_common_causes");
    }

    public FgesSearchEditor(TsImagesRunner runner) {
        super(runner, "Result forbid_latent_common_causes");
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
        IKnowledge knowledge = (IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2());
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
                    IKnowledge knowledge = (IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2());
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
                    IFgesRunner runner = (IFgesRunner) getAlgorithmRunner();
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
        Parameters params = getAlgorithmRunner().getParams();
        return (List<String>) params.get("varNames", null);
    }

    public void setKnowledge(IKnowledge knowledge) {
        getAlgorithmRunner().getParams().set("knowledge", knowledge);
    }

    public IKnowledge getKnowledge() {
        return (IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2());
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
        tabbedPane.add("forbid_latent_common_causes", gesDisplay());

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

//        if (getAlgorithmRunner().getParameters() instanceof Parameters) {
//            Parameters params = (Parameters) getAlgorithmRunner().getParameters();
//            JCheckBox preventCycles = new JCheckBox("Aggressively Prevent Cycles");
//            preventCycles.setHorizontalTextPosition(AbstractButton.RIGHT);
//            preventCycles.setSelected(params.isAggressivelyPreventCycles());
//
//            preventCycles.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    JCheckBox box = (JCheckBox) e.getSource();
//                    Parameters params = (Parameters) getAlgorithmRunner().getParameters();
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

    protected void addSpecialMenus(JMenuBar menuBar) {
        if (!(getAlgorithmRunner() instanceof FgesRunner)) {
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
        JMenuItem showDags = new JMenuItem("Show DAGs in forbid_latent_common_causes");
//        JMenuItem meekOrient = new JMenuItem("Meek Orientation");
        final JMenuItem dagInPattern = new JMenuItem("Choose DAG in forbid_latent_common_causes");
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

//        graph.add(meekOrient);
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
                        Graph graph = runner.getGraph();


                        if (graph == null) {
                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(),
                                    "No result gaph.");
                            return;
                        }

                        if (runner instanceof FgesRunner) {
                            GraphScorer scorer = ((FgesRunner) runner).getGraphScorer();
                            Graph _graph = ((FgesRunner) runner).getTopGraphs().get(getIndex()).getGraph();

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

        dagInPattern.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Graph graph = new EdgeListGraph(getGraph());

                // Removing bidirected edges from the pattern before selecting a DAG.                                   4
                for (Edge edge : graph.getEdges()) {
                    if (Edges.isBidirectedEdge(edge)) {
                        graph.removeEdge(edge);
                    }
                }

                PatternToDag search = new PatternToDag(new EdgeListGraphSingleConnections(graph));
                Graph dag = search.patternToDagMeek();

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

    private FgesDisplay gesDisplay() {
        Graph resultGraph = resultGraph();
        List<ScoredGraph> topGraphs = arrangeGraphs();
        FgesDisplay display = new FgesDisplay(resultGraph, topGraphs, this);
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

    private List<ScoredGraph> arrangeGraphs() {
        IFgesRunner runner = (IFgesRunner) getAlgorithmRunner();

        List<ScoredGraph> topGraphs = runner.getTopGraphs();

        if (topGraphs == null) topGraphs = new ArrayList<>();

//        Graph latestWorkbenchGraph = (Graph) runner.getParameters().get("sourceGraph", null);
        Graph sourceGraph = runner.getSourceGraph();

        boolean arrangedAll = false;

//        for (int i = 0; i < topGraphs.size(); i++) {
//            arrangedAll = GraphUtils.arrangeBySourceGraph(topGraphs.get(i).getGraph(),
//                    latestWorkbenchGraph);
//        }

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
        Graph resultGraph = getAlgorithmRunner().getGraph();

        if (resultGraph == null) {
            resultGraph = new EdgeListGraph();
        }

        return resultGraph;
    }


    private void calcStats() {
        FgesRunner runner = (FgesRunner) getAlgorithmRunner();

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

            Graph dag = SearchGraphUtils.dagFromPattern(resultGraph);

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

            String bayesFactorsReport = ((FgesRunner) getAlgorithmRunner()).getBayesFactorsReport(dag);
//            String bootstrapEdgeCountsReport = ((ImagesRunner) getAlgorithmRunner()).getBootstrapEdgeCountsReport(25);

            JScrollPane dagWorkbenchScroll = dagWorkbenchScroll(dag);

//            modelStatsText = new JTextArea();
            JTextArea logBayesFactorsScroll = new JTextArea();
//            bootstrapEdgeCountsScroll = new JTextArea();

//            modelStatsText.setLineWrap(true);
//            modelStatsText.setWrapStyleWord(true);
//            modelStatsText.setText(report);

            logBayesFactorsScroll.setLineWrap(true);
            logBayesFactorsScroll.setWrapStyleWord(true);
            logBayesFactorsScroll.setText(bayesFactorsReport);

//            bootstrapEdgeCountsScroll.setLineWrap(true);
//            bootstrapEdgeCountsScroll.setWrapStyleWord(true);
//            bootstrapEdgeCountsScroll.setText(bootstrapEdgeCountsReport);

//            JPanel bootstrapPanel = new JPanel();
//            bootstrapPanel.setLayout(new BorderLayout());
//            bootstrapPanel.add(bootstrapEdgeCountsScroll, BorderLayout.CENTER);

            JTextArea modelStatisticsScroll = new JTextArea();
//            bootstrapEdgeCountsScroll = new JTextArea();

//            modelStatsText.setLineWrap(true);
//            modelStatsText.setWrapStyleWord(true);
//            modelStatsText.setText(report);

            modelStatisticsScroll.setLineWrap(true);
            modelStatisticsScroll.setWrapStyleWord(true);
            modelStatisticsScroll.setText(reportIfDiscrete(dag, (DataSet) getDataModel()));

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
//                            String bootstrapEdgeCountsReport = ((ImagesRunner) getAlgorithmRunner()).getBootstrapEdgeCountsReport(n);
//                            bootstrapEdgeCountsScroll.setText(bootstrapEdgeCountsReport);
                        }
                    };
                }
            });

            b.add(Box.createHorizontalGlue());
            b.add(goButton);

//            bootstrapPanel.add(b, BorderLayout.NORTH);

            removeStatsTabs();
            tabbedPane.addTab("DAG in pattern", dagWorkbenchScroll);
//            tabbedPane.addTab("DAG Model Statistics", new JScrollPane(modelStatsText));
            tabbedPane.addTab("Log Bayes Factors", new JScrollPane(logBayesFactorsScroll));
//            tabbedPane.addTab("Edge Bootstraps", new JScrollPane(bootstrapPanel));
            tabbedPane.addTab("Model Stats", new JScrollPane(modelStatisticsScroll));
        }
    }

    private String reportIfDiscrete(Graph dag, DataSet dataSet) {
        List vars = dataSet.getVariables();
        Map<String, DiscreteVariable> nodesToVars =
                new HashMap<>();
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            DiscreteVariable var = (DiscreteVariable) vars.get(i);
            String name = var.getName();
            Node node = new GraphNode(name);
            nodesToVars.put(node.getName(), var);
        }

        BayesPm bayesPm = new BayesPm(new Dag(dag));
        List<Node> nodes = bayesPm.getDag().getNodes();

        for (Node node : nodes) {
            Node var = nodesToVars.get(node.getName());

            if (var instanceof DiscreteVariable) {
                DiscreteVariable var2 = nodesToVars.get(node.getName());
                int numCategories = var2.getNumCategories();
                List<String> categories = new ArrayList<>();
                for (int j = 0; j < numCategories; j++) {
                    categories.add(var2.getCategory(j));
                }
                bayesPm.setCategories(node, categories);
            }
        }


        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(4);

        StringBuilder buf = new StringBuilder();

        BayesProperties properties = new BayesProperties(dataSet);

        double p = properties.getLikelihoodRatioP(dag);
        double chisq = properties.getChisq();
        double bic = properties.getBic();
        double dof = properties.getDof();

        buf.append("\nP  = ").append(p);
        buf.append("\nDOF = ").append(dof);
        buf.append("\nChiSq = ").append(nf.format(chisq));
        buf.append("\nBIC = ").append(nf.format(bic));
        buf.append("\n\nH0: Complete DAG.");

        return buf.toString();
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
        Parameters params = getAlgorithmRunner().getParams();
        return getIndTestParamBox(params);
    }

    /**
     * Factory to return the correct param editor for independence test params.
     * This will go in a little box in the search editor.
     */
    private JComponent getIndTestParamBox(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        AlgorithmRunner algorithmRunner = getAlgorithmRunner();

        if (algorithmRunner instanceof IFgesRunner) {
            IFgesRunner fgesRunner = ((IFgesRunner) algorithmRunner);
            return new FgesIndTestParamsEditor(params, fgesRunner.getType());
        }

        if (algorithmRunner instanceof FgesMbRunner) {
            FgesMbRunner fgesRunner = ((FgesMbRunner) algorithmRunner);
            return new FgesIndTestParamsEditor(params, fgesRunner.getType());
        }

        throw new IllegalArgumentException();
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
}


