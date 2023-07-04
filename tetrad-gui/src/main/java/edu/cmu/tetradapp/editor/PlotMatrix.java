///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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


import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Node;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements a matrix of scatterplots and histograms for variables that users can select from a list.
 *
 * @author Adrian Tang
 * @author josephramsey
 */
public class PlotMatrix extends JPanel {
    private JPanel charts;
    private JList<Node> rowSelector;
    private JList<Node> colSelector;
    private int numBins = 9;
    private boolean addRegressionLines = false;
    private int[] lastRows = new int[]{0};
    private int[] lastCols = new int[]{0};
    private Map<Node, VariableConditioningEditor.ConditioningPanel> conditioningPanelMap = new HashMap<>();

    public PlotMatrix(DataSet dataSet) {
        setLayout(new BorderLayout());

        List<Node> nodes = dataSet.getVariables();
        Collections.sort(nodes);

        Collections.sort(nodes);
        Node[] _vars = new Node[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) _vars[i] = nodes.get(i);

        this.rowSelector = new JList<>(_vars);
        this.colSelector = new JList<>(_vars);

        this.rowSelector.setSelectedIndex(0);
        this.colSelector.setSelectedIndex(0);

        charts = new JPanel();

        this.rowSelector.addListSelectionListener(e ->
                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector));
        this.colSelector.addListSelectionListener(e ->
                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector));

        constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);

        JMenuBar menuBar = new JMenuBar();
        JMenu settings = new JMenu("Settings");
        menuBar.add(settings);
        JMenuItem numBins = new JMenu("Num Bins for Histograms");
        ButtonGroup group = new ButtonGroup();

        for (int i = 2; i <= 20; i++) {
            int _i = i;
            JMenuItem comp = new JCheckBoxMenuItem(String.valueOf(i));
            numBins.add(comp);
            group.add(comp);
            if (i == getNumBins()) comp.setSelected(true);

            comp.addActionListener(e -> {
                setNumBins(_i);
                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);
            });
        }

        settings.add(numBins);

        JMenuItem addRegressionLines = new JCheckBoxMenuItem("Add Regression Lines to Histograms");
        addRegressionLines.setSelected(false);
        settings.add(addRegressionLines);

        addRegressionLines.addActionListener(e -> {
            setAddRegressionLines(!isAddRegressionLines());
            constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);
        });

        JMenuItem editConditioning = new JMenuItem("Edit Conditioning Variables and Ranges");

        editConditioning.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                VariableConditioningEditor conditioningEditor
                        = new VariableConditioningEditor(dataSet, conditioningPanelMap);
                conditioningEditor.setPreferredSize(new Dimension(300, 300));
                JOptionPane.showMessageDialog(PlotMatrix.this, new JScrollPane(conditioningEditor));
                conditioningPanelMap = conditioningEditor.getConditioningPanelMap();
                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);
            }
        });

        settings.add(editConditioning);

        add(menuBar, BorderLayout.NORTH);

        Box b1 = Box.createHorizontalBox();
        JScrollPane comp2 = new JScrollPane(charts);
        comp2.setPreferredSize(new Dimension(750, 750));
        b1.add(comp2);

        Box b3 = Box.createVerticalBox();
        b3.add(new JLabel("Rows"));
        b3.add(new JScrollPane(this.rowSelector));

        Box b4 = Box.createVerticalBox();
        b4.add(new JLabel("Cols"));
        b4.add(new JScrollPane(this.colSelector));

        b1.add(b3);
        b1.add(b4);

        add(b1, BorderLayout.CENTER);
        setPreferredSize(new Dimension(750, 450));
    }

    private void constructPlotMatrix(JPanel charts, DataSet dataSet, List<Node> nodes, JList<Node> leftSelector, JList<Node> topSelector) {
        int[] leftIndices = leftSelector.getSelectedIndices();
        int[] topIndices = topSelector.getSelectedIndices();
        charts.removeAll();

        charts.setLayout(new GridLayout(leftIndices.length, topIndices.length));

        for (int leftIndex : leftIndices) {
            for (int topIndex : topIndices) {
                if (leftIndex == topIndex) {
                    Histogram histogram = new Histogram(dataSet);
                    histogram.setTarget(nodes.get(leftIndex).getName());

                    for (Node node : conditioningPanelMap.keySet()) {
                        if (node instanceof ContinuousVariable) {
                            ContinuousVariable var = (ContinuousVariable) node;
                            VariableConditioningEditor.ContinuousConditioningPanel panel
                                    = (VariableConditioningEditor.ContinuousConditioningPanel)
                                    conditioningPanelMap.get(var);
                            histogram.addConditioningVariable(var.getName(), panel.getLow(), panel.getHigh());
                        } else if (node instanceof DiscreteVariable) {
                            DiscreteVariable var = (DiscreteVariable) node;
                            VariableConditioningEditor.DiscreteConditioningPanel panel
                                    = (VariableConditioningEditor.DiscreteConditioningPanel)
                                    conditioningPanelMap.get(var);
                            histogram.addConditioningVariable(var.getName(), panel.getIndex());
                        }
                    }

                    if (!(nodes.get(leftIndex) instanceof DiscreteVariable)) {
                        histogram.setNumBins(numBins);
                    }

                    HistogramPanel panel = new HistogramPanel(histogram,
                            leftIndices.length == 1 && topIndices.length == 1);

                    addPanelListener(charts, dataSet, nodes, leftIndex, topIndex, panel);

                    charts.add(panel);
                } else {
                    ScatterPlot scatterPlot = new ScatterPlot(dataSet, addRegressionLines, nodes.get(topIndex).getName(),
                            nodes.get(leftIndex).getName());

                    for (Node node : conditioningPanelMap.keySet()) {
                        if (node instanceof ContinuousVariable) {
                            ContinuousVariable var = (ContinuousVariable) node;
                            VariableConditioningEditor.ContinuousConditioningPanel panel
                                    = (VariableConditioningEditor.ContinuousConditioningPanel)
                                    conditioningPanelMap.get(var);
                            scatterPlot.addConditioningVariable(var.getName(), panel.getLow(), panel.getHigh());
                        }
                    }

                    ScatterplotPanel panel = new ScatterplotPanel(scatterPlot);
                    panel.setDrawAxes(leftIndices.length == 1 && topIndices.length == 1);
                    addPanelListener(charts, dataSet, nodes, leftIndex, topIndex, panel);
                    charts.add(panel);
                }
            }
        }

        revalidate();
        repaint();
    }

    private void addPanelListener(JPanel charts, DataSet dataSet, List<Node> nodes, int leftIndex, int topIndex, JPanel panel) {
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (rowSelector.getSelectedIndices().length == 1
                            && colSelector.getSelectedIndices().length == 1) {
                        rowSelector.setSelectedIndices(lastRows);
                        colSelector.setSelectedIndices(lastCols);
                        lastRows = new int[]{leftIndex};
                        lastCols = new int[]{topIndex};
                        constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);
                    } else {
                        lastRows = rowSelector.getSelectedIndices();
                        lastCols = colSelector.getSelectedIndices();
                        rowSelector.setSelectedIndex(leftIndex);
                        colSelector.setSelectedIndex(topIndex);
                        constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);
                    }
                }
            }
        });
    }

    public int getNumBins() {
        return numBins;
    }

    public void setNumBins(int numBins) {
        this.numBins = numBins;
    }

    public boolean isAddRegressionLines() {
        return addRegressionLines;
    }

    public void setAddRegressionLines(boolean addRegressionLines) {
        this.addRegressionLines = addRegressionLines;
    }
}


