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
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
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
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * Edits some algorithms to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class FgsSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable, Indexable, DoNotScroll {

    private JTextArea modelStatsText;
    private JTabbedPane tabbedPane;
    private GesDisplay gesDisplay;

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given GesRunner.
     */
    public FgsSearchEditor(GesRunner runner) {
        super(runner, "Result Pattern");
//        getWorkbench().setGraph(runner.getTopGraphs().get(runner.getTopGraphs().size() - 1).getGraph());
    }

    /**
     * Opens up an editor to let the user view the given GesRunner.
     */
    public FgsSearchEditor(FgsRunner runner) {
        super(runner, "Result Pattern");
//        getWorkbench().setGraph(runner.getTopGraphs().get(runner.getTopGraphs().size() - 1).getGraph());
    }

    public FgsSearchEditor(ImagesRunner runner) {
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
                                JOptionUtils.centeringComp(),
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

        modelStatsText = new JTextArea();
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

        if (getAlgorithmRunner().getDataModel() instanceof DataSet) {
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

                        if (runner instanceof GesRunner) {
                            GraphScorer scorer = ((GesRunner) runner).getGraphScorer();
                            Graph _graph = ((GesRunner) runner).getTopGraphs().get(getIndex()).getGraph();

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

    private List<ScoredGraph> arrangeGraphs() {
        IGesRunner runner = (IGesRunner) getAlgorithmRunner();
        Graph resultGraph = runner.getResultGraph();

        List<ScoredGraph> topGraphs = runner.getTopGraphs();

        if (topGraphs == null) topGraphs = new ArrayList<ScoredGraph>();

        Graph latestWorkbenchGraph = runner.getParams().getSourceGraph();
        Graph sourceGraph = runner.getSourceGraph();

        boolean arrangedAll = false;

        for (ScoredGraph topGraph1 : topGraphs) {
            arrangedAll = GraphUtils.arrangeBySourceGraph(topGraph1.getGraph(),
                    latestWorkbenchGraph);
        }

        if (!arrangedAll) {
            arrangedAll = GraphUtils.arrangeBySourceGraph(resultGraph, sourceGraph);
        }

        if (!arrangedAll) {
            for (ScoredGraph topGraph : topGraphs) {
                GraphUtils.circleLayout(topGraph.getGraph(), 200, 200, 150);
                GraphUtils.circleLayout(resultGraph, 200, 200, 150);
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
//        Graph resultGraph = getAlgorithmRunner().getResultGraph();
        IGesRunner runner = (IGesRunner) getAlgorithmRunner();

        if (runner.getTopGraphs().isEmpty()) {
            throw new IllegalArgumentException("No patterns were recorded. Please adjust the number of " +
                    "patterns to store.");
        }

        Graph resultGraph = runner.getTopGraphs().get(runner.getIndex()).getGraph();

        if (getAlgorithmRunner().getDataModel() instanceof DataSet) {

            //resultGraph may be the output of a PC search.
            //Such graphs sometimes contain doubly directed edges.

            // /We converte such edges to directed edges here.
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
            PatternToDag ptd = new PatternToDag(pattern);
            Graph dag = ptd.patternToDagMeekRules();

            DataSet dataSet =
                    (DataSet) getAlgorithmRunner().getDataModel();
            String report;

            if (dataSet.isContinuous()) {
                report = reportIfContinuous(dag, dataSet);
            } else if (dataSet.isDiscrete()) {
                report = reportIfDiscrete(dag, dataSet);
            } else {
                throw new IllegalArgumentException("");
            }

            JScrollPane dagWorkbenchScroll = dagWorkbenchScroll(dag);
            modelStatsText.setLineWrap(true);
            modelStatsText.setWrapStyleWord(true);
            modelStatsText.setText(report);

            removeStatsTabs();
            tabbedPane.addTab("DAG in pattern", dagWorkbenchScroll);
            tabbedPane.addTab("DAG Model Statistics", modelStatsText);
        }
    }

    private String reportIfContinuous(Graph dag, DataSet dataSet) {
        SemPm semPm = new SemPm(dag);

        SemEstimator estimator = new SemEstimator(dataSet, semPm);
        estimator.estimate();
        SemIm semIm = estimator.getEstimatedSem();

        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(4);

        StringBuilder buf = new StringBuilder();
        buf.append("\nDegrees of Freedom = ").append(semPm.getDof())
                .append("Chi-Square = ").append(nf.format(semIm.getChiSquare()))
                .append("\nP Value = ").append(nf.format(semIm.getPValue()))
                .append("\nBIC Score = ").append(nf.format(semIm.getBicScore()));

        buf.append("\n\nThe above chi square test assumes that the maximum " +
                "likelihood function over the measured variables has been " +
                "maximized. Under that assumption, the null hypothesis for " +
                "the test is that the population covariance matrix over all " +
                "of the measured variables is equal to the estimated covariance " +
                "matrix over all of the measured variables written as a function " +
                "of the free model parameters--that is, the unfixed parameters " +
                "for each directed edge (the linear coefficient for that edge), " +
                "each exogenous variable (the variance for the error term for " +
                "that variable), and each bidirected edge (the covariance for " +
                "the exogenous variables it connects).  The model is explained " +
                "in Bollen, Structural Equations with Latent Variable, 110. ");

        return buf.toString();
    }

    private String reportIfDiscrete(Graph dag, DataSet dataSet) {
        List vars = dataSet.getVariables();
        Map<String, DiscreteVariable> nodesToVars =
                new HashMap<String, DiscreteVariable>();
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
                List<String> categories = new ArrayList<String>();
                for (int j = 0; j < numCategories; j++) {
                    categories.add(var2.getCategory(j));
                }
                bayesPm.setCategories(node, categories);
            }
        }


        BayesProperties properties = new BayesProperties(dataSet, dag);
        properties.setGraph(dag);

        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(4);

        StringBuilder buf = new StringBuilder();
        buf.append("\nP-value = ").append(properties.getLikelihoodRatioP());
        buf.append("\nDf = ").append(properties.getPValueDf());
        buf.append("\nChi square = ")
                .append(nf.format(properties.getPValueChisq()));
        buf.append("\nBIC score = ").append(nf.format(properties.getBic()));
        buf.append("\n\nH0: Completely disconnected graph.");

        return buf.toString();
    }

    private void removeStatsTabs() {
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            String name = tabbedPane.getTitleAt(i);

            if (name.equals("DAG Model Statistics")) {
                tabbedPane.removeTabAt(i);
            } else if (name.equals("DAG in pattern")) {
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
//                && testType != IndTestType.CORRELATION_T
                ) {
            setTestType(IndTestType.FISHER_Z);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem fishersZ = new JCheckBoxMenuItem("Fisher's Z");
        group.add(fishersZ);
        test.add(fishersZ);

//        JCheckBoxMenuItem tTest = new JCheckBoxMenuItem("Cramer's T");
//        group.add(tTest);
//        test.add(tTest);

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
        FgsParams params = (FgsParams) getAlgorithmRunner().getParams();
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

        if (indTestParams instanceof GesIndTestParams) {
            if (getAlgorithmRunner() instanceof IGesRunner) {
                IGesRunner gesRunner = ((IGesRunner) getAlgorithmRunner());
                GesIndTestParams params = (GesIndTestParams) indTestParams;
                DataModel dataModel = gesRunner.getDataModel();
                boolean discreteData = dataModel instanceof DataSet && ((DataSet) dataModel).isDiscrete();
                return new GesIndTestParamsEditor(params, discreteData);
            }

            if (getAlgorithmRunner() instanceof ImagesRunner) {
                ImagesRunner gesRunner = ((ImagesRunner) getAlgorithmRunner());
                GesIndTestParams params = (GesIndTestParams) indTestParams;
                DataSet dataSet = (DataSet) gesRunner.getDataModel();
                boolean discreteData = dataSet.isDiscrete();
                return new GesIndTestParamsEditor(params, discreteData);
            }
        }

        if (indTestParams instanceof FgsIndTestParams) {
            FgsRunner fgsRunner = ((FgsRunner) getAlgorithmRunner());
            FgsIndTestParams params = (FgsIndTestParams) indTestParams;
            DataModel dataModel = fgsRunner.getDataModel();
            boolean discreteData = dataModel instanceof DataSet && ((DataSet) dataModel).isDiscrete();
            return new FgsIndTestParamsEditor(params, discreteData);
        }

        return new IndTestParamsEditor(indTestParams);
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


