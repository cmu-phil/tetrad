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
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.Lofs;
import edu.cmu.tetrad.search.SearchGraphUtils;
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
import java.util.List;
import java.util.Map;


/**
 * Edits some algorithm to search for Markov blanket CPDAGs.
 *
 * @author Joseph Ramsey
 */
public class LofsSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable, DoNotScroll {

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given PcRunner.
     */
    public LofsSearchEditor(final LofsRunner runner) {
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
        final JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("forbid_latent_common_causes", workbenchScroll(resultLabel));

        add(tabbedPane, BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
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

        final JCheckBox doRuleR1CheckBox = new JCheckBox("R1");
        final JCheckBox doRuleR2CheckBox = new JCheckBox("R2");

        final Parameters searchParams = getAlgorithmRunner().getParams();

        final JRadioButton B = new JRadioButton("B");
        final JRadioButton A = new JRadioButton("A");
        final ButtonGroup group = new ButtonGroup();
        group.add(B);
        group.add(A);

        if (!searchParams.getBoolean("orientStrongerDirection", true)) {
            A.setSelected(true);
        } else {
            B.setSelected(true);
        }

        A.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent actionEvent) {
                final JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    searchParams.set("orientStrongerDirection", false);
                }
            }
        });

        B.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent actionEvent) {
                final JRadioButton button = (JRadioButton) actionEvent.getSource();
                if (button.isSelected()) {
                    searchParams.set("orientStrongerDirection", true);
                }
            }
        });

        final JCheckBox orient2cycles = new JCheckBox("Orient 2 cycles in R2");

        orient2cycles.setSelected(searchParams.getBoolean("r2Orient2Cycles", false));

        orient2cycles.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent actionEvent) {
                final JCheckBox checkBox = (JCheckBox) actionEvent.getSource();
                searchParams.set("r2Orient2Cycles", checkBox.isSelected());
            }
        });

        final JCheckBox meanCenterResiduals = new JCheckBox("Mean center residuals");

        meanCenterResiduals.setSelected(searchParams.getBoolean("meanCenterResiduals", false));

        meanCenterResiduals.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent actionEvent) {
                final JCheckBox checkBox = (JCheckBox) actionEvent.getSource();
                searchParams.set("meanCenterResiduals", checkBox.isSelected());
            }
        });

        final JComboBox scoreBox = new JComboBox();
        scoreBox.addItem("Anderson Darling");
//        scoreBox.addItem("Skew");
        scoreBox.addItem("Kurtosis");
//        scoreBox.addItem("Fifth Moment");
        scoreBox.addItem("Mean Absolute");

        Lofs.Score _score = (Lofs.Score) searchParams.get("score", Lofs.Score.andersonDarling);

        if (_score == Lofs.Score.andersonDarling) {
            scoreBox.setSelectedItem("Anderson Darling");
        } else if (_score == Lofs.Score.skew) {
            scoreBox.setSelectedItem("Skew");
        } else if (_score == Lofs.Score.kurtosis) {
            scoreBox.setSelectedItem("Kurtosis");
        } else if (_score == Lofs.Score.fifthMoment) {
            scoreBox.setSelectedItem("Fifth Moment");
        } else if (_score == Lofs.Score.absoluteValue) {
            scoreBox.setSelectedItem("Mean Absolute");
        }

        scoreBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JComboBox box = (JComboBox) actionEvent.getSource();
                String item = (String) box.getSelectedItem();
                System.out.println(item);

                if ("Anderson Darling".equals(item)) {
                    searchParams.set("score", Lofs.Score.andersonDarling);
                } else if ("Skew".equals(item)) {
                    searchParams.set("score", Lofs.Score.skew);
                } else if ("Kurtosis".equals(item)) {
                    searchParams.set("score", Lofs.Score.kurtosis);
                } else if ("Fifth Moment".equals(item)) {
                    searchParams.set("score", Lofs.Score.fifthMoment);
                } else if ("Mean Absolute".equals(item)) {
                    searchParams.set("score", Lofs.Score.absoluteValue);
                } else {
                    throw new IllegalStateException();
                }
            }
        });

        final Box b1 = Box.createVerticalBox();
        b1.add(getParamsPanel());
        b1.add(Box.createVerticalStrut(10));

        final Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(getExecuteButton());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

//        Box b3a = Box.createHorizontalBox();
//        b3a.add(Box.createGlue());
//        b1.add(b3a);;

        final Box b3b = Box.createHorizontalBox();
        b3b.add(new JLabel("Do rules:"));
        b3b.add(doRuleR1CheckBox);
        b3b.add(doRuleR2CheckBox);
//        b3b.add(doMeekCheckBox);
        b3b.add(Box.createHorizontalGlue());
        b1.add(b3b);

        final Box b3c = Box.createHorizontalBox();
//        b3c.add(new JLabel("R2:"));
        b3c.add(B);
        b3c.add(A);
        b3c.add(Box.createHorizontalGlue());
        b1.add(b3c);

        final Box b3d = Box.createHorizontalBox();
//        b3d.add(new JLabel("R2:"));
        b3d.add(orient2cycles);
        b3d.add(Box.createHorizontalGlue());
        b1.add(b3d);

        final Box b3e = Box.createHorizontalBox();
        b3e.add(meanCenterResiduals);
        b3e.add(Box.createHorizontalGlue());
        b1.add(b3e);

        final Box b3f = Box.createHorizontalBox();
        b3f.add(new JLabel("Score:"));
        b3f.add(scoreBox);
        b3f.add(Box.createHorizontalGlue());
        b1.add(b3f);

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

    public void setTestType(final IndTestType testType) {
        super.setTestType(testType);
    }

    public IndTestType getTestType() {
        return super.getTestType();
    }

    public void setKnowledge(final IKnowledge knowledge) {
        getAlgorithmRunner().getParams().set("knowledge", knowledge);
    }

    public IKnowledge getKnowledge() {
        return (IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2());
    }

    //================================PRIVATE METHODS====================//

    private JPanel getParamsPanel() {
        final JPanel paramsPanel = new JPanel();

        final Box b2 = Box.createVerticalBox();

        final JComponent indTestParamBox = getIndTestParamBox();
        if (indTestParamBox != null) {
            b2.add(indTestParamBox);
        }

        paramsPanel.add(b2);
        paramsPanel.setBorder(new TitledBorder("Parameters"));
        return paramsPanel;
    }

    private JComponent getIndTestParamBox() {
        final Parameters params = getAlgorithmRunner().getParams();
        return getIndTestParamBox(params);
    }

    /**
     * Factory to return the correct param editor for independence test params.
     * This will go in a little box in the search editor.
     */
    private JComponent getIndTestParamBox(final Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        if (params instanceof Parameters) {
            if (getAlgorithmRunner() instanceof IFgesRunner) {
                final IFgesRunner fgesRunner = ((IFgesRunner) getAlgorithmRunner());
                return new FgesIndTestParamsEditor(params, fgesRunner.getType());
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
            if (getAlgorithmRunner() instanceof LingamCPDAGRunner) {
                return new PcLingamIndTestParamsEditor(params);
            }

            if (getAlgorithmRunner() instanceof LofsRunner) {
                return new PcLingamIndTestParamsEditor(params);
            }

            return new PcIndTestParamsEditor(params);
        }

        return new IndTestParamsEditor(params);
    }

    protected void doDefaultArrangement(final Graph resultGraph) {
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

