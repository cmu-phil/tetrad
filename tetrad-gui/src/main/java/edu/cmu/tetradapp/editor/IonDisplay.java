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
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.IonParams;
import edu.cmu.tetradapp.model.IonRunner;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays the output DGs of the LiNG algorithm.
 *
 * @author Joseph Ramsey
 */
public class IonDisplay extends JPanel implements GraphEditable {
    private GraphWorkbench workbench;
    private List<Graph> storedGraphs;
    private List<Integer> indices;
    private JSpinner spinner;
    private JLabel totalLabel;

    public IonDisplay(final List<Graph> storedGraphs, final IonRunner runner) {
        this.storedGraphs = storedGraphs;
        int graphIndex = ((IonParams)runner.getParams()).getGraphIndex();

        if (storedGraphs.size() == 0) {
            workbench = new GraphWorkbench();
        } else {
            workbench = new GraphWorkbench(storedGraphs.get(0));
        }

        indices = getAllIndices(storedGraphs);

        final SpinnerNumberModel model =
                new SpinnerNumberModel(indices.size() == 0 ? 0 : 1, indices.size() == 0 ? 0 : 1,
                        indices.size(), 1);
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = (Integer) model.getValue();
                workbench.setGraph(storedGraphs.get(indices.get(index - 1)));
                firePropertyChange("modelChanged", null, null);
                runner.setResultGraph(workbench.getGraph());
                ((IonParams)runner.getParams()).setGraphIndex(index - 1);
            }
        });

        if (graphIndex >= indices.size()) {
            graphIndex = 0;
            ((IonParams) runner.getParams()).setGraphIndex(0);
        }

        if (indices.size() > 0) {
            model.setValue(graphIndex + 1);
        }

        spinner = new JSpinner();
        spinner.setModel(model);
        totalLabel = new JLabel(" of " + indices.size());


        spinner.setPreferredSize(new Dimension(50, 20));
        spinner.setMaximumSize(spinner.getPreferredSize());
        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
//        b1.add(Box.createHorizontalGlue());
//        b1.add(Box.createHorizontalStrut(10));
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("DAG "));
        b1.add(spinner);
        b1.add(totalLabel);

        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new BorderLayout());
        JScrollPane jScrollPane = new JScrollPane(workbench);
//        jScrollPane.setPreferredSize(new Dimension(400, 400));
        graphPanel.add(jScrollPane);
//        graphPanel.setBorder(new TitledBorder("DAG"));
        b2.add(graphPanel);
        b.add(b2);

        setLayout(new BorderLayout());
//        add(menuBar(), BorderLayout.NORTH);
        add(b, BorderLayout.CENTER);
    }

    private void resetDisplay() {
        final List<Integer> _subsetIndices = getAllIndices(getStoredGraphs());
        indices.clear();
        indices.addAll(_subsetIndices);

        int min = indices.size() == 0 ? 0 : 1;
        final SpinnerNumberModel model = new SpinnerNumberModel(min, min, indices.size(), 1);
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = model.getNumber().intValue();
                workbench.setGraph(storedGraphs.get(indices.get(index - 1)));
            }
        });

        spinner.setModel(model);
        totalLabel.setText(" of " + _subsetIndices.size());

        if (indices.isEmpty()) {
            workbench.setGraph(new EdgeListGraph());
        } else {
            workbench.setGraph(storedGraphs.get(indices.get(0)));
        }
    }

    public void resetGraphs(List<Graph> storedGraphs) {
        this.storedGraphs = storedGraphs;
        resetDisplay();
    }

    private List<Integer> getAllIndices(List<Graph> storedGraphs) {
        List<Integer> indices = new ArrayList<Integer>();

        for (int i = 0; i < storedGraphs.size(); i++) {
            indices.add(i);
        }

        return indices;
    }

    public List getSelectedModelComponents() {
        Component[] components = getWorkbench().getComponents();
        List<TetradSerializable> selectedModelComponents =
                new ArrayList<TetradSerializable>();

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

    public List<Graph> getStoredGraphs() {
        return storedGraphs;
    }
}


