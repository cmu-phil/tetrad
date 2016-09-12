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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class PcGesSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable, IndTestTypeSetter, DoNotScroll     {

    private JTextArea modelStatsText;
    private JTabbedPane tabbedPane;

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given PcRunner.
     */
    public PcGesSearchEditor(PcRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(PcMaxRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(PcStableRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(PcPatternRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(CpcRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(VcpcRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(SampleVcpcRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(VcpcFastRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(SampleVcpcFastRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(VcpcAltRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(MbfsPatternRunner runner) {
        super(runner, "Result Pattern");
    }

    /**
     * Opens up an editor to let the user view the given PcRunner.
     */
    public PcGesSearchEditor(PcdRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(KpcRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(PValueImproverWrapper runner) {
        super(runner, "Result Graph");

        runner.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {

                if ("graph".equals(evt.getPropertyName())) {
                    Graph graph = (Graph) evt.getNewValue();
                    getWorkbench().setGraph(graph);
                }
            }
        });
    }

    public PcGesSearchEditor(MmhcRunner runner) {
        super(runner, "Result Pattern");
    }

    public PcGesSearchEditor(LingamPatternRunner runner) {
        super(runner, "Result Graph");
    }

    public PcGesSearchEditor(LofsRunner runner) {
        super(runner, "Result Graph");
    }

    public PcGesSearchEditor(LingamStructureRunner runner) {
        super(runner, "Result Graph");
    }

    public PcGesSearchEditor(FasRunner runner) {
        super(runner, "Result Graph");
    }

    public PcGesSearchEditor(InverseCorrelationRunner runner) {
        super(runner, "Result Graph");
    }

    public PcGesSearchEditor(RandomMixedRunner runner) {
        super(runner, "Result Graph");
    }

//    public PcGesSearchEditor(GlassoRunner runner) {
//        super(runner, "Result Graph");
//    }

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

        if (getAlgorithmRunner().getDataModel() instanceof DataSet ||
                getAlgorithmRunner().getDataModel() instanceof ICovarianceMatrix) {
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
            //We convert such edges to directed edges here.
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

            DataSet dataSet = (DataSet) getAlgorithmRunner().getDataModel();
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
        } else if (getAlgorithmRunner().getDataModel() instanceof ICovarianceMatrix) {
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

            ICovarianceMatrix dataSet = (ICovarianceMatrix) getAlgorithmRunner().getDataModel();
            String report = reportIfCovMatrix(dag, dataSet);

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
                .append(" Chi-Square = ").append(nf.format(semIm.getChiSquare()))
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

    private String reportIfCovMatrix(Graph dag, ICovarianceMatrix dataSet) {
        SemPm semPm = new SemPm(dag);

        SemEstimator estimator = new SemEstimator(dataSet, semPm);
        estimator.estimate();
        SemIm semIm = estimator.getEstimatedSem();

        NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(4);

        StringBuilder buf = new StringBuilder();
        buf.append("\nDegrees of Freedom = ").append(semPm.getDof())
                .append(" Chi-Square = ").append(nf.format(semIm.getChiSquare()))
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

//                // Removing bidirected edges from the pattern before selecting a DAG.                                   4
//                for (Edge edge : graph.getEdges()) {
//                    if (Edges.isBidirectedEdge(edge)) {
//                        graph.removeEdge(edge);
//                    }
//                }

                Graph dag = SearchGraphUtils.dagFromPattern(graph);

//                PatternToDag search = new PatternToDag(new Pattern(graph));
//                Graph dag = search.patternToDagMeek();

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

    private void addMixedTestMenuItems(JMenu test) {
        IndTestType testType = getTestType();
        if (testType != IndTestType.MIXED_MLR) {
            setTestType(IndTestType.MIXED_MLR);
        }

        ButtonGroup group = new ButtonGroup();
        JCheckBoxMenuItem logr = new JCheckBoxMenuItem("Multinomial Logistic Regression");
        group.add(logr);
        test.add(logr);
        logr.setSelected(true);

        logr.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setTestType(IndTestType.MIXED_MLR);
            }
        });
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
                return new FgsIndTestParamsEditor(params, ((IFgsRunner) getAlgorithmRunner()).getType());
            }
        }

        if (params instanceof Parameters) {
            return new TimeSeriesIndTestParamsEditor(
                     params);
        }

        if (params instanceof Parameters) {
            return new IndTestParamsEditor(params);
        }

        if (params instanceof Parameters) {
            return new DiscDetIndepParamsEditor(
                    params);
        }

        if (params instanceof Parameters) {
            if (getAlgorithmRunner() instanceof LingamPatternRunner) {
                return new PcLingamIndTestParamsEditor(params);
            }

            if (getAlgorithmRunner() instanceof LofsRunner) {
                return new PcLingamIndTestParamsEditor(params);
            }

            return new PcIndTestParamsEditor(params);
        }

        return new IndTestParamsEditor(params);
    }

    protected void doDefaultArrangement(Graph resultGraph) {
        if (getLatestWorkbenchGraph() != null) {
            GraphUtils.arrangeBySourceGraph(resultGraph,
                    getLatestWorkbenchGraph());
        } else if (getKnowledge().isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(resultGraph,
                    getKnowledge());
        } else {
            GraphUtils.circleLayout(resultGraph, 200, 200, 150);
        }
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




