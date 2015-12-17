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
import java.util.*;
import java.util.List;

/**
 * This is the wizard which allows the user to select the x and y-axis variables
 * to chart the ScatterPlot.
 *
 * @author Adrian Tang
 * @author Joseph Ramsey
 */
public class ScatterPlotView extends JPanel {
    private final DataSet dataSet;
    private ScatterPlot scatterPlot;
    private ScatterPlotChart scatterPlotChart;
    private String x;
    private String y;
    private static String[] tiles = new String[]{"1-tile", "2-tile", "tertile", "quartile", "quintile", "sextile",
            "septile", "octile", "nontile", "decile"};

    public ScatterPlotView(DataSet dataSet) {

        // This is annoying. jdramsey 5/14/2014
//        if (!dataSet.isContinuous()) throw new IllegalArgumentException("Data set not continuous.");
        if (!(dataSet.getNumColumns() >= 2)) throw new IllegalArgumentException("Need at least two columns.");

        this.dataSet = dataSet;

        this.x = dataSet.getVariable(0).getName();
        this.y = dataSet.getVariable(1).getName();

        setLayout(new BorderLayout());
        ScatterPlot ScatterPlot = new ScatterPlot(this.dataSet, false, x, y);
        ScatterPlotChart ScatterPlotChart = new ScatterPlotChart(ScatterPlot);
        this.scatterPlot = ScatterPlot;
        this.scatterPlotChart = ScatterPlotChart;

        add(ScatterPlotChart, BorderLayout.CENTER);
        add(new ScatterPlotController(this), BorderLayout.EAST);

        setPreferredSize(new Dimension(750, 450));
    }

    public void setX(String x) {
        this.x = x;
    }

    private void setY(String y) {
        this.y = y;
    }

    public ScatterPlot getScatterPlot() {
        return scatterPlot;
    }

    public static class ScatterPlotController extends JPanel {
        private ScatterPlot scatterPlot;
        private JComboBox xSelector;
        private JComboBox ySelector;
        private JComboBox newConditioningVariableSelector;
        private JButton newConditioningVariableButton;
        private JButton removeConditioningVariableButton;
        private java.util.List<ConditioningPanel> conditioningPanels = new ArrayList<ConditioningPanel>();
        private JCheckBox includeLineCheckbox;

        // To provide some memory of previous settings for the inquiry dialogs.
        private Map<Node, ConditioningPanel> conditioningPanelMap = new HashMap<Node, ConditioningPanel>();

        /**
         * Constructs the editor panel given the initial ScatterPlot and the dataset.
         */
        public ScatterPlotController(final ScatterPlotView ScatterPlotView) {
            this.setLayout(new BorderLayout());
            this.scatterPlot = ScatterPlotView.getScatterPlot();
            Node xNode = getXNode();
            Node yNode = getYNode();
            this.xSelector = new JComboBox();
            this.ySelector = new JComboBox();
            ListCellRenderer renderer = new VariableBoxRenderer();
            this.xSelector.setRenderer(renderer);
            this.ySelector.setRenderer(renderer);

            includeLineCheckbox = new JCheckBox("Show Regression Line");
            List<Node> variables = scatterPlot.getDataSet().getVariables();

            Collections.sort(variables);

            for (Node node : variables) {
                this.xSelector.addItem(node);

                if (node == xNode) {
                    this.xSelector.setSelectedItem(node);
                }
            }

            for (Node node : variables) {
                this.ySelector.addItem(node);

                if (node == yNode) {
                    this.ySelector.setSelectedItem(node);
                }
            }

            this.xSelector.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    String node = ((Node) xSelector.getSelectedItem()).getName();
                    ScatterPlotView.setX(node);
                    refreshChart(ScatterPlotView);
                }
            });

            this.ySelector.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    String node = ((Node) ySelector.getSelectedItem()).getName();
                    ScatterPlotView.setY(node);
                    refreshChart(ScatterPlotView);
                }
            });

            includeLineCheckbox.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    refreshChart(ScatterPlotView);
                }
            });

            this.newConditioningVariableSelector = new JComboBox();

            for (Node node : variables) {
                this.newConditioningVariableSelector.addItem(node);
            }

            this.newConditioningVariableButton = new JButton("Add");

            this.newConditioningVariableButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println("New conditioning variable action performed");
                    Node selected = (Node) newConditioningVariableSelector.getSelectedItem();

                    for (ConditioningPanel panel : conditioningPanels) {
                        if (selected == panel.getVariable()) {
                            JOptionPane.showMessageDialog(ScatterPlotController.this,
                                    "There is already a conditioning variable called " + selected + ".");
                            return;
                        }
                    }

                    if (selected instanceof ContinuousVariable) {
                        final ContinuousVariable _var = (ContinuousVariable) selected;

                        ContinuousConditioningPanel panel1 = (ContinuousConditioningPanel) conditioningPanelMap.get(_var);

                        if (panel1 == null) {
                            panel1 = ContinuousConditioningPanel.getDefault(_var, scatterPlot);
                        }

                        ContinuousInquiryPanel panel2 = new ContinuousInquiryPanel(_var, scatterPlot, panel1);

                        JOptionPane.showOptionDialog(ScatterPlotController.this, panel2,
                                null, JOptionPane.DEFAULT_OPTION,
                                JOptionPane.PLAIN_MESSAGE, null, null, null);

                        ContinuousConditioningPanel.Type type = panel2.getType();
                        double low = panel2.getLow();
                        double high = panel2.getHigh();
                        int ntile = panel2.getNtile();
                        int ntileIndex = panel2.getNtileIndex();

                        ContinuousConditioningPanel panel3 = new ContinuousConditioningPanel(_var, low, high, ntile, ntileIndex, type);

                        conditioningPanels.add(panel3);
                        conditioningPanelMap.put(_var, panel3);
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
                public void actionPerformed(ActionEvent e) {
                    for (ConditioningPanel panel : new ArrayList<ConditioningPanel>(conditioningPanels)) {
                        if (panel.isSelected()) {
                            panel.setSelected(false);
                            conditioningPanels.remove(panel);
                            scatterPlot.removeConditioningVariable(panel.getVariable().toString());
                            refreshChart(ScatterPlotView);
                        }
                    }

                    buildEditArea();
                    resetConditioning();
                }
            });

            // build the gui.
            restrictSize(this.xSelector);
            restrictSize(this.newConditioningVariableSelector);
            restrictSize(this.newConditioningVariableButton);
            restrictSize(this.removeConditioningVariableButton);

            buildEditArea();
        }

        private void refreshChart(ScatterPlotView ScatterPlotView) {
            ScatterPlot ScatterPlot = new ScatterPlot(ScatterPlotView.scatterPlot.getDataSet(),
                    includeLineCheckbox.isSelected(),
                    ScatterPlotView.x, ScatterPlotView.y);
            ScatterPlot.removeConditioningVariables();
            for (ConditioningPanel panel : conditioningPanels) {
                if (panel instanceof ContinuousConditioningPanel) {
                    Node node = panel.getVariable();
                    double low = ((ContinuousConditioningPanel) panel).getLow();
                    double high = ((ContinuousConditioningPanel) panel).getHigh();
                    ScatterPlot.addConditioningVariable(node.getName(), low, high);
                }
            }

            ScatterPlotView.scatterPlotChart.setScatterPlot(ScatterPlot);
            ScatterPlotView.scatterPlotChart.repaint();
        }

        private void resetConditioning() {

            // Need to set the conditions on the ScatterPlot and also update the list of conditions in the view.
            scatterPlot.removeConditioningVariables();

            for (ConditioningPanel panel : conditioningPanels) {
                if (panel instanceof ContinuousConditioningPanel) {
                    Node node = panel.getVariable();
                    double low = ((ContinuousConditioningPanel) panel).getLow();
                    double high = ((ContinuousConditioningPanel) panel).getHigh();
                    scatterPlot.addConditioningVariable(node.getName(), low, high);
                }
            }
        }

        private void buildEditArea() {
            restrictSize(xSelector);
            restrictSize(ySelector);

            Box main = Box.createVerticalBox();
            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("ScatterPlot for: "));
            b1.add(Box.createHorizontalGlue());
            main.add(b1);

            Box b1a = Box.createHorizontalBox();
            b1a.add(new JLabel("X-axis = "));
            b1a.add(Box.createHorizontalGlue());
            b1a.add(xSelector);
            main.add(b1a);

            Box b1b = Box.createHorizontalBox();
            b1b.add(new JLabel("Y-axis = "));
            b1b.add(Box.createHorizontalGlue());
            b1b.add(ySelector);
            main.add(b1b);

            Box b1c = Box.createHorizontalBox();
            b1c.add(includeLineCheckbox);
            main.add(b1c);

            main.add(Box.createVerticalStrut(20));

            Box b3 = Box.createHorizontalBox();
            JLabel l1 = new JLabel("Conditioning on: ");
            l1.setFont(l1.getFont().deriveFont(Font.ITALIC));
            b3.add(l1);
            b3.add(Box.createHorizontalGlue());
            main.add(b3);

            main.add(Box.createVerticalStrut(20));

            for (ConditioningPanel panel : conditioningPanels) {
                main.add(panel.getBox());
                main.add(Box.createVerticalStrut(10));
            }

            main.add(Box.createVerticalStrut(10));
            main.add(Box.createVerticalGlue());
            main.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            Box b6 = Box.createHorizontalBox();
            b6.add(newConditioningVariableSelector);
            b6.add(newConditioningVariableButton);
            b6.add(Box.createHorizontalGlue());
            main.add(b6);

            Box b7 = Box.createHorizontalBox();
            b7.add(removeConditioningVariableButton);
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

        private static void restrictSize(JComponent component) {
            component.setMaximumSize(component.getPreferredSize());
        }

        //========================== Inner classes ===========================//


        private static class VariableBoxRenderer extends DefaultListCellRenderer {

            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Node node = (Node) value;
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

        private static class ContinuousConditioningPanel implements ConditioningPanel {

            public int getNtile() {
                return ntile;
            }

            public int getNtileIndex() {
                return ntileIndex;
            }

            public enum Type {Range, Ntile, AboveAverage, BelowAverage}

            private ContinuousVariable variable;
            private Box box;

            private Type type;
            private double low;
            private double high;
            private int ntile;
            private int ntileIndex;

            // Mark selected if this panel is to be removed.
            private JCheckBox checkBox;

            public ContinuousConditioningPanel(ContinuousVariable variable, double low, double high, int ntile, int ntileIndex, Type type) {
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
                b4.add(Box.createRigidArea(new Dimension(10, 0)));

                if (type == Type.Range) {
                    b4.add(new JLabel(variable + " = (" + nf.format(low) + ", " + nf.format(high) + ")"));
                } else if (type == Type.AboveAverage) {
                    b4.add(new JLabel(variable + " = Above Average"));
                } else if (type == Type.BelowAverage) {
                    b4.add(new JLabel(variable + " = Below Average"));
                } else if (type == Type.Ntile) {
                    b4.add(new JLabel(variable + " = " + tiles[ntile - 1] + " " + ntileIndex));
                }

                b4.add(Box.createHorizontalGlue());
                this.checkBox = new JCheckBox();
                restrictSize(checkBox);
                b4.add(checkBox);
                this.box = b4;

            }

            public static ContinuousConditioningPanel getDefault(ContinuousVariable variable, ScatterPlot ScatterPlot) {
                double[] data = ScatterPlot.getContinuousData(variable.getName());
                double max = StatUtils.max(data);
                double avg = StatUtils.mean(data);
                return new ContinuousConditioningPanel(variable, avg, max, 2, 1, Type.AboveAverage);
            }

            public ContinuousVariable getVariable() {
                return variable;
            }

            public Type getType() {
                return type;
            }

            public Box getBox() {
                return box;
            }

            public boolean isSelected() {
                return checkBox.isSelected();
            }

            public void setSelected(boolean b) {
                checkBox.setSelected(false);
            }

            public double getLow() {
                return low;
            }

            public double getHigh() {
                return high;
            }
        }
    }

    private static class ContinuousInquiryPanel extends JPanel {
        private JComboBox ntileCombo;
        private JComboBox ntileIndexCombo;
        private DoubleTextField field1;
        private DoubleTextField field2;
        private ScatterPlotController.ContinuousConditioningPanel.Type type;
        private final Map<String, Integer> ntileMap = new HashMap<String, Integer>();
        private double[] data;

        /**
         * @param variable          This is the variable being conditioned on. Must be continuous and one of the variables
         *                          in the ScatterPlot.
         * @param ScatterPlot       We need this to get the column of data for the variable.
         * @param conditioningPanel We will try to get some initialization information out of the conditioning
         *                          panel. This must be for the same variable as variable.
         */
        public ContinuousInquiryPanel(final ContinuousVariable variable, ScatterPlot ScatterPlot,
                                      ScatterPlotController.ContinuousConditioningPanel conditioningPanel) {
            data = ScatterPlot.getContinuousData(variable.getName());

            if (conditioningPanel == null)
                throw new NullPointerException();
            if (!(variable == conditioningPanel.getVariable()))
                throw new IllegalArgumentException("Wrong variable for conditioning panel.");

            // There is some order dependence in the below; careful rearranging things.
            NumberFormat nf = new DecimalFormat("0.00");

            field1 = new DoubleTextField(conditioningPanel.getLow(), 4, nf);
            field2 = new DoubleTextField(conditioningPanel.getHigh(), 4, nf);

            JRadioButton radio1 = new JRadioButton();
            JRadioButton radio2 = new JRadioButton();
            JRadioButton radio3 = new JRadioButton();
            JRadioButton radio4 = new JRadioButton();

            radio1.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    type = ScatterPlotController.ContinuousConditioningPanel.Type.AboveAverage;
                    field1.setValue(StatUtils.mean(data));
                    field2.setValue(StatUtils.max(data));
                }
            });

            radio2.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    type = ScatterPlotController.ContinuousConditioningPanel.Type.BelowAverage;
                    field1.setValue(StatUtils.min(data));
                    field2.setValue(StatUtils.mean(data));
                }
            });

            radio3.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    type = ScatterPlotController.ContinuousConditioningPanel.Type.Ntile;
                    double[] breakpoints = getNtileBreakpoints(data, getNtile());
                    double breakpoint1 = breakpoints[getNtileIndex() - 1];
                    double breakpoint2 = breakpoints[getNtileIndex()];
                    field1.setValue(breakpoint1);
                    field2.setValue(breakpoint2);
                }
            });

            radio4.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    type = ScatterPlotController.ContinuousConditioningPanel.Type.Range;
                }
            });

            ButtonGroup group = new ButtonGroup();
            group.add(radio1);
            group.add(radio2);
            group.add(radio3);
            group.add(radio4);

            type = conditioningPanel.getType();

            ntileCombo = new JComboBox();
            ntileIndexCombo = new JComboBox();

            int ntile = conditioningPanel.getNtile();
            int ntileIndex = conditioningPanel.getNtileIndex();

            for (int n = 2; n <= 10; n++) {
                ntileCombo.addItem(tiles[n - 1]);
                ntileMap.put(tiles[n - 1], n);
            }

            for (int n = 1; n <= ntile; n++) {
                ntileIndexCombo.addItem(n);
            }

            ntileCombo.setSelectedItem(tiles[ntile - 1]);
            ntileIndexCombo.setSelectedItem(ntileIndex);

            ntileCombo.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    String item = (String) e.getItem();
                    int ntileIndex = ntileMap.get(item);

                    for (int i = ntileIndexCombo.getItemCount() - 1; i >= 0; i--) {
                        ntileIndexCombo.removeItemAt(i);
                    }

                    for (int n = 1; n <= ntileIndex; n++) {
                        ntileIndexCombo.addItem(n);
                    }

                    double[] breakpoints = getNtileBreakpoints(data, getNtile());
                    double breakpoint1 = breakpoints[getNtileIndex() - 1];
                    double breakpoint2 = breakpoints[getNtileIndex()];
                    field1.setValue(breakpoint1);
                    field2.setValue(breakpoint2);
                }
            });

            ntileIndexCombo.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    int ntile = getNtile();
                    int ntileIndex = getNtileIndex();
                    double[] breakpoints = getNtileBreakpoints(data, ntile);
                    double breakpoint1 = breakpoints[ntileIndex - 1];
                    double breakpoint2 = breakpoints[ntileIndex];
                    field1.setValue(breakpoint1);
                    field2.setValue(breakpoint2);
                }
            });


            if (type == ScatterPlotController.ContinuousConditioningPanel.Type.AboveAverage) {
                radio1.setSelected(true);
                field1.setValue(StatUtils.mean(data));
                field2.setValue(StatUtils.max(data));
            } else if (type == ScatterPlotController.ContinuousConditioningPanel.Type.BelowAverage) {
                radio2.setSelected(true);
                field1.setValue(StatUtils.min(data));
                field2.setValue(StatUtils.mean(data));
            } else if (type == ScatterPlotController.ContinuousConditioningPanel.Type.Ntile) {
                radio3.setSelected(true);
                double[] breakpoints = getNtileBreakpoints(data, getNtile());
                double breakpoint1 = breakpoints[getNtileIndex() - 1];
                double breakpoint2 = breakpoints[getNtileIndex()];
                field1.setValue(breakpoint1);
                field2.setValue(breakpoint2);
            } else if (type == ScatterPlotController.ContinuousConditioningPanel.Type.Range) {
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
            b3.add(ntileCombo);
            b3.add(ntileIndexCombo);
            b3.add(Box.createHorizontalGlue());
            main.add(b3);

            Box b4 = Box.createHorizontalBox();
            b4.add(radio4);
            b4.add(new JLabel("In ("));
            b4.add(field1);
            b4.add(new JLabel(", "));
            b4.add(field2);
            b4.add(new JLabel(")"));
            b4.add(Box.createHorizontalGlue());
            main.add(b4);

            add(main, BorderLayout.CENTER);
        }

        public ScatterPlotController.ContinuousConditioningPanel.Type getType() {
            return type;
        }

        public double getLow() {
            return field1.getValue();
        }

        public double getHigh() {
            return field2.getValue();
        }

        public int getNtile() {
            String selectedItem = (String) ntileCombo.getSelectedItem();
            return ntileMap.get(selectedItem);
        }

        public int getNtileIndex() {
            Object selectedItem = ntileIndexCombo.getSelectedItem();
            return selectedItem == null ? 1 : (Integer) selectedItem;
        }

        /**
         * @return an array of breakpoints that divides the data into equal sized buckets,
         * including the min and max.
         */
        public static double[] getNtileBreakpoints(double[] data, int ntiles) {
            double[] _data = new double[data.length];
            System.arraycopy(data, 0, _data, 0, _data.length);

            // first sort the _data.
            Arrays.sort(_data);
            java.util.List<Chunk> chunks = new ArrayList<Chunk>(_data.length);
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
            double interval = _data.length / ntiles;
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
         * Represents a chunk of data in a sorted array of data.  If low == high then
         * then the chunk only contains one member.
         */
        private static class Chunk {

            private int valuesInChunk;
            private double value;

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
     * This view draws the ScatterPlot using the information from the ScatterPlot
     * class. It draws the ScatterPlot line, axes, labels and the statistical values.
     *
     * @author Adrian Tang
     */
    private static class ScatterPlotChart extends JPanel {
        private ScatterPlot scatterPlot;

        private NumberFormat nf;

        /**
         * Constructor.
         */
        public ScatterPlotChart(ScatterPlot ScatterPlot) {
            this.scatterPlot = ScatterPlot;

            setPreferredSize(new Dimension(600, 600));

            setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

            nf = NumberFormat.getNumberInstance();
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
        }

        public void setScatterPlot(ScatterPlot ScatterPlot) {
            this.scatterPlot = ScatterPlot;
        }

        /**
         * Renders the view.
         */
        public void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics;

            g.setColor(Color.white);
            g.setFont(new Font("Dialog", Font.PLAIN, 11));
            g.fillRect(0, 0, getPreferredSize().width, getPreferredSize().height);

            int chartWidth = getPreferredSize().width * 8 / 10;
            int chartHeight = getPreferredSize().height * 7 / 10;

            int xStringMin = 10;
            int xMin = 60;
            int xMax = chartWidth - 10;
            int xRange = xMax - xMin;
            int yMin = 35;
            int yMax = chartHeight - 18;
            int yRange = yMax - yMin;

            /* draws axis lines */
            g.setStroke(new BasicStroke());
            g.setPaint(Color.black);
            g.drawLine(xMin, yMax, xMax, yMax);
            g.drawLine(xMin, yMin, xMin, yMax);

            /* draws the labels for the corresponding experiment and sample names */
            g.setFont(g.getFont().deriveFont(11f));

            /* draws the labels for the corresponding experiment and sample names */
            String name = scatterPlot.getDataSet().getName();
            if (name != null) {
                g.setFont(g.getFont().deriveFont(11f));
                g.drawString(name, 5, 10);
            }

            /* draws axis labels and scale */
            g.drawString(nf.format(scatterPlot.getYmax()), 2 + xStringMin, yMin + 7);
            g.drawString(nf.format(scatterPlot.getYmin()), 2 + xStringMin, yMax);
            g.drawString(nf.format(scatterPlot.getXmax()), xMax - 20, yMax + 14);
            g.drawString(nf.format(scatterPlot.getXmin()), 20 + 30, yMax + 14);
            g.drawString(scatterPlot.getXvar(), xMin + (xRange / 2) - 10, yMax + 14);
            g.translate(xMin - 7, yMin + (yRange / 2) + 10);
            g.rotate(-Math.PI / 2.0);
            g.drawString(scatterPlot.getYvar(), xStringMin, 0);
            g.rotate(Math.PI / 2.0);
            g.translate(-(xMin - 7), -(yMin + (yRange / 2) + 10));

            /* draws ScatterPlot of the values */
            Vector<Point2D.Double> pts = scatterPlot.getSievedValues();
            double _xRange = scatterPlot.getXmax() - scatterPlot.getXmin();
            double _yRange = scatterPlot.getYmax() - scatterPlot.getYmin();
            int x, y;

            g.setColor(Color.red);
            for (
                    Point2D.Double _pt
                    : pts)

            {
                x = (int) (((_pt.getX() - scatterPlot.getXmin()) / _xRange) * xRange + xMin);
                y = (int) (((scatterPlot.getYmax() - _pt.getY()) / _yRange) * yRange + yMin);
                g.fillOval(x - 2, y - 2, 5, 5);
            }

            /* draws best-fit line */
            if (scatterPlot.isIncludeLine())

            {
                double a = scatterPlot.getRegressionCoeff();
                double b = scatterPlot.getRegressionIntercept();

                double xmin = scatterPlot.getXmin();
                double xmax = scatterPlot.getXmax();
                double ymin = scatterPlot.getYmin();
                double ymax = scatterPlot.getYmax();

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

                int xa = (int) (((x1 - xmin) / _xRange) * xRange + xMin);
                int ya = (int) (((ymax - y1) / _yRange) * yRange + yMin);

                int xb = (int) (((x2 - xmin) / _xRange) * xRange + xMin);
                int yb = (int) (((ymax - y2) / _yRange) * yRange + yMin);

                g.setColor(Color.BLUE);
                g.drawLine(xa, ya, xb, yb);
            }

            /* draws statistical values */
            if (scatterPlot.isIncludeLine())

            {
                g.setColor(Color.black);
                nf.setMinimumFractionDigits(3);
                nf.setMaximumFractionDigits(3);
                double r = scatterPlot.getCorrelationCoeff();
                double p = scatterPlot.getCorrelationPValue();
                g.drawString("correlation coef = " + nf.format(r) + "  (p=" + nf.format(p) + ")", 100, 21);
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



