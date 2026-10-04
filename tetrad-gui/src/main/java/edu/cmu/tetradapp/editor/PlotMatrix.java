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


import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.util.NaturalSort;

import javax.swing.*;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * The data set being plotted. It can be changed, among data sets with the same variables, by the chooser that
     * setDataSetChoices adds.
     */
    private DataSet dataSet;

    /**
     * The variables listed in the row and column selectors.
     */
    private List<Node> nodes;

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
     * Whether scatterplots are added-variable (partial regression) plots: each of the two variables is shown with
     * the other selected continuous variables regressed out.
     */
    private boolean addedVariablePlots = false;

    /**
     * Whether every plot is drawn from just the rows that have no missing value in any selected variable, instead
     * of each plot using the rows complete for its own variables.
     */
    private boolean completeRowsOnly = false;

    /**
     * Last rows
     */
    private int[] lastRows = new int[]{0};

    /**
     * Last columns
     */
    private int[] lastCols = new int[]{0};

    /**
     * While a single plot is enlarged by clicking on it, the row and column selections of the matrix it was
     * clicked in; null otherwise. The enlarged plot is computed in the context of that selection, so that it shows
     * the same points and trend line as the cell it enlarges.
     */
    private int[] focusRows = null;

    /**
     * See focusRows.
     */
    private int[] focusCols = null;

    /**
     * True while the click handler is changing the selections itself, so the selection listeners stand down.
     */
    private boolean changingSelection = false;

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
        this(dataSet, null, null);
    }

    /**
     * Constructs a plot matrix over all variables of the data set, with the given variables initially selected in
     * the row and column selectors. Variables not in the data set are ignored; if a selection is null or resolves to
     * nothing, the first variable (in natural order) is selected, as for {@link #PlotMatrix(DataSet)}.
     *
     * @param dataSet     the data set to plot
     * @param initialRows variables to preselect as rows, or null
     * @param initialCols variables to preselect as columns, or null
     */
    public PlotMatrix(DataSet dataSet, Collection<Node> initialRows, Collection<Node> initialCols) {
        this(dataSet, null, initialRows, initialCols);
    }

    /**
     * Constructs a plot matrix whose row and column selectors list only the given variables (all variables of the
     * data set if {@code variables} is null), with the given initial selections. The data set itself is not
     * subsetted, so conditioning (Settings &gt; Edit Conditioning Variables...) may still use any variable in it.
     *
     * @param data        the data set to plot
     * @param variables   variables to list in the row/column selectors, or null for all
     * @param initialRows variables to preselect as rows, or null
     * @param initialCols variables to preselect as columns, or null
     */
    public PlotMatrix(DataSet data, Collection<Node> variables,
                      Collection<Node> initialRows, Collection<Node> initialCols) {
        setLayout(new BorderLayout());

        // Below, "dataSet" is the field, read when a listener runs, so that the listeners follow a change of data
        // set.
        this.dataSet = data;

        List<Node> nodes = restrictTo(dataSet.getVariables(), variables);
        nodes.sort(NaturalSort.naturalComparator());
        this.nodes = nodes;

        Node[] _vars = new Node[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) _vars[i] = nodes.get(i);

        this.rowSelector = new JList<>(_vars);
        this.colSelector = new JList<>(_vars);

        this.rowSelector.setSelectedIndices(indicesOf(nodes, initialRows));
        this.colSelector.setSelectedIndices(indicesOf(nodes, initialCols));

        charts = new JPanel();

        // A selection made by hand is a new matrix, not an enlargement of a cell of the old one.
        ListSelectionListener selectionListener = e -> {
            if (changingSelection) return;
            focusRows = null;
            focusCols = null;
            constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
        };

        this.rowSelector.addListSelectionListener(selectionListener);
        this.colSelector.addListSelectionListener(selectionListener);

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

        JMenuItem adjustForOthers = new JCheckBoxMenuItem("Adjust for Other Selected Variables (Added-Variable Plots)");
        adjustForOthers.setToolTipText("<html>In each scatterplot, plot the two variables with the other selected continuous "
                                       + "variables regressed out of both.<br>The trend line's slope is then the variable's "
                                       + "coefficient in the regression on all of them together.</html>");
        adjustForOthers.setSelected(false);
        settings.add(adjustForOthers);

        adjustForOthers.addActionListener(e -> {
            this.addedVariablePlots = adjustForOthers.isSelected();
            constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
        });

        JMenuItem completeRows = new JCheckBoxMenuItem("Use Only Rows Complete for All Selected Variables");
        completeRows.setToolTipText("<html>Draw every plot from just the rows with no missing value in any selected "
                                    + "variable,<br>so that plain and adjusted plots can be compared on the same rows.</html>");
        completeRows.setSelected(false);
        settings.add(completeRows);

        completeRows.addActionListener(e -> {
            this.completeRowsOnly = completeRows.isSelected();
            constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
        });

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

    /**
     * Offers a choice among several data sets with the same variables (for instance, the completed data sets of a
     * multiple imputation), by a chooser below the plots. Choosing one redraws the plots from it, keeping the
     * selected variables and all settings. Data sets lacking any of the listed variables are not offered; if fewer
     * than two remain, no chooser is added. Call once, after construction.
     *
     * @param dataSets the data sets to choose among, in the order to list them
     * @param selected the one showing now (the one given to the constructor)
     */
    public void setDataSetChoices(List<DataSet> dataSets, DataSet selected) {
        if (dataSets == null) return;

        List<DataSet> choices = new ArrayList<>();

        CHOICE:
        for (DataSet choice : dataSets) {
            if (choice == null) continue;

            for (Node node : this.nodes) {
                if (choice.getVariable(node.getName()) == null) continue CHOICE;
            }

            choices.add(choice);
        }

        // The shared chooser: a drop-down, previous and next buttons, and the arrow keys.
        Box box = DataSetChooser.create(choices, Math.max(0, choices.indexOf(selected)), chosen -> {
            this.dataSet = choices.get(chosen);
            constructPlotMatrix(charts, this.dataSet, this.nodes, rowSelector, colSelector,
                    isRemoveTrendLinesPerPlot());
        });

        if (box == null) return;

        // Ctrl or Cmd with Up or Down steps through the data sets from anywhere in the window. A list would
        // otherwise keep those keys for itself while it has the focus, which it usually does here; plain Up and
        // Down are left to the lists, for moving through the variables.
        int menu;

        try {
            menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException e) {
            menu = InputEvent.CTRL_DOWN_MASK;
        }

        for (JList<Node> list : List.of(rowSelector, colSelector)) {
            list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, menu), "none");
            list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, menu), "none");
        }

        box.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        box.add(Box.createHorizontalGlue());
        add(box, BorderLayout.SOUTH);
        revalidate();
    }

    /**
     * Returns the members of {@code all} whose names appear in {@code keep}, or a copy of {@code all} if
     * {@code keep} is null or matches nothing (so the selectors are never empty).
     */
    private static List<Node> restrictTo(List<Node> all, Collection<Node> keep) {
        if (keep == null) return new ArrayList<>(all);
        Set<String> names = new HashSet<>();
        for (Node n : keep) if (n != null) names.add(n.getName());
        List<Node> out = new ArrayList<>();
        for (Node n : all) if (names.contains(n.getName())) out.add(n);
        return out.isEmpty() ? new ArrayList<>(all) : out;
    }

    /**
     * Maps the given variables (matched by name) to their indices in {@code nodes}. Returns {0} if the result would
     * be empty, so that a selection is always present.
     */
    private static int[] indicesOf(List<Node> nodes, Collection<Node> selected) {
        if (selected == null) return new int[]{0};
        Set<String> names = new HashSet<>();
        for (Node n : selected) if (n != null) names.add(n.getName());
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            if (names.contains(nodes.get(i).getName())) idx.add(i);
        }
        if (idx.isEmpty()) return new int[]{0};
        return idx.stream().mapToInt(Integer::intValue).toArray();
    }

    private void setRemoveMinPointsPerPlot(boolean removeZeroPointsPerPlot) {
        this.removeZeroPointsPerPlot = removeZeroPointsPerPlot;
    }

    private void constructPlotMatrix(JPanel charts, DataSet dataSet, List<Node> nodes, JList<Node> rowSelector,
                                     JList<Node> colSelector, boolean removeZeroPointsPerPlot) {
        int[] rowIndices = rowSelector.getSelectedIndices();
        int[] colIndices = colSelector.getSelectedIndices();
        charts.removeAll();

        final DataSet original = dataSet;

        // The selection that decides which rows are complete and which variables are adjusted for. For a plot
        // enlarged by a click this is the selection of the matrix it came from, not the single row and column
        // now selected; otherwise the enlargement would be a different plot from the cell that was clicked.
        boolean focused = focusRows != null && focusCols != null
                          && rowIndices.length == 1 && colIndices.length == 1;
        int[] contextRows = focused ? focusRows : rowIndices;
        int[] contextCols = focused ? focusCols : colIndices;

        if (this.completeRowsOnly) {
            List<Integer> complete = completeRows(dataSet, nodes, contextRows, contextCols);

            if (complete.isEmpty()) {
                charts.setLayout(new BorderLayout());
                charts.add(new JLabel("No rows are complete for all of the selected variables.",
                        SwingConstants.CENTER), BorderLayout.CENTER);
                revalidate();
                repaint();
                return;
            }

            dataSet = dataSet.subsetRows(complete);
        }

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

                    addPanelListener(charts, original, nodes, rowIndex, colIndex, panel);

                    charts.add(panel);
                } else {
                    DataSet adjusted = this.addedVariablePlots
                            ? addedVariableData(dataSet, nodes, rowIndex, colIndex, contextRows, contextCols) : null;

                    // An adjusted cell plots residuals from a two-column data set of its own; the conditioning
                    // ranges were applied in choosing its rows, so they are not applied again below.
                    ScatterPlot scatterPlot = adjusted != null
                            ? new ScatterPlot(adjusted, addRegressionLines, adjusted.getVariable(0).getName(),
                            adjusted.getVariable(1).getName(), false)
                            : new ScatterPlot(dataSet, addRegressionLines, nodes.get(rowIndex).getName(),
                            nodes.get(colIndex).getName(), removeZeroPointsPerPlot);

                    for (Node node : adjusted != null ? Collections.<Node>emptySet() : conditioningPanelMap.keySet()) {
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

                    addPanelListener(charts, original, nodes, rowIndex, colIndex, panel);
                    charts.add(panel);
                }
            }
        }

        revalidate();
        repaint();
    }

    /**
     * The data for an added-variable (partial regression) plot of one cell: the row variable and the column
     * variable, each replaced by its residuals from a linear regression on the other selected continuous variables.
     * The regression of the second residual on the first has the slope that the row variable gets in the regression
     * of the column variable on the row variable and the others together. Uses the rows that satisfy the
     * conditioning ranges and are complete for the two variables and the others. Returns null, so that the ordinary
     * scatterplot is shown, if either variable is discrete, there are no other continuous variables selected, there
     * are too few such rows, or the regression fails.
     */
    private DataSet addedVariableData(DataSet dataSet, List<Node> nodes, int rowIndex, int colIndex,
                                      int[] rowIndices, int[] colIndices) {
        Node x = nodes.get(rowIndex);
        Node y = nodes.get(colIndex);
        if (!(x instanceof ContinuousVariable) || !(y instanceof ContinuousVariable)) return null;

        List<Node> others = new ArrayList<>();

        for (int[] indices : new int[][]{rowIndices, colIndices}) {
            for (int index : indices) {
                Node node = nodes.get(index);
                if (node == x || node == y || others.contains(node)) continue;
                if (node instanceof ContinuousVariable) others.add(node);
            }
        }

        if (others.isEmpty()) return null;

        List<Node> needed = new ArrayList<>(others);
        needed.add(x);
        needed.add(y);

        List<Integer> rows = new ArrayList<>();

        ROW:
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (Node node : needed) {
                if (!Double.isFinite(dataSet.getDouble(i, column(dataSet, node)))) continue ROW;
            }

            for (Node node : conditioningPanelMap.keySet()) {
                int column = column(dataSet, node);

                if (node instanceof ContinuousVariable) {
                    VariableConditioningEditor.ContinuousConditioningPanel panel
                            = (VariableConditioningEditor.ContinuousConditioningPanel) conditioningPanelMap.get(node);
                    double value = dataSet.getDouble(i, column);
                    if (!(value >= panel.getLow() && value <= panel.getHigh())) continue ROW;
                } else if (node instanceof DiscreteVariable) {
                    VariableConditioningEditor.DiscreteConditioningPanel panel
                            = (VariableConditioningEditor.DiscreteConditioningPanel) conditioningPanelMap.get(node);
                    if (dataSet.getInt(i, column) != panel.getIndex()) continue ROW;
                }
            }

            rows.add(i);
        }

        // Need residual degrees of freedom: more rows than an intercept, the others, and the plotted variable.
        if (rows.size() < others.size() + 3) return null;

        try {
            int[] _rows = rows.stream().mapToInt(Integer::intValue).toArray();
            RegressionDataset regression = new RegressionDataset(dataSet);
            regression.setRows(_rows);
            double[] rx = regression.regress(x, others).getResiduals().toArray();
            double[] ry = regression.regress(y, others).getResiduals().toArray();
            if (rx.length != _rows.length || ry.length != _rows.length) return null;

            List<Node> variables = new ArrayList<>();
            variables.add(new ContinuousVariable(x.getName() + " | others"));
            variables.add(new ContinuousVariable(y.getName() + " | others"));
            return new BoxDataSet(new VerticalDoubleDataBox(new double[][]{rx, ry}), variables);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The column of the given variable in the given data set, matched by name (a row subset of the data carries
     * its own variable objects).
     */
    private static int column(DataSet dataSet, Node node) {
        return dataSet.getColumnIndex(dataSet.getVariable(node.getName()));
    }

    /**
     * The rows of the data set with no missing value in any selected row or column variable: finite for a
     * continuous variable, not the missing-value code for a discrete one.
     */
    private static List<Integer> completeRows(DataSet dataSet, List<Node> nodes, int[] rowIndices, int[] colIndices) {
        Set<Node> selected = new HashSet<>();
        for (int index : rowIndices) selected.add(nodes.get(index));
        for (int index : colIndices) selected.add(nodes.get(index));

        List<Integer> rows = new ArrayList<>();

        ROW:
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (Node node : selected) {
                int column = column(dataSet, node);

                if (node instanceof DiscreteVariable) {
                    if (dataSet.getInt(i, column) == DiscreteVariable.MISSING_VALUE) continue ROW;
                } else if (!Double.isFinite(dataSet.getDouble(i, column))) {
                    continue ROW;
                }
            }

            rows.add(i);
        }

        return rows;
    }

    private void addPanelListener(JPanel charts, DataSet dataSet, List<Node> nodes, int rowIndex, int colIndex, JPanel panel) {
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                changingSelection = true;

                try {
                    if (rowSelector.getSelectedIndices().length == 1
                        && colSelector.getSelectedIndices().length == 1) {
                        focusRows = null;
                        focusCols = null;
                        rowSelector.setSelectedIndices(lastRows);
                        colSelector.setSelectedIndices(lastCols);
                        lastRows = new int[]{rowIndex};
                        lastCols = new int[]{colIndex};
                    } else {
                        lastRows = rowSelector.getSelectedIndices();
                        lastCols = colSelector.getSelectedIndices();
                        focusRows = lastRows;
                        focusCols = lastCols;
                        rowSelector.setSelectedIndex(rowIndex);
                        colSelector.setSelectedIndex(colIndex);
                    }
                } finally {
                    changingSelection = false;
                }

                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector, isRemoveTrendLinesPerPlot());
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




