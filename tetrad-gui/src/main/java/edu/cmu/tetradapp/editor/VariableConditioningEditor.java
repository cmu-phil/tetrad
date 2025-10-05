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


import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * Edits the conditions used for the Plot Matrix.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class VariableConditioningEditor extends JPanel {

    /**
     * New conditioning variable selector.
     */
    private final JComboBox<Node> newConditioningVariableSelector;

    /**
     * New conditioning variable button.
     */
    private final JButton newConditioningVariableButton;

    /**
     * Remove conditioning variable button.
     */
    private final JButton removeConditioningVariableButton;

    /**
     * Map of conditioning panels.
     */
    private final Map<Node, ConditioningPanel> conditioningPanelMap = new HashMap<>();

    /**
     * Constructs the editor panel given the initial histogram and any previous conditioning panel map.
     *
     * @param dataset               a {@link edu.cmu.tetrad.data.DataSet} object
     * @param _conditioningPanelMap a {@link java.util.Map} object
     */
    public VariableConditioningEditor(DataSet dataset, Map<Node, ConditioningPanel> _conditioningPanelMap) {
        if (_conditioningPanelMap == null) throw new NullPointerException();

        this.setLayout(new BorderLayout());

        List<Node> variables = dataset.getVariables();
        Collections.sort(variables);

        if (!_conditioningPanelMap.isEmpty()) {
            this.conditioningPanelMap.putAll(_conditioningPanelMap);
        }

        this.newConditioningVariableSelector = new JComboBox<>();

        for (Node node : variables) {
            this.newConditioningVariableSelector.addItem(node);
        }

        this.newConditioningVariableButton = new JButton("Add");

        this.newConditioningVariableButton.addActionListener(e -> {
            Node selected = (Node) VariableConditioningEditor.this.newConditioningVariableSelector.getSelectedItem();

            if (selected instanceof ContinuousVariable _var) {
                ContinuousConditioningPanel panel1 = (ContinuousConditioningPanel) VariableConditioningEditor.this.conditioningPanelMap.get(_var);

                if (panel1 == null) {
                    panel1 = ContinuousConditioningPanel.getDefault(_var, dataset);
                }

                ContinuousInquiryPanel panel2 = new ContinuousInquiryPanel(_var, dataset, panel1);

                JOptionPane.showOptionDialog(VariableConditioningEditor.this, panel2, null, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);

                ContinuousConditioningPanel.Type type = panel2.getType();
                double low = panel2.getLow();
                double high = panel2.getHigh();
                int ntile = panel2.getNtile();
                int ntileIndex = panel2.getNtileIndex();

                ContinuousConditioningPanel panel3 = new ContinuousConditioningPanel(_var, low, high, ntile, ntileIndex, type);
                VariableConditioningEditor.this.conditioningPanelMap.put(_var, panel3);
            } else if (selected instanceof DiscreteVariable _var) {
                DiscreteConditioningPanel panel1 = (DiscreteConditioningPanel) VariableConditioningEditor.this.conditioningPanelMap.get(_var);

                if (panel1 == null) {
                    panel1 = DiscreteConditioningPanel.getDefault(_var);
                    VariableConditioningEditor.this.conditioningPanelMap.put(_var, panel1);
                }

                DiscreteInquiryPanel panel2 = new DiscreteInquiryPanel(_var, panel1);

                JOptionPane.showOptionDialog(VariableConditioningEditor.this, panel2, null, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, null, null);

                String category = (String) panel2.getValuesDropdown().getSelectedItem();
                int index = _var.getIndex(category);

                DiscreteConditioningPanel panel3 = new DiscreteConditioningPanel(_var, index);
                VariableConditioningEditor.this.conditioningPanelMap.put(_var, panel3);
            } else {
                throw new IllegalStateException();
            }

            buildEditArea(dataset);
        });

        this.removeConditioningVariableButton = new JButton("Remove Checked");

        this.removeConditioningVariableButton.addActionListener(e -> {
            for (Node var : dataset.getVariables()) {
                if (conditioningPanelMap.containsKey(var)) {
                    ConditioningPanel conditioningPanel = conditioningPanelMap.get(var);
                    if (conditioningPanel != null && conditioningPanel.isSelected()) {
                        conditioningPanelMap.remove(var);
                    }
                }
            }

            buildEditArea(dataset);
        });

        // build the gui.
        VariableConditioningEditor.restrictSize(this.newConditioningVariableSelector);
        VariableConditioningEditor.restrictSize(this.newConditioningVariableButton);
        VariableConditioningEditor.restrictSize(this.removeConditioningVariableButton);

        buildEditArea(dataset);
    }

    private static void restrictSize(JComponent component) {
        component.setMaximumSize(component.getPreferredSize());
    }

    private void buildEditArea(DataSet dataSet) {
        Box main = Box.createVerticalBox();
        main.add(Box.createVerticalStrut(20));

        Box b6 = Box.createHorizontalBox();
        b6.add(this.newConditioningVariableSelector);
        b6.add(this.newConditioningVariableButton);
        b6.add(Box.createHorizontalGlue());
        main.add(b6);

        main.add(Box.createVerticalStrut(20));

        Box b3 = Box.createHorizontalBox();
        JLabel l1 = new JLabel("Conditioning on: ");
        l1.setFont(l1.getFont().deriveFont(Font.ITALIC));
        b3.add(l1);
        b3.add(Box.createHorizontalGlue());
        main.add(b3);

        main.add(Box.createVerticalStrut(10));

        for (Node node : conditioningPanelMap.keySet()) {
            ConditioningPanel panel = conditioningPanelMap.get(node);
            main.add(panel.getBox());
            main.add(Box.createVerticalStrut(5));
        }

        main.add(Box.createVerticalStrut(10));

        for (int i = this.newConditioningVariableSelector.getItemCount() - 1; i >= 0; i--) {
            this.newConditioningVariableSelector.removeItemAt(i);
        }

        List<Node> variables = dataSet.getVariables();
        Collections.sort(variables);

        for (Node node : variables) {
            ConditioningPanel panel = conditioningPanelMap.get(node);
            if (panel != null && node == panel.getVariable()) continue;
            this.newConditioningVariableSelector.addItem(node);
        }

        if (!conditioningPanelMap.isEmpty()) {
            Box b7 = Box.createHorizontalBox();
            b7.add(this.removeConditioningVariableButton);
            b7.add(Box.createHorizontalGlue());
            main.add(b7);
        }

        this.removeAll();
        this.setLayout(new BorderLayout());
        this.add(main, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * <p>Getter for the field <code>conditioningPanelMap</code>.</p>
     *
     * @return a {@link java.util.Map} object
     */
    public Map<Node, ConditioningPanel> getConditioningPanelMap() {
        return new HashMap<>(conditioningPanelMap);
    }

    //=======
    //=================== Inner classes ===========================//

    /**
     * Inquiry panel for discrete conditioning.
     */
    public interface ConditioningPanel {

        /**
         * <p>getBox.</p>
         *
         * @return a {@link java.lang.String} object
         */
        Box getBox();

        /**
         * selected for removal.
         *
         * @return a boolean
         */
        boolean isSelected();

        /**
         * <p>getVariable.</p>
         *
         * @return a {@link edu.cmu.tetrad.graph.Node} object
         */
        Node getVariable();
    }

    /**
     * Discrete inquiry panel.
     */
    public static class DiscreteConditioningPanel implements ConditioningPanel {

        /**
         * The variable.
         */
        private final DiscreteVariable variable;

        /**
         * The value.
         */
        private final String value;

        /**
         * The box.
         */
        private final Box box;

        /**
         * Set selected if this checkbox should be removed.
         */
        private final JCheckBox checkBox;

        /**
         * The index.
         */
        private final int index;

        /**
         * Constructs a new discrete conditioning panel.
         *
         * @param variable   a {@link edu.cmu.tetrad.data.DiscreteVariable} object
         * @param valueIndex a int
         */
        public DiscreteConditioningPanel(DiscreteVariable variable, int valueIndex) {
            if (variable == null) throw new NullPointerException();
            if (valueIndex < 0 || valueIndex >= variable.getNumCategories()) {
                throw new IllegalArgumentException("Not a category for this varible.");
            }

            this.variable = variable;
            this.value = variable.getCategory(valueIndex);
            this.index = valueIndex;

            Box b4 = Box.createHorizontalBox();
            this.checkBox = new JCheckBox();
            VariableConditioningEditor.restrictSize(this.checkBox);
            b4.add(this.checkBox);
            b4.add(new JLabel(variable + " = " + variable.getCategory(valueIndex)));
            b4.add(Box.createHorizontalGlue());
            b4.add(Box.createHorizontalGlue());
            this.box = b4;
        }

        /**
         * <p>getDefault.</p>
         *
         * @param var a {@link edu.cmu.tetrad.data.DiscreteVariable} object
         * @return a {@link edu.cmu.tetradapp.editor.VariableConditioningEditor.DiscreteConditioningPanel} object
         */
        public static VariableConditioningEditor.DiscreteConditioningPanel getDefault(DiscreteVariable var) {
            return new VariableConditioningEditor.DiscreteConditioningPanel(var, 0);
        }

        /**
         * <p>getVariable.</p>
         *
         * @return a {@link edu.cmu.tetrad.data.DiscreteVariable} object
         */
        public DiscreteVariable getVariable() {
            return this.variable;
        }

        /**
         * <p>getValue.</p>
         *
         * @return a {@link java.lang.String} object
         */
        public String getValue() {
            return this.value;
        }

        /**
         * <p>getIndex.</p>
         *
         * @return a int
         */
        public int getIndex() {
            return this.index;
        }

        /**
         * <p>getBox.</p>
         *
         * @return a {@link javax.swing.Box} object
         */
        public Box getBox() {
            return this.box;
        }

        public boolean isSelected() {
            return this.checkBox.isSelected();
        }
    }

    /**
     * Continuous conditioning panel.
     */
    public static class ContinuousConditioningPanel implements ConditioningPanel {

        /**
         * The variable.
         */
        private final ContinuousVariable variable;

        /**
         * The box.
         */
        private final Box box;

        /**
         * The type.
         */
        private final VariableConditioningEditor.ContinuousConditioningPanel.Type type;

        /**
         * The low.
         */
        private final double low;

        /**
         * The high.
         */
        private final double high;

        /**
         * The ntile.
         */
        private final int ntile;

        /**
         * The ntile index.
         */
        private final int ntileIndex;

        /**
         * Mark selected if this panel is to be removed.
         */
        private final JCheckBox checkBox;

        /**
         * Constructs a new continuous conditioning panel.
         *
         * @param variable   a {@link edu.cmu.tetrad.data.ContinuousVariable} object
         * @param low        a double
         * @param high       a double
         * @param ntile      a int
         * @param ntileIndex a int
         * @param type       a
         *                   {@link
         *                   edu.cmu.tetradapp.editor.VariableConditioningEditor.ContinuousConditioningPanel.Type}
         *                   object
         */
        public ContinuousConditioningPanel(ContinuousVariable variable, double low, double high, int ntile, int ntileIndex, VariableConditioningEditor.ContinuousConditioningPanel.Type type) {
            if (variable == null) throw new NullPointerException();
            if (low >= high) {
                throw new IllegalArgumentException("Low >= high.");
            }
            if (ntile < 2 || ntile > 10) {
                throw new IllegalArgumentException("Ntile should be in range 2 to 10: " + ntile);
            }

            this.variable = variable;
            NumberFormat nf = new DecimalFormat("0.0000");

            this.type = type;
            this.low = low;
            this.high = high;
            this.ntile = ntile;
            this.ntileIndex = ntileIndex;

            Box b4 = Box.createHorizontalBox();
            this.checkBox = new JCheckBox();
            b4.add(this.checkBox);

            if (type == VariableConditioningEditor.ContinuousConditioningPanel.Type.Range) {
                b4.add(new JLabel(variable + " = (" + nf.format(low) + ", " + nf.format(high) + ")"));
            } else if (type == VariableConditioningEditor.ContinuousConditioningPanel.Type.AboveAverage) {
                b4.add(new JLabel(variable + " = Above Average"));
            } else if (type == VariableConditioningEditor.ContinuousConditioningPanel.Type.BelowAverage) {
                b4.add(new JLabel(variable + " = Below Average"));
            } else if (type == VariableConditioningEditor.ContinuousConditioningPanel.Type.Ntile) {
                b4.add(new JLabel(variable + " = " + edu.cmu.tetradapp.editor.HistogramPanel.tiles[ntile - 1] + " " + ntileIndex));
            }

            b4.add(Box.createHorizontalGlue());
            this.box = b4;
        }

        /**
         * <p>getDefault.</p>
         *
         * @param variable a {@link edu.cmu.tetrad.data.ContinuousVariable} object
         * @param dataSet  a {@link edu.cmu.tetrad.data.DataSet} object
         * @return a {@link edu.cmu.tetradapp.editor.VariableConditioningEditor.ContinuousConditioningPanel} object
         */
        public static VariableConditioningEditor.ContinuousConditioningPanel getDefault(ContinuousVariable variable, DataSet dataSet) {
            double[] data = getContinuousData(variable.getName(), dataSet);
            double max = StatUtils.max(data);
            double avg = StatUtils.mean(data);
            return new VariableConditioningEditor.ContinuousConditioningPanel(variable, avg, max, 2, 1, VariableConditioningEditor.ContinuousConditioningPanel.Type.AboveAverage);
        }

        /**
         * <p>getContinuousData.</p>
         *
         * @param variable a {@link java.lang.String} object
         * @param dataSet  a {@link edu.cmu.tetrad.data.DataSet} object
         * @return an array of double
         */
        public static double[] getContinuousData(String variable, DataSet dataSet) {
            int index = dataSet.getColumn(dataSet.getVariable(variable));
            List<Double> _data = new ArrayList<>();

            for (int i = 0; i < dataSet.getNumRows(); i++) {
                _data.add(dataSet.getDouble(i, index));
            }

            return asDoubleArray(_data);
        }

        private static double[] asDoubleArray(List<Double> data) {
            double[] _data = new double[data.size()];
            for (int i = 0; i < data.size(); i++) _data[i] = data.get(i);
            return _data;
        }

        /**
         * <p>getNtile.</p>
         *
         * @return a int
         */
        public int getNtile() {
            return this.ntile;
        }

        /**
         * <p>getNtileIndex.</p>
         *
         * @return a int
         */
        public int getNtileIndex() {
            return this.ntileIndex;
        }

        /**
         * <p>getVariable.</p>
         *
         * @return a {@link edu.cmu.tetrad.data.ContinuousVariable} object
         */
        public ContinuousVariable getVariable() {
            return this.variable;
        }

        /**
         * <p>getType.</p>
         *
         * @return a {@link edu.cmu.tetradapp.editor.VariableConditioningEditor.ContinuousConditioningPanel.Type} object
         */
        public VariableConditioningEditor.ContinuousConditioningPanel.Type getType() {
            return this.type;
        }

        /**
         * <p>getBox.</p>
         *
         * @return a {@link javax.swing.Box} object
         */
        public Box getBox() {
            return this.box;
        }

        /**
         * <p>isSelected.</p>
         *
         * @return a boolean
         */
        public boolean isSelected() {
            return this.checkBox.isSelected();
        }

        /**
         * <p>getLow.</p>
         *
         * @return a double
         */
        public double getLow() {
            return this.low;
        }

        /**
         * <p>getHigh.</p>
         *
         * @return a double
         */
        public double getHigh() {
            return this.high;
        }

        /**
         * An enum for the type of conditioning.
         */
        public enum Type {

            /**
             * Range.
             */
            Range,

            /**
             * Ntile.
             */
            Ntile,

            /**
             * Above average.
             */
            AboveAverage,

            /**
             * Below average.
             */
            BelowAverage
        }
    }

    /**
     * Continuous inquiry panel.
     */
    static class ContinuousInquiryPanel extends JPanel {

        /**
         * The combo box for ntile.
         */
        private final JComboBox<String> ntileCombo;

        /**
         * The combo box for ntile index.
         */
        private final JComboBox<Integer> ntileIndexCombo;

        /**
         * The field1.
         */
        private final DoubleTextField field1;

        /**
         * The field2.
         */
        private final DoubleTextField field2;

        /**
         * The ntile map.
         */
        private final Map<String, Integer> ntileMap = new HashMap<>();

        /**
         * The data.
         */
        private final double[] data;

        /**
         * The type.
         */
        private ContinuousConditioningPanel.Type type;

        /**
         * @param variable          This is the variable being conditioned on. Must be continuous and one of the
         *                          variables in the histogram.
         * @param dataSet           The dataset.
         * @param conditioningPanel We will try to get some initialization information out of the conditioning panel.
         *                          This must be for the same variable as variable.
         */
        public ContinuousInquiryPanel(ContinuousVariable variable, DataSet dataSet, ContinuousConditioningPanel conditioningPanel) {
            this.data = ContinuousConditioningPanel.getContinuousData(variable.getName(), dataSet);

            if (conditioningPanel == null) throw new NullPointerException();
            if (!(variable == conditioningPanel.getVariable()))
                throw new IllegalArgumentException("Wrong variable for conditioning panel.");

            // There is some order dependence in the below; careful rearranging things.
            NumberFormat nf = new DecimalFormat("0.00");

            this.field1 = new DoubleTextField(conditioningPanel.getLow(), 4, nf);
            this.field2 = new DoubleTextField(conditioningPanel.getHigh(), 4, nf);

            JRadioButton radio1 = new JRadioButton();
            JRadioButton radio2 = new JRadioButton();
            JRadioButton radio3 = new JRadioButton();
            JRadioButton radio4 = new JRadioButton();

            radio1.addActionListener(e -> {
                ContinuousInquiryPanel.this.type = ContinuousConditioningPanel.Type.AboveAverage;
                ContinuousInquiryPanel.this.field1.setValue(StatUtils.mean(ContinuousInquiryPanel.this.data));
                ContinuousInquiryPanel.this.field2.setValue(StatUtils.max(ContinuousInquiryPanel.this.data));
            });

            radio2.addActionListener(e -> {
                ContinuousInquiryPanel.this.type = ContinuousConditioningPanel.Type.BelowAverage;
                ContinuousInquiryPanel.this.field1.setValue(StatUtils.min(ContinuousInquiryPanel.this.data));
                ContinuousInquiryPanel.this.field2.setValue(StatUtils.mean(ContinuousInquiryPanel.this.data));
            });

            radio3.addActionListener(e -> {
                ContinuousInquiryPanel.this.type = ContinuousConditioningPanel.Type.Ntile;
                double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(ContinuousInquiryPanel.this.data, getNtile());
                double breakpoint1 = breakpoints[getNtileIndex() - 1];
                double breakpoint2 = breakpoints[getNtileIndex()];
                ContinuousInquiryPanel.this.field1.setValue(breakpoint1);
                ContinuousInquiryPanel.this.field2.setValue(breakpoint2);
            });

            radio4.addActionListener(e -> ContinuousInquiryPanel.this.type = ContinuousConditioningPanel.Type.Range);

            ButtonGroup group = new ButtonGroup();
            group.add(radio1);
            group.add(radio2);
            group.add(radio3);
            group.add(radio4);

            this.type = conditioningPanel.getType();

            this.ntileCombo = new JComboBox<>();
            this.ntileIndexCombo = new JComboBox<>();

            int ntile = conditioningPanel.getNtile();
            int ntileIndex = conditioningPanel.getNtileIndex();

            for (int n = 2; n <= 10; n++) {
                this.ntileCombo.addItem(HistogramPanel.tiles[n - 1]);
                this.ntileMap.put(HistogramPanel.tiles[n - 1], n);
            }

            for (int n = 1; n <= ntile; n++) {
                this.ntileIndexCombo.addItem(n);
            }

            this.ntileCombo.setSelectedItem(HistogramPanel.tiles[ntile - 1]);
            this.ntileIndexCombo.setSelectedItem(ntileIndex);

            this.ntileCombo.addItemListener(e -> {
                String item = (String) e.getItem();
                int ntileIndex1 = ContinuousInquiryPanel.this.ntileMap.get(item);

                for (int i = ContinuousInquiryPanel.this.ntileIndexCombo.getItemCount() - 1; i >= 0; i--) {
                    ContinuousInquiryPanel.this.ntileIndexCombo.removeItemAt(i);
                }

                for (int n = 1; n <= ntileIndex1; n++) {
                    ContinuousInquiryPanel.this.ntileIndexCombo.addItem(n);
                }

                double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(ContinuousInquiryPanel.this.data, getNtile());
                double breakpoint1 = breakpoints[getNtileIndex() - 1];
                double breakpoint2 = breakpoints[getNtileIndex()];
                ContinuousInquiryPanel.this.field1.setValue(breakpoint1);
                ContinuousInquiryPanel.this.field2.setValue(breakpoint2);
            });

            this.ntileIndexCombo.addItemListener(e -> {
                int ntile1 = getNtile();
                int ntileIndex12 = getNtileIndex();
                double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(ContinuousInquiryPanel.this.data, ntile1);
                double breakpoint1 = breakpoints[ntileIndex12 - 1];
                double breakpoint2 = breakpoints[ntileIndex12];
                ContinuousInquiryPanel.this.field1.setValue(breakpoint1);
                ContinuousInquiryPanel.this.field2.setValue(breakpoint2);
            });


            if (this.type == ContinuousConditioningPanel.Type.AboveAverage) {
                radio1.setSelected(true);
                this.field1.setValue(StatUtils.mean(this.data));
                this.field2.setValue(StatUtils.max(this.data));
            } else if (this.type == ContinuousConditioningPanel.Type.BelowAverage) {
                radio2.setSelected(true);
                this.field1.setValue(StatUtils.min(this.data));
                this.field2.setValue(StatUtils.mean(this.data));
            } else if (this.type == ContinuousConditioningPanel.Type.Ntile) {
                radio3.setSelected(true);
                double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(this.data, getNtile());
                double breakpoint1 = breakpoints[getNtileIndex() - 1];
                double breakpoint2 = breakpoints[getNtileIndex()];
                this.field1.setValue(breakpoint1);
                this.field2.setValue(breakpoint2);
            } else if (this.type == ContinuousConditioningPanel.Type.Range) {
                radio4.setSelected(true);
            }

            Box main = Box.createVerticalBox();

            Box b0 = Box.createHorizontalBox();
            b0.add(new JLabel("Condition on " + variable.getName() + " as:"));
            b0.add(Box.createHorizontalGlue());
            main.add(b0);
            main.add(Box.createVerticalStrut(10));

            Box b1 = Box.createHorizontalBox();
            b1.add(radio1);
            b1.add(new JLabel("Above average"));
            b1.add(Box.createHorizontalGlue());
            main.add(b1);

            Box b2 = Box.createHorizontalBox();
            b2.add(radio2);
            b2.add(new JLabel("Below average"));
            b2.add(Box.createHorizontalGlue());
            main.add(b2);

            Box b3 = Box.createHorizontalBox();
            b3.add(radio3);
            b3.add(new JLabel("In "));
            b3.add(this.ntileCombo);
            b3.add(this.ntileIndexCombo);
            b3.add(Box.createHorizontalGlue());
            main.add(b3);

            Box b4 = Box.createHorizontalBox();
            b4.add(radio4);
            b4.add(new JLabel("In ("));
            b4.add(this.field1);
            b4.add(new JLabel(", "));
            b4.add(this.field2);
            b4.add(new JLabel(")"));
            b4.add(Box.createHorizontalGlue());
            main.add(b4);

            add(main, BorderLayout.CENTER);
        }

        /**
         * @return an array of breakpoints that divides the data into equal sized buckets, including the min and max.
         */
        public static double[] getNtileBreakpoints(double[] data, int ntiles) {
            double[] _data = new double[data.length];
            System.arraycopy(data, 0, _data, 0, _data.length);

            // first sort the _data.
            Arrays.sort(_data);
            List<Chunk> chunks = new ArrayList<>(_data.length);
            int startChunkCount = 0;
            double lastValue = _data[0];

            for (int i = 0; i < _data.length; i++) {
                double value = _data[i];
                if (value != lastValue) {
                    chunks.add(new Chunk(startChunkCount, i, value));
                    startChunkCount = i;
                }
                lastValue = value;
            }

            chunks.add(new Chunk(startChunkCount, _data.length, _data[_data.length - 1]));

            // now find the breakpoints.
            double interval = _data.length / (double) ntiles;
            double[] breakpoints = new double[ntiles + 1];
            breakpoints[0] = StatUtils.min(_data);

            int current = 1;
            int freq = 0;

            for (Chunk chunk : chunks) {
                int valuesInChunk = chunk.getNumberOfValuesInChunk();
                int halfChunk = (int) (valuesInChunk * .5);

                // if more than half the values in the chunk fit this bucket then put here,
                // otherwise the chunk should be added to the next bucket.
                if (freq + halfChunk <= interval) {
                    freq += valuesInChunk;
                } else {
                    freq = valuesInChunk;
                }

                if (interval <= freq) {
                    freq = 0;
                    if (current < ntiles + 1) {
                        breakpoints[current++] = chunk.value;
                    }
                }
            }

            for (int i = current; i < breakpoints.length; i++) {
                breakpoints[i] = StatUtils.max(_data);
            }

            return breakpoints;
        }

        /**
         * @return the type of conditioning.
         */
        public ContinuousConditioningPanel.Type getType() {
            return this.type;
        }

        /**
         * @return the low value for the range.
         */
        public double getLow() {
            return this.field1.getValue();
        }

        /**
         * @return the high value for the range.
         */
        public double getHigh() {
            return this.field2.getValue();
        }

        /**
         * @return the ntile.
         */
        public int getNtile() {
            String selectedItem = (String) this.ntileCombo.getSelectedItem();
            return this.ntileMap.get(selectedItem);
        }

        /**
         * @return the index of the ntile.
         */
        public int getNtileIndex() {
            Object selectedItem = this.ntileIndexCombo.getSelectedItem();
            return selectedItem == null ? 1 : (Integer) selectedItem;
        }

        /**
         * Represents a chunk of data in a sorted array of data.  If low == high then the chunk only contains one
         * member.
         */
        private static class Chunk {

            private final int valuesInChunk;
            private final double value;

            public Chunk(int low, int high, double value) {
                this.valuesInChunk = (high - low);
                this.value = value;
            }

            public int getNumberOfValuesInChunk() {
                return this.valuesInChunk;
            }

        }
    }

    /**
     * Discrete inquiry panel.
     */
    static class DiscreteInquiryPanel extends JPanel {

        /**
         * The combo box for values.
         */
        private final JComboBox<String> valuesDropdown;

        /**
         * @param var   The variable being conditioned on.
         * @param panel The conditioning panel for this variable.
         */
        public DiscreteInquiryPanel(DiscreteVariable var, DiscreteConditioningPanel panel) {
            this.valuesDropdown = new JComboBox<>();

            for (String category : var.getCategories()) {
                getValuesDropdown().addItem(category);
            }

            this.valuesDropdown.setSelectedItem(panel.getValue());

            Box main = Box.createVerticalBox();
            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Condition on:"));
            b1.add(Box.createHorizontalGlue());
            main.add(b1);
            main.add(Box.createVerticalStrut(10));

            Box b2 = Box.createHorizontalBox();
            b2.add(Box.createHorizontalStrut(10));
            b2.add(new JLabel(var.getName() + " = "));
            b2.add(getValuesDropdown());
            main.add(b2);

            add(main, BorderLayout.CENTER);
        }

        /**
         * @return the selected value.
         */
        public JComboBox<String> getValuesDropdown() {
            return this.valuesDropdown;
        }
    }
}


