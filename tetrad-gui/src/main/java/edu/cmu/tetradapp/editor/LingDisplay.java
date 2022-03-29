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
    private final GraphWorkbench workbench;
    private final JComboBox subsetCombo;
    private Ling.StoredGraphs storedGraphs;
    private final List<Integer> subsetIndices;
    private final JSpinner spinner;
    private final JLabel totalLabel;

    public LingDisplay(final Ling.StoredGraphs storedGraphs) {
        this.storedGraphs = storedGraphs;

        if (storedGraphs.getNumGraphs() == 0) {
            this.workbench = new GraphWorkbench();
        } else {
            this.workbench = new GraphWorkbench(storedGraphs.getGraph(0));
        }

        this.subsetIndices = getStableIndices(storedGraphs);

        final SpinnerNumberModel model =
                new SpinnerNumberModel(this.subsetIndices.size() == 0 ? 0 : 1, 0, this.subsetIndices.size(), 1);
        model.addChangeListener(new ChangeListener() {
            public void stateChanged(final ChangeEvent e) {
                final int index = model.getNumber().intValue();
                LingDisplay.this.workbench.setGraph(storedGraphs.getGraph(LingDisplay.this.subsetIndices.get(index - 1)));
            }
        });

        this.spinner = new JSpinner();
        this.subsetCombo = new JComboBox(
                new String[]{"Show Stable", "Show Unstable", "Show All"});
        this.subsetCombo.setSelectedItem("Show Stable");
        this.spinner.setModel(model);
        this.totalLabel = new JLabel(" of " + this.subsetIndices.size());

        this.subsetCombo.setMaximumSize(this.subsetCombo.getPreferredSize());
        this.subsetCombo.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                resetDisplay();
            }
        });

        this.spinner.setPreferredSize(new Dimension(50, 20));
        this.spinner.setMaximumSize(this.spinner.getPreferredSize());
        final Box b = Box.createVerticalBox();
        final Box b1 = Box.createHorizontalBox();
//        b1.add(Box.createHorizontalGlue());
//        b1.add(Box.createHorizontalStrut(10));
        b1.add(this.subsetCombo);
        b1.add(Box.createHorizontalGlue());
        b1.add(new JLabel("DAG "));
        b1.add(this.spinner);
        b1.add(this.totalLabel);

        b.add(b1);

        final Box b2 = Box.createHorizontalBox();
        final JPanel graphPanel = new JPanel();
        graphPanel.setLayout(new BorderLayout());
        final JScrollPane jScrollPane = new JScrollPane(this.workbench);
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
        final String option = (String) this.subsetCombo.getSelectedItem();

        if ("Show All".equals(option)) {
            final List<Integer> _subsetIndices = getAllIndices(getStoredGraphs());
            this.subsetIndices.clear();
            this.subsetIndices.addAll(_subsetIndices);

            final int min = this.subsetIndices.size() == 0 ? 0 : 1;
            final SpinnerNumberModel model = new SpinnerNumberModel(min, min, this.subsetIndices.size(), 1);
            model.addChangeListener(new ChangeListener() {
                public void stateChanged(final ChangeEvent e) {
                    final int index = model.getNumber().intValue();
                    LingDisplay.this.workbench.setGraph(LingDisplay.this.storedGraphs.getGraph(LingDisplay.this.subsetIndices.get(index - 1)));
                }
            });

            this.spinner.setModel(model);
            this.totalLabel.setText(" of " + _subsetIndices.size());

            if (this.subsetIndices.isEmpty()) {
                this.workbench.setGraph(new EdgeListGraph());
            } else {
                this.workbench.setGraph(this.storedGraphs.getGraph(this.subsetIndices.get(0)));
            }
        } else if ("Show Stable".equals(option)) {
            final List<Integer> _subsetIndices = getStableIndices(getStoredGraphs());
            this.subsetIndices.clear();
            this.subsetIndices.addAll(_subsetIndices);

            final int min = this.subsetIndices.size() == 0 ? 0 : 1;
            final SpinnerNumberModel model = new SpinnerNumberModel(min, min, this.subsetIndices.size(), 1);
            model.addChangeListener(new ChangeListener() {
                public void stateChanged(final ChangeEvent e) {
                    final int index = model.getNumber().intValue();
                    LingDisplay.this.workbench.setGraph(LingDisplay.this.storedGraphs.getGraph(LingDisplay.this.subsetIndices.get(index - 1)));
                }
            });

            this.spinner.setModel(model);
            this.totalLabel.setText(" of " + _subsetIndices.size());

            if (this.subsetIndices.isEmpty()) {
                this.workbench.setGraph(new EdgeListGraph());
            } else {
                this.workbench.setGraph(this.storedGraphs.getGraph(this.subsetIndices.get(0)));
            }
        } else if ("Show Unstable".equals(option)) {
            final List<Integer> _subsetIndices = getUnstableIndices(getStoredGraphs());
            this.subsetIndices.clear();
            this.subsetIndices.addAll(_subsetIndices);

            final int min = this.subsetIndices.size() == 0 ? 0 : 1;
            final SpinnerNumberModel model = new SpinnerNumberModel(min, min, this.subsetIndices.size(), 1);
            model.addChangeListener(new ChangeListener() {
                public void stateChanged(final ChangeEvent e) {
                    final int index = model.getNumber().intValue();
                    LingDisplay.this.workbench.setGraph(LingDisplay.this.storedGraphs.getGraph(LingDisplay.this.subsetIndices.get(index - 1)));
                }
            });

            this.spinner.setModel(model);
            this.totalLabel.setText(" of " + _subsetIndices.size());

            if (this.subsetIndices.isEmpty()) {
                this.workbench.setGraph(new EdgeListGraph());
            } else {
                this.workbench.setGraph(this.storedGraphs.getGraph(this.subsetIndices.get(0)));
            }
        }
    }

    public void resetGraphs(final Ling.StoredGraphs storedGraphs) {
        this.storedGraphs = storedGraphs;
        resetDisplay();
    }

    private List<Integer> getAllIndices(final Ling.StoredGraphs storedGraphs) {
        final List<Integer> indices = new ArrayList<>();

        for (int i = 0; i < storedGraphs.getNumGraphs(); i++) {
            indices.add(i);
        }

        return indices;
    }

    private List<Integer> getStableIndices(final Ling.StoredGraphs storedGraphs) {
        final List<Integer> indices = new ArrayList<>();

        for (int i = 0; i < storedGraphs.getNumGraphs(); i++) {
            if (storedGraphs.isStable(i)) {
                indices.add(i);
            }
        }

        return indices;
    }

    private List<Integer> getUnstableIndices(final Ling.StoredGraphs storedGraphs) {
        final List<Integer> indices = new ArrayList<>();

        for (int i = 0; i < storedGraphs.getNumGraphs(); i++) {
            if (!storedGraphs.isStable(i)) {
                indices.add(i);
            }
        }

        return indices;
    }


    public List getSelectedModelComponents() {
        final Component[] components = getWorkbench().getComponents();
        final List<TetradSerializable> selectedModelComponents =
                new ArrayList<>();

        for (final Component comp : components) {
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

    public void pasteSubsession(final List sessionElements, final Point upperLeft) {
        getWorkbench().pasteSubgraph(sessionElements, upperLeft);
        getWorkbench().deselectAll();

        for (int i = 0; i < sessionElements.size(); i++) {

            final Object o = sessionElements.get(i);

            if (o instanceof GraphNode) {
                final Node modelNode = (Node) o;
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

    public void setGraph(final Graph graph) {
        this.workbench.setGraph(graph);
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
    private Ling.StoredGraphs getStoredGraphs() {
        return this.storedGraphs;
    }
}


