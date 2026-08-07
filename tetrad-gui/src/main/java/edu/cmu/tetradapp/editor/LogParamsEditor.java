///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.LogTransformSpec;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Edits the parameters of a logarithmic transform, in either of two modes.
 * <p>
 * In <b>uniform</b> mode (the default, and the only mode available before per-variable transforms were added) a
 * single offset, base, and direction are applied to every continuous variable, exactly as before. In
 * <b>per-variable</b> mode each continuous variable has its own offset, base, and direction, and variables left
 * unchecked are passed through unchanged.
 * <p>
 * Per-variable settings exist because the offset is scale-relative: log(a + x) is approximately log(a) + x/a when x
 * is much smaller than a, so a single offset shared across variables of different magnitude logs the large-valued
 * ones while merely rescaling the small-valued ones. The table therefore shows each variable's minimum and flags any
 * row whose offset would take the logarithm outside its domain.
 *
 * @author Frank Wimberly based on similar classes by Joe Ramsey
 * @author josephramsey
 * @version $Id: $Id
 */
public class LogParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The name of the card showing the dataset-wide controls.
     */
    private static final String UNIFORM_CARD = "uniform";

    /**
     * The name of the card showing the per-variable table.
     */
    private static final String PER_VARIABLE_CARD = "perVariable";

    /**
     * The parameters being edited.
     */
    private Parameters params;

    /**
     * The dataset the transform will be applied to, used to list the continuous variables and their minima. Null if
     * no tabular data parent was supplied, in which case only uniform mode is offered.
     */
    private DataSet sourceDataSet;

    /**
     * The per-variable rows shown in the table.
     */
    private List<Row> rows = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Captures the source dataset so that the per-variable table can be built. A missing or non-tabular parent is
     * tolerated: the editor then offers uniform mode only, rather than failing to open.
     */
    public void setParentModels(Object[] parentModels) {
        if (parentModels == null) return;

        for (Object parent : parentModels) {
            if (parent instanceof DataWrapper data) {
                DataModel model = data.getSelectedDataModel();

                if (model instanceof DataSet dataSet) {
                    this.sourceDataSet = dataSet;
                }
            }
        }
    }

    /**
     * <p>setup.</p>
     */
    public void setup() {
        buildGui();
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return true;
    }

    //================================= Private Methods ===============================//

    /**
     * Constructs the Gui used to edit properties.
     */
    private void buildGui() {
        setLayout(new BorderLayout());

        Map<String, LogTransformSpec> existing =
                LogTransformSpec.decode(this.params.getString("logVariableSpecs", ""));

        JPanel uniformPanel = buildUniformPanel();
        JPanel perVariablePanel = buildPerVariablePanel(existing);

        JRadioButton uniformButton = new JRadioButton("Apply one transform to the whole dataset");
        JRadioButton perVariableButton = new JRadioButton("Apply a transform per variable");
        ButtonGroup group = new ButtonGroup();
        group.add(uniformButton);
        group.add(perVariableButton);

        boolean perVariable = !existing.isEmpty() && this.sourceDataSet != null;
        uniformButton.setSelected(!perVariable);
        perVariableButton.setSelected(perVariable);
        perVariableButton.setEnabled(this.sourceDataSet != null);

        // A CardLayout is used rather than toggling visibility because this editor is displayed by
        // SessionEditorNode.editParameters via JOptionPane.showOptionDialog, which sizes the dialog once from the
        // editor's preferred size and is neither resizable nor scrollable. CardLayout reports the maximum preferred
        // size over all cards whether or not they are showing, so the dialog is sized for the per-variable table
        // even when the editor opens in uniform mode. (Toggling visibility instead left the dialog sized for the
        // uniform panel, and the table had nowhere to go when the user switched modes.)
        CardLayout cardLayout = new CardLayout();
        JPanel cards = new JPanel(cardLayout);
        cards.add(uniformPanel, UNIFORM_CARD);
        cards.add(perVariablePanel, PER_VARIABLE_CARD);
        cardLayout.show(cards, perVariable ? PER_VARIABLE_CARD : UNIFORM_CARD);

        uniformButton.addActionListener(e -> {
            cardLayout.show(cards, UNIFORM_CARD);
            // Clearing the specs is what selects the uniform code path in LogData.
            this.params.set("logVariableSpecs", "");
        });

        perVariableButton.addActionListener(e -> {
            cardLayout.show(cards, PER_VARIABLE_CARD);
            writeSpecs();
        });

        Box modeBox = Box.createVerticalBox();

        Box b0 = Box.createHorizontalBox();
        b0.add(uniformButton);
        b0.add(Box.createHorizontalGlue());
        modeBox.add(b0);

        Box b1 = Box.createHorizontalBox();
        b1.add(perVariableButton);
        b1.add(Box.createHorizontalGlue());
        modeBox.add(b1);

        if (this.sourceDataSet == null) {
            Box b2 = Box.createHorizontalBox();
            b2.add(new JLabel("<html><i>(No tabular data parent found; per-variable settings unavailable.)</i></html>"));
            b2.add(Box.createHorizontalGlue());
            modeBox.add(b2);
        }

        add(modeBox, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);

        // Keep the dialog to a workable size on small screens; the table scrolls within it. Guarded because
        // Toolkit.getScreenSize() throws HeadlessException when there is no display, and the editor should still be
        // constructible (for tests, for instance) in that case.
        if (!GraphicsEnvironment.isHeadless()) {
            Dimension preferred = getPreferredSize();
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            setPreferredSize(new Dimension(
                    Math.min(preferred.width, (int) (screen.width * 0.6)),
                    Math.min(preferred.height, (int) (screen.height * 0.7))));
        }
    }

    /**
     * The dataset-wide controls, unchanged in behavior from the original editor.
     */
    private JPanel buildUniformPanel() {
        DoubleTextField aField = new DoubleTextField(this.params.getDouble("a", 10.0), 6,
                NumberFormatUtil.getInstance().getNumberFormat());
        aField.setFilter((value, oldValue) -> {
            try {
                LogParamsEditor.this.params.set("a", value);
                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        IntTextField baseField = new IntTextField(this.params.getInt("base", 0), 4);
        baseField.setFilter((value, oldValue) -> {
            try {
                LogParamsEditor.this.params.set("base", value);
                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        JCheckBox unlog = new JCheckBox();
        unlog.setSelected(this.params.getBoolean("unlog", false));
        unlog.addActionListener(e -> {
            JCheckBox box = (JCheckBox) e.getSource();
            LogParamsEditor.this.params.set("unlog", box.isSelected());
        });

        Box b = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        // The explicit width makes the HTML label wrap instead of demanding a very wide dialog, which matters
        // because JOptionPane sizes the dialog from this editor's preferred size.
        b2.add(new JLabel("<html><body style='width: 460px'>"
                + "The input dataset will be logarithmically transformed by applying f(x) = ln(a + x) to each data point x."
                + " Can also 'unlog' the data i.e., apply g(x) = exp(x) - a, or override the base.</body></html>"));
        b2.add(Box.createHorizontalGlue());
        b.add(b2);

        Box b9 = Box.createHorizontalBox();
        b9.add(Box.createHorizontalGlue());
        b9.add(new JLabel("<html> base (use 0 for natural log and base <i>e</i>): </html>"));
        b9.add(baseField);
        b.add(b9);

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("<html><i>a =  </i></html>"));
        b7.add(aField);
        b.add(b7);

        Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalGlue());
        b8.add(new JLabel("Unlog data: "));
        b8.add(unlog);
        b.add(b8);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(b, BorderLayout.CENTER);
        return panel;
    }

    /**
     * The per-variable table.
     */
    private JPanel buildPerVariablePanel(Map<String, LogTransformSpec> existing) {
        JPanel panel = new JPanel(new BorderLayout());

        this.rows = new ArrayList<>();

        if (this.sourceDataSet != null) {
            for (Node variable : this.sourceDataSet.getVariables()) {
                if (variable instanceof DiscreteVariable) continue;

                String name = variable.getName();
                double[] column = columnOf(name);
                double min = Double.POSITIVE_INFINITY;

                for (double x : column) {
                    if (!Double.isNaN(x) && x < min) min = x;
                }

                LogTransformSpec spec = existing.get(name);

                Row row = new Row();
                row.name = name;
                row.min = Double.isInfinite(min) ? Double.NaN : min;
                row.selected = spec != null;
                row.a = spec != null ? spec.a() : LogTransformSpec.safeOffsetFor(column);
                row.base = spec != null ? spec.base() : 0;
                row.unlog = spec != null && spec.unlog();
                this.rows.add(row);
            }
        }

        SpecTableModel tableModel = new SpecTableModel();
        JTable table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        // Bound the viewport so that a dataset with many variables scrolls inside a fixed-size dialog rather than
        // demanding a dialog taller than the screen; JOptionPane will not resize or scroll on our behalf.
        int visibleRows = Math.min(Math.max(this.rows.size(), 4), 15);
        table.setPreferredScrollableViewportSize(new Dimension(560,
                visibleRows * table.getRowHeight()));

        JLabel warning = new JLabel(" ");
        warning.setForeground(new Color(150, 30, 30));
        updateWarning(warning);

        tableModel.addTableModelListener(e -> {
            writeSpecs();
            updateWarning(warning);
        });

        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("<html><body style='width: 460px'>"
                + "Check the variables to transform and set each one's offset, base, and direction."
                + " Unchecked variables are passed through unchanged. A checked variable is logged as"
                + " f(x) = log<sub>base</sub>(a + x); base 0 means the natural log."
                + " <i>Note that a is scale-relative: log(a + x) is nearly affine in x when x is much"
                + " smaller than a, so one offset rarely suits variables of different magnitude.</i>"
                + "</body></html>"));
        b1.add(Box.createHorizontalGlue());
        b.add(b1);

        panel.add(b, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(warning, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Flags rows whose offset would take the logarithm outside its domain.
     */
    private void updateWarning(JLabel warning) {
        StringBuilder bad = new StringBuilder();

        for (Row row : this.rows) {
            if (row.selected && !row.unlog && !Double.isNaN(row.min) && row.a + row.min <= 0.0) {
                if (bad.length() > 0) bad.append(", ");
                bad.append(row.name);
            }
        }

        warning.setText(bad.length() == 0 ? " "
                : "a + min is not positive for: " + bad + " - these will produce NaN or -infinity.");
    }

    /**
     * Writes the checked rows into the parameters in the shared encoding. An empty result selects the uniform code
     * path in LogData, so at least one variable must be checked for per-variable mode to take effect.
     */
    private void writeSpecs() {
        Map<String, LogTransformSpec> specs = new LinkedHashMap<>();

        for (Row row : this.rows) {
            if (row.selected) {
                specs.put(row.name, new LogTransformSpec(row.a, row.base, row.unlog));
            }
        }

        this.params.set("logVariableSpecs", LogTransformSpec.encode(specs));
    }

    /**
     * Returns the values of the named column.
     */
    private double[] columnOf(String name) {
        int col = this.sourceDataSet.getColumnIndex(this.sourceDataSet.getVariable(name));
        double[] column = new double[this.sourceDataSet.getNumRows()];

        for (int i = 0; i < column.length; i++) {
            column[i] = this.sourceDataSet.getDouble(i, col);
        }

        return column;
    }

    /**
     * One editable row of the per-variable table.
     */
    private static class Row {

        /**
         * The variable name.
         */
        private String name;

        /**
         * The minimum observed value, or NaN if there is none.
         */
        private double min;

        /**
         * True if this variable is to be transformed.
         */
        private boolean selected;

        /**
         * The offset for this variable.
         */
        private double a;

        /**
         * The base for this variable; 0 means the natural log.
         */
        private int base;

        /**
         * True to apply the inverse transform.
         */
        private boolean unlog;
    }

    /**
     * The table model for the per-variable table.
     */
    private class SpecTableModel extends AbstractTableModel {

        /**
         * The column headers, in order.
         */
        private final String[] columns = {"Transform", "Variable", "Min", "a", "Base", "Unlog"};

        /**
         * {@inheritDoc}
         */
        public String getColumnName(int col) {
            return this.columns[col];
        }

        /**
         * {@inheritDoc}
         */
        public int getRowCount() {
            return LogParamsEditor.this.rows.size();
        }

        /**
         * {@inheritDoc}
         */
        public int getColumnCount() {
            return this.columns.length;
        }

        /**
         * {@inheritDoc}
         */
        public Class<?> getColumnClass(int col) {
            return switch (col) {
                case 0, 5 -> Boolean.class;
                case 3 -> Double.class;
                case 4 -> Integer.class;
                default -> String.class;
            };
        }

        /**
         * {@inheritDoc}
         */
        public boolean isCellEditable(int row, int col) {
            if (col == 1 || col == 2) return false;
            return col == 0 || LogParamsEditor.this.rows.get(row).selected;
        }

        /**
         * {@inheritDoc}
         */
        public Object getValueAt(int row, int col) {
            Row r = LogParamsEditor.this.rows.get(row);

            return switch (col) {
                case 0 -> r.selected;
                case 1 -> r.name;
                case 2 -> Double.isNaN(r.min) ? "-"
                        : NumberFormatUtil.getInstance().getNumberFormat().format(r.min);
                case 3 -> r.a;
                case 4 -> r.base;
                case 5 -> r.unlog;
                default -> null;
            };
        }

        /**
         * {@inheritDoc}
         */
        public void setValueAt(Object value, int row, int col) {
            Row r = LogParamsEditor.this.rows.get(row);

            switch (col) {
                case 0 -> r.selected = (Boolean) value;
                case 3 -> r.a = ((Number) value).doubleValue();
                case 4 -> r.base = ((Number) value).intValue();
                case 5 -> r.unlog = (Boolean) value;
                default -> {
                    return;
                }
            }

            fireTableRowsUpdated(row, row);
        }
    }
}
