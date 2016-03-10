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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.GraphScorer;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.ScoredGraphsWrapper;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * Assumes that the search method of the pattern search has been run and shows the
 * various options for DAG's consistent with correlation information over the variables.
 *
 * @author Joseph Ramsey
 */
public class ScoredGraphsDisplay extends JPanel implements GraphEditable {
    private GraphWorkbench workbench;
    private boolean showHighestScoreOnly;
    private NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
    private Map<Graph, Double> dagsToScores;
    private List<Graph> dags;
    private JLabel scoreLabel;
    private ScoredGraphsWrapper scoredGraphsWrapper;
//    private Indexable indexable;

    public ScoredGraphsDisplay(final ScoredGraphsWrapper scoredGraphsWrapper) {
        if (scoredGraphsWrapper == null) {
            throw new NullPointerException();
        }

        this.scoredGraphsWrapper = scoredGraphsWrapper;
        this.dagsToScores = scoredGraphsWrapper.getGraphsToScores();
        setup();
    }

    public ScoredGraphsDisplay(Graph graph, GraphScorer scorer) {
        final List<Graph> _dags = SearchGraphUtils.generatePatternDags(graph, true);

        for (Graph _graph : _dags) {
            double score = Double.NaN;

            if (scorer != null) {
                score = scorer.scoreDag(_graph);
            }

            dagsToScores.put(_graph, score);
        }

        setup();
    }

    private void setup() {
        if (dagsToScores.isEmpty()) {
            throw new IllegalArgumentException("Empty map.");
        }

        double max = Double.NEGATIVE_INFINITY;

        for (Graph dag : dagsToScores.keySet()) {
            if (dagsToScores.get(dag) > max) max = dagsToScores.get(dag);
        }

        final List<Graph> dags = new ArrayList<Graph>();

        if (max != Double.NEGATIVE_INFINITY && showHighestScoreOnly) {
            for (Graph dag : dagsToScores.keySet()) {
                if (dagsToScores.get(dag) == max) {
                    dags.add(dag);
                }
            }
        }
        else {
            for (Graph dag : dagsToScores.keySet()) {
                dags.add(dag);
            }
        }

        if (max != Double.NEGATIVE_INFINITY) {
            Collections.sort(dags, new Comparator<Graph>() {
                public int compare(Graph graph, Graph graph1) {
                    return (int) Math.signum(dagsToScores.get(graph) - dagsToScores.get(graph1));
                }
            });
        }

        this.dags = dags;

        if (dags.size() == 0) {
            throw new IllegalArgumentException("No graphs to display.");
        }

        int index = -1;

        Graph dag = scoredGraphsWrapper.getGraph();

        if (dag == null) {
            scoredGraphsWrapper.setSelectedGraph(dags.get(0));
            dag = scoredGraphsWrapper.getGraph();
        }

        index = dags.indexOf(dag);

        if (index == -1) {
            dag = dags.get(0);
            index = 0;
        }

        workbench = new GraphWorkbench(dag);

        final SpinnerNumberModel model =
                new SpinnerNumberModel(index + 1, 1, dags.size(), 1);
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = model.getNumber().intValue() - 1;

                workbench.setGraph(dags.get(index));

                if (scoredGraphsWrapper != null) {
                    scoredGraphsWrapper.setSelectedGraph(dags.get(index));
                }

                setScore(index - 1);
                firePropertyChange("modelChanged", null, null);
            }
        });

        final JSpinner spinner = new JSpinner();
        spinner.setModel(model);
        final JLabel totalLabel = new JLabel(" of " + dags.size() + " ");

        scoreLabel = new JLabel();
        setScore(dags.size() - 1);

        spinner.setPreferredSize(new Dimension(50, 20));
        spinner.setMaximumSize(spinner.getPreferredSize());


        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("DAG "));
        b1.add(spinner);
        b1.add(totalLabel);
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("Score =  "));
        b1.add(scoreLabel);
        b1.add(Box.createHorizontalStrut(10));
        b1.add(Box.createHorizontalGlue());
        b1.add(new JButton(new CopySubgraphAction(this)));

        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new BorderLayout());
        JScrollPane jScrollPane = new JScrollPane(workbench);
        jScrollPane.setPreferredSize(new Dimension(400, 400));
        graphPanel.add(jScrollPane);
        graphPanel.setBorder(new TitledBorder("Maximum Scoring DAGs in Pattern"));
        b2.add(graphPanel);
        b.add(b2);

        setLayout(new BorderLayout());
        add(menuBar(), BorderLayout.NORTH);
        add(b, BorderLayout.CENTER);
    }

    private void setScore(int i) {
        Double score = dagsToScores.get(dags.get(i));
        String text;

        if (Double.isNaN(score)) {
            text = "Not provided";
        }
        else {
            text = nf.format(score);
        }

        scoreLabel.setText(text);
    }

    public List getSelectedModelComponents() {
        Component[] components = getWorkbench().getComponents();
        List<TetradSerializable> selectedModelComponents =
                new ArrayList<TetradSerializable>();

        for (Component comp : components) {
            if (comp instanceof DisplayNode) {
                selectedModelComponents.add(
                        ((DisplayNode) comp).getModelNode());
            }
            else if (comp instanceof DisplayEdge) {
                selectedModelComponents.add(
                        ((DisplayEdge) comp).getModelEdge());
            }
        }

        return selectedModelComponents;
    }

    public void pasteSubsession(List sessionElements, Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        for (int i = 0; i < sessionElements.size(); i++) {

            Object o = sessionElements.get(i);

            if (o instanceof GraphNode) {
                Node modelNode = (Node) o;
                getWorkbench().selectNode(modelNode);
            }
        }

        getWorkbench().selectConnectingEdges();
    }

    public GraphWorkbench getWorkbench() {
        return workbench;
    }

    public Graph getGraph() {
        return workbench.getGraph();
    }

    public void setGraph(Graph graph) {
        workbench.setGraph(graph);
    }

    /**
     * Creates the "file" menu, which allows the user to load, save, and post
     * workbench models.
     *
     * @return this menu.
     */
    private JMenuBar menuBar() {
        JMenu edit = new JMenu("Edit");
        JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        edit.add(copy);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(edit);

        return menuBar;
    }

    public void setShowHighestScoreOnly(boolean showHighestScoreOnly) {
        this.showHighestScoreOnly = showHighestScoreOnly;

    }
}


