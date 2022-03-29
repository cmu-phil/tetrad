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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.GlassoRunner;
import edu.cmu.tetradapp.model.InverseCorrelationRunner;
import edu.cmu.tetradapp.model.KnowledgeEditable;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;
import edu.cmu.tetradapp.util.LayoutEditable;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

/**
 * Edits some algorithm to search for Markov blanket CPDAGs.
 *
 * @author Joseph Ramsey
 */
public class GlassoSearchEditor extends AbstractSearchEditor
        implements KnowledgeEditable, LayoutEditable {

    private final boolean alreadyLaidOut = false;

    //=========================CONSTRUCTORS============================//

    public GlassoSearchEditor(final GlassoRunner runner) {
        super(runner, "Result Graph");
    }

    public GlassoSearchEditor(final InverseCorrelationRunner runner) {
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
        final JTextArea modelStatsText = new JTextArea();
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

        final Box b1 = Box.createVerticalBox();
        b1.add(getParamsPanel());
        b1.add(Box.createVerticalStrut(10));

        final Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(getExecuteButton());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

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

    @Override
    protected void addSpecialMenus(final JMenuBar menuBar) {
        //To change body of implemented methods use File | Settings | File Templates.
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

        final IntTextField maxItField = new IntTextField((int) params.get("maxit", 10000), 6);
        maxItField.setFilter(new IntTextField.Filter() {
            public int filter(final int value, final int oldValue) {
                try {
                    params.set("maxit", value);
                    return value;
                } catch (final Exception e) {
                    return oldValue;
                }
            }
        });

        final DoubleTextField thrField = new DoubleTextField(params.getDouble("thr", 1e-4), 8,
                new DecimalFormat("0.0########"), new DecimalFormat("0.00E00"), 1e-4);
        thrField.setFilter(new DoubleTextField.Filter() {
            public double filter(final double value, final double oldValue) {
                try {
                    params.set("thr", value);
                    return value;
                } catch (final Exception e) {
                    return oldValue;
                }
            }
        });

        final JCheckBox iaCheckBox = new JCheckBox("Meinhausen-Buhlman");
        iaCheckBox.setSelected(params.getBoolean("ia", false));

        iaCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                final JCheckBox checkBox = (JCheckBox) actionEvent.getSource();
                params.set("is", checkBox.isSelected());
            }
        });

        final JCheckBox isCheckBox = new JCheckBox("Warm start");
        isCheckBox.setSelected(params.getBoolean("ia", false));

        isCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                final JCheckBox checkBox = (JCheckBox) actionEvent.getSource();
                params.set("is", checkBox.isSelected());
            }
        });

        final JCheckBox itrCheckBox = new JCheckBox("Log trace");
        itrCheckBox.setSelected(params.getBoolean("ia", false));

        itrCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                final JCheckBox checkBox = (JCheckBox) actionEvent.getSource();
                params.set("itr", checkBox.isSelected());
            }
        });

        final JCheckBox ipenCheckBox = new JCheckBox("Penalize diagonal");
        ipenCheckBox.setSelected(params.getBoolean("ipen", false));

        ipenCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                final JCheckBox checkBox = (JCheckBox) actionEvent.getSource();
                params.set("ipen", checkBox.isSelected());
            }
        });

        final Box b = Box.createVerticalBox();

        final Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Max iterations"));
        b1.add(Box.createHorizontalGlue());
        b1.add(maxItField);
        b.add(b1);

        final Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Threshold"));
        b2.add(Box.createHorizontalGlue());
        b2.add(thrField);
        b.add(b2);

        final Box b3 = Box.createHorizontalBox();
        b3.add(iaCheckBox);
        b3.add(Box.createHorizontalGlue());
        b.add(b3);

        final Box b4 = Box.createHorizontalBox();
        b4.add(isCheckBox);
        b4.add(Box.createHorizontalGlue());
        b.add(b4);

        final Box b5 = Box.createHorizontalBox();
        b5.add(itrCheckBox);
        b5.add(Box.createHorizontalGlue());
        b.add(b5);

        final Box b6 = Box.createHorizontalBox();
        b6.add(ipenCheckBox);
        b6.add(Box.createHorizontalGlue());
        b.add(b6);

        return b;
    }

    protected void doDefaultArrangement(final Graph resultGraph) {
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

    private JScrollPane dagWorkbenchScroll(final String resultLabel, final Graph dag) {

        final GraphWorkbench dagWorkbench = new GraphWorkbench(dag);
        dagWorkbench.setAllowDoubleClickActions(false);
        dagWorkbench.setAllowNodeEdgeSelection(true);
        final JScrollPane dagWorkbenchScroll = new JScrollPane(dagWorkbench);
        dagWorkbenchScroll.setPreferredSize(new Dimension(450, 450));
//        dagWorkbenchScroll.setBorder(new TitledBorder(resultLabel));

        dagWorkbench.addMouseListener(new MouseAdapter() {
            public void mouseExited(final MouseEvent e) {
                storeLatestWorkbenchGraph();
            }
        });

        return dagWorkbenchScroll;
    }

}




