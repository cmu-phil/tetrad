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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.Mbfs;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.*;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.cmu.tetradapp.workbench.LayoutMenu;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;


/**
 * Edits some algorithm to search for Markov blanket CPDAGs.
 *
 * @author Joseph Ramsey
 */
public class MbSearchEditor extends AbstractSearchEditor
        implements LayoutEditable, KnowledgeEditable, DoNotScroll {

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given PcxRunner.
     */
    public MbSearchEditor(final MbfsRunner runner) {
        super(runner, "Result MB forbid_latent_common_causes");
    }

    public MbSearchEditor(final FgesMbRunner runner) {
        super(runner, "Result MB forbid_latent_common_causes");
    }

    public MbSearchEditor(final MbFanSearchRunner runner) {
        super(runner, "Result MB forbid_latent_common_causes");
    }

    /**
     * Opens up an editor to let the user view the given PcxRunner.
     */
    public MbSearchEditor(final CeFanSearchRunner runner) {
        super(runner, "Result Causal Environment");
    }

    public void setKnowledge(final IKnowledge knowledge) {
        getAlgorithmRunner().getParams().set("knowledge", knowledge);
    }

    @Override
    public Map getModelEdgesToDisplay() {
        return getWorkbench().getModelEdgesToDisplay();
    }

    public Map getModelNodesToDisplay() {
        return getWorkbench().getModelNodesToDisplay();
    }

    public IKnowledge getKnowledge() {
        return (IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2());
    }

    public java.util.List<String> getVarNames() {
        final Parameters params = getAlgorithmRunner().getParams();
        return (java.util.List<String>) params.get("varNames", null);
    }

    public Graph getSourceGraph() {
        Graph sourceGraph = getWorkbench().getGraph();

        if (sourceGraph == null) {
            sourceGraph = getAlgorithmRunner().getSourceGraph();
        }
        return sourceGraph;
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
        b1.add(getParamsPanel());
        b1.add(Box.createVerticalStrut(10));
        final Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(getExecuteButton());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        final Box b3 = Box.createHorizontalBox();
        final JLabel label = new JLabel("<html>" + "*Please note that some" +
                "<br>searches may take a" + "<br>long time to complete." +
                "</html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setBorder(new TitledBorder(""));
        b3.add(label);
        b1.add(b3);

        toolbar.add(b1);
        return toolbar;
    }

    protected void addSpecialMenus(final JMenuBar menuBar) {
        final JMenu test = new JMenu("Independence");
        menuBar.add(test);

        IndTestMenuItems.addIndependenceTestChoices(test, this);

//        test.addSeparator();
//
//        AlgorithmRunner algorithmRunner = getAlgorithmRunner();

//        if (algorithmRunner instanceof IndTestProducer) {
//            IndTestProducer p = (IndTestProducer) algorithmRunner;
//            IndependenceFactsAction action =
//                    new IndependenceFactsAction(this, p, "Independence Facts...");
//            test.add(action);
//        }

        if (getAlgorithmRunner() instanceof MbfsRunner) {
            final JMenu graph = new JMenu("Graph");
            final JMenuItem showDags = new JMenuItem("Show DAG's Consistent with forbid_latent_common_causes");
//            JMenuItem meekOrient = new JMenuItem("Meek Orientation");
            final JMenuItem gesOrient = new JMenuItem("Global Score-based Reorientation");
            final JMenuItem nextGraph = new JMenuItem("Next Graph");
            final JMenuItem previousGraph = new JMenuItem("Previous Graph");

            graph.add(new GraphPropertiesAction(getWorkbench()));
            graph.add(new PathsAction(getWorkbench()));
//            graph.add(new DirectedPathsAction(getWorkbench()));
//            graph.add(new TreksAction(getWorkbench()));
//            graph.add(new AllPathsAction(getWorkbench()));
//            graph.add(new NeighborhoodsAction(getWorkbench()));
            graph.addSeparator();

//            graph.add(meekOrient);
            graph.add(gesOrient);
            graph.addSeparator();

            graph.add(previousGraph);
            graph.add(nextGraph);
            graph.addSeparator();

            graph.add(showDags);
            menuBar.add(graph);

            showDags.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    final AlgorithmRunner runner = getAlgorithmRunner();

                    if (!(runner instanceof MbfsRunner)) {
                        return;
                    }

                    final MbfsRunner mbRunner = (MbfsRunner) runner;
                    final Mbfs search = mbRunner.getMbFanSearch();

                    if (search == null) {
                        JOptionPane.showMessageDialog(
                                JOptionUtils.centeringComp(),
                                "The search was not stored.");
                        return;
                    }

                    final MbCPDAGDisplay display = new MbCPDAGDisplay(search);

                    JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                            display, "MB DAG's Consistent with forbid_latent_common_causes",
                            JOptionPane.PLAIN_MESSAGE);
                }
            });

//            meekOrient.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    ImpliedOrientation rules = getAlgorithmRunner().getMeekRules();
//                    rules.setKnowledge((IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2()));
//                    rules.orientImplied(getGraph());
//                    getWorkbench().setGraph(getGraph());
//                }
//            });

            gesOrient.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    final DataModel dataModel = getAlgorithmRunner().getDataModel();

                    final Graph graph = SearchGraphUtils.reorient(getGraph(), dataModel, getKnowledge());

                    getGraphHistory().add(graph);
                    getWorkbench().setGraph(graph);
                    firePropertyChange("modelChanged", null, null);
                }

            });
        }

//        if (getAlgorithmRunner().supportsKnowledge()) {
//            menuBar.add(new Knowledge2Menu(this));
//        }

        menuBar.add(new LayoutMenu(this));
    }

    //================================PRIVATE METHODS====================//

    private JPanel getParamsPanel() {
        final JPanel paramsPanel = new JPanel();
        paramsPanel.setLayout(new BoxLayout(paramsPanel, BoxLayout.Y_AXIS));
        paramsPanel.add(getSearchParamBox());
        paramsPanel.setBorder(new TitledBorder("Parameters"));
        return paramsPanel;
    }

    private Box getSearchParamBox() {
        if (!(getAlgorithmRunner().getParams() instanceof Parameters)) {
            throw new IllegalStateException();
        }

        final Box b = Box.createHorizontalBox();
        final Parameters params =
                getAlgorithmRunner().getParams();
        final MbSearchParamEditor comp = new MbSearchParamEditor();
        comp.setParams(params);

        comp.setParams(params);
        comp.setup();

        b.add(comp);
        return b;
    }


    public void setTestType(final IndTestType testType) {
        super.setTestType(testType);
    }

    public IndTestType getTestType() {
        return super.getTestType();
    }

    /**
     * Should always layout out nodes as in source graph. Otherwise, for source
     * graphs with many nodes, it's just impossible to find the nodes of the MB
     * DAG.
     */
    protected void doDefaultArrangement(final Graph resultGraph) {
        final Graph sourceGraph = getAlgorithmRunner().getSourceGraph();
        final boolean arrangedAll =
                GraphUtils.arrangeBySourceGraph(resultGraph, sourceGraph);

        if (!arrangedAll) {
            GraphUtils.circleLayout(resultGraph, 200, 200, 150);
        }
    }
}


