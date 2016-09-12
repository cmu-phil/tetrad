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
import edu.cmu.tetrad.search.ImpliedOrientation;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.PatternToDag;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.sem.SemEstimator;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
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
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class PcLocalSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable, DoNotScroll {

    private JTextArea modelStatsText;
    private JTabbedPane tabbedPane;
    private boolean alreadyLaidOut = false;

    //=========================CONSTRUCTORS============================//

    public PcLocalSearchEditor(PcLocalRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcLocalSearchEditor(CpcLocalRunner runner) {
        super(runner, "Result E-Pattern");
    }

    public PcLocalSearchEditor(PcMaxLocalRunner runner) {
        super(runner, "Result E-Pattern");
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

    //==========================PROTECTED METHODS============================//


    /**
     * Sets up the editor, does the layout, and so on.
     */
    protected void setup(String resultLabel) {
        setLayout(new BorderLayout());
        add(getToolbar(), BorderLayout.WEST);
        //JTabbedPane tabbedPane = new JTabbedPane();
        modelStatsText = new JTextArea();
        tabbedPane = new JTabbedPane();
        tabbedPane.add("Pattern", workbenchScroll(resultLabel));

        /*if (getAlgorithmRunner().getSelectedDataModel() instanceof DataSet) {
            tabbedPane.add("Model Statistics", modelStatsText);
            tabbedPane.add("DAG in pattern", dagWorkbench);
        }*/

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

        if (getAlgorithmRunner().getParams() instanceof Parameters) {
            Parameters params = getAlgorithmRunner().getParams();
            JCheckBox preventCycles = new JCheckBox("Aggressively Prevent Cycles");
            preventCycles.setHorizontalTextPosition(AbstractButton.RIGHT);
            preventCycles.setSelected(params.getBoolean("aggressivelyPreventCycles", false));

            preventCycles.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JCheckBox box = (JCheckBox) e.getSource();
                    Parameters params = getAlgorithmRunner().getParams();
                    params.set("aggressivelyPreventCycles", box.isSelected());
                }
            });

            b1.add(Box.createVerticalStrut(5));
            Box hBox = Box.createHorizontalBox();
            hBox.add(Box.createHorizontalGlue());
            hBox.add(preventCycles);
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
//        calcStats();
        System.out.println("Post execution.");

//        getWorkbench().addPropertyChangeListener(new PropertyChangeListener() {
//            public void propertyChange(PropertyChangeEvent evt) {
//                System.out.println(evt.getPropertyName());
//            }
//        });
    }


    private void calcStats() {
        Graph resultGraph = getAlgorithmRunner().getGraph();

        if (getAlgorithmRunner().getDataModel() instanceof DataSet) {

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
            PatternToDag ptd = new PatternToDag(pattern);
            Graph dag = ptd.patternToDagMeek();

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

            JScrollPane dagWorkbenchScroll = dagWorkbenchScroll("Dag", dag);
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


        BayesProperties properties = new BayesProperties(dataSet);

        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(4);

        StringBuilder buf = new StringBuilder();
        buf.append("\nP-value = ").append(properties.getLikelihoodRatioP(dag));
        buf.append("\nDf = ").append(properties.getDof());
        buf.append("\nChi square = ")
                .append(nf.format(properties.getChisq()));
        buf.append("\nBIC score = ").append(nf.format(properties.getBic()));
        buf.append("\n\nH0: Completely disconnected graph.");

        return buf.toString();
    }

    private void removeStatsTabs() {
        for (int i = tabbedPane.getTabCount() - 1; i >= 0; i--) {
            String name = tabbedPane.getTitleAt(i);

            if (name.equals("Model Statistics")) {
                tabbedPane.removeTabAt(i);
            } else if (name.equals("DAG in pattern")) {
                tabbedPane.removeTabAt(i);
            }
        }
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
                        Graph graph = runner.getGraph();


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
                rules.setKnowledge((IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2()));
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
        Parameters params = getAlgorithmRunner().getParams();
        return (List<String>) params.get("varNames", null);
    }

    private void addMultiContinuousTestMenuItems(JMenu test) {
        IndTestType testType = getTestType();
        if (testType != IndTestType.POOL_RESIDUALS_FISHER_Z
                && testType != IndTestType.TIPPETT) {
            setTestType(IndTestType.POOL_RESIDUALS_FISHER_Z);
        }

        ButtonGroup group = new ButtonGroup();

        JCheckBoxMenuItem fisher = new JCheckBoxMenuItem("Fisher (Fisher Z)");
        group.add(fisher);
        test.add(fisher);

        JCheckBoxMenuItem tippett = new JCheckBoxMenuItem("Tippett (Fisher Z)");
        group.add(tippett);
        test.add(tippett);

        JCheckBoxMenuItem pooledResidualsFisherZ = new JCheckBoxMenuItem("Pooled Residuals (Fisher Z)");
        group.add(pooledResidualsFisherZ);
        test.add(pooledResidualsFisherZ);

        testType = getTestType();

        if (testType == IndTestType.POOL_RESIDUALS_FISHER_Z) {
            pooledResidualsFisherZ.setSelected(true);
        }

        if (testType == IndTestType.FISHER) {
            fisher.setSelected(true);
        }

        if (testType == IndTestType.TIPPETT) {
            tippett.setSelected(true);
        }

        pooledResidualsFisherZ.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setTestType(IndTestType.POOL_RESIDUALS_FISHER_Z);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Pooled Residuals Fisher Z");
            }
        });

        fisher.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setTestType(IndTestType.TIPPETT);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Fisher");
            }
        });

        tippett.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setTestType(IndTestType.TIPPETT);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Tippett");
            }
        });
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

    private void addDiscreteTestMenuItems(JMenu test) {
        IndTestType testType = getTestType();
        if (testType != IndTestType.CHI_SQUARE &&
                testType != IndTestType.G_SQUARE) {
            setTestType(IndTestType.CHI_SQUARE);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem chiSquare = new JCheckBoxMenuItem("Chi Square");
        group.add(chiSquare);
        test.add(chiSquare);

        JCheckBoxMenuItem gSquare = new JCheckBoxMenuItem("G Square");
        group.add(gSquare);
        test.add(gSquare);

        if (getTestType() == IndTestType.CHI_SQUARE) {
            chiSquare.setSelected(true);
        } else if (getTestType() == IndTestType.G_SQUARE) {
            gSquare.setSelected(true);
        }

        chiSquare.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setTestType(IndTestType.CHI_SQUARE);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using Chi Square.");
            }
        });

        gSquare.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setTestType(IndTestType.G_SQUARE);
                JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                        "Using G square.");
            }
        });
    }

    public void setTestType(IndTestType testType) {
        super.setTestType(testType);
    }

    public IndTestType getTestType() {
        return super.getTestType();
    }

    public void setKnowledge(IKnowledge knowledge) {
        getAlgorithmRunner().getParams().set("knowledge", knowledge);
    }

    public IKnowledge getKnowledge() {
        return (IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2());
    }

    //================================PRIVATE METHODS====================//

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
        return getIndTestParamBox(getAlgorithmRunner().getParams());
    }

    /**
     * Factory to return the correct param editor for independence test params.
     * This will go in a little box in the search editor.
     */
    private JComponent getIndTestParamBox(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        if (params instanceof Parameters) {
            if (getAlgorithmRunner() instanceof IFgsRunner) {
                IFgsRunner fgsRunner = ((IFgsRunner) getAlgorithmRunner());
                return new FgsIndTestParamsEditor(params, fgsRunner.getType());
            }
        }

        if (params instanceof Parameters) {
            return new TimeSeriesIndTestParamsEditor(params);
        }

        if (params instanceof Parameters) {
            return new IndTestParamsEditor(params);
        }

        if (params instanceof Parameters) {
            return new DiscDetIndepParamsEditor(
                    params);
        }

        if (params instanceof Parameters) {
            return new PcIndTestParamsEditor(params);
        }

        if (params instanceof Parameters) {
            return new FciIndTestParamsEditor(params);
        }

        return new IndTestParamsEditor(params);
    }

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

    private JScrollPane dagWorkbenchScroll(String resultLabel, Graph dag) {

        GraphWorkbench dagWorkbench = new GraphWorkbench(dag);
        dagWorkbench.setAllowDoubleClickActions(false);
        dagWorkbench.setAllowNodeEdgeSelection(true);
        JScrollPane dagWorkbenchScroll = new JScrollPane(dagWorkbench);
        dagWorkbenchScroll.setPreferredSize(new Dimension(450, 450));
//        dagWorkbenchScroll.setBorder(new TitledBorder(resultLabel));

        dagWorkbench.addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) {
                storeLatestWorkbenchGraph();
            }
        });

        return dagWorkbenchScroll;
    }

}


