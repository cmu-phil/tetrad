/// ////////////////////////////////////////////////////////////////////////////
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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
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
 * @version $Id: $Id
 */
public class PlotMatrix extends JPanel {

    /**
     * Charts
     */
    private final JPanel charts;

    /**
     * Row selector
     */
    private final JList<Node> rowSelector;

    /**
     * Column selector
     */
    private final JList<Node> colSelector;

    /**
     * Number of bins
     */
    private int numBins = 9;

    /**
     * Add regression lines
     */
    private boolean addRegressionLines = false;

    /**
     * Remove zero points per plot
     */
    private boolean removeZeroPointsPerPlot = false;

    /**
     * Last rows
     */
    private int[] lastRows = new int[]{0};

    /**
     * Last columns
     */
    private int[] lastCols = new int[]{0};

    /**
     * Conditioning panel map
     */
    private Map<Node, VariableConditioningEditor.ConditioningPanel> conditioningPanelMap = new HashMap<>();

    /**
     * Jitter style
     */
    private ScatterPlot.JitterStyle jitterStyle = ScatterPlot.JitterStyle.None;

    /**
     * <p>Constructor for PlotMatrix.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public PlotMatrix(DataSet dataSet) {
        setLayout(new BorderLayout());

        List<Node> nodes = dataSet.getVariables();
        Collections.sort(nodes);

        Node[] _vars = new Node[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) _vars[i] = nodes.get(i);

        this.rowSelector = new JList<>(_vars);
        this.colSelector = new JList<>(_vars);

        this.rowSelector.setSelectedIndex(0);
        this.colSelector.setSelectedIndex(0);

        charts = new JPanel();

        this.rowSelector.addListSelectionListener(e ->
                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot()));
        this.colSelector.addListSelectionListener(e ->
                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot()));

        constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());

        JMenuBar menuBar = new JMenuBar();
        JMenu settings = new JMenu("Settings");
        menuBar.add(settings);

        JMenuItem addTrendLines = new JCheckBoxMenuItem("Add Trend Lines");
        addTrendLines.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));
        addTrendLines.setSelected(false);
        settings.add(addTrendLines);

        JMenuItem removeZeroPointsPerPlot = new JCheckBoxMenuItem("Remove Zero Points Per Plot");
        removeZeroPointsPerPlot.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        removeZeroPointsPerPlot.setSelected(false);
        settings.add(removeZeroPointsPerPlot);

        removeZeroPointsPerPlot.addActionListener(e -> {
            setRemoveMinPointsPerPlot(!isRemoveTrendLinesPerPlot());
            constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
        });

        addTrendLines.addActionListener(e -> {
            setAddRegressionLines(!isAddRegressionLines());
            constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
        });

        JMenuItem numBins = new JMenu("Set number of Bins for Histograms");
        ButtonGroup group = new ButtonGroup();

        for (int i = 2; i <= 30; i++) {
            int _i = i;
            JMenuItem comp = new JCheckBoxMenuItem(String.valueOf(i));
            numBins.add(comp);
            group.add(comp);
            if (i == getNumBins()) comp.setSelected(true);

            comp.addActionListener(e -> {
                setNumBins(_i);
                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
            });
        }

        settings.add(numBins);

        JMenu jitterDiscrete = new JMenu("Jitter Style (Display Only)");

        final JMenuItem menuItem1 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.Gaussian.toString());
        final JMenuItem menuItem2 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.Uniform.toString());
        final JMenuItem menuItem3 = new JCheckBoxMenuItem(ScatterPlot.JitterStyle.None.toString());

        menuItem1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK));
        menuItem2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK));
        menuItem3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));

        ButtonGroup group1 = new ButtonGroup();
        group1.add(menuItem1);
        group1.add(menuItem2);
        group1.add(menuItem3);

        menuItem3.setSelected(true);

        jitterDiscrete.add(menuItem1);
        jitterDiscrete.add(menuItem2);
        jitterDiscrete.add(menuItem3);

        menuItem1.addActionListener(e -> {
            this.jitterStyle = ScatterPlot.JitterStyle.Gaussian;
            constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
        });

        menuItem2.addActionListener(e -> {
            this.jitterStyle = ScatterPlot.JitterStyle.Uniform;
            constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
        });

        menuItem3.addActionListener(e -> {
            this.jitterStyle = ScatterPlot.JitterStyle.None;
            constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
        });

        settings.add(jitterDiscrete);

        JMenuItem editConditioning = new JMenuItem("Edit Conditioning Variables...");
        editConditioning.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK));

        editConditioning.addActionListener(e -> {
            VariableConditioningEditor conditioningEditor
                    = new VariableConditioningEditor(dataSet, conditioningPanelMap);
            conditioningEditor.setPreferredSize(new Dimension(300, 300));
            JOptionPane.showMessageDialog(PlotMatrix.this, new JScrollPane(conditioningEditor));
            conditioningPanelMap = conditioningEditor.getConditioningPanelMap();
            constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
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

    private void setRemoveMinPointsPerPlot(boolean removeZeroPointsPerPlot) {
        this.removeZeroPointsPerPlot = removeZeroPointsPerPlot;
    }

    private void constructPlotMatrix(JPanel charts, DataSet dataSet, List<Node> nodes, JList<Node> rowSelector,
                                     JList<Node> colSelector, boolean removeZeroPointsPerPlot) {
        int[] rowIndices = rowSelector.getSelectedIndices();
        int[] colIndices = colSelector.getSelectedIndices();
        charts.removeAll();

        charts.setLayout(new GridLayout(rowIndices.length, colIndices.length));

        for (int rowIndex : rowIndices) {
            for (int colIndex : colIndices) {
                if (rowIndex == colIndex) {
                    Histogram histogram = new Histogram(dataSet, nodes.get(rowIndex).getName(), removeZeroPointsPerPlot);
//                    histogram.setTarget(nodes.get(rowIndex).getName());

                    for (Node node : conditioningPanelMap.keySet()) {
                        if (node instanceof ContinuousVariable var) {
                            VariableConditioningEditor.ContinuousConditioningPanel panel
                                    = (VariableConditioningEditor.ContinuousConditioningPanel)
                                    conditioningPanelMap.get(var);
                            histogram.addConditioningVariable(var.getName(), panel.getLow(), panel.getHigh());
                        } else if (node instanceof DiscreteVariable var) {
                            VariableConditioningEditor.DiscreteConditioningPanel panel
                                    = (VariableConditioningEditor.DiscreteConditioningPanel)
                                    conditioningPanelMap.get(var);
                            histogram.addConditioningVariable(var.getName(), panel.getIndex());
                        }
                    }

                    if (!(nodes.get(rowIndex) instanceof DiscreteVariable)) {
                        histogram.setNumBins(numBins);
                    }

                    HistogramPanel panel = new HistogramPanel(histogram,
                            rowIndices.length == 1 && colIndices.length == 1);
                    panel.setMinimumSize(new Dimension(10, 10));

                    addPanelListener(charts, dataSet, nodes, rowIndex, colIndex, panel);

                    charts.add(panel);
                } else {
                    ScatterPlot scatterPlot = new ScatterPlot(dataSet, addRegressionLines, nodes.get(colIndex).getName(),
                            nodes.get(rowIndex).getName(), removeZeroPointsPerPlot);

                    for (Node node : conditioningPanelMap.keySet()) {
                        if (node instanceof ContinuousVariable var) {
                            VariableConditioningEditor.ContinuousConditioningPanel panel
                                    = (VariableConditioningEditor.ContinuousConditioningPanel)
                                    conditioningPanelMap.get(var);
                            scatterPlot.addConditioningVariable(var.getName(), panel.getLow(), panel.getHigh());
                        } else if (node instanceof DiscreteVariable var) {
                            VariableConditioningEditor.DiscreteConditioningPanel panel
                                    = (VariableConditioningEditor.DiscreteConditioningPanel)
                                    conditioningPanelMap.get(var);
                            scatterPlot.addConditioningVariable(var.getName(), panel.getIndex());
                        }
                    }

                    scatterPlot.setJitterStyle(jitterStyle);

                    ScatterplotPanel panel = new ScatterplotPanel(scatterPlot, removeZeroPointsPerPlot);
                    panel.setDrawAxes(rowIndices.length == 1 && colIndices.length == 1);
                    panel.setMinimumSize(new Dimension(10, 10));

                    int pointSize = 5;
                    if (rowIndices.length > 2 || colIndices.length > 2) pointSize = 4;
                    if (rowIndices.length > 3 || colIndices.length > 3) pointSize = 3;
                    if (rowIndices.length > 5 || colIndices.length > 5) pointSize = 2;
                    panel.setPointSize(pointSize);

                    addPanelListener(charts, dataSet, nodes, rowIndex, colIndex, panel);
                    charts.add(panel);
                }
            }
        }

        revalidate();
        repaint();
    }

    private void addPanelListener(JPanel charts, DataSet dataSet, List<Node> nodes, int rowIndex, int colIndex, JPanel panel) {
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (rowSelector.getSelectedIndices().length == 1
                    && colSelector.getSelectedIndices().length == 1) {
                    rowSelector.setSelectedIndices(lastRows);
                    colSelector.setSelectedIndices(lastCols);
                    lastRows = new int[]{rowIndex};
                    lastCols = new int[]{colIndex};
                    constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
                } else {
                    lastRows = rowSelector.getSelectedIndices();
                    lastCols = colSelector.getSelectedIndices();
                    rowSelector.setSelectedIndex(rowIndex);
                    colSelector.setSelectedIndex(colIndex);
                    constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
                }
            }
        });
    }

    /**
     * <p>Getter for the field <code>numBins</code>.</p>
     *
     * @return a int
     */
    public int getNumBins() {
        return numBins;
    }

    /**
     * <p>Setter for the field <code>numBins</code>.</p>
     *
     * @param numBins a int
     */
    public void setNumBins(int numBins) {
        this.numBins = numBins;
    }

    /**
     * <p>isAddRegressionLines.</p>
     *
     * @return a boolean
     */
    public boolean isAddRegressionLines() {
        return addRegressionLines;
    }

    /**
     * <p>Setter for the field <code>addRegressionLines</code>.</p>
     *
     * @param addRegressionLines a boolean
     */
    public void setAddRegressionLines(boolean addRegressionLines) {
        this.addRegressionLines = addRegressionLines;
    }

    /**
     * <p>isRemoveTrendLinesPerPlot.</p>
     *
     * @return a boolean
     */
    public boolean isRemoveTrendLinesPerPlot() {
        return removeZeroPointsPerPlot;
    }
}



