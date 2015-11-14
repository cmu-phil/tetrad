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
import java.util.*;
import java.util.List;

/**
 * View for the Histogram class. Shows a histogram and gives controls for conditioning, etc.
 *
 * @author Joseph Ramsey
 */
public class HistogramView extends JPanel {
    private final HistogramPanel histogramPanel;
    private static String[] tiles = new String[]{"1-tile", "2-tile", "tertile", "quartile", "quintile", "sextile",
            "septile", "octile", "nontile", "decile"};

    /**
     * Constructs the view with a given histogram and data set.
     */
    public HistogramView(Histogram histogram) {
        this.histogramPanel = new HistogramPanel(histogram);
        HistogramController controller = new HistogramController(histogramPanel);
        controller.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                histogramPanel.updateView();
            }
        });

        Box box = Box.createHorizontalBox();
        box.add(histogramPanel);
        box.add(Box.createHorizontalStrut(3));
        box.add(controller);
        box.add(Box.createHorizontalStrut(5));
        box.add(Box.createHorizontalGlue());

        Box vBox = Box.createVerticalBox();
        vBox.add(Box.createVerticalStrut(15));
        vBox.add(box);
        vBox.add(Box.createVerticalStrut(5));

        setLayout(new BorderLayout());
        add(vBox, BorderLayout.CENTER);

        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem(new SaveComponentImage(histogramPanel, "Save Histogram")));
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
        private static Color LINE_COLOR = Color.GRAY.darker();

        /**
         * Bar colors for the histogram (ripped from causality lab)
         */
        private static Color BAR_COLORS[] = {
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
        private final static int HEIGHT = 250 + PADDINGY;
        private final static int DISPLAYED_HEIGHT = (int) ((HEIGHT - PADDINGY) - HEIGHT * .10);
        private final static int WIDTH = 290 + PADDINGX;
        private final static int SPACE = 2;
        private final static int DASH = 10;

        /**
         * The histogram to display.
         */
        private Histogram histogram;

        /**
         * The default size of the component.
         */
        private Dimension size = new Dimension(WIDTH + 2 * SPACE, HEIGHT);

        /**
         * Format for continuous data.
         */
        private NumberFormat format = new DecimalFormat("0.#");// NumberFormatUtil.getInstance().getNumberFormat();

//        /**
//         * A cached string displaying what is being viewed in the histogram.
//         */
//        private String displayString;

        /**
         * A map from the rectangles that define the bars, to the number of units in the bar.
         */
        private Map<Rectangle, Integer> rectMap = new LinkedHashMap<Rectangle, Integer>();

        /**
         * Constructs the histogram display panel given the initial histogram to display.
         *
         * @param histogram The histogram to display.
         */
        public HistogramPanel(Histogram histogram) {
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


        public String getToolTipText(MouseEvent evt) {
            Point point = evt.getPoint();
            for (Rectangle rect : rectMap.keySet()) {
                if (rect.contains(point)) {
                    Integer i = rectMap.get(rect);
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
        public void paintComponent(Graphics graphics) {

            // set up variables.
            this.rectMap.clear();
            Graphics2D g2d = (Graphics2D) graphics;
            Histogram histogram = this.getHistogram();
            int[] freqs = histogram.getFrequencies();
            int categories = freqs.length;
//            int barWidth = Math.max((WIDTH - PADDINGX) / categories, 12) - SPACE;
            int barWidth = Math.max((WIDTH - PADDINGX) / categories, 2) - SPACE;
            int height = HEIGHT - PADDINGY;
            int topFreq = getMax(freqs);
            double scale = DISPLAYED_HEIGHT / (double) topFreq;
            FontMetrics fontMetrics = g2d.getFontMetrics();
            // draw background/surrounding box.
            g2d.setColor(this.getBackground());
            g2d.fillRect(0, 0, WIDTH + 2 * SPACE, HEIGHT);
            g2d.setColor(Color.WHITE);
            g2d.fillRect(PADDINGX, 0, (WIDTH + SPACE) - PADDINGX, height);
            // draw the histogram
            for (int i = 0; i < categories; i++) {
                int freq = freqs[i];
                int y = (int) Math.ceil(scale * freq);
                int x = SPACE * (i + 1) + barWidth * i + PADDINGX;
                g2d.setColor(getBarColor(i));
                Rectangle rect = new Rectangle(x, (height - y), barWidth, y);
                g2d.fill(rect);
                rectMap.put(rect, freq);
            }
            //border
            g2d.setColor(LINE_COLOR);
            g2d.drawRect(PADDINGX, 0, (WIDTH + SPACE) - PADDINGX, height);
            // draw the buttom line
            g2d.setColor(LINE_COLOR);

            Node target = histogram.getTargetNode();

            if (target instanceof ContinuousVariable) {
                Map<Integer, Double> pointsAndValues = pickGoodPointsAndValues(PADDINGX, WIDTH + SPACE, histogram.getMin(),
                        histogram.getMax());

                for (int point : pointsAndValues.keySet()) {
                    double value = pointsAndValues.get(point);
                    if (point < WIDTH + SPACE - 10) {
                        g2d.drawString(format.format(value), point + 2, height + 15);
                    }
                    g2d.drawLine(point, height + DASH, point, height);
                }
            } else if (target instanceof DiscreteVariable) {
                DiscreteVariable var = (DiscreteVariable) target;
                java.util.List<String> _categories = var.getCategories();
                int i = -1;

                for (Rectangle rect : rectMap.keySet()) {
                    int x = (int) rect.getX();
                    g2d.drawString(_categories.get(++i), x, height + 15);
                }
            }

            // draw the side line
            g2d.setColor(LINE_COLOR);
            int topY = height - (int) Math.ceil(scale * topFreq);
            String top = String.valueOf(topFreq);
            g2d.drawString(top, PADDINGX - fontMetrics.stringWidth(top), topY - 2);
            g2d.drawLine(PADDINGX - DASH, topY, PADDINGX, topY);
            g2d.drawString("0", PADDINGX - fontMetrics.stringWidth("0"), height - 2);
            g2d.drawLine(PADDINGX - DASH, height, PADDINGX, height);
            int hSize = (height - topY) / 4;
            for (int i = 1; i < 4; i++) {
                int topHeight = height - hSize * i;
                g2d.drawLine(PADDINGX - DASH, topHeight, PADDINGX, topHeight);
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

        private Map<Integer, Double> pickGoodPointsAndValues(int min, int max, double minValue, double maxValue) {
            double range = maxValue - minValue;
            int powerOfTen = (int) Math.floor(Math.log(range) / Math.log(10));
            Map<Integer, Double> points = new HashMap<Integer, Double>();

            int low = (int) Math.floor(minValue / Math.pow(10, powerOfTen));
            int high = (int) Math.ceil(maxValue / Math.pow(10, powerOfTen));

            for (int i = low; i < high; i++) {
                double realValue = i * Math.pow(10, powerOfTen);
                Integer intValue = translateToInt(min, max, minValue, maxValue, realValue);

                if (intValue == null) {
                    continue;
                }

                points.put(intValue, realValue);
            }

            return points;
        }

        private Integer translateToInt(int min, int max, double minValue, double maxValue, double value) {
            if (minValue >= maxValue) {
                throw new IllegalArgumentException();
            }
            if (min >= max) {
                throw new IllegalArgumentException();
            }

            double ratio = (value - minValue) / (maxValue - minValue);

            int intValue = (int) (Math.round(min + ratio * (double) (max - min)));

            if (intValue < min || intValue > max) {
                return null;
            }

            return intValue;
        }

        private static Color getBarColor(int i) {
            return BAR_COLORS[i % BAR_COLORS.length];
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

        private static int getMax(int[] freqs) {
            int max = freqs[0];
            for (int i = 1; i < freqs.length; i++) {
                int current = freqs[i];
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
            return histogram;
        }
    }

    public static class HistogramController extends JPanel {

        /**
         * The histogram we are working on.
         */
        private Histogram histogram;

        /**
         * Combo box of all the variables.
         */
        private JComboBox targetSelector;

        /**
         * A spinner that deals with category selection.
         */
        private IntSpinner numBarsSelector;

        private JComboBox newConditioningVariableSelector;
        private JButton newConditioningVariableButton;
        private JButton removeConditioningVariableButton;
        private List<ConditioningPanel> conditioningPanels = new ArrayList<ConditioningPanel>();

        // To provide some memory of previous settings for the inquiry dialogs.
        private Map<Node, ConditioningPanel> conditioningPanelMap = new HashMap<Node, ConditioningPanel>();

        /**
         * Constructs the editor panel given the initial histogram and the dataset.
         */
        public HistogramController(HistogramPanel histogramPanel) {
            this.setLayout(new BorderLayout());
            this.histogram = histogramPanel.getHistogram();
            Node target = histogram.getTargetNode();
            this.targetSelector = new JComboBox();
            ListCellRenderer renderer = new VariableBoxRenderer();
            this.targetSelector.setRenderer(renderer);

            List<Node> variables = histogram.getDataSet().getVariables();
            Collections.sort(variables);

            for (Node node : variables) {
                this.targetSelector.addItem(node);

                if (node == target) {
                    this.targetSelector.setSelectedItem(node);
                }
            }

            this.targetSelector.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        Node node = (Node) e.getItem();
                        getHistogram().setTarget(node.getName());
                        int maxBins = getMaxCategoryValue(getHistogram());
                        int numBins = getHistogram().getNumBins();

                        // Don't try to set the max on the existing num bars selector; there is (at least currently)
                        // a bug in the IntSpinner that prevents the max from being increased once it's decreased, so
                        // you can go from continuous to discrete but not discrete to continuous and have the number
                        // of bins be reasonable. jdramsey 7/17/13
                        numBarsSelector = new IntSpinner(numBins, 1, 3);
                        numBarsSelector.setMin(2);
                        numBarsSelector.setMax(maxBins);

                        numBarsSelector.addChangeListener(new ChangeListener() {
                            public void stateChanged(ChangeEvent e) {
                                JSpinner s = (JSpinner) e.getSource();
                                if ((getHistogram().getTargetNode() instanceof ContinuousVariable)) {
                                    int value = (Integer) s.getValue();
                                    getHistogram().setNumBins(value);
                                    changeHistogram();
                                }
                            }
                        });

                        for (ConditioningPanel panel : new ArrayList<ConditioningPanel>(conditioningPanels)) {
                            conditioningPanels.remove(panel);
                        }

                        buildEditArea();
                        resetConditioning();
                        changeHistogram();
                    }
                }
            });

            this.numBarsSelector = new IntSpinner(histogram.getNumBins(), 1, 3);
            this.numBarsSelector.setMin(2);
            this.numBarsSelector.setMax(getMaxCategoryValue(histogram));

            this.numBarsSelector.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    JSpinner s = (JSpinner) e.getSource();
                    if ((getHistogram().getTargetNode() instanceof ContinuousVariable)) {
                        int value = (Integer) s.getValue();
                        getHistogram().setNumBins(value);
                        changeHistogram();
                    }
                }
            });

            this.newConditioningVariableSelector = new JComboBox();

            for (Node node : variables) {
                this.newConditioningVariableSelector.addItem(node);
            }

            this.newConditioningVariableSelector.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    if (e.getStateChange() == ItemEvent.SELECTED) {
                        System.out.println("New conditioning varible " + e.getItem());
                    }
                }
            });

            this.newConditioningVariableButton = new JButton("Add");

            this.newConditioningVariableButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    Node selected = (Node) newConditioningVariableSelector.getSelectedItem();

                        if (selected == targetSelector.getSelectedItem()) {
                        JOptionPane.showMessageDialog(HistogramController.this,
                                "The target variable cannot be conditioned on.");
                        return;
                    }

                    for (ConditioningPanel panel : conditioningPanels) {
                        if (selected == panel.getVariable()) {
                            JOptionPane.showMessageDialog(HistogramController.this,
                                    "There is already a conditioning variable called " + selected + ".");
                            return;
                        }
                    }

                    if (selected instanceof ContinuousVariable) {
                        final ContinuousVariable _var = (ContinuousVariable) selected;

                        ContinuousConditioningPanel panel1 = (ContinuousConditioningPanel) conditioningPanelMap.get(_var);

                        if (panel1 == null) {
                            panel1 = ContinuousConditioningPanel.getDefault(_var, histogram);
                        }

                        ContinuousInquiryPanel panel2 = new ContinuousInquiryPanel(_var, histogram, panel1);

                        JOptionPane.showOptionDialog(HistogramController.this, panel2,
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
                    } else if (selected instanceof DiscreteVariable) {
                        DiscreteVariable _var = (DiscreteVariable) selected;
                        DiscreteConditioningPanel panel1 = (DiscreteConditioningPanel) conditioningPanelMap.get(_var);

                        if (panel1 == null) {
                            panel1 = DiscreteConditioningPanel.getDefault(_var);
                            conditioningPanelMap.put(_var, panel1);
                        }

                        DiscreteInquiryPanel panel2 = new DiscreteInquiryPanel(_var, panel1);

                        JOptionPane.showOptionDialog(HistogramController.this, panel2,
                                null, JOptionPane.DEFAULT_OPTION,
                                JOptionPane.PLAIN_MESSAGE, null, null, null);

                        String category = (String) panel2.getValuesDropdown().getSelectedItem();
                        int index = _var.getIndex(category);

                        DiscreteConditioningPanel panel3 = new DiscreteConditioningPanel(_var, index);
                        conditioningPanels.add(panel3);
                        conditioningPanelMap.put(_var, panel3);
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
                public void actionPerformed(ActionEvent e) {
                    for (ConditioningPanel panel : new ArrayList<ConditioningPanel>(conditioningPanels)) {
                        if (panel.isSelected()) {
                            panel.setSelected(false);
                            conditioningPanels.remove(panel);
                        }
                    }

                    buildEditArea();
                    resetConditioning();
                    changeHistogram();
                }
            });

            // build the gui.
            restrictSize(this.targetSelector);
            restrictSize(this.numBarsSelector);
            restrictSize(this.newConditioningVariableSelector);
            restrictSize(this.newConditioningVariableButton);
            restrictSize(this.removeConditioningVariableButton);

            buildEditArea();
        }

        private void resetConditioning() {

            // Need to set the conditions on the histogram and also update the list of conditions in the view.
            histogram.removeConditioningVariables();

            for (ConditioningPanel panel : conditioningPanels) {
                if (panel instanceof ContinuousConditioningPanel) {
                    Node node = panel.getVariable();
                    double low = ((ContinuousConditioningPanel) panel).getLow();
                    double high = ((ContinuousConditioningPanel) panel).getHigh();
                    histogram.addConditioningVariable(node.getName(), low, high);

                } else if (panel instanceof DiscreteConditioningPanel) {
                    Node node = panel.getVariable();
                    int index = ((DiscreteConditioningPanel) panel).getIndex();
                    histogram.addConditioningVariable(node.getName(), index);
                }
            }
        }

        private void buildEditArea() {
            Box main = Box.createVerticalBox();
            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Histogram for: "));
            b1.add(this.targetSelector);
            b1.add(new JLabel("# Bars: "));
            b1.add(this.numBarsSelector);
            b1.add(Box.createHorizontalGlue());
            main.add(b1);

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

            for (int i = newConditioningVariableSelector.getItemCount() - 1; i >= 0; i--) {
                newConditioningVariableSelector.removeItemAt(i);
            }

            List<Node> variables = histogram.getDataSet().getVariables();
            Collections.sort(variables);

            NODE:
            for (Node node : variables) {
                if (node ==  targetSelector.getSelectedItem()) continue;

                for (ConditioningPanel panel : conditioningPanels) {
                    if (node == panel.getVariable()) continue NODE;
                }

                this.newConditioningVariableSelector.addItem(node);
            }

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


        /**
         * @return the max category value that should be accepted for the given histogram.
         */
        private static int getMaxCategoryValue(Histogram histogram) {
            Node node = histogram.getTargetNode();

            if (node instanceof DiscreteVariable) {
                DiscreteVariable var = (DiscreteVariable) node;
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

        private static class DiscreteConditioningPanel implements ConditioningPanel {
            private DiscreteVariable variable;
            private String value;
            private Box box;

            // Set selected if this checkbox should be removed.
            private JCheckBox checkBox;
            private int index;

            public DiscreteConditioningPanel(DiscreteVariable variable, int valueIndex) {
                if (variable == null) throw new NullPointerException();
                if (valueIndex < 0 || valueIndex >= variable.getNumCategories()) {
                    throw new IllegalArgumentException("Not a category for this varible.");
                }

                this.variable = variable;
                this.value = variable.getCategory(valueIndex);
                this.index = valueIndex;

                Box b4 = Box.createHorizontalBox();
                b4.add(Box.createRigidArea(new Dimension(10, 0)));
                b4.add(new JLabel(variable + " = " + variable.getCategory(valueIndex)));
                b4.add(Box.createHorizontalGlue());
                this.checkBox = new JCheckBox();
                restrictSize(checkBox);
                b4.add(checkBox);
                this.box = b4;
            }

            public static DiscreteConditioningPanel getDefault(DiscreteVariable var) {
                return new DiscreteConditioningPanel(var, 0);
            }

            public DiscreteVariable getVariable() {
                return variable;
            }

            public String getValue() {
                return value;
            }

            public int getIndex() {
                return index;
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

            public static ContinuousConditioningPanel getDefault(ContinuousVariable variable, Histogram histogram) {
                double[] data = histogram.getContinuousData(variable.getName());
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
        private HistogramController.ContinuousConditioningPanel.Type type;
        private final Map<String, Integer> ntileMap = new HashMap<String, Integer>();
        private double[] data;

        /**
         * @param variable          This is the variable being conditioned on. Must be continuous and one of the variables
         *                          in the histogram.
         * @param histogram         We need this to get the column of data for the variable.
         * @param conditioningPanel We will try to get some initialization information out of the conditioning
         *                          panel. This must be for the same variable as variable.
         */
        public ContinuousInquiryPanel(final ContinuousVariable variable, Histogram histogram,
                                      HistogramController.ContinuousConditioningPanel conditioningPanel) {
            data = histogram.getContinuousData(variable.getName());

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
                    type = HistogramController.ContinuousConditioningPanel.Type.AboveAverage;
                    field1.setValue(StatUtils.mean(data));
                    field2.setValue(StatUtils.max(data));
                }
            });

            radio2.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    type = HistogramController.ContinuousConditioningPanel.Type.BelowAverage;
                    field1.setValue(StatUtils.min(data));
                    field2.setValue(StatUtils.mean(data));
                }
            });

            radio3.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    type = HistogramController.ContinuousConditioningPanel.Type.Ntile;
                    double[] breakpoints = getNtileBreakpoints(data, getNtile());
                    double breakpoint1 = breakpoints[getNtileIndex() - 1];
                    double breakpoint2 = breakpoints[getNtileIndex()];
                    field1.setValue(breakpoint1);
                    field2.setValue(breakpoint2);
                }
            });

            radio4.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    type = HistogramController.ContinuousConditioningPanel.Type.Range;
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


            if (type == HistogramController.ContinuousConditioningPanel.Type.AboveAverage) {
                radio1.setSelected(true);
                field1.setValue(StatUtils.mean(data));
                field2.setValue(StatUtils.max(data));
            } else if (type == HistogramController.ContinuousConditioningPanel.Type.BelowAverage) {
                radio2.setSelected(true);
                field1.setValue(StatUtils.min(data));
                field2.setValue(StatUtils.mean(data));
            } else if (type == HistogramController.ContinuousConditioningPanel.Type.Ntile) {
                radio3.setSelected(true);
                double[] breakpoints = getNtileBreakpoints(data, getNtile());
                double breakpoint1 = breakpoints[getNtileIndex() - 1];
                double breakpoint2 = breakpoints[getNtileIndex()];
                field1.setValue(breakpoint1);
                field2.setValue(breakpoint2);
            } else if (type == HistogramController.ContinuousConditioningPanel.Type.Range) {
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

        public HistogramController.ContinuousConditioningPanel.Type getType() {
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
            List<Chunk> chunks = new ArrayList<Chunk>(_data.length);
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

    private static class DiscreteInquiryPanel extends JPanel {
        private JComboBox valuesDropdown;

        public DiscreteInquiryPanel(DiscreteVariable var, HistogramController.DiscreteConditioningPanel panel) {
            valuesDropdown = new JComboBox();

            for (String category : var.getCategories()) {
                getValuesDropdown().addItem(category);
            }

            valuesDropdown.setSelectedItem(panel.getValue());

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

        public JComboBox getValuesDropdown() {
            return valuesDropdown;
        }
    }

}




