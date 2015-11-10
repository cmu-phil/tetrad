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
import edu.cmu.tetrad.search.Ling;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Displays the output DGs of the LiNG algorithm.
 *
 * @author Joseph Ramsey
 */
public class LingDisplay extends JPanel implements GraphEditable {
    private GraphWorkbench workbench;
    private JComboBox subsetCombo;
    private Ling.StoredGraphs storedGraphs;
    private List<Integer> subsetIndices;
    private JSpinner spinner;
    private JLabel totalLabel;

    public LingDisplay(final Ling.StoredGraphs storedGraphs) {
        this.storedGraphs = storedGraphs;

        if (storedGraphs.getNumGraphs() == 0) {
            workbench = new GraphWorkbench();
        }
        else {
            workbench = new GraphWorkbench(storedGraphs.getGraph(0));
        }

        subsetIndices = getStableIndices(storedGraphs);

        final SpinnerNumberModel model =
                new SpinnerNumberModel(subsetIndices.size() == 0 ? 0 : 1, 0, subsetIndices.size(), 1);
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                int index = model.getNumber().intValue();
                workbench.setGraph(storedGraphs.getGraph(subsetIndices.get(index - 1)));
            }
        });

        spinner = new JSpinner();
        subsetCombo = new JComboBox(
                new String[]{"Show Stable", "Show Unstable", "Show All"});
        subsetCombo.setSelectedItem("Show Stable");
        spinner.setModel(model);
        totalLabel = new JLabel(" of " + subsetIndices.size());

        subsetCombo.setMaximumSize(subsetCombo.getPreferredSize());
        subsetCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                resetDisplay();
            }
        });

        spinner.setPreferredSize(new Dimension(50, 20));
        spinner.setMaximumSize(spinner.getPreferredSize());
        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
//        b1.add(Box.createHorizontalGlue());
//        b1.add(Box.createHorizontalStrut(10));
        b1.add(subsetCombo);
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
        String option = (String) subsetCombo.getSelectedItem();

        if ("Show All".equals(option)) {
            final List<Integer> _subsetIndices = getAllIndices(getStoredGraphs());
            subsetIndices.clear();
            subsetIndices.addAll(_subsetIndices);

            int min = subsetIndices.size() == 0 ? 0 : 1;
            final SpinnerNumberModel model = new SpinnerNumberModel(min, min, subsetIndices.size(), 1);
            model.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    int index = model.getNumber().intValue();
                    workbench.setGraph(storedGraphs.getGraph(subsetIndices.get(index - 1)));
                }
            });

            spinner.setModel(model);
            totalLabel.setText(" of " + _subsetIndices.size());

            if (subsetIndices.isEmpty()) {
                workbench.setGraph(new EdgeListGraph());
            }
            else {
                workbench.setGraph(storedGraphs.getGraph(subsetIndices.get(0)));
            }
        }
        else if ("Show Stable".equals(option)) {
            final List<Integer> _subsetIndices = getStableIndices(getStoredGraphs());
            subsetIndices.clear();
            subsetIndices.addAll(_subsetIndices);

            int min = subsetIndices.size() == 0 ? 0 : 1;
            final SpinnerNumberModel model = new SpinnerNumberModel(min, min, subsetIndices.size(), 1);
            model.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    int index = model.getNumber().intValue();
                    workbench.setGraph(storedGraphs.getGraph(subsetIndices.get(index - 1)));
                }
            });

            spinner.setModel(model);
            totalLabel.setText(" of " + _subsetIndices.size());

            if (subsetIndices.isEmpty()) {
                workbench.setGraph(new EdgeListGraph());
            }
            else {
                workbench.setGraph(storedGraphs.getGraph(subsetIndices.get(0)));
            }
        }
        else if ("Show Unstable".equals(option)) {
            final List<Integer> _subsetIndices = getUnstableIndices(getStoredGraphs());
            subsetIndices.clear();
            subsetIndices.addAll(_subsetIndices);

            int min = subsetIndices.size() == 0 ? 0 : 1;
            final SpinnerNumberModel model = new SpinnerNumberModel(min, min, subsetIndices.size(), 1);
            model.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    int index = model.getNumber().intValue();
                    workbench.setGraph(storedGraphs.getGraph(subsetIndices.get(index - 1)));
                }
            });

            spinner.setModel(model);
            totalLabel.setText(" of " + _subsetIndices.size());

            if (subsetIndices.isEmpty()) {
                workbench.setGraph(new EdgeListGraph());
            }
            else {
                workbench.setGraph(storedGraphs.getGraph(subsetIndices.get(0)));
            }
        }
    }

    public void resetGraphs(Ling.StoredGraphs storedGraphs) {
        this.storedGraphs = storedGraphs;
        resetDisplay();
    }

    private List<Integer> getAllIndices(Ling.StoredGraphs storedGraphs) {
        List<Integer> indices = new ArrayList<Integer>();

        for (int i = 0; i < storedGraphs.getNumGraphs(); i++) {
            indices.add(i);
        }

        return indices;
    }

    private List<Integer> getStableIndices(Ling.StoredGraphs storedGraphs) {
        List<Integer> indices = new ArrayList<Integer>();

        for (int i = 0; i < storedGraphs.getNumGraphs(); i++) {
            if (storedGraphs.isStable(i)) {
                indices.add(i);
            }
        }

        return indices;
    }

    private List<Integer> getUnstableIndices(Ling.StoredGraphs storedGraphs) {
        List<Integer> indices = new ArrayList<Integer>();

        for (int i = 0; i < storedGraphs.getNumGraphs(); i++) {
            if (!storedGraphs.isStable(i)) {
                indices.add(i);
            }
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
//    private JMenuBar menuBar() {
//        JMenu edit = new JMenu("Edit");
//        JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
//        copy.setAccelerator(
//                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
//        edit.add(copy);
//
//        JMenuBar menuBar = new JMenuBar();
//        menuBar.add(edit);
//
//        return menuBar;
//    }

    public Ling.StoredGraphs getStoredGraphs() {
        return storedGraphs;
    }
}


