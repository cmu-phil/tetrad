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

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Michael Freenor
 */
class ScatterPlotEditorPanel extends JPanel {


    /**
     * Combo box of all the variables.
     */
    private final JComboBox yVariableBox;
    private final JComboBox xVariableBox;
    public final JComboBox newCondBox;

    /**
     * The dataset being viewed.
     */
    public final DataSet dataSet;

    private ScatterPlotOld scatterPlot;

    Vector boxes; //check boxes that activate the use of conditional variables
    private final JCheckBox regressionBox; //check box that enables the drawing of the regression line
    Vector granularity; //text fields containing the resolution of our conditional variables
    Vector slideLabels; //displays information about the conditional variables used, such as the interval being used
    Vector scrollers;  //the actual thumb-scrollers used to adjust the conditional variables
    Vector condVariables; //stores the conditional variables

    /**
     * Constructs the editor panel given the initial scatter plot and the dataset.
     */
    public ScatterPlotEditorPanel(final ScatterPlotOld scatterPlot, final DataSet dataSet) {
        //   construct components
        this.regressionBox = new JCheckBox();
        this.setLayout(new BorderLayout());
        // first build scatter plot and components used in the editor.
        this.scatterPlot = scatterPlot;
        final Node selected = scatterPlot.getYVariable();
        this.dataSet = dataSet;
        this.yVariableBox = new JComboBox();
        this.xVariableBox = new JComboBox();
        final ListCellRenderer renderer = new VariableBoxRenderer();
        this.yVariableBox.setRenderer(renderer);
        for (final Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                this.yVariableBox.addItem(node);
                if (node == selected) {
                    this.yVariableBox.setSelectedItem(node);
                }
            }
        }

        this.xVariableBox.setRenderer(renderer);
        for (final Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                this.xVariableBox.addItem(node);
                if (node == selected) {
                    this.xVariableBox.setSelectedItem(node);
                }
            }
        }

        this.newCondBox = new JComboBox();
        this.newCondBox.setRenderer(renderer);
        for (final Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                this.newCondBox.addItem(node);
                if (node == selected) {
                    this.newCondBox.setSelectedItem(node);
                }
            }
        }

        // build the gui.
        this.add(buildEditArea(dataSet));
    }

    private void changeScatterPlot(final ScatterPlotOld scatterPlot) {
        this.scatterPlot = scatterPlot;
        // fire event
        this.firePropertyChange("histogramChange", null, scatterPlot);
    }

    public static void setPreferredAsMax(final JComponent component) {
        component.setMaximumSize(component.getPreferredSize());

    }

    private Box buildEditArea(final DataSet dataset) {
        ScatterPlotEditorPanel.setPreferredAsMax(this.yVariableBox);
        ScatterPlotEditorPanel.setPreferredAsMax(this.xVariableBox);
        ScatterPlotEditorPanel.setPreferredAsMax(this.newCondBox);

        final Box main2 = Box.createVerticalBox();

        final Box main = Box.createVerticalBox();

        final Box hBox2 = Box.createHorizontalBox();
        hBox2.add(Box.createHorizontalStrut(10));
        hBox2.add(new JLabel("Select Variable for X-Axis: "));
        hBox2.add(Box.createHorizontalStrut(10));
        hBox2.add(this.xVariableBox);
        hBox2.add(Box.createHorizontalGlue());
        main.add(hBox2);

        final Box hBox = Box.createHorizontalBox();
        hBox.add(Box.createHorizontalStrut(10));
        hBox.add(new JLabel("Select Variable for Y-Axis: "));
        hBox.add(Box.createHorizontalStrut(10));
        hBox.add(this.yVariableBox);
        hBox.add(Box.createHorizontalGlue());
        main.add(hBox);


        this.xVariableBox.addActionListener(new ScatterListener(this));
        this.yVariableBox.addActionListener(new ScatterListener(this));

        final Box hBox6 = Box.createHorizontalBox();
        hBox6.add(Box.createHorizontalStrut(10));
        hBox6.add(new JLabel("Display Regression Line: "));
        hBox6.add(Box.createHorizontalStrut(10));
        hBox6.add(this.regressionBox);
        hBox6.add(Box.createHorizontalGlue());
        main.add(hBox6);

        this.regressionBox.addActionListener(new ScatterListener(this));


        final JButton newCond = new JButton("Add New Conditional Variable");
        final Box hBox3 = Box.createHorizontalBox();
        hBox3.add(Box.createHorizontalStrut(10));
        this.newCondBox.setPreferredSize(new Dimension(50, 20));
        hBox3.add(this.newCondBox);
        hBox3.add(Box.createHorizontalStrut(10));
        hBox3.add(newCond);
        main.add(hBox3);


        newCond.addActionListener(new AddVariableListener(main, this));

        this.boxes = new Vector();
        this.granularity = new Vector();
        this.slideLabels = new Vector();
        this.scrollers = new Vector();
        this.condVariables = new Vector();

        main2.add(main);
        //main2.add(Box.createVerticalStrut(10));
        main2.add(Box.createVerticalGlue());

        return main2;
    }

    /**
     * Redraws the scatter plot.
     */
    public void redrawScatterPlot() {
        final ScatterPlotOld newPlot = new ScatterPlotOld(this.scatterPlot.getDataSet(), (ContinuousVariable) (this.yVariableBox.getSelectedItem()),
                (ContinuousVariable) (this.xVariableBox.getSelectedItem()));
        if (this.regressionBox.isSelected())
            newPlot.setDrawRegLine(true);
        for (int i = 0; i < this.scrollers.size(); i++) {
            boolean breakNow = false;
            //if(((JCheckBox)boxes.get(i)).isSelected())
            //{
            final double low = ((JScrollBar) this.scrollers.get(i)).getValue();
            final double high = ((JScrollBar) this.scrollers.get(i)).getValue() + ((JScrollBar) this.scrollers.get(i)).getVisibleAmount();
            if (low > high) breakNow = true;

            final ContinuousVariable currentNode = (ContinuousVariable) (this.condVariables.get(i));
            final int variableIndex = newPlot.getDataSet().getColumn(currentNode);

            //edit the index set here
            final Vector newIndexSet = new Vector();
            final Vector newComplementSet = new Vector();
            for (int j = 0; j < newPlot.getIndexSet().size(); j++) {
                final int currentIndex = (Integer) newPlot.getIndexSet().get(j);
                //lookup value at this index
                final double value = newPlot.getDataSet().getDouble(currentIndex, variableIndex);
                //check if value is in the right interval -- if so we add to the new indexSet
                if (value >= low && value <= high) {
                    newIndexSet.add(currentIndex);
                } else {
                    newComplementSet.add(currentIndex);
                }
            }
            newPlot.setIndexSet(newIndexSet);
            newPlot.setComplementIndexSet(newComplementSet);
            //}
            if (breakNow) break;
        }

        changeScatterPlot(newPlot);
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


}

class SliderListener implements AdjustmentListener {
    private final ScatterPlotEditorPanel sp;
    private final int index;

    public SliderListener(final ScatterPlotEditorPanel sp, final int index) {
        this.sp = sp;
        this.index = index;
    }

    public void adjustmentValueChanged(final AdjustmentEvent evt) {
        this.sp.redrawScatterPlot();
        ((JLabel) this.sp.slideLabels.get(this.index)).setText("Viewing Range: " +
                "[" + ((JScrollBar) this.sp.scrollers.get(this.index)).getValue() + ", " +
                (((JScrollBar) this.sp.scrollers.get(this.index)).getValue() +
                        ((JScrollBar) this.sp.scrollers.get(this.index)).getVisibleAmount()) + "]");
    }
}

class GranularityListener implements FocusListener, ActionListener {
    private final ScatterPlotEditorPanel sp;
    private final int index;

    public GranularityListener(final ScatterPlotEditorPanel sp, final int index) {
        this.sp = sp;
        this.index = index;
    }

    public void focusGained(final FocusEvent evt) {
    }

    public void actionPerformed(final ActionEvent evt) {
        focusLost(null);
    }

    public void focusLost(final FocusEvent evt) {
        final JScrollBar currentBar = ((JScrollBar) this.sp.scrollers.get(this.index));
        currentBar.setValue((int) Math.floor(currentBar.getMinimum()));
        int newVisibleAmount = (int) Double.parseDouble(((JTextField) this.sp.granularity.get(this.index)).getText());
        if (newVisibleAmount > Math.ceil(currentBar.getMaximum()) - Math.floor(currentBar.getMinimum()))
            newVisibleAmount = (int) (Math.ceil(currentBar.getMaximum()) - Math.floor(currentBar.getMinimum()));
        currentBar.setVisibleAmount(newVisibleAmount);
        ((JLabel) this.sp.slideLabels.get(this.index)).setText("Viewing Range: [" + currentBar.getValue() +
                ", " + (currentBar.getValue() + currentBar.getVisibleAmount()) + "]");
    }
}

class ScatterListener implements ActionListener {
    private final ScatterPlotEditorPanel sp;

    public ScatterListener(final ScatterPlotEditorPanel sp) {
        this.sp = sp;
    }

    public void actionPerformed(final ActionEvent evt) {
        this.sp.redrawScatterPlot();
    }
}

/*
    This class listens to the "Add New Conditional Variable" button.
    It adds more components to the editor panel to allow the user to tweak
    conditional variables.  
 */
class AddVariableListener implements ActionListener {
    private final ScatterPlotEditorPanel sp;
    private final Box main;

    public AddVariableListener(final Box main, final ScatterPlotEditorPanel sp) {
        this.sp = sp;
        this.main = main;
    }

    public void actionPerformed(final ActionEvent e) {
        for (int i = 0; i < this.sp.boxes.size(); i++) {
            if (((Node) this.sp.newCondBox.getSelectedItem()).getName().equals(((Node) this.sp.condVariables.get(i)).getName())) {
                return;
            }
        }

        final int i = this.sp.boxes.size();
        final Box hBox4 = Box.createHorizontalBox();
        hBox4.add(Box.createHorizontalStrut(10));
        this.sp.boxes.add(new JCheckBox());
        final JButton removeButton = new JButton("Remove " + ((Node) this.sp.newCondBox.getSelectedItem()).getName());
        //hBox4.add((JCheckBox)sp.boxes.get(i));
        hBox4.add(Box.createHorizontalStrut(10));
        this.sp.condVariables.add(this.sp.newCondBox.getSelectedItem());
        hBox4.add(new JLabel(((Node) this.sp.newCondBox.getSelectedItem()).getName() + ": "));
        //hBox4.add(Box.createHorizontalStrut(10));
        ((JCheckBox) this.sp.boxes.get(i)).addActionListener(new ScatterListener(this.sp));
        this.sp.granularity.add(new JTextField(5));
        ((JTextField) this.sp.granularity.get(i)).setText("1");
        ScatterPlotEditorPanel.setPreferredAsMax((JTextField) this.sp.granularity.get(i));
        hBox4.add(new JLabel("Set granularity of slider: "));
        hBox4.add((JTextField) this.sp.granularity.get(i));

        ((JTextField) this.sp.granularity.get(i)).addFocusListener(new GranularityListener(this.sp, i));
        ((JTextField) this.sp.granularity.get(i)).addActionListener(new GranularityListener(this.sp, i));

        hBox4.add(Box.createHorizontalGlue());
        this.main.add(hBox4);

        double min, max;
        final int varIndex = this.sp.dataSet.getColumn(((Node) this.sp.newCondBox.getSelectedItem()));
        min = max = this.sp.dataSet.getDouble(0, varIndex);

        for (int j = 0; j < this.sp.dataSet.getNumRows(); j++) {
            final double temp = this.sp.dataSet.getDouble(j, varIndex);
            if (temp < min) min = temp;
            if (temp > max) max = temp;
        }

        this.sp.scrollers.add(new JScrollBar(Adjustable.HORIZONTAL, (int) Math.floor(min), 1, (int) Math.floor(min), (int) Math.ceil(max)));

        final Box hBox10 = Box.createHorizontalBox();
        hBox10.add(Box.createHorizontalStrut(10));
        hBox10.add((JScrollBar) this.sp.scrollers.get(i));
        this.main.add(hBox10);

        ((JScrollBar) this.sp.scrollers.get(i)).addAdjustmentListener(new SliderListener(this.sp, i));

        final Box hBox12 = Box.createHorizontalBox();
        hBox12.add(Box.createHorizontalStrut(10));
        hBox12.add(removeButton);
        this.main.add(hBox12);

        this.sp.slideLabels.add(new JLabel("Viewing Range: [" + ((JScrollBar) this.sp.scrollers.get(i)).getValue() + ", " + (((JScrollBar) this.sp.scrollers.get(i)).getValue() + ((JScrollBar) this.sp.scrollers.get(i)).getVisibleAmount()) + "]"));
        final Box hBox11 = Box.createHorizontalBox();
        hBox11.add(Box.createHorizontalStrut(10));
        hBox11.add((JLabel) this.sp.slideLabels.get(i));
        this.main.add(hBox11);

        final JComponent[] toRemove = new JComponent[4];
        toRemove[0] = hBox4;
        toRemove[1] = hBox10;
        toRemove[2] = hBox11;
        toRemove[3] = hBox12;
        removeButton.addActionListener(new RemovalListener(this.sp, this.main, toRemove, i));

        this.sp.redrawScatterPlot();

        this.main.revalidate();
        this.main.repaint();
    }
}

class RemovalListener implements ActionListener {
    private final JComponent container;
    private final JComponent[] contained;
    private final int index;
    private final ScatterPlotEditorPanel sp;

    public RemovalListener(final ScatterPlotEditorPanel sp, final JComponent container, final JComponent[] contained, final int index) {
        this.container = container;
        this.contained = contained;
        this.index = index;
        this.sp = sp;
    }

    public void actionPerformed(final ActionEvent e) {
        this.sp.boxes.remove(this.index);
        this.sp.granularity.remove(this.index);
        this.sp.slideLabels.remove(this.index);
        this.sp.scrollers.remove(this.index);
        this.sp.condVariables.remove(this.index);
        this.sp.redrawScatterPlot();
        for (int i = 0; i < this.contained.length; i++)
            this.container.remove(this.contained[i]);
        this.container.revalidate();
        this.container.repaint();
    }
}



