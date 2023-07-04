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


import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Node;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

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

        this.rowSelector.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);
            }
        });

        this.colSelector.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);
            }
        });

        constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);

        JMenuBar menuBar = new JMenuBar();
        JMenu settings = new JMenu("Settings");
        menuBar.add(settings);
        JMenuItem numBins = new JMenu("Num Bins for Histograms");
        ButtonGroup group = new ButtonGroup();

        for (int i = 2; i <= 20; i++) {
            int _i = i;
            JMenuItem comp = new JCheckBoxMenuItem(i + "");
            numBins.add(comp);
            group.add(comp);
            if (i == getNumBins()) comp.setSelected(true);

            comp.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    setNumBins(_i);
                    constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);
                }
            });
        }

        settings.add(numBins);

        JMenuItem addRegressionLines = new JCheckBoxMenuItem("Add Regression Lines to Histograms");
        addRegressionLines.setSelected(false);
        settings.add(addRegressionLines);

        addRegressionLines.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setAddRegressionLines(!isAddRegressionLines());
                constructPlotMatrix(charts, dataSet, nodes, rowSelector, colSelector);
            }
        });

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

                    if (!(nodes.get(leftIndex) instanceof DiscreteVariable)) {
                        histogram.setNumBins(numBins);
                    }

                    HistogramPanel panel = new HistogramPanel(histogram,
                            leftIndices.length == 1 && topIndices.length == 1);

                    addPenelListener(charts, dataSet, nodes, leftIndex, topIndex, panel);

                    charts.add(panel);
                } else {
                    ScatterPlot scatterPlot = new ScatterPlot(dataSet, addRegressionLines, nodes.get(topIndex).getName(),
                            nodes.get(leftIndex).getName());
                    ScatterplotPanel panel = new ScatterplotPanel(scatterPlot
                    );
                    panel.setDrawAxes(leftIndices.length == 1 && topIndices.length == 1);

                    addPenelListener(charts, dataSet, nodes, leftIndex, topIndex, panel);

                    charts.add(panel);
                }
            }
        }

        revalidate();
        repaint();
    }

    private void addPenelListener(JPanel charts, DataSet dataSet, List<Node> nodes, int leftIndex, int topIndex, JPanel panel) {
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

//    public static class ScatterPlotController extends JPanel {
//        private final ScatterPlot scatterPlot;
//        private final JComboBox newConditioningVariableSelector;
//        private final JButton newConditioningVariableButton;
//        private final JButton removeConditioningVariableButton;
//        private final java.util.List<ConditioningPanel> conditioningPanels = new ArrayList<>();
//        private final JCheckBox includeLineCheckbox;
//
//        // To provide some memory of previous settings for the inquiry dialogs.
//        private final Map<Node, ConditioningPanel> conditioningPanelMap = new HashMap<>();
//        private final JList<Node> leftSelector;
//        private final JList<Node> topSelector;
//
//        /**
//         * Constructs the editor panel given the initial ScatterPlot and the dataset.
//         */
//        public ScatterPlotController(ScatterPlotView ScatterPlotView, DataSet dataSet, JList<Node> leftSelector, JList<Node> topSelector) {
//            this.leftSelector = leftSelector;
//            this.topSelector = topSelector;
//
//
//            this.setLayout(new BorderLayout());
//            this.scatterPlot = ScatterPlotView.getScatterPlot();
//
//            leftSelector.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
//            topSelector.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
//
//            this.includeLineCheckbox = new JCheckBox("Show Regression Line");
//            List<Node> variables = this.scatterPlot.getDataSet().getVariables();
//
//            Collections.sort(variables);
//
//            leftSelector.addListSelectionListener(new ListSelectionListener() {
//                @Override
//                public void valueChanged(ListSelectionEvent e) {
//                    revalidate();
//                    repaint();
//                }
//            });
//
//            topSelector.addListSelectionListener(new ListSelectionListener() {
//                @Override
//                public void valueChanged(ListSelectionEvent e) {
//                    revalidate();
//                    repaint();
//                }
//            });
//
//            this.includeLineCheckbox.addActionListener(e -> refreshChart(ScatterPlotView));
//
//            this.newConditioningVariableSelector = new JComboBox();
//
//            for (Node node : variables) {
//                this.newConditioningVariableSelector.addItem(node);
//            }
//
//            this.newConditioningVariableButton = new JButton("Add");
//
//            this.newConditioningVariableButton.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    System.out.println("New conditioning variable action performed");
//                    Node selected = (Node) ScatterPlotController.this.newConditioningVariableSelector.getSelectedItem();
//
//                    for (ConditioningPanel panel : ScatterPlotController.this.conditioningPanels) {
//                        if (selected == panel.getVariable()) {
//                            JOptionPane.showMessageDialog(ScatterPlotController.this,
//                                    "There is already a conditioning variable called " + selected + ".");
//                            return;
//                        }
//                    }
//
//                    if (selected instanceof ContinuousVariable) {
//                        ContinuousVariable _var = (ContinuousVariable) selected;
//
//                        ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel panel1 = (ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) ScatterPlotController.this.conditioningPanelMap.get(_var);
//
//                        if (panel1 == null) {
//                            panel1 = ScatterPlotController.ContinuousConditioningPanel.getDefault(_var, ScatterPlotController.this.scatterPlot);
//                        }
//
//                        ContinuousInquiryPanel panel2 = new ContinuousInquiryPanel(_var, ScatterPlotController.this.scatterPlot, panel1);
//
//                        JOptionPane.showOptionDialog(ScatterPlotController.this, panel2,
//                                null, JOptionPane.DEFAULT_OPTION,
//                                JOptionPane.PLAIN_MESSAGE, null, null, null);
//
//                        ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type type = panel2.getType();
//                        double low = panel2.getLow();
//                        double high = panel2.getHigh();
//                        int ntile = panel2.getNtile();
//                        int ntileIndex = panel2.getNtileIndex();
//
//                        ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel panel3 = new ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel(_var, low, high, ntile, ntileIndex, type);
//
//                        ScatterPlotController.this.conditioningPanels.add(panel3);
//                        ScatterPlotController.this.conditioningPanelMap.put(_var, panel3);
//                    } else {
//                        throw new IllegalStateException();
//                    }
//
//                    buildEditArea();
//                    resetConditioning();
//
//                    refreshChart(ScatterPlotView);
//                }
//            });
//
//            this.removeConditioningVariableButton = new JButton("Remove Checked");
//
//            this.removeConditioningVariableButton.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    for (ConditioningPanel panel : new ArrayList<>(ScatterPlotController.this.conditioningPanels)) {
//                        if (panel.isSelected()) {
//                            panel.setSelected(false);
//                            ScatterPlotController.this.conditioningPanels.remove(panel);
//                            ScatterPlotController.this.scatterPlot.removeConditioningVariable(panel.getVariable().toString());
//                            refreshChart(ScatterPlotView);
//                        }
//                    }
//
//                    buildEditArea();
//                    resetConditioning();
//                }
//            });
//
//            // build the gui.
//            ScatterPlotController.restrictSize(new JScrollPane(leftSelector));
//            ScatterPlotController.restrictSize(this.newConditioningVariableSelector);
//            ScatterPlotController.restrictSize(this.newConditioningVariableButton);
//            ScatterPlotController.restrictSize(this.removeConditioningVariableButton);
//
//            buildEditArea();
//        }
//
//        private void refreshChart(ScatterPlotView ScatterPlotView) {
//            ScatterPlot ScatterPlot = new ScatterPlot(ScatterPlotView.scatterPlot.getDataSet(),
//                    this.includeLineCheckbox.isSelected(),
//                    ScatterPlotView.x, ScatterPlotView.y);
//            ScatterPlot.removeConditioningVariables();
//            for (ConditioningPanel panel : this.conditioningPanels) {
//                if (panel instanceof ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) {
//                    Node node = panel.getVariable();
//                    double low = ((ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) panel).getLow();
//                    double high = ((ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) panel).getHigh();
//                    ScatterPlot.addConditioningVariable(node.getName(), low, high);
//                }
//            }
//
//            ScatterPlotView.scatterPlotChart.setScatterPlot(ScatterPlot);
//            ScatterPlotView.scatterPlotChart.repaint();
//        }
//
//        private void resetConditioning() {
//
//            // Need to set the conditions on the ScatterPlot and also update the list of conditions in the view.
//            this.scatterPlot.removeConditioningVariables();
//
//            for (ConditioningPanel panel : this.conditioningPanels) {
//                if (panel instanceof ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) {
//                    Node node = panel.getVariable();
//                    double low = ((ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) panel).getLow();
//                    double high = ((ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel) panel).getHigh();
//                    this.scatterPlot.addConditioningVariable(node.getName(), low, high);
//                }
//            }
//        }
//
//        private void buildEditArea() {
//            ScatterPlotController.restrictSize(leftSelector);
//            ScatterPlotController.restrictSize(topSelector);
//
//            Box main = Box.createVerticalBox();
//            Box b1 = Box.createHorizontalBox();
//            b1.add(new JLabel("ScatterPlot for: "));
//            b1.add(Box.createHorizontalGlue());
//            main.add(b1);
//
//            Box b1a = Box.createHorizontalBox();
//            b1a.add(new JLabel("Left: "));
//            b1a.add(Box.createHorizontalGlue());
//            b1a.add(new JScrollPane(leftSelector));
//            main.add(b1a);
//
//            Box b1b = Box.createHorizontalBox();
//            b1b.add(new JLabel("Top: = "));
//            b1b.add(Box.createHorizontalGlue());
//            b1b.add(new JScrollPane(topSelector));
//            main.add(b1b);
//
//            Box b1c = Box.createHorizontalBox();
//            b1c.add(this.includeLineCheckbox);
//            main.add(b1c);
//
////            main.add(Box.createVerticalStrut(20));
//
//            Box b3 = Box.createHorizontalBox();
//            JLabel l1 = new JLabel("Conditioning on: ");
//            l1.setFont(l1.getFont().deriveFont(Font.ITALIC));
//            b3.add(l1);
//            b3.add(Box.createHorizontalGlue());
//            main.add(b3);
//
////            main.add(Box.createVerticalStrut(20));
//
//            for (ConditioningPanel panel : this.conditioningPanels) {
//                main.add(panel.getBox());
////                main.add(Box.createVerticalStrut(10));
//            }
//
////            main.add(Box.createVerticalStrut(10));
//            main.add(Box.createVerticalGlue());
//            main.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
//
//            Box b6 = Box.createHorizontalBox();
//            b6.add(this.newConditioningVariableSelector);
//            b6.add(this.newConditioningVariableButton);
//            b6.add(Box.createHorizontalGlue());
//            main.add(b6);
//
//            Box b7 = Box.createHorizontalBox();
//            b7.add(this.removeConditioningVariableButton);
//            b7.add(Box.createHorizontalGlue());
//            main.add(b7);
//
//            this.removeAll();
//            this.add(main, BorderLayout.CENTER);
//            revalidate();
//            repaint();
//        }
//
//        //========================== Private Methods ================================//
//
//        private Node getXNode() {
//            return this.scatterPlot.getDataSet().getVariable(this.scatterPlot.getXvar());
//        }
//
//        private Node getYNode() {
//            return this.scatterPlot.getDataSet().getVariable(this.scatterPlot.getYvar());
//        }
//
//        private static void restrictSize(JComponent component) {
//            component.setMaximumSize(component.getPreferredSize());
//        }
//
//        private interface ConditioningPanel {
//            Box getBox();
//
//            // selected for removal.
//            boolean isSelected();
//
//            Node getVariable();
//
//            void setSelected(boolean b);
//        }
//
//        public static class ContinuousConditioningPanel implements ConditioningPanel {
//
//            public int getNtile() {
//                return this.ntile;
//            }
//
//            public int getNtileIndex() {
//                return this.ntileIndex;
//            }
//
//            public enum Type {Range, Ntile, AboveAverage, BelowAverage}
//
//            private final ContinuousVariable variable;
//            private final Box box;
//
//            private final ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type type;
//            private final double low;
//            private final double high;
//            private final int ntile;
//            private final int ntileIndex;
//
//            // Mark selected if this panel is to be removed.
//            private final JCheckBox checkBox;
//
//            public ContinuousConditioningPanel(ContinuousVariable variable, double low, double high, int ntile, int ntileIndex, ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type type) {
//                if (variable == null) throw new NullPointerException();
//                if (low >= high) {
//                    throw new IllegalArgumentException("Low >= high.");
//                }
//                if (ntile < 2 || ntile > 10) {
//                    throw new IllegalArgumentException("Ntile should be in range 2 to 10: " + ntile);
//                }
//
//                this.variable = variable;
//                NumberFormat nf = new DecimalFormat("0.0000");
//
//                this.type = type;
//                this.low = low;
//                this.high = high;
//                this.ntile = ntile;
//                this.ntileIndex = ntileIndex;
//
//                Box b4 = Box.createHorizontalBox();
//                b4.add(Box.createRigidArea(new Dimension(10, 0)));
//
//                if (type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Range) {
//                    b4.add(new JLabel(variable + " = (" + nf.format(low) + ", " + nf.format(high) + ")"));
//                } else if (type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.AboveAverage) {
//                    b4.add(new JLabel(variable + " = Above Average"));
//                } else if (type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.BelowAverage) {
//                    b4.add(new JLabel(variable + " = Below Average"));
//                } else if (type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Ntile) {
//                    b4.add(new JLabel(variable + " = " + ScatterPlotView.tiles[ntile - 1] + " " + ntileIndex));
//                }
//
//                b4.add(Box.createHorizontalGlue());
//                this.checkBox = new JCheckBox();
//                ScatterPlotController.restrictSize(this.checkBox);
//                b4.add(this.checkBox);
//                this.box = b4;
//
//            }
//
//            public static ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel getDefault(ContinuousVariable variable, ScatterPlot ScatterPlot) {
//                double[] data = ScatterPlot.getContinuousData(variable.getName());
//                double max = StatUtils.max(data);
//                double avg = StatUtils.mean(data);
//                return new ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel(variable, avg, max, 2, 1, ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.AboveAverage);
//            }
//
//            public ContinuousVariable getVariable() {
//                return this.variable;
//            }
//
//            public ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type getType() {
//                return this.type;
//            }
//
//            public Box getBox() {
//                return this.box;
//            }
//
//            public boolean isSelected() {
//                return this.checkBox.isSelected();
//            }
//
//            public void setSelected(boolean b) {
//                this.checkBox.setSelected(false);
//            }
//
//            public double getLow() {
//                return this.low;
//            }
//
//            public double getHigh() {
//                return this.high;
//            }
//        }
//    }

//    private static class ContinuousInquiryPanel extends JPanel {
//        private final JComboBox ntileCombo;
//        private final JComboBox ntileIndexCombo;
//        private final DoubleTextField field1;
//        private final DoubleTextField field2;
////        private ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type type;
//        private final Map<String, Integer> ntileMap = new HashMap<>();
//        private final double[] data;
//
//        /**
//         * @param variable          This is the variable being conditioned on. Must be continuous and one of the variables
//         *                          in the ScatterPlot.
//         * @param ScatterPlot       We need this to get the column of data for the variable.
//         * @param conditioningPanel We will try to get some initialization information out of the conditioning
//         *                          panel. This must be for the same variable as variable.
//         */
////        public ContinuousInquiryPanel(ContinuousVariable variable, ScatterPlot ScatterPlot,
////                                      ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel conditioningPanel) {
////            this.data = ScatterPlot.getContinuousData(variable.getName());
////
////            if (conditioningPanel == null)
////                throw new NullPointerException();
////            if (!(variable == conditioningPanel.getVariable()))
////                throw new IllegalArgumentException("Wrong variable for conditioning panel.");
////
////            // There is some order dependence in the below; careful rearranging things.
////            NumberFormat nf = new DecimalFormat("0.00");
////
////            this.field1 = new DoubleTextField(conditioningPanel.getLow(), 4, nf);
////            this.field2 = new DoubleTextField(conditioningPanel.getHigh(), 4, nf);
////
////            JRadioButton radio1 = new JRadioButton();
////            JRadioButton radio2 = new JRadioButton();
////            JRadioButton radio3 = new JRadioButton();
////            JRadioButton radio4 = new JRadioButton();
////
////            radio1.addActionListener(new ActionListener() {
////                public void actionPerformed(ActionEvent e) {
////                    ContinuousInquiryPanel.this.type = ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.AboveAverage;
////                    ContinuousInquiryPanel.this.field1.setValue(StatUtils.mean(ContinuousInquiryPanel.this.data));
////                    ContinuousInquiryPanel.this.field2.setValue(StatUtils.max(ContinuousInquiryPanel.this.data));
////                }
////            });
////
////            radio2.addActionListener(new ActionListener() {
////                public void actionPerformed(ActionEvent e) {
////                    ContinuousInquiryPanel.this.type = ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.BelowAverage;
////                    ContinuousInquiryPanel.this.field1.setValue(StatUtils.min(ContinuousInquiryPanel.this.data));
////                    ContinuousInquiryPanel.this.field2.setValue(StatUtils.mean(ContinuousInquiryPanel.this.data));
////                }
////            });
////
////            radio3.addActionListener(new ActionListener() {
////                public void actionPerformed(ActionEvent e) {
////                    ContinuousInquiryPanel.this.type = ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Ntile;
////                    double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(ContinuousInquiryPanel.this.data, getNtile());
////                    double breakpoint1 = breakpoints[getNtileIndex() - 1];
////                    double breakpoint2 = breakpoints[getNtileIndex()];
////                    ContinuousInquiryPanel.this.field1.setValue(breakpoint1);
////                    ContinuousInquiryPanel.this.field2.setValue(breakpoint2);
////                }
////            });
////
////            radio4.addActionListener(new ActionListener() {
////                public void actionPerformed(ActionEvent e) {
////                    ContinuousInquiryPanel.this.type = ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Range;
////                }
////            });
////
////            ButtonGroup group = new ButtonGroup();
////            group.add(radio1);
////            group.add(radio2);
////            group.add(radio3);
////            group.add(radio4);
////
////            this.type = conditioningPanel.getType();
////
////            this.ntileCombo = new JComboBox();
////            this.ntileIndexCombo = new JComboBox();
////
////            int ntile = conditioningPanel.getNtile();
////            int ntileIndex = conditioningPanel.getNtileIndex();
////
////            for (int n = 2; n <= 10; n++) {
////                this.ntileCombo.addItem(ScatterPlotView.tiles[n - 1]);
////                this.ntileMap.put(ScatterPlotView.tiles[n - 1], n);
////            }
////
////            for (int n = 1; n <= ntile; n++) {
////                this.ntileIndexCombo.addItem(n);
////            }
////
////            this.ntileCombo.setSelectedItem(ScatterPlotView.tiles[ntile - 1]);
////            this.ntileIndexCombo.setSelectedItem(ntileIndex);
////
////            this.ntileCombo.addItemListener(new ItemListener() {
////                public void itemStateChanged(ItemEvent e) {
////                    String item = (String) e.getItem();
////                    int ntileIndex = ContinuousInquiryPanel.this.ntileMap.get(item);
////
////                    for (int i = ContinuousInquiryPanel.this.ntileIndexCombo.getItemCount() - 1; i >= 0; i--) {
////                        ContinuousInquiryPanel.this.ntileIndexCombo.removeItemAt(i);
////                    }
////
////                    for (int n = 1; n <= ntileIndex; n++) {
////                        ContinuousInquiryPanel.this.ntileIndexCombo.addItem(n);
////                    }
////
////                    double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(ContinuousInquiryPanel.this.data, getNtile());
////                    double breakpoint1 = breakpoints[getNtileIndex() - 1];
////                    double breakpoint2 = breakpoints[getNtileIndex()];
////                    ContinuousInquiryPanel.this.field1.setValue(breakpoint1);
////                    ContinuousInquiryPanel.this.field2.setValue(breakpoint2);
////                }
////            });
////
////            this.ntileIndexCombo.addItemListener(new ItemListener() {
////                public void itemStateChanged(ItemEvent e) {
////                    int ntile = getNtile();
////                    int ntileIndex = getNtileIndex();
////                    double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(ContinuousInquiryPanel.this.data, ntile);
////                    double breakpoint1 = breakpoints[ntileIndex - 1];
////                    double breakpoint2 = breakpoints[ntileIndex];
////                    ContinuousInquiryPanel.this.field1.setValue(breakpoint1);
////                    ContinuousInquiryPanel.this.field2.setValue(breakpoint2);
////                }
////            });
////
////
////            if (this.type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.AboveAverage) {
////                radio1.setSelected(true);
////                this.field1.setValue(StatUtils.mean(this.data));
////                this.field2.setValue(StatUtils.max(this.data));
////            } else if (this.type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.BelowAverage) {
////                radio2.setSelected(true);
////                this.field1.setValue(StatUtils.min(this.data));
////                this.field2.setValue(StatUtils.mean(this.data));
////            } else if (this.type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Ntile) {
////                radio3.setSelected(true);
////                double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(this.data, getNtile());
////                double breakpoint1 = breakpoints[getNtileIndex() - 1];
////                double breakpoint2 = breakpoints[getNtileIndex()];
////                this.field1.setValue(breakpoint1);
////                this.field2.setValue(breakpoint2);
////            } else if (this.type == ScatterPlotView.ScatterPlotController.ContinuousConditioningPanel.Type.Range) {
////                radio4.setSelected(true);
////            }
////
////            Box main = Box.createVerticalBox();
////
////            Box b0 = Box.createHorizontalBox();
////            b0.add(new JLabel("Condition on " + variable.getName() + " as:"));
////            b0.add(Box.createHorizontalGlue());
////            main.add(b0);
////            main.add(Box.createVerticalStrut(10));
////
////            Box b1 = Box.createHorizontalBox();
////            b1.add(radio1);
////            b1.add(new JLabel("Above average"));
////            b1.add(Box.createHorizontalGlue());
////            main.add(b1);
////
////            Box b2 = Box.createHorizontalBox();
////            b2.add(radio2);
////            b2.add(new JLabel("Below average"));
////            b2.add(Box.createHorizontalGlue());
////            main.add(b2);
////
////            Box b3 = Box.createHorizontalBox();
////            b3.add(radio3);
////            b3.add(new JLabel("In "));
////            b3.add(this.ntileCombo);
////            b3.add(this.ntileIndexCombo);
////            b3.add(Box.createHorizontalGlue());
////            main.add(b3);
////
////            Box b4 = Box.createHorizontalBox();
////            b4.add(radio4);
////            b4.add(new JLabel("In ("));
////            b4.add(this.field1);
////            b4.add(new JLabel(", "));
////            b4.add(this.field2);
////            b4.add(new JLabel(")"));
////            b4.add(Box.createHorizontalGlue());
////            main.add(b4);
////
////            add(main, BorderLayout.CENTER);
////        }
//
////        public ScatterPlotView.ScatterPlotViewrPlotController.ContinuousConditioningPanel.Type getType() {
////            return this.type;
////        }
//
//        public double getLow() {
//            return this.field1.getValue();
//        }
//
//        public double getHigh() {
//            return this.field2.getValue();
//        }
//
//        public int getNtile() {
//            String selectedItem = (String) this.ntileCombo.getSelectedItem();
//            return this.ntileMap.get(selectedItem);
//        }
//
//        public int getNtileIndex() {
//            Object selectedItem = this.ntileIndexCombo.getSelectedItem();
//            return selectedItem == null ? 1 : (Integer) selectedItem;
//        }
//
//        /**
//         * @return an array of breakpoints that divides the data into equal sized buckets,
//         * including the min and max.
//         */
//        public static double[] getNtileBreakpoints(double[] data, int ntiles) {
//            double[] _data = new double[data.length];
//            System.arraycopy(data, 0, _data, 0, _data.length);
//
//            // first sort the _data.
//            Arrays.sort(_data);
//            java.util.List<Chunk> chunks = new ArrayList<>(_data.length);
//            int startChunkCount = 0;
//            double lastValue = _data[0];
//
//            for (int i = 0; i < _data.length; i++) {
//                double value = _data[i];
//                if (value != lastValue) {
//                    chunks.add(new Chunk(startChunkCount, i, value));
//                    startChunkCount = i;
//                }
//                lastValue = value;
//            }
//
//            chunks.add(new Chunk(startChunkCount, _data.length, _data[_data.length - 1]));
//
//            // now find the breakpoints.
//            double interval = _data.length / (double) ntiles;
//            double[] breakpoints = new double[ntiles + 1];
//            breakpoints[0] = StatUtils.min(_data);
//
//            int current = 1;
//            int freq = 0;
//
//            for (Chunk chunk : chunks) {
//                int valuesInChunk = chunk.getNumberOfValuesInChunk();
//                int halfChunk = (int) (valuesInChunk * .5);
//
//                // if more than half the values in the chunk fit this bucket then put here,
//                // otherwise the chunk should be added to the next bucket.
//                if (freq + halfChunk <= interval) {
//                    freq += valuesInChunk;
//                } else {
//                    freq = valuesInChunk;
//                }
//
//                if (interval <= freq) {
//                    freq = 0;
//                    if (current < ntiles + 1) {
//                        breakpoints[current++] = chunk.value;
//                    }
//                }
//            }
//
//            for (int i = current; i < breakpoints.length; i++) {
//                breakpoints[i] = StatUtils.max(_data);
//            }
//
//            return breakpoints;
//        }
//
//        /**
//         * Represents a chunk of data in a sorted array of data.  If low == high then
//         * then the chunk only contains one member.
//         */
//        private static class Chunk {
//
//            private final int valuesInChunk;
//            private final double value;
//
//            public Chunk(int low, int high, double value) {
//                this.valuesInChunk = (high - low);
//                this.value = value;
//            }
//
//            public int getNumberOfValuesInChunk() {
//                return this.valuesInChunk;
//            }
//
//        }
//    }

}



