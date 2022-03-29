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


import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.geom.Point2D;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;

/**
 * This is the wizard which allows the user to select the x and y-axis variables
 * to chart the ScatterPlot.
 *
 * @author Adrian Tang
 * @author Joseph Ramsey
 */
public class ScatterPlotView extends JPanel {
    private final ScatterPlot scatterPlot;
    private final ScatterPlotChart scatterPlotChart;
    private String x;
    private String y;
    private static final String[] tiles = {"1-tile", "2-tile", "tertile", "quartile", "quintile", "sextile",
            "septile", "octile", "nontile", "decile"};

    public ScatterPlotView(final DataSet dataSet) {

        // This is annoying. jdramsey 5/14/2014
//        if (!dataSet.isContinuous()) throw new IllegalArgumentException("Data set not continuous.");
        if (!(dataSet.getNumColumns() >= 2)) throw new IllegalArgumentException("Need at least two columns.");

        final DataSet dataSet1 = dataSet;

        this.x = dataSet.getVariable(0).getName();
        this.y = dataSet.getVariable(1).getName();

        setLayout(new BorderLayout());
        final ScatterPlot ScatterPlot = new ScatterPlot(dataSet1, false, this.x, this.y);
        final ScatterPlotChart ScatterPlotChart = new ScatterPlotChart(ScatterPlot);
        this.scatterPlot = ScatterPlot;
        this.scatterPlotChart = ScatterPlotChart;

        add(ScatterPlotChart, BorderLayout.CENTER);
        add(new ScatterPlotController(this), BorderLayout.EAST);

        setPreferredSize(new Dimension(750, 450));
    }

    private void setX(final String x) {
        this.x = x;
    }

    private void setY(final String y) {
        this.y = y;
    }

    private ScatterPlot getScatterPlot() {
        return this.scatterPlot;
    }

    public static class ScatterPlotController extends JPanel {
        private final ScatterPlot scatterPlot;
        private final JComboBox xSelector;
        private final JComboBox ySelector;
        private final JComboBox newConditioningVariableSelector;
        private final JButton newConditioningVariableButton;
        private final JButton removeConditioningVariableButton;
        private final java.util.List<ConditioningPanel> conditioningPanels = new ArrayList<>();
        private final JCheckBox includeLineCheckbox;

        // To provide some memory of previous settings for the inquiry dialogs.
        private final Map<Node, ConditioningPanel> conditioningPanelMap = new HashMap<>();

        /**
         * Constructs the editor panel given the initial ScatterPlot and the dataset.
         */
        public ScatterPlotController(final ScatterPlotView ScatterPlotView) {
            this.setLayout(new BorderLayout());
            this.scatterPlot = ScatterPlotView.getScatterPlot();
            final Node xNode = getXNode();
            final Node yNode = getYNode();
            this.xSelector = new JComboBox();
            this.ySelector = new JComboBox();
            final ListCellRenderer renderer = new VariableBoxRenderer();
            this.xSelector.setRenderer(renderer);
            this.ySelector.setRenderer(renderer);

            this.includeLineCheckbox = new JCheckBox("Show Regression Line");
            final List<Node> variables = this.scatterPlot.getDataSet().getVariables();

            Collections.sort(variables);

            for (final Node node : variables) {
                this.xSelector.addItem(node);

                if (node == xNode) {
                    this.xSelector.setSelectedItem(node);
                }
            }

            for (final Node node : variables) {
                this.ySelector.addItem(node);

                if (node == yNode) {
                    this.ySelector.setSelectedItem(node);
                }
            }

            this.xSelector.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    final String node = ((Node) ScatterPlotController.this.xSelector.getSelectedItem()).getName();
                    ScatterPlotView.setX(node);
                    refreshChart(ScatterPlotView);
                }
            });

            this.ySelector.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    final String node = ((Node) ySelector.getSelectedItem()).getName();
                    ScatterPlotView.setY(node);
                    refreshChart(ScatterPlotView);
                }
            });

            this.includeLineCheckbox.addActionListener(e -> refreshChart(ScatterPlotView));

            this.newConditioningVariableSelector = new JComboBox();

            for (final Node node : variables) {
                this.newConditioningVariableSelector.addItem(node);
            }

            this.newConditioningVariableButton = new JButton("Add");

            this.newConditioningVariableButton.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    System.out.println("New conditioning variable action performed");
                    final Node selected = (Node) ScatterPlotController.this.newConditioningVariableSelector.getSelectedItem();

                    for (final ConditioningPanel panel : ScatterPlotController.this.conditioningPanels) {
                        if (selected == panel.getVariable()) {
                            JOptionPane.showMessageDialog(ScatterPlotController.this,
                                    "There is already a conditioning variable called " + selected + ".");
                            return;
                        }
                    }

                    if (selected instanceof ContinuousVariable) {
                        final ContinuousVariable _var = (ContinuousVariable) selected;

                        ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel panel1 = (ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) ScatterPlotController.this.conditioningPanelMap.get(_var);

                        if (panel1 == null) {
                            panel1 = ScatterPlotController.ContinuousConditioningPanel.getDefault(_var, ScatterPlotController.this.scatterPlot);
                        }

                        final ContinuousInquiryPanel panel2 = new ContinuousInquiryPanel(_var, ScatterPlotController.this.scatterPlot, panel1);

                        JOptionPane.showOptionDialog(ScatterPlotController.this, panel2,
                                null, JOptionPane.DEFAULT_OPTION,
                                JOptionPane.PLAIN_MESSAGE, null, null, null);

                        final ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type type = panel2.getType();
                        final double low = panel2.getLow();
                        final double high = panel2.getHigh();
                        final int ntile = panel2.getNtile();
                        final int ntileIndex = panel2.getNtileIndex();

                        final ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel panel3 = new ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel(_var, low, high, ntile, ntileIndex, type);

                        ScatterPlotController.this.conditioningPanels.add(panel3);
                        ScatterPlotController.this.conditioningPanelMap.put(_var, panel3);
                    } else {
                        throw new IllegalStateException();
                    }

                    buildEditArea();
                    resetConditioning();

                    refreshChart(ScatterPlotView);
                }
            });

            this.removeConditioningVariableButton = new JButton("Remove Checked");

            this.removeConditioningVariableButton.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    for (final ConditioningPanel panel : new ArrayList<>(ScatterPlotController.this.conditioningPanels)) {
                        if (panel.isSelected()) {
                            panel.setSelected(false);
                            ScatterPlotController.this.conditioningPanels.remove(panel);
                            ScatterPlotController.this.scatterPlot.removeConditioningVariable(panel.getVariable().toString());
                            refreshChart(ScatterPlotView);
                        }
                    }

                    buildEditArea();
                    resetConditioning();
                }
            });

            // build the gui.
            ScatterPlotController.restrictSize(this.xSelector);
            ScatterPlotController.restrictSize(this.newConditioningVariableSelector);
            ScatterPlotController.restrictSize(this.newConditioningVariableButton);
            ScatterPlotController.restrictSize(this.removeConditioningVariableButton);

            buildEditArea();
        }

        private void refreshChart(final ScatterPlotView ScatterPlotView) {
            final ScatterPlot ScatterPlot = new ScatterPlot(ScatterPlotView.scatterPlot.getDataSet(),
                    this.includeLineCheckbox.isSelected(),
                    ScatterPlotView.x, ScatterPlotView.y);
            ScatterPlot.removeConditioningVariables();
            for (final ConditioningPanel panel : this.conditioningPanels) {
                if (panel instanceof ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) {
                    final Node node = panel.getVariable();
                    final double low = ((ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) panel).getLow();
                    final double high = ((ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) panel).getHigh();
                    ScatterPlot.addConditioningVariable(node.getName(), low, high);
                }
            }

            ScatterPlotView.scatterPlotChart.setScatterPlot(ScatterPlot);
            ScatterPlotView.scatterPlotChart.repaint();
        }

        private void resetConditioning() {

            // Need to set the conditions on the ScatterPlot and also update the list of conditions in the view.
            this.scatterPlot.removeConditioningVariables();

            for (final ConditioningPanel panel : this.conditioningPanels) {
                if (panel instanceof ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) {
                    final Node node = panel.getVariable();
                    final double low = ((ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) panel).getLow();
                    final double high = ((ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) panel).getHigh();
                    this.scatterPlot.addConditioningVariable(node.getName(), low, high);
                }
            }
        }

        private void buildEditArea() {
            ScatterPlotController.restrictSize(this.xSelector);
            ScatterPlotController.restrictSize(this.ySelector);

            final Box main = Box.createVerticalBox();
            final Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("ScatterPlot for: "));
            b1.add(Box.createHorizontalGlue());
            main.add(b1);

            final Box b1a = Box.createHorizontalBox();
            b1a.add(new JLabel("X-axis = "));
            b1a.add(Box.createHorizontalGlue());
            b1a.add(this.xSelector);
            main.add(b1a);

            final Box b1b = Box.createHorizontalBox();
            b1b.add(new JLabel("Y-axis = "));
            b1b.add(Box.createHorizontalGlue());
            b1b.add(this.ySelector);
            main.add(b1b);

            final Box b1c = Box.createHorizontalBox();
            b1c.add(this.includeLineCheckbox);
            main.add(b1c);

            main.add(Box.createVerticalStrut(20));

            final Box b3 = Box.createHorizontalBox();
            final JLabel l1 = new JLabel("Conditioning on: ");
            l1.setFont(l1.getFont().deriveFont(Font.ITALIC));
            b3.add(l1);
            b3.add(Box.createHorizontalGlue());
            main.add(b3);

            main.add(Box.createVerticalStrut(20));

            for (final ConditioningPanel panel : this.conditioningPanels) {
                main.add(panel.getBox());
                main.add(Box.createVerticalStrut(10));
            }

            main.add(Box.createVerticalStrut(10));
            main.add(Box.createVerticalGlue());
            main.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            final Box b6 = Box.createHorizontalBox();
            b6.add(this.newConditioningVariableSelector);
            b6.add(this.newConditioningVariableButton);
            b6.add(Box.createHorizontalGlue());
            main.add(b6);

            final Box b7 = Box.createHorizontalBox();
            b7.add(this.removeConditioningVariableButton);
            b7.add(Box.createHorizontalGlue());
            main.add(b7);

            this.removeAll();
            this.add(main, BorderLayout.CENTER);
            revalidate();
            repaint();
        }

        //========================== Private Methods ================================//

        private Node getXNode() {
            return this.scatterPlot.getDataSet().getVariable(this.scatterPlot.getXvar());
        }

        private Node getYNode() {
            return this.scatterPlot.getDataSet().getVariable(this.scatterPlot.getYvar());
        }

        private static void restrictSize(final JComponent component) {
            component.setMaximumSize(component.getPreferredSize());
        }

        //========================== Inner classes ===========================//


        private static class VariableBoxRenderer extends DefaultListCellRenderer {

            public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
                final Node node = (Node) value;
                if (node == null) {
                    this.setText("");
                } else {
                    this.setText(node.getName());
                }
                if (isSelected) {
                    setBackground(list.getSelectionBackground());
                    setForeground(list.getSelectionForeground());
                } else {
                    setBackground(list.getBackground());
                    setForeground(list.getForeground());
                }

                return this;
            }
        }

        private interface ConditioningPanel {
            Box getBox();

            // selected for removal.
            boolean isSelected();

            Node getVariable();

            void setSelected(boolean b);
        }

        public static class ContinuousConditioningPanel implements ConditioningPanel {

            public int getNtile() {
                return this.ntile;
            }

            public int getNtileIndex() {
                return this.ntileIndex;
            }

            public enum Type {Range, Ntile, AboveAverage, BelowAverage}

            private final ContinuousVariable variable;
            private final Box box;

            private final ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type type;
            private final double low;
            private final double high;
            private final int ntile;
            private final int ntileIndex;

            // Mark selected if this panel is to be removed.
            private final JCheckBox checkBox;

            public ContinuousConditioningPanel(final ContinuousVariable variable, final double low, final double high, final int ntile, final int ntileIndex, final ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type type) {
                if (variable == null) throw new NullPointerException();
                if (low >= high) {
                    throw new IllegalArgumentException("Low >= high.");
                }
                if (ntile < 2 || ntile > 10) {
                    throw new IllegalArgumentException("Ntile should be in range 2 to 10: " + ntile);
                }

                this.variable = variable;
                final NumberFormat nf = new DecimalFormat("0.0000");

                this.type = type;
                this.low = low;
                this.high = high;
                this.ntile = ntile;
                this.ntileIndex = ntileIndex;

                final Box b4 = Box.createHorizontalBox();
                b4.add(Box.createRigidArea(new Dimension(10, 0)));

                if (type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Range) {
                    b4.add(new JLabel(variable + " = (" + nf.format(low) + ", " + nf.format(high) + ")"));
                } else if (type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.AboveAverage) {
                    b4.add(new JLabel(variable + " = Above Average"));
                } else if (type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.BelowAverage) {
                    b4.add(new JLabel(variable + " = Below Average"));
                } else if (type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Ntile) {
                    b4.add(new JLabel(variable + " = " + ScatterPlotView.tiles[ntile - 1] + " " + ntileIndex));
                }

                b4.add(Box.createHorizontalGlue());
                this.checkBox = new JCheckBox();
                ScatterPlotController.restrictSize(this.checkBox);
                b4.add(this.checkBox);
                this.box = b4;

            }

            public static ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel getDefault(final ContinuousVariable variable, final ScatterPlot ScatterPlot) {
                final double[] data = ScatterPlot.getContinuousData(variable.getName());
                final double max = StatUtils.max(data);
                final double avg = StatUtils.mean(data);
                return new ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel(variable, avg, max, 2, 1, ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.AboveAverage);
            }

            public ContinuousVariable getVariable() {
                return this.variable;
            }

            public ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type getType() {
                return this.type;
            }

            public Box getBox() {
                return this.box;
            }

            public boolean isSelected() {
                return this.checkBox.isSelected();
            }

            public void setSelected(final boolean b) {
                this.checkBox.setSelected(false);
            }

            public double getLow() {
                return this.low;
            }

            public double getHigh() {
                return this.high;
            }
        }
    }

    private static class ContinuousInquiryPanel extends JPanel {
        private final JComboBox ntileCombo;
        private final JComboBox ntileIndexCombo;
        private final DoubleTextField field1;
        private final DoubleTextField field2;
        private ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type type;
        private final Map<String, Integer> ntileMap = new HashMap<>();
        private final double[] data;

        /**
         * @param variable          This is the variable being conditioned on. Must be continuous and one of the variables
         *                          in the ScatterPlot.
         * @param ScatterPlot       We need this to get the column of data for the variable.
         * @param conditioningPanel We will try to get some initialization information out of the conditioning
         *                          panel. This must be for the same variable as variable.
         */
        public ContinuousInquiryPanel(final ContinuousVariable variable, final ScatterPlot ScatterPlot,
                                      final ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel conditioningPanel) {
            this.data = ScatterPlot.getContinuousData(variable.getName());

            if (conditioningPanel == null)
                throw new NullPointerException();
            if (!(variable == conditioningPanel.getVariable()))
                throw new IllegalArgumentException("Wrong variable for conditioning panel.");

            // There is some order dependence in the below; careful rearranging things.
            final NumberFormat nf = new DecimalFormat("0.00");

            this.field1 = new DoubleTextField(conditioningPanel.getLow(), 4, nf);
            this.field2 = new DoubleTextField(conditioningPanel.getHigh(), 4, nf);

            final JRadioButton radio1 = new JRadioButton();
            final JRadioButton radio2 = new JRadioButton();
            final JRadioButton radio3 = new JRadioButton();
            final JRadioButton radio4 = new JRadioButton();

            radio1.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    ContinuousInquiryPanel.this.type = ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.AboveAverage;
                    ContinuousInquiryPanel.this.field1.setValue(StatUtils.mean(ContinuousInquiryPanel.this.data));
                    ContinuousInquiryPanel.this.field2.setValue(StatUtils.max(ContinuousInquiryPanel.this.data));
                }
            });

            radio2.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    ContinuousInquiryPanel.this.type = ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.BelowAverage;
                    ContinuousInquiryPanel.this.field1.setValue(StatUtils.min(ContinuousInquiryPanel.this.data));
                    ContinuousInquiryPanel.this.field2.setValue(StatUtils.mean(ContinuousInquiryPanel.this.data));
                }
            });

            radio3.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    ContinuousInquiryPanel.this.type = ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Ntile;
                    final double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(ContinuousInquiryPanel.this.data, getNtile());
                    final double breakpoint1 = breakpoints[getNtileIndex() - 1];
                    final double breakpoint2 = breakpoints[getNtileIndex()];
                    ContinuousInquiryPanel.this.field1.setValue(breakpoint1);
                    ContinuousInquiryPanel.this.field2.setValue(breakpoint2);
                }
            });

            radio4.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    ContinuousInquiryPanel.this.type = ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Range;
                }
            });

            final ButtonGroup group = new ButtonGroup();
            group.add(radio1);
            group.add(radio2);
            group.add(radio3);
            group.add(radio4);

            this.type = conditioningPanel.getType();

            this.ntileCombo = new JComboBox();
            this.ntileIndexCombo = new JComboBox();

            final int ntile = conditioningPanel.getNtile();
            final int ntileIndex = conditioningPanel.getNtileIndex();

            for (int n = 2; n <= 10; n++) {
                this.ntileCombo.addItem(ScatterPlotView.tiles[n - 1]);
                this.ntileMap.put(ScatterPlotView.tiles[n - 1], n);
            }

            for (int n = 1; n <= ntile; n++) {
                this.ntileIndexCombo.addItem(n);
            }

            this.ntileCombo.setSelectedItem(ScatterPlotView.tiles[ntile - 1]);
            this.ntileIndexCombo.setSelectedItem(ntileIndex);

            this.ntileCombo.addItemListener(new ItemListener() {
                public void itemStateChanged(final ItemEvent e) {
                    final String item = (String) e.getItem();
                    final int ntileIndex = ContinuousInquiryPanel.this.ntileMap.get(item);

                    for (int i = ContinuousInquiryPanel.this.ntileIndexCombo.getItemCount() - 1; i >= 0; i--) {
                        ContinuousInquiryPanel.this.ntileIndexCombo.removeItemAt(i);
                    }

                    for (int n = 1; n <= ntileIndex; n++) {
                        ContinuousInquiryPanel.this.ntileIndexCombo.addItem(n);
                    }

                    final double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(ContinuousInquiryPanel.this.data, getNtile());
                    final double breakpoint1 = breakpoints[getNtileIndex() - 1];
                    final double breakpoint2 = breakpoints[getNtileIndex()];
                    ContinuousInquiryPanel.this.field1.setValue(breakpoint1);
                    ContinuousInquiryPanel.this.field2.setValue(breakpoint2);
                }
            });

            this.ntileIndexCombo.addItemListener(new ItemListener() {
                public void itemStateChanged(final ItemEvent e) {
                    final int ntile = getNtile();
                    final int ntileIndex = getNtileIndex();
                    final double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(ContinuousInquiryPanel.this.data, ntile);
                    final double breakpoint1 = breakpoints[ntileIndex - 1];
                    final double breakpoint2 = breakpoints[ntileIndex];
                    ContinuousInquiryPanel.this.field1.setValue(breakpoint1);
                    ContinuousInquiryPanel.this.field2.setValue(breakpoint2);
                }
            });


            if (this.type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.AboveAverage) {
                radio1.setSelected(true);
                this.field1.setValue(StatUtils.mean(this.data));
                this.field2.setValue(StatUtils.max(this.data));
            } else if (this.type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.BelowAverage) {
                radio2.setSelected(true);
                this.field1.setValue(StatUtils.min(this.data));
                this.field2.setValue(StatUtils.mean(this.data));
            } else if (this.type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Ntile) {
                radio3.setSelected(true);
                final double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(this.data, getNtile());
                final double breakpoint1 = breakpoints[getNtileIndex() - 1];
                final double breakpoint2 = breakpoints[getNtileIndex()];
                this.field1.setValue(breakpoint1);
                this.field2.setValue(breakpoint2);
            } else if (this.type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Range) {
                radio4.setSelected(true);
            }

            final Box main = Box.createVerticalBox();

            final Box b0 = Box.createHorizontalBox();
            b0.add(new JLabel("Condition on " + variable.getName() + " as:"));
            b0.add(Box.createHorizontalGlue());
            main.add(b0);
            main.add(Box.createVerticalStrut(10));

            final Box b1 = Box.createHorizontalBox();
            b1.add(radio1);
            b1.add(new JLabel("Above average"));
            b1.add(Box.createHorizontalGlue());
            main.add(b1);

            final Box b2 = Box.createHorizontalBox();
            b2.add(radio2);
            b2.add(new JLabel("Below average"));
            b2.add(Box.createHorizontalGlue());
            main.add(b2);

            final Box b3 = Box.createHorizontalBox();
            b3.add(radio3);
            b3.add(new JLabel("In "));
            b3.add(this.ntileCombo);
            b3.add(this.ntileIndexCombo);
            b3.add(Box.createHorizontalGlue());
            main.add(b3);

            final Box b4 = Box.createHorizontalBox();
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

        public ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type getType() {
            return this.type;
        }

        public double getLow() {
            return this.field1.getValue();
        }

        public double getHigh() {
            return this.field2.getValue();
        }

        public int getNtile() {
            final String selectedItem = (String) this.ntileCombo.getSelectedItem();
            return this.ntileMap.get(selectedItem);
        }

        public int getNtileIndex() {
            final Object selectedItem = this.ntileIndexCombo.getSelectedItem();
            return selectedItem == null ? 1 : (Integer) selectedItem;
        }

        /**
         * @return an array of breakpoints that divides the data into equal sized buckets,
         * including the min and max.
         */
        public static double[] getNtileBreakpoints(final double[] data, final int ntiles) {
            final double[] _data = new double[data.length];
            System.arraycopy(data, 0, _data, 0, _data.length);

            // first sort the _data.
            Arrays.sort(_data);
            final java.util.List<Chunk> chunks = new ArrayList<>(_data.length);
            int startChunkCount = 0;
            double lastValue = _data[0];

            for (int i = 0; i < _data.length; i++) {
                final double value = _data[i];
                if (value != lastValue) {
                    chunks.add(new Chunk(startChunkCount, i, value));
                    startChunkCount = i;
                }
                lastValue = value;
            }

            chunks.add(new Chunk(startChunkCount, _data.length, _data[_data.length - 1]));

            // now find the breakpoints.
            final double interval = _data.length / ntiles;
            final double[] breakpoints = new double[ntiles + 1];
            breakpoints[0] = StatUtils.min(_data);

            int current = 1;
            int freq = 0;

            for (final Chunk chunk : chunks) {
                final int valuesInChunk = chunk.getNumberOfValuesInChunk();
                final int halfChunk = (int) (valuesInChunk * .5);

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
         * Represents a chunk of data in a sorted array of data.  If low == high then
         * then the chunk only contains one member.
         */
        private static class Chunk {

            private final int valuesInChunk;
            private final double value;

            public Chunk(final int low, final int high, final double value) {
                this.valuesInChunk = (high - low);
                this.value = value;
            }

            public int getNumberOfValuesInChunk() {
                return this.valuesInChunk;
            }

        }
    }

    /**
     * This view draws the ScatterPlot using the information from the ScatterPlot
     * class. It draws the ScatterPlot line, axes, labels and the statistical values.
     *
     * @author Adrian Tang
     */
    private static class ScatterPlotChart extends JPanel {
        private ScatterPlot scatterPlot;

        private final NumberFormat nf;

        /**
         * Constructor.
         */
        public ScatterPlotChart(final ScatterPlot ScatterPlot) {
            this.scatterPlot = ScatterPlot;

            setPreferredSize(new Dimension(600, 600));

            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

            this.nf = NumberFormat.getNumberInstance();
            this.nf.setMinimumFractionDigits(2);
            this.nf.setMaximumFractionDigits(2);
        }

        public void setScatterPlot(final ScatterPlot ScatterPlot) {
            this.scatterPlot = ScatterPlot;
        }

        /**
         * Renders the view.
         */
        public void paintComponent(final Graphics graphics) {
            final double xmin = this.scatterPlot.getXmin();
            final double xmax = this.scatterPlot.getXmax();
            final double ymin = this.scatterPlot.getYmin();
            final double ymax = this.scatterPlot.getYmax();


            final Graphics2D g = (Graphics2D) graphics;

            g.setColor(Color.white);
            g.setFont(new Font("Dialog", Font.PLAIN, 11));
            g.fillRect(0, 0, getPreferredSize().width, getPreferredSize().height);

            final int chartWidth = getPreferredSize().width * 8 / 10;
            final int chartHeight = getPreferredSize().height * 7 / 10;

            final int xStringMin = 10;
            final int xMin = 60;
            final int xMax = chartWidth - 10;
            final int xRange = xMax - xMin;
            final int yMin = 35;
            final int yMax = chartHeight - 18;
            final int yRange = yMax - yMin;

            /* draws axis lines */
            g.setStroke(new BasicStroke());
            g.setPaint(Color.black);
            g.drawLine(xMin, yMax, xMax, yMax);
            g.drawLine(xMin, yMin, xMin, yMax);

            /* draws the labels for the corresponding experiment and sample names */
            g.setFont(g.getFont().deriveFont(11f));

            /* draws the labels for the corresponding experiment and sample names */
            final String name = this.scatterPlot.getDataSet().getName();
            if (name != null) {
                g.setFont(g.getFont().deriveFont(11f));
                g.drawString(name, 5, 10);
            }

            /* draws axis labels and scale */
            g.drawString(this.nf.format(ymax), 2 + xStringMin, yMin + 7);
            g.drawString(this.nf.format(ymin), 2 + xStringMin, yMax);
            g.drawString(this.nf.format(xmax), xMax - 20, yMax + 14);
            g.drawString(this.nf.format(xmin), 20 + 30, yMax + 14);
            g.drawString(this.scatterPlot.getXvar(), xMin + (xRange / 2) - 10, yMax + 14);
            g.translate(xMin - 7, yMin + (yRange / 2) + 10);
            g.rotate(-Math.PI / 2.0);
            g.drawString(this.scatterPlot.getYvar(), xStringMin, 0);
            g.rotate(Math.PI / 2.0);
            g.translate(-(xMin - 7), -(yMin + (yRange / 2) + 10));

            /* draws ScatterPlot of the values */
            final Vector<Point2D.Double> pts = this.scatterPlot.getSievedValues();
            final double _xRange = xmax - xmin;
            final double _yRange = ymax - ymin;
            int x, y;

            g.setColor(Color.red);
            for (final Point2D.Double _pt : pts) {
                x = (int) (((_pt.getX() - xmin) / _xRange) * xRange + xMin);
                y = (int) (((ymax - _pt.getY()) / _yRange) * yRange + yMin);
                g.fillOval(x - 2, y - 2, 5, 5);
            }

            /* draws best-fit line */
            if (this.scatterPlot.isIncludeLine()) {
                final double a = this.scatterPlot.getRegressionCoeff();
                final double b = this.scatterPlot.getRegressionIntercept();

                double x1, y1 = 0;

                for (x1 = xmin; x1 <= xmax; x1 += 0.01) {
                    y1 = a * x1 + b;
                    if (y1 >= ymin && y1 <= ymax) {
                        break;
                    }
                }

                double x2, y2 = 0;

                for (x2 = xmax; x2 >= xmin; x2 -= 0.01) {
                    y2 = a * x2 + b;
                    if (y2 >= ymin && y2 <= ymax) {
                        break;
                    }
                }

                final int xa = (int) (((x1 - xmin) / _xRange) * xRange + xMin);
                final int ya = (int) (((ymax - y1) / _yRange) * yRange + yMin);

                final int xb = (int) (((x2 - xmin) / _xRange) * xRange + xMin);
                final int yb = (int) (((ymax - y2) / _yRange) * yRange + yMin);

                g.setColor(Color.BLUE);
                g.drawLine(xa, ya, xb, yb);
            }

            /* draws statistical values */
            if (this.scatterPlot.isIncludeLine()) {
                g.setColor(Color.black);
                this.nf.setMinimumFractionDigits(3);
                this.nf.setMaximumFractionDigits(3);
                final double r = this.scatterPlot.getCorrelationCoeff();
                final double p = this.scatterPlot.getCorrelationPValue();
                g.drawString("correlation coef = " + this.nf.format(r) + "  (p=" + this.nf.format(p) + ")", 100, 21);
            }
        }

        /**
         * @return the minimum dimension of the ScatterPlot.
         */
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        /**
         * @return the maximum dimension of the ScatterPlot.
         */
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }
    }
}



