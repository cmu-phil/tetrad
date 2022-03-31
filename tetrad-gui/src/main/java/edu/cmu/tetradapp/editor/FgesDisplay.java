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

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ScoredGraph;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.Indexable;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays the output DGs of the LiNG algorithm.
 *
 * @author Joseph Ramsey
 */
public class FgesDisplay extends JPanel implements GraphEditable {
    private final Graph resultGraph;
    private final GraphWorkbench workbench;
    private List<ScoredGraph> topGraphs;
    private final JSpinner spinner = new JSpinner();
    private final JLabel totalLabel;
    private final NumberFormat nf;
    private final JLabel scoreLabel;
    private Indexable indexable;

    public FgesDisplay(Graph resultGraph, List<ScoredGraph> topGraphs, Indexable indexable) {
        this.nf = NumberFormatUtil.getInstance().getNumberFormat();
        this.indexable = indexable;
        this.topGraphs = topGraphs;

        int numCPDAGs = topGraphs.size();

        if (topGraphs.size() == 0) {
            this.workbench = new GraphWorkbench();
        } else {
            this.workbench = new GraphWorkbench(topGraphs.get(indexable.getIndex()).getGraph());
        }

        this.resultGraph = resultGraph;

        this.scoreLabel = new JLabel();
        setCPDAG();

        SpinnerNumberModel model =
                new SpinnerNumberModel(numCPDAGs == 0 ? 1 : indexable.getIndex() + 1, 1, numCPDAGs == 0 ? 1 : numCPDAGs, 1);

        model.addChangeListener(e -> {
            getIndexable().setIndex((Integer) model.getValue() - 1);
            setCPDAG();
        });

//        spinner = new JSpinner();
        this.spinner.setModel(model);
        this.totalLabel = new JLabel(" of " + numCPDAGs);

        this.spinner.setPreferredSize(new Dimension(50, 20));
        this.spinner.setMaximumSize(this.spinner.getPreferredSize());
        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel(" Score = "));
        b1.add(this.scoreLabel);
        b1.add(new JLabel(" forbid_latent_common_causes "));
        b1.add(this.spinner);
        b1.add(this.totalLabel);

        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new BorderLayout());
        JScrollPane jScrollPane = new JScrollPane(this.workbench);
        graphPanel.add(jScrollPane);
        b2.add(graphPanel);
        b.add(b2);

        setLayout(new BorderLayout());
        add(b, BorderLayout.CENTER);
    }

    private void setCPDAG() {
        setDisplayGraph();
        setDisplayScore();
    }

    private void setDisplayGraph() {
        int index = getIndexable().getIndex();

        if (this.topGraphs.size() == 0) {
            this.workbench.setGraph(new EdgeListGraph());
        } else {
            ScoredGraph scoredGraph = this.topGraphs.get(index);
            this.workbench.setGraph(scoredGraph.getGraph());
        }
    }

    private void setDisplayScore() {
        if (this.topGraphs.isEmpty()) {
            this.scoreLabel.setText("*");
        } else {
            double score = this.topGraphs.get(getIndexable().getIndex()).getScore();

            if (Double.isNaN(score)) {
                this.scoreLabel.setText("*");
            } else {
                this.scoreLabel.setText(this.nf.format(score));
            }
        }
    }

    private void resetDisplay() {
        int numCPDAGs = this.topGraphs.size();

        SpinnerNumberModel model = new SpinnerNumberModel(numCPDAGs, 0, numCPDAGs, 1);
        model.addChangeListener(e -> {
            getIndexable().setIndex((Integer) model.getValue() - 1);
            setCPDAG();
        });

        this.spinner.setModel(model);
        this.totalLabel.setText(" of " + numCPDAGs);

        if (numCPDAGs == 0) {
            this.workbench.setGraph(this.resultGraph);
        } else {
            this.workbench.setGraph(this.topGraphs.get(numCPDAGs - 1).getGraph());
        }

        setDisplayScore();
    }

    public void resetGraphs(List<ScoredGraph> topGraphs) {
        this.topGraphs = topGraphs;
        resetDisplay();
    }

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

    public void pasteSubsession(List sessionElements, Point upperLeft) {
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

    public GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    public Graph getGraph() {
        return this.workbench.getGraph();
    }

    public void setGraph(Graph graph) {
        this.workbench.setGraph(graph);
    }

    public List<ScoredGraph> getTopGraphs() {
        return this.topGraphs;
    }

    private Indexable getIndexable() {
        return this.indexable;
    }

}


