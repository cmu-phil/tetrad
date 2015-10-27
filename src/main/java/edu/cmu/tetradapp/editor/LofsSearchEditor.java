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
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.JOptionUtils;
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
import java.util.List;
import java.util.Map;


/**
 * Edits some algorithms to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class LofsSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable, DoNotScroll {

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given PcRunner.
     */
    public LofsSearchEditor(LofsRunner runner) {
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
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Pattern", workbenchScroll(resultLabel));

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
                execute();
            }
        });

        JCheckBox doRuleR1CheckBox = new JCheckBox("R1");
        JCheckBox doRuleR2CheckBox = new JCheckBox("R2");

        final PcSearchParams searchParams = (PcSearchParams) getAlgorithmRunner().getParams();

        JRadioButton B = new JRadioButton("B");
        JRadioButton A = new JRadioButton("A");
        ButtonGroup group = new ButtonGroup();
        group.add(B);
        group.add(A);

        if (!searchParams.isOrientStrongerDirection()) {
            A.setSelected(true);
        }
        else {
            B.setSelected(true);
        }

        A.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    searchParams.setOrientStrongerDirection(false);
                }
            }
        });

        B.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    searchParams.setOrientStrongerDirection(true);
                }
            }
        });

        JCheckBox orient2cycles = new JCheckBox("Orient 2 cycles in R2");

        orient2cycles.setSelected(searchParams.isR2Orient2Cycles());

        orient2cycles.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JCheckBox checkBox = (JCheckBox) actionEvent.getSource();
                searchParams.setR2Orient2Cycles(checkBox.isSelected());
            }
        });

        JCheckBox meanCenterResiduals = new JCheckBox("Mean center residuals");

        meanCenterResiduals.setSelected(searchParams.isMeanCenterResiduals());

        meanCenterResiduals.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JCheckBox checkBox = (JCheckBox) actionEvent.getSource();
                searchParams.setMeanCenterResiduals(checkBox.isSelected());
            }
        });

        JComboBox scoreBox = new JComboBox();
        scoreBox.addItem("Anderson Darling");
//        scoreBox.addItem("Skew");
        scoreBox.addItem("Kurtosis");
//        scoreBox.addItem("Fifth Moment");
        scoreBox.addItem("Mean Absolute");

        Lofs.Score _score = searchParams.getScore();

        if (_score == Lofs.Score.andersonDarling) {
            scoreBox.setSelectedItem("Anderson Darling");
        }
        else if (_score == Lofs.Score.skew) {
            scoreBox.setSelectedItem("Skew");
        }
        else if (_score == Lofs.Score.kurtosis) {
            scoreBox.setSelectedItem("Kurtosis");
        }
        else if (_score == Lofs.Score.fifthMoment) {
            scoreBox.setSelectedItem("Fifth Moment");
        }
        else if (_score == Lofs.Score.absoluteValue) {
            scoreBox.setSelectedItem("Mean Absolute");
        }

        scoreBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JComboBox box = (JComboBox) actionEvent.getSource();
                String item = (String) box.getSelectedItem();
                System.out.println(item);

                if ("Anderson Darling".equals(item)) {
                    searchParams.setScore(Lofs.Score.andersonDarling);
                }
                else if ("Skew".equals(item)) {
                    searchParams.setScore(Lofs.Score.skew);
                }
                else if ("Kurtosis".equals(item)) {
                    searchParams.setScore(Lofs.Score.kurtosis);
                }
                else if ("Fifth Moment".equals(item)) {
                    searchParams.setScore(Lofs.Score.fifthMoment);
                }
                else if ("Mean Absolute".equals(item)) {
                    searchParams.setScore(Lofs.Score.absoluteValue);
                }
                else {
                    throw new IllegalStateException();
                }
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

//        Box b3a = Box.createHorizontalBox();
//        b3a.add(Box.createGlue());
//        b1.add(b3a);;

        Box b3b = Box.createHorizontalBox();
        b3b.add(new JLabel("Do rules:"));
        b3b.add(doRuleR1CheckBox);
        b3b.add(doRuleR2CheckBox);
//        b3b.add(doMeekCheckBox);
        b3b.add(Box.createHorizontalGlue());
        b1.add(b3b);

        Box b3c = Box.createHorizontalBox();
//        b3c.add(new JLabel("R2:"));
        b3c.add(B);
        b3c.add(A);
        b3c.add(Box.createHorizontalGlue());
        b1.add(b3c);

        Box b3d = Box.createHorizontalBox();
//        b3d.add(new JLabel("R2:"));
        b3d.add(orient2cycles);
        b3d.add(Box.createHorizontalGlue());
        b1.add(b3d);

        Box b3e = Box.createHorizontalBox();
        b3e.add(meanCenterResiduals);
        b3e.add(Box.createHorizontalGlue());
        b1.add(b3e);

        Box b3f = Box.createHorizontalBox();
        b3f.add(new JLabel("Score:"));
        b3f.add(scoreBox);
        b3f.add(Box.createHorizontalGlue());
        b1.add(b3f);

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
        getAlgorithmRunner().getParams().setKnowledge(knowledge);
    }

    public IKnowledge getKnowledge() {
        return getAlgorithmRunner().getParams().getKnowledge();
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

        if (indTestParams instanceof GesIndTestParams) {
            if (getAlgorithmRunner() instanceof IGesRunner) {
                GesRunner gesRunner = ((GesRunner) getAlgorithmRunner());
                GesIndTestParams params = (GesIndTestParams) indTestParams;
                DataModel dataModel = gesRunner.getDataModel();
                boolean discreteData = dataModel instanceof DataSet && ((DataSet) dataModel).isDiscrete();
                return new GesIndTestParamsEditor(params, discreteData);
            }

            if (getAlgorithmRunner() instanceof  ImagesRunner) {
                ImagesRunner gesRunner = ((ImagesRunner) getAlgorithmRunner());
                GesIndTestParams params = (GesIndTestParams) indTestParams;
                DataSet dataSet = (DataSet) gesRunner.getDataModel();
                boolean discreteData = dataSet.isDiscrete();
                return new GesIndTestParamsEditor(params, discreteData);
            }
//            else if (getAlgorithmRunner() instanceof PValueImproverWrapper) {
//                PValueImproverWrapper runner = ((PValueImproverWrapper) getAlgorithmRunner());
//                GesIndTestParams params = (GesIndTestParams) indTestParams;
//                boolean discreteData =
//                        runner.getSelectedDataModel() instanceof RectangularDataSet;
//                return new GesIndTestParamsEditor(params, discreteData);
//            }
        }

        if (indTestParams instanceof LagIndTestParams) {
            return new TimeSeriesIndTestParamsEditor(
                    (LagIndTestParams) indTestParams);
        }

        if (indTestParams instanceof GraphIndTestParams) {
            return new IndTestParamsEditor((GraphIndTestParams) indTestParams);
        }

        if (indTestParams instanceof DiscDetIndepParams) {
            return new DiscDetIndepParamsEditor(
                    (DiscDetIndepParams) indTestParams);
        }

        if (indTestParams instanceof PcIndTestParams) {
            if (getAlgorithmRunner() instanceof LingamPatternRunner) {
                return new PcLingamIndTestParamsEditor((PcIndTestParams) indTestParams);
            }

            if (getAlgorithmRunner() instanceof LofsRunner) {
                return new PcLingamIndTestParamsEditor((PcIndTestParams) indTestParams);
            }

            return new PcIndTestParamsEditor((PcIndTestParams) indTestParams);
        }

        return new IndTestParamsEditor(indTestParams);
    }

    protected void doDefaultArrangement(Graph resultGraph) {
        if (getLatestWorkbenchGraph() != null) {   //(alreadyLaidOut) {
            GraphUtils.arrangeBySourceGraph(resultGraph,
                    getLatestWorkbenchGraph());
        } else if (getKnowledge().isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(resultGraph,
                    getKnowledge());
        } else {
            GraphUtils.circleLayout(resultGraph, 200, 200, 150);
        }
    }

}

