///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.DagScorer;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.ScoredGraphsWrapper;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * Assumes that the search method of the CPDAG search has been run and shows the various options for DAG's consistent
 * with correlation information over the variables.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ScoredGraphsDisplay extends JPanel implements GraphEditable {

    /**
     * The number format for displaying scores.
     */
    private final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * The workbench for displaying the graphs.
     */
    private GraphWorkbench workbench;

    /**
     * Whether to show only the highest scoring graph.
     */
    private boolean showHighestScoreOnly;

    /**
     * The DAGs to scores.
     */
    private Map<Graph, Double> dagsToScores;

    /**
     * The DAGs.
     */
    private List<Graph> dags;

    /**
     * The label for the score.
     */
    private JLabel scoreLabel;

    /**
     * The scored graphs wrapper.
     */
    private ScoredGraphsWrapper scoredGraphsWrapper;

    /**
     * <p>Constructor for ScoredGraphsDisplay.</p>
     *
     * @param scoredGraphsWrapper a {@link edu.cmu.tetradapp.model.ScoredGraphsWrapper} object
     */
    public ScoredGraphsDisplay(ScoredGraphsWrapper scoredGraphsWrapper) {
        if (scoredGraphsWrapper == null) {
            throw new NullPointerException();
        }

        this.scoredGraphsWrapper = scoredGraphsWrapper;
        this.dagsToScores = scoredGraphsWrapper.getGraphsToScores();
        setup();
    }

    /**
     * <p>Constructor for ScoredGraphsDisplay.</p>
     *
     * @param graph  a {@link edu.cmu.tetrad.graph.Graph} object
     * @param scorer a {@link edu.cmu.tetrad.search.utils.DagScorer} object
     */
    public ScoredGraphsDisplay(Graph graph, DagScorer scorer) {
        List<Graph> _dags = GraphTransforms.generateCpdagDags(graph, true);

        for (Graph _graph : _dags) {
            double score = Double.NaN;

            if (scorer != null) {
                score = scorer.scoreDag(_graph);
            }

            this.dagsToScores.put(_graph, score);
        }

        setup();
    }

    private void setup() {
        if (this.dagsToScores.isEmpty()) {
            throw new IllegalArgumentException("Empty map.");
        }

        double max = Double.NEGATIVE_INFINITY;

        for (Graph dag : this.dagsToScores.keySet()) {
            if (this.dagsToScores.get(dag) > max) max = this.dagsToScores.get(dag);
        }

        List<Graph> dags = new ArrayList<>();

        if (max != Double.NEGATIVE_INFINITY && this.showHighestScoreOnly) {
            for (Graph dag : this.dagsToScores.keySet()) {
                if (this.dagsToScores.get(dag) == max) {
                    dags.add(dag);
                }
            }
        } else {
            dags.addAll(this.dagsToScores.keySet());
        }

        if (max != Double.NEGATIVE_INFINITY) {
            Collections.sort(dags, new Comparator<Graph>() {
                public int compare(Graph graph, Graph graph1) {
                    return (int) FastMath.signum(ScoredGraphsDisplay.this.dagsToScores.get(graph) - ScoredGraphsDisplay.this.dagsToScores.get(graph1));
                }
            });
        }

        this.dags = dags;

        if (dags.size() == 0) {
            throw new IllegalArgumentException("No graphs to display.");
        }

        int index = -1;

        Graph dag = this.scoredGraphsWrapper.getGraph();

        if (dag == null) {
            this.scoredGraphsWrapper.setSelectedGraph(dags.get(0));
            dag = this.scoredGraphsWrapper.getGraph();
        }

        index = dags.indexOf(dag);

        if (index == -1) {
            dag = dags.get(0);
            index = 0;
        }

        this.workbench = new GraphWorkbench(dag);

        SpinnerNumberModel model =
                new SpinnerNumberModel(index + 1, 1, dags.size(), 1);
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = model.getNumber().intValue() - 1;

                ScoredGraphsDisplay.this.workbench.setGraph(dags.get(index));

                if (ScoredGraphsDisplay.this.scoredGraphsWrapper != null) {
                    ScoredGraphsDisplay.this.scoredGraphsWrapper.setSelectedGraph(dags.get(index));
                }

                setScore(index - 1);
                firePropertyChange("modelChanged", null, null);
            }
        });

        JSpinner spinner = new JSpinner();
        spinner.setModel(model);
        JLabel totalLabel = new JLabel(" of " + dags.size() + " ");

        this.scoreLabel = new JLabel();
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
        b1.add(this.scoreLabel);
        b1.add(Box.createHorizontalStrut(10));
        b1.add(Box.createHorizontalGlue());
        b1.add(new JButton(new CopySubgraphAction(this)));

        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new BorderLayout());
        JScrollPane jScrollPane = new JScrollPane(this.workbench);
        jScrollPane.setPreferredSize(new Dimension(400, 400));
        graphPanel.add(jScrollPane);
        graphPanel.setBorder(new TitledBorder("Maximum Scoring DAGs in forbid_latent_common_causes"));
        b2.add(graphPanel);
        b.add(b2);

        setLayout(new BorderLayout());
        add(menuBar(), BorderLayout.NORTH);
        add(b, BorderLayout.CENTER);
    }

    private void setScore(int i) {
        Double score = this.dagsToScores.get(this.dags.get(i));
        String text;

        if (Double.isNaN(score)) {
            text = "Not provided";
        } else {
            text = this.nf.format(score);
        }

        this.scoreLabel.setText(text);
    }

    /**
     * <p>getSelectedModelComponents.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List getSelectedModelComponents() {
        Component[] components = getWorkbench().getComponents();
        List<TetradSerializable> selectedModelComponents =
                new ArrayList<>();

        for (Component comp : components) {
            if (comp instanceof DisplayNode) {
                selectedModelComponents.add(
                        ((DisplayNode) comp).getModelNode());
            } else if (comp instanceof DisplayEdge) {
                selectedModelComponents.add(
                        ((DisplayEdge) comp).getModelEdge());
            }
        }

        return selectedModelComponents;
    }

    /**
     * {@inheritDoc}
     */
    public void pasteSubsession(List<Object> sessionElements, Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        for (Object o : sessionElements) {

            if (o instanceof GraphNode) {
                Node modelNode = (Node) o;
                getWorkbench().selectNode(modelNode);
            }
        }

        getWorkbench().selectConnectingEdges();
    }

    /**
     * <p>Getter for the field <code>workbench</code>.</p>
     *
     * @return a {@link edu.cmu.tetradapp.workbench.GraphWorkbench} object
     */
    public GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.workbench.getGraph();
    }

    /**
     * {@inheritDoc}
     */
    public void setGraph(Graph graph) {
        this.workbench.setGraph(graph);
    }

    /**
     * Creates the "file" menu, which allows the user to load, save, and post workbench models.
     *
     * @return this menu.
     */
    private JMenuBar menuBar() {
        JMenu edit = new JMenu("Edit");
        JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        edit.add(copy);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(edit);

        return menuBar;
    }

    /**
     * <p>Setter for the field <code>showHighestScoreOnly</code>.</p>
     *
     * @param showHighestScoreOnly a boolean
     */
    public void setShowHighestScoreOnly(boolean showHighestScoreOnly) {
        this.showHighestScoreOnly = showHighestScoreOnly;

    }
}



