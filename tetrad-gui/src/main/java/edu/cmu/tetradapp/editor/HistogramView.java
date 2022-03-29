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
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Histogram;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntSpinner;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;

/**
 * View for the Histogram class. Shows a histogram and gives controls for conditioning, etc.
 *
 * @author Joseph Ramsey
 */
public class HistogramView extends JPanel {
    private final HistogramPanel histogramPanel;
    private static final String[] tiles = new String[]{"1-tile", "2-tile", "tertile", "quartile", "quintile", "sextile",
            "septile", "octile", "nontile", "decile"};

    /**
     * Constructs the view with a given histogram and data set.
     */
    public HistogramView(final Histogram histogram) {
        this.histogramPanel = new HistogramPanel(histogram);
        final HistogramController controller = new HistogramController(this.histogramPanel);
        controller.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                HistogramView.this.histogramPanel.updateView();
            }
        });

        final Box box = Box.createHorizontalBox();
        box.add(this.histogramPanel);
        box.add(Box.createHorizontalStrut(3));
        box.add(controller);
        box.add(Box.createHorizontalStrut(5));
        box.add(Box.createHorizontalGlue());

        final Box vBox = Box.createVerticalBox();
        vBox.add(Box.createVerticalStrut(15));
        vBox.add(box);
        vBox.add(Box.createVerticalStrut(5));

        setLayout(new BorderLayout());
        add(vBox, BorderLayout.CENTER);

        final JMenuBar bar = new JMenuBar();
        final JMenu menu = new JMenu("File");
        menu.add(new JMenuItem(new SaveComponentImage(this.histogramPanel, "Save Histogram")));
        bar.add(menu);

        add(bar, BorderLayout.NORTH);
    }

    //========================== Private Methods ============           ====================//

    /**
     * A panel that is responsible for drawing a histogram.
     *
     * @author Tyler Gibson
     */
    public static class HistogramPanel extends JPanel {

        /**
         * The line color around the histogram.
         */
        private static final Color LINE_COLOR = Color.GRAY.darker();

        /**
         * Bar colors for the histogram (ripped from causality lab)
         */
        private static final Color[] BAR_COLORS = {
                new Color(153, 102, 102), new Color(102, 102, 153), new Color(102, 153, 102), new Color(153, 102, 153),
                new Color(153, 153, 102), new Color(102, 153, 153), new Color(204, 153, 153), new Color(153, 153, 204),
                new Color(153, 204, 153), new Color(204, 153, 204),
                new Color(204, 204, 153), new Color(153, 204, 204), new Color(255, 204, 204), new Color(204, 204, 255),
                new Color(204, 255, 204)
        };

        /**
         * Variables that control the size of the drawing area.
         */
        private final static int PADDINGX = 40;
        private final static int PADDINGY = 15;
        private final static int HEIGHT = 250 + HistogramPanel.PADDINGY;
        private final static int DISPLAYED_HEIGHT = (int) ((HistogramPanel.HEIGHT - HistogramPanel.PADDINGY) - HistogramPanel.HEIGHT * .10);
        private final static int WIDTH = 290 + HistogramPanel.PADDINGX;
        private final static int SPACE = 2;
        private final static int DASH = 10;

        /**
         * The histogram to display.
         */
        private final Histogram histogram;

        /**
         * The default size of the component.
         */
        private final Dimension size = new Dimension(HistogramPanel.WIDTH + 2 * HistogramPanel.SPACE, HistogramPanel.HEIGHT);

        /**
         * Format for continuous data.
         */
        private final NumberFormat format = new DecimalFormat("0.#");// NumberFormatUtil.getInstance().getNumberFormat();

//        /**
//         * A cached string displaying what is being viewed in the histogram.
//         */
//        private String displayString;

        /**
         * A map from the rectangles that define the bars, to the number of units in the bar.
         */
        private final Map<Rectangle, Integer> rectMap = new LinkedHashMap<>();

        /**
         * Constructs the histogram display panel given the initial histogram to display.
         *
         * @param histogram The histogram to display.
         */
        public HistogramPanel(final Histogram histogram) {
            if (histogram == null) {
                throw new NullPointerException("Given histogram must be null");
            }
            this.histogram = histogram;

            this.setToolTipText(" ");
        }

        //============================ PUblic Methods =============================//

        /**
         * Updates the histogram that is dispalyed to the given one.
         */
        public synchronized void updateView() {
            if (getHistogram() == null) {
                throw new NullPointerException("The given histogram must not be null");
            }
//            this.displayString = null;
            this.repaint();
        }


        public String getToolTipText(final MouseEvent evt) {
            final Point point = evt.getPoint();
            for (final Rectangle rect : this.rectMap.keySet()) {
                if (rect.contains(point)) {
                    final Integer i = this.rectMap.get(rect);
                    if (i != null) {
                        return i.toString();
                    }
                    break;
                }
            }
            return null;
        }


        /**
         * Paints the histogram and related items.
         */
        public void paintComponent(final Graphics graphics) {

            // set up variables.
            this.rectMap.clear();
            final Graphics2D g2d = (Graphics2D) graphics;
            final Histogram histogram = this.getHistogram();
            final int[] freqs = histogram.getFrequencies();
            final int categories = freqs.length;
//            int barWidth = Math.max((WIDTH - PADDINGX) / categories, 12) - SPACE;
            final int barWidth = Math.max((HistogramPanel.WIDTH - HistogramPanel.PADDINGX) / categories, 2) - HistogramPanel.SPACE;
            final int height = HistogramPanel.HEIGHT - HistogramPanel.PADDINGY;
            final int topFreq = HistogramPanel.getMax(freqs);
            final double scale = HistogramPanel.DISPLAYED_HEIGHT / (double) topFreq;
            final FontMetrics fontMetrics = g2d.getFontMetrics();
            // draw background/surrounding box.
            g2d.setColor(this.getBackground());
            g2d.fillRect(0, 0, HistogramPanel.WIDTH + 2 * HistogramPanel.SPACE, HistogramPanel.HEIGHT);
            g2d.setColor(Color.WHITE);
            g2d.fillRect(HistogramPanel.PADDINGX, 0, (HistogramPanel.WIDTH + HistogramPanel.SPACE) - HistogramPanel.PADDINGX, height);
            // draw the histogram
            for (int i = 0; i < categories; i++) {
                final int freq = freqs[i];
                final int y = (int) Math.ceil(scale * freq);
                final int x = HistogramPanel.SPACE * (i + 1) + barWidth * i + HistogramPanel.PADDINGX;
                g2d.setColor(HistogramPanel.getBarColor(i));
                final Rectangle rect = new Rectangle(x, (height - y), barWidth, y);
                g2d.fill(rect);
                this.rectMap.put(rect, freq);
            }
            //border
            g2d.setColor(HistogramPanel.LINE_COLOR);
            g2d.drawRect(HistogramPanel.PADDINGX, 0, (HistogramPanel.WIDTH + HistogramPanel.SPACE) - HistogramPanel.PADDINGX, height);
            // draw the buttom line
            g2d.setColor(HistogramPanel.LINE_COLOR);

            final Node target = histogram.getTargetNode();

            if (target instanceof ContinuousVariable) {
                final Map<Integer, Double> pointsAndValues = pickGoodPointsAndValues(HistogramPanel.PADDINGX, HistogramPanel.WIDTH + HistogramPanel.SPACE, histogram.getMin(),
                        histogram.getMax());

                for (final int point : pointsAndValues.keySet()) {
                    final double value = pointsAndValues.get(point);
                    if (point < HistogramPanel.WIDTH + HistogramPanel.SPACE - 10) {
                        g2d.drawString(this.format.format(value), point + 2, height + 15);
                    }
                    g2d.drawLine(point, height + HistogramPanel.DASH, point, height);
                }
            } else if (target instanceof DiscreteVariable) {
                final DiscreteVariable var = (DiscreteVariable) target;
                final java.util.List<String> _categories = var.getCategories();
                int i = -1;

                for (final Rectangle rect : this.rectMap.keySet()) {
                    final int x = (int) rect.getX();
                    g2d.drawString(_categories.get(++i), x, height + 15);
                }
            }

            // draw the side line
            g2d.setColor(HistogramPanel.LINE_COLOR);
            final int topY = height - (int) Math.ceil(scale * topFreq);
            final String top = String.valueOf(topFreq);
            g2d.drawString(top, HistogramPanel.PADDINGX - fontMetrics.stringWidth(top), topY - 2);
            g2d.drawLine(HistogramPanel.PADDINGX - HistogramPanel.DASH, topY, HistogramPanel.PADDINGX, topY);
            g2d.drawString("0", HistogramPanel.PADDINGX - fontMetrics.stringWidth("0"), height - 2);
            g2d.drawLine(HistogramPanel.PADDINGX - HistogramPanel.DASH, height, HistogramPanel.PADDINGX, height);
            final int hSize = (height - topY) / 4;
            for (int i = 1; i < 4; i++) {
                final int topHeight = height - hSize * i;
                g2d.drawLine(HistogramPanel.PADDINGX - HistogramPanel.DASH, topHeight, HistogramPanel.PADDINGX, topHeight);
            }

////            draw the display string.
//            g2d.setColor(LINE_COLOR);
//            g2d.drawString(getDisplayString(), PADDINGX, HEIGHT - 5);
        }


        public Dimension getPreferredSize() {
            return this.size;
        }


        public Dimension getMaximumSize() {
            return this.size;
        }


        public Dimension getMinimumSize() {
            return this.size;
        }

        //========================== private methods ==========================//

        private Map<Integer, Double> pickGoodPointsAndValues(final int min, final int max, final double minValue, final double maxValue) {
            final double range = maxValue - minValue;
            final int powerOfTen = (int) Math.floor(Math.log(range) / Math.log(10));
            final Map<Integer, Double> points = new HashMap<>();

            final int low = (int) Math.floor(minValue / Math.pow(10, powerOfTen));
            final int high = (int) Math.ceil(maxValue / Math.pow(10, powerOfTen));

            for (int i = low; i < high; i++) {
                final double realValue = i * Math.pow(10, powerOfTen);
                final Integer intValue = translateToInt(min, max, minValue, maxValue, realValue);

                if (intValue == null) {
                    continue;
                }

                points.put(intValue, realValue);
            }

            return points;
        }

        private Integer translateToInt(final int min, final int max, final double minValue, final double maxValue, final double value) {
            if (minValue >= maxValue) {
                throw new IllegalArgumentException();
            }
            if (min >= max) {
                throw new IllegalArgumentException();
            }

            final double ratio = (value - minValue) / (maxValue - minValue);

            final int intValue = (int) (Math.round(min + ratio * (double) (max - min)));

            if (intValue < min || intValue > max) {
                return null;
            }

            return intValue;
        }

        private static Color getBarColor(final int i) {
            return HistogramPanel.BAR_COLORS[i % HistogramPanel.BAR_COLORS.length];
        }

//        private String getDisplayString() {
//            if (this.displayString == null) {
//                StringBuilder builder = new StringBuilder();
//                builder.append("Showing: ");
//                builder.append(getHistogram().getTarget());
//                this.displayString = builder.toString();
//            }
//
//            return this.displayString;
//        }

        private static int getMax(final int[] freqs) {
            int max = freqs[0];
            for (int i = 1; i < freqs.length; i++) {
                final int current = freqs[i];
                if (current > max) {
                    max = current;
                }
            }
            return max;
        }

        /**
         * The histogram we are displaying.
         */
        public Histogram getHistogram() {
            return this.histogram;
        }
    }

    public static class HistogramController extends JPanel {

        /**
         * The histogram we are working on.
         */
        private final Histogram histogram;

        /**
         * Combo box of all the variables.
         */
        private final JComboBox targetSelector;

        /**
         * A spinner that deals with category selection.
         */
        private IntSpinner numBarsSelector;

        private final JComboBox newConditioningVariableSelector;
        private final JButton newConditioningVariableButton;
        private final JButton removeConditioningVariableButton;
        private final List<ConditioningPanel> conditioningPanels = new ArrayList<>();

        // To provide some memory of previous settings for the inquiry dialogs.
        private final Map<Node, ConditioningPanel> conditioningPanelMap = new HashMap<>();

        /**
         * Constructs the editor panel given the initial histogram and the dataset.
         */
        public HistogramController(final HistogramPanel histogramPanel) {
            this.setLayout(new BorderLayout());
            this.histogram = histogramPanel.getHistogram();
            final Node target = this.histogram.getTargetNode();
            this.targetSelector = new JComboBox();
            final ListCellRenderer renderer = new VariableBoxRenderer();
            this.targetSelector.setRenderer(renderer);

            final List<Node> variables = this.histogram.getDataSet().getVariables();
            Collections.sort(variables);

            for (final Node node : variables) {
                this.targetSelector.addItem(node);

                if (node == target) {
                    this.targetSelector.setSelectedItem(node);
                }
            }

            this.targetSelector.addItemListener(new ItemListener() {
                public void itemStateChanged(final ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        final Node node = (Node) e.getItem();
                        getHistogram().setTarget(node.getName());
                        final int maxBins = HistogramController.getMaxCategoryValue(getHistogram());
                        final int numBins = getHistogram().getNumBins();

                        // Don't try to set the max on the existing num bars selector; there is (at least currently)
                        // a bug in the IntSpinner that prevents the max from being increased once it's decreased, so
                        // you can go from continuous to discrete but not discrete to continuous and have the number
                        // of bins be reasonable. jdramsey 7/17/13
                        HistogramController.this.numBarsSelector = new IntSpinner(numBins, 1, 3);
                        HistogramController.this.numBarsSelector.setMin(2);
                        HistogramController.this.numBarsSelector.setMax(maxBins);

                        HistogramController.this.numBarsSelector.addChangeListener(new ChangeListener() {
                            public void stateChanged(final ChangeEvent e) {
                                final JSpinner s = (JSpinner) e.getSource();
                                if ((getHistogram().getTargetNode() instanceof ContinuousVariable)) {
                                    final int value = (Integer) s.getValue();
                                    getHistogram().setNumBins(value);
                                    changeHistogram();
                                }
                            }
                        });

                        for (final ConditioningPanel panel : new ArrayList<>(HistogramController.this.conditioningPanels)) {
                            HistogramController.this.conditioningPanels.remove(panel);
                        }

                        buildEditArea();
                        resetConditioning();
                        changeHistogram();
                    }
                }
            });

            this.numBarsSelector = new IntSpinner(this.histogram.getNumBins(), 1, 3);
            this.numBarsSelector.setMin(2);
            this.numBarsSelector.setMax(HistogramController.getMaxCategoryValue(this.histogram));

            this.numBarsSelector.addChangeListener(new ChangeListener() {
                public void stateChanged(final ChangeEvent e) {
                    final JSpinner s = (JSpinner) e.getSource();
                    if ((getHistogram().getTargetNode() instanceof ContinuousVariable)) {
                        final int value = (Integer) s.getValue();
                        getHistogram().setNumBins(value);
                        changeHistogram();
                    }
                }
            });

            this.newConditioningVariableSelector = new JComboBox();

            for (final Node node : variables) {
                this.newConditioningVariableSelector.addItem(node);
            }

            this.newConditioningVariableSelector.addItemListener(new ItemListener() {
                public void itemStateChanged(final ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        System.out.println("New conditioning varible " + e.getItem());
                    }
                }
            });

            this.newConditioningVariableButton = new JButton("Add");

            this.newConditioningVariableButton.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    final Node selected = (Node) HistogramController.this.newConditioningVariableSelector.getSelectedItem();

                    if (selected == HistogramController.this.targetSelector.getSelectedItem()) {
                        JOptionPane.showMessageDialog(HistogramController.this,
                                "The target variable cannot be conditioned on.");
                        return;
                    }

                    for (final ConditioningPanel panel : HistogramController.this.conditioningPanels) {
                        if (selected == panel.getVariable()) {
                            JOptionPane.showMessageDialog(HistogramController.this,
                                    "There is already a conditioning variable called " + selected + ".");
                            return;
                        }
                    }

                    if (selected instanceof ContinuousVariable) {
                        final ContinuousVariable _var = (ContinuousVariable) selected;

                        ContinuousConditioningPanel panel1 = (ContinuousConditioningPanel) HistogramController.this.conditioningPanelMap.get(_var);

                        if (panel1 == null) {
                            panel1 = ContinuousConditioningPanel.getDefault(_var, HistogramController.this.histogram);
                        }

                        final ContinuousInquiryPanel panel2 = new ContinuousInquiryPanel(_var, HistogramController.this.histogram, panel1);

                        JOptionPane.showOptionDialog(HistogramController.this, panel2,
                                null, JOptionPane.DEFAULT_OPTION,
                                JOptionPane.PLAIN_MESSAGE, null, null, null);

                        final ContinuousConditioningPanel.Type type = panel2.getType();
                        final double low = panel2.getLow();
                        final double high = panel2.getHigh();
                        final int ntile = panel2.getNtile();
                        final int ntileIndex = panel2.getNtileIndex();

                        final ContinuousConditioningPanel panel3 = new ContinuousConditioningPanel(_var, low, high, ntile, ntileIndex, type);

                        HistogramController.this.conditioningPanels.add(panel3);
                        HistogramController.this.conditioningPanelMap.put(_var, panel3);
                    } else if (selected instanceof DiscreteVariable) {
                        final DiscreteVariable _var = (DiscreteVariable) selected;
                        DiscreteConditioningPanel panel1 = (DiscreteConditioningPanel) HistogramController.this.conditioningPanelMap.get(_var);

                        if (panel1 == null) {
                            panel1 = DiscreteConditioningPanel.getDefault(_var);
                            HistogramController.this.conditioningPanelMap.put(_var, panel1);
                        }

                        final DiscreteInquiryPanel panel2 = new DiscreteInquiryPanel(_var, panel1);

                        JOptionPane.showOptionDialog(HistogramController.this, panel2,
                                null, JOptionPane.DEFAULT_OPTION,
                                JOptionPane.PLAIN_MESSAGE, null, null, null);

                        final String category = (String) panel2.getValuesDropdown().getSelectedItem();
                        final int index = _var.getIndex(category);

                        final DiscreteConditioningPanel panel3 = new DiscreteConditioningPanel(_var, index);
                        HistogramController.this.conditioningPanels.add(panel3);
                        HistogramController.this.conditioningPanelMap.put(_var, panel3);
                    } else {
                        throw new IllegalStateException();
                    }

                    buildEditArea();
                    resetConditioning();
                    changeHistogram();
                }
            });

            this.removeConditioningVariableButton = new JButton("Remove Checked");

            this.removeConditioningVariableButton.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    for (final ConditioningPanel panel : new ArrayList<>(HistogramController.this.conditioningPanels)) {
                        if (panel.isSelected()) {
                            panel.setSelected(false);
                            HistogramController.this.conditioningPanels.remove(panel);
                        }
                    }

                    buildEditArea();
                    resetConditioning();
                    changeHistogram();
                }
            });

            // build the gui.
            HistogramController.restrictSize(this.targetSelector);
            HistogramController.restrictSize(this.numBarsSelector);
            HistogramController.restrictSize(this.newConditioningVariableSelector);
            HistogramController.restrictSize(this.newConditioningVariableButton);
            HistogramController.restrictSize(this.removeConditioningVariableButton);

            buildEditArea();
        }

        private void resetConditioning() {

            // Need to set the conditions on the histogram and also update the list of conditions in the view.
            this.histogram.removeConditioningVariables();

            for (final ConditioningPanel panel : this.conditioningPanels) {
                if (panel instanceof ContinuousConditioningPanel) {
                    final Node node = panel.getVariable();
                    final double low = ((ContinuousConditioningPanel) panel).getLow();
                    final double high = ((ContinuousConditioningPanel) panel).getHigh();
                    this.histogram.addConditioningVariable(node.getName(), low, high);

                } else if (panel instanceof DiscreteConditioningPanel) {
                    final Node node = panel.getVariable();
                    final int index = ((DiscreteConditioningPanel) panel).getIndex();
                    this.histogram.addConditioningVariable(node.getName(), index);
                }
            }
        }

        private void buildEditArea() {
            final Box main = Box.createVerticalBox();
            final Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Histogram for: "));
            b1.add(this.targetSelector);
            b1.add(new JLabel("# Bars: "));
            b1.add(this.numBarsSelector);
            b1.add(Box.createHorizontalGlue());
            main.add(b1);

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

            for (int i = this.newConditioningVariableSelector.getItemCount() - 1; i >= 0; i--) {
                this.newConditioningVariableSelector.removeItemAt(i);
            }

            final List<Node> variables = this.histogram.getDataSet().getVariables();
            Collections.sort(variables);

            NODE:
            for (final Node node : variables) {
                if (node == this.targetSelector.getSelectedItem()) continue;

                for (final ConditioningPanel panel : this.conditioningPanels) {
                    if (node == panel.getVariable()) continue NODE;
                }

                this.newConditioningVariableSelector.addItem(node);
            }

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


        /**
         * @return the max category value that should be accepted for the given histogram.
         */
        private static int getMaxCategoryValue(final Histogram histogram) {
            final Node node = histogram.getTargetNode();

            if (node instanceof DiscreteVariable) {
                final DiscreteVariable var = (DiscreteVariable) node;
                return var.getNumCategories();
            } else {
                return 40;
            }
        }

        private Histogram getHistogram() {
            return this.histogram;
        }

        // This causes the histogram panel to update.
        private void changeHistogram() {
            firePropertyChange("histogramChanged", null, this.histogram);
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

        private static class DiscreteConditioningPanel implements ConditioningPanel {
            private final DiscreteVariable variable;
            private final String value;
            private final Box box;

            // Set selected if this checkbox should be removed.
            private final JCheckBox checkBox;
            private final int index;

            public DiscreteConditioningPanel(final DiscreteVariable variable, final int valueIndex) {
                if (variable == null) throw new NullPointerException();
                if (valueIndex < 0 || valueIndex >= variable.getNumCategories()) {
                    throw new IllegalArgumentException("Not a category for this varible.");
                }

                this.variable = variable;
                this.value = variable.getCategory(valueIndex);
                this.index = valueIndex;

                final Box b4 = Box.createHorizontalBox();
                b4.add(Box.createRigidArea(new Dimension(10, 0)));
                b4.add(new JLabel(variable + " = " + variable.getCategory(valueIndex)));
                b4.add(Box.createHorizontalGlue());
                this.checkBox = new JCheckBox();
                HistogramController.restrictSize(this.checkBox);
                b4.add(this.checkBox);
                this.box = b4;
            }

            public static DiscreteConditioningPanel getDefault(final DiscreteVariable var) {
                return new DiscreteConditioningPanel(var, 0);
            }

            public DiscreteVariable getVariable() {
                return this.variable;
            }

            public String getValue() {
                return this.value;
            }

            public int getIndex() {
                return this.index;
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

        }

        private static class ContinuousConditioningPanel implements ConditioningPanel {

            public int getNtile() {
                return this.ntile;
            }

            public int getNtileIndex() {
                return this.ntileIndex;
            }

            public enum Type {Range, Ntile, AboveAverage, BelowAverage}

            private final ContinuousVariable variable;
            private final Box box;

            private final Type type;
            private final double low;
            private final double high;
            private final int ntile;
            private final int ntileIndex;

            // Mark selected if this panel is to be removed.
            private final JCheckBox checkBox;

            public ContinuousConditioningPanel(final ContinuousVariable variable, final double low, final double high, final int ntile, final int ntileIndex, final Type type) {
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

                if (type == Type.Range) {
                    b4.add(new JLabel(variable + " = (" + nf.format(low) + ", " + nf.format(high) + ")"));
                } else if (type == Type.AboveAverage) {
                    b4.add(new JLabel(variable + " = Above Average"));
                } else if (type == Type.BelowAverage) {
                    b4.add(new JLabel(variable + " = Below Average"));
                } else if (type == Type.Ntile) {
                    b4.add(new JLabel(variable + " = " + HistogramView.tiles[ntile - 1] + " " + ntileIndex));
                }

                b4.add(Box.createHorizontalGlue());
                this.checkBox = new JCheckBox();
                HistogramController.restrictSize(this.checkBox);
                b4.add(this.checkBox);
                this.box = b4;

            }

            public static ContinuousConditioningPanel getDefault(final ContinuousVariable variable, final Histogram histogram) {
                final double[] data = histogram.getContinuousData(variable.getName());
                final double max = StatUtils.max(data);
                final double avg = StatUtils.mean(data);
                return new ContinuousConditioningPanel(variable, avg, max, 2, 1, Type.AboveAverage);
            }

            public ContinuousVariable getVariable() {
                return this.variable;
            }

            public Type getType() {
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
        private HistogramController.ContinuousConditioningPanel.Type type;
        private final Map<String, Integer> ntileMap = new HashMap<>();
        private final double[] data;

        /**
         * @param variable          This is the variable being conditioned on. Must be continuous and one of the variables
         *                          in the histogram.
         * @param histogram         We need this to get the column of data for the variable.
         * @param conditioningPanel We will try to get some initialization information out of the conditioning
         *                          panel. This must be for the same variable as variable.
         */
        public ContinuousInquiryPanel(final ContinuousVariable variable, final Histogram histogram,
                                      final HistogramController.ContinuousConditioningPanel conditioningPanel) {
            this.data = histogram.getContinuousData(variable.getName());

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
                    ContinuousInquiryPanel.this.type = HistogramController.ContinuousConditioningPanel.Type.AboveAverage;
                    ContinuousInquiryPanel.this.field1.setValue(StatUtils.mean(ContinuousInquiryPanel.this.data));
                    ContinuousInquiryPanel.this.field2.setValue(StatUtils.max(ContinuousInquiryPanel.this.data));
                }
            });

            radio2.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    ContinuousInquiryPanel.this.type = HistogramController.ContinuousConditioningPanel.Type.BelowAverage;
                    ContinuousInquiryPanel.this.field1.setValue(StatUtils.min(ContinuousInquiryPanel.this.data));
                    ContinuousInquiryPanel.this.field2.setValue(StatUtils.mean(ContinuousInquiryPanel.this.data));
                }
            });

            radio3.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    ContinuousInquiryPanel.this.type = HistogramController.ContinuousConditioningPanel.Type.Ntile;
                    final double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(ContinuousInquiryPanel.this.data, getNtile());
                    final double breakpoint1 = breakpoints[getNtileIndex() - 1];
                    final double breakpoint2 = breakpoints[getNtileIndex()];
                    ContinuousInquiryPanel.this.field1.setValue(breakpoint1);
                    ContinuousInquiryPanel.this.field2.setValue(breakpoint2);
                }
            });

            radio4.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    ContinuousInquiryPanel.this.type = HistogramController.ContinuousConditioningPanel.Type.Range;
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
                this.ntileCombo.addItem(HistogramView.tiles[n - 1]);
                this.ntileMap.put(HistogramView.tiles[n - 1], n);
            }

            for (int n = 1; n <= ntile; n++) {
                this.ntileIndexCombo.addItem(n);
            }

            this.ntileCombo.setSelectedItem(HistogramView.tiles[ntile - 1]);
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


            if (this.type == HistogramController.ContinuousConditioningPanel.Type.AboveAverage) {
                radio1.setSelected(true);
                this.field1.setValue(StatUtils.mean(this.data));
                this.field2.setValue(StatUtils.max(this.data));
            } else if (this.type == HistogramController.ContinuousConditioningPanel.Type.BelowAverage) {
                radio2.setSelected(true);
                this.field1.setValue(StatUtils.min(this.data));
                this.field2.setValue(StatUtils.mean(this.data));
            } else if (this.type == HistogramController.ContinuousConditioningPanel.Type.Ntile) {
                radio3.setSelected(true);
                final double[] breakpoints = ContinuousInquiryPanel.getNtileBreakpoints(this.data, getNtile());
                final double breakpoint1 = breakpoints[getNtileIndex() - 1];
                final double breakpoint2 = breakpoints[getNtileIndex()];
                this.field1.setValue(breakpoint1);
                this.field2.setValue(breakpoint2);
            } else if (this.type == HistogramController.ContinuousConditioningPanel.Type.Range) {
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

        public HistogramController.ContinuousConditioningPanel.Type getType() {
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
            final List<Chunk> chunks = new ArrayList<>(_data.length);
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

    private static class DiscreteInquiryPanel extends JPanel {
        private final JComboBox valuesDropdown;

        public DiscreteInquiryPanel(final DiscreteVariable var, final HistogramController.DiscreteConditioningPanel panel) {
            this.valuesDropdown = new JComboBox();

            for (final String category : var.getCategories()) {
                getValuesDropdown().addItem(category);
            }

            this.valuesDropdown.setSelectedItem(panel.getValue());

            final Box main = Box.createVerticalBox();
            final Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Condition on:"));
            b1.add(Box.createHorizontalGlue());
            main.add(b1);
            main.add(Box.createVerticalStrut(10));

            final Box b2 = Box.createHorizontalBox();
            b2.add(Box.createHorizontalStrut(10));
            b2.add(new JLabel(var.getName() + " = "));
            b2.add(getValuesDropdown());
            main.add(b2);

            add(main, BorderLayout.CENTER);
        }

        public JComboBox getValuesDropdown() {
            return this.valuesDropdown;
        }
    }

}




