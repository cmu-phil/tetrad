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
 *
 * @author Michael Freenor
 */
public class ScatterPlotEditorPanel extends JPanel {


    /**
     * Combo box of all the variables.
     */
    public JComboBox yVariableBox, xVariableBox, newCondBox;

    /**
     * The dataset being viewed.
     */
    public DataSet dataSet;

    public ScatterPlotOld scatterPlot;

    Vector boxes; //check boxes that activate the use of conditional variables
    JCheckBox regressionBox; //check box that enables the drawing of the regression line
    Vector granularity; //text fields containing the resolution of our conditional variables
    Vector slideLabels; //displays information about the conditional variables used, such as the interval being used
    Vector scrollers;  //the actual thumb-scrollers used to adjust the conditional variables
    Vector condVariables; //stores the conditional variables

    /**
     * Constructs the editor panel given the initial scatter plot and the dataset.
     *
     * @param scatterPlot
     * @param dataSet
     */
    public ScatterPlotEditorPanel(ScatterPlotOld scatterPlot, DataSet dataSet) {
        //   construct components
        regressionBox = new JCheckBox();
        this.setLayout(new BorderLayout());
        // first build scatter plot and components used in the editor.
        this.scatterPlot = scatterPlot;
        Node selected = scatterPlot.getYVariable();
        this.dataSet = dataSet;
        this.yVariableBox = new JComboBox();
        this.xVariableBox = new JComboBox();
        ListCellRenderer renderer = new VariableBoxRenderer();
        this.yVariableBox.setRenderer(renderer);
        for (Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                this.yVariableBox.addItem(node);
                if (node == selected) {
                    this.yVariableBox.setSelectedItem(node);
                }
            }
        }

        this.xVariableBox.setRenderer(renderer);
        for (Node node : dataSet.getVariables()) {
            if (node instanceof ContinuousVariable) {
                this.xVariableBox.addItem(node);
                if (node == selected) {
                    this.xVariableBox.setSelectedItem(node);
                }
            }
        }

        this.newCondBox = new JComboBox();
        this.newCondBox.setRenderer(renderer);
        for (Node node : dataSet.getVariables()) {
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

    public void changeScatterPlot(ScatterPlotOld scatterPlot) {
        this.scatterPlot = scatterPlot;
        // fire event
        this.firePropertyChange("histogramChange", null, scatterPlot);
    }

    public static void setPreferredAsMax(JComponent component) {
        component.setMaximumSize(component.getPreferredSize());

    }

    private Box buildEditArea(DataSet dataset) {
        setPreferredAsMax(this.yVariableBox);
        setPreferredAsMax(this.xVariableBox);
        setPreferredAsMax(this.newCondBox);

        Box main2 = Box.createVerticalBox();

        Box main = Box.createVerticalBox();

        Box hBox2 = Box.createHorizontalBox();
        hBox2.add(Box.createHorizontalStrut(10));
        hBox2.add(new JLabel("Select Variable for X-Axis: "));
        hBox2.add(Box.createHorizontalStrut(10));
        hBox2.add(this.xVariableBox);
        hBox2.add(Box.createHorizontalGlue());
        main.add(hBox2);

        Box hBox = Box.createHorizontalBox();
        hBox.add(Box.createHorizontalStrut(10));
        hBox.add(new JLabel("Select Variable for Y-Axis: "));
        hBox.add(Box.createHorizontalStrut(10));
        hBox.add(this.yVariableBox);
        hBox.add(Box.createHorizontalGlue());
        main.add(hBox);


        this.xVariableBox.addActionListener(new ScatterListener(this));
        this.yVariableBox.addActionListener(new ScatterListener(this));

        Box hBox6 = Box.createHorizontalBox();
        hBox6.add(Box.createHorizontalStrut(10));
        hBox6.add(new JLabel("Display Regression Line: "));
        hBox6.add(Box.createHorizontalStrut(10));
        hBox6.add(regressionBox);
        hBox6.add(Box.createHorizontalGlue());
        main.add(hBox6);

        regressionBox.addActionListener(new ScatterListener(this));


        JButton newCond = new JButton("Add New Conditional Variable");
        Box hBox3 = Box.createHorizontalBox();
        hBox3.add(Box.createHorizontalStrut(10));
        newCondBox.setPreferredSize(new Dimension(50, 20));
        hBox3.add(newCondBox);
        hBox3.add(Box.createHorizontalStrut(10));
        hBox3.add(newCond);
        main.add(hBox3);


        newCond.addActionListener(new AddVariableListener(main, this));

        boxes = new Vector();
        granularity = new Vector();
        slideLabels = new Vector();
        scrollers = new Vector();
        condVariables = new Vector();

        main2.add(main);
        //main2.add(Box.createVerticalStrut(10));
        main2.add(Box.createVerticalGlue());

        return main2;
    }

    /**
     * Redraws the scatter plot.
     */
    public void redrawScatterPlot()
    {
        ScatterPlotOld newPlot = new ScatterPlotOld(scatterPlot.getDataSet(), (ContinuousVariable)(yVariableBox.getSelectedItem()),
                (ContinuousVariable)(xVariableBox.getSelectedItem()));
        if(regressionBox.isSelected())
            newPlot.setDrawRegLine(true);
        for(int i = 0; i < scrollers.size(); i++)
        {
            boolean breakNow = false;
            //if(((JCheckBox)boxes.get(i)).isSelected())
            //{
                double low = ((JScrollBar)scrollers.get(i)).getValue();
                double high = ((JScrollBar)scrollers.get(i)).getValue() + ((JScrollBar)scrollers.get(i)).getVisibleAmount();
                if (low > high) breakNow = true;

                ContinuousVariable currentNode = (ContinuousVariable)(condVariables.get(i));
                int variableIndex = newPlot.getDataSet().getColumn(currentNode);

                //edit the index set here
                Vector newIndexSet = new Vector();
                Vector newComplementSet = new Vector();
                for(int j = 0; j < newPlot.getIndexSet().size(); j++)
                {
                    int currentIndex = (Integer) newPlot.getIndexSet().get(j);
                    //lookup value at this index
                    double value = newPlot.getDataSet().getDouble(currentIndex, variableIndex);
                    //check if value is in the right interval -- if so we add to the new indexSet
                    if(value >= low && value <= high)
                    {
                        newIndexSet.add(currentIndex);
                    }
                    else
                    {
                        newComplementSet.add(currentIndex);
                    }
                }
                newPlot.setIndexSet(newIndexSet);
                newPlot.setComplementIndexSet(newComplementSet);
            //}
            if(breakNow) break;
        }

        changeScatterPlot(newPlot);    
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


}

class SliderListener implements AdjustmentListener
{
    ScatterPlotEditorPanel sp;
    int index;

    public SliderListener(ScatterPlotEditorPanel sp, int index)
    {
        this.sp = sp;
        this.index = index;
    }

    public void adjustmentValueChanged(AdjustmentEvent evt)
    { 
        sp.redrawScatterPlot();
        ((JLabel)sp.slideLabels.get(index)).setText("Viewing Range: " +
                "[" + ((JScrollBar)sp.scrollers.get(index)).getValue() + ", " +
                (((JScrollBar)sp.scrollers.get(index)).getValue() +
                        ((JScrollBar)sp.scrollers.get(index)).getVisibleAmount()) + "]");                  
    }
}

class GranularityListener implements FocusListener, ActionListener
{
    ScatterPlotEditorPanel sp;
    int index;

    public GranularityListener(ScatterPlotEditorPanel sp, int index)
    {
        this.sp = sp;
        this.index = index;
    }

    public void focusGained(FocusEvent evt){}

    public void actionPerformed(ActionEvent evt)
    {
        focusLost(null);
    }

    public void focusLost(FocusEvent evt)
    {
        JScrollBar currentBar = ((JScrollBar)sp.scrollers.get(index));
        currentBar.setValue((int)Math.floor(currentBar.getMinimum()));
        int newVisibleAmount = (int)Double.parseDouble(((JTextField)this.sp.granularity.get(index)).getText());
        if (newVisibleAmount > Math.ceil(currentBar.getMaximum()) - Math.floor(currentBar.getMinimum()))
            newVisibleAmount = (int)(Math.ceil(currentBar.getMaximum()) - Math.floor(currentBar.getMinimum()));
        currentBar.setVisibleAmount(newVisibleAmount);
        ((JLabel)sp.slideLabels.get(index)).setText("Viewing Range: [" + currentBar.getValue() + 
                ", " + (currentBar.getValue() + currentBar.getVisibleAmount()) + "]");
    }
}

class ScatterListener implements ActionListener
{
    ScatterPlotEditorPanel sp;

    public ScatterListener(ScatterPlotEditorPanel sp)
    {
        this.sp = sp;
    }

    public void actionPerformed(ActionEvent evt)
    {
        this.sp.redrawScatterPlot();
    }
}

/*
    This class listens to the "Add New Conditional Variable" button.
    It adds more components to the editor panel to allow the user to tweak
    conditional variables.  
 */
class AddVariableListener implements ActionListener
{
    ScatterPlotEditorPanel sp;
    Box main;

    public AddVariableListener(Box main, ScatterPlotEditorPanel sp)
    {
        this.sp = sp;
        this.main = main;
    }

    public void actionPerformed(ActionEvent e)
    {
        for(int i = 0; i < sp.boxes.size(); i++)
        {
            if(((Node)sp.newCondBox.getSelectedItem()).getName().equals(((Node)sp.condVariables.get(i)).getName()))
            {
                return;
            }
        }
        
        int i = sp.boxes.size();
        Box hBox4 = Box.createHorizontalBox();
        hBox4.add(Box.createHorizontalStrut(10));
        sp.boxes.add(new JCheckBox());
        JButton removeButton = new JButton("Remove " + ((Node)sp.newCondBox.getSelectedItem()).getName());
        //hBox4.add((JCheckBox)sp.boxes.get(i));
        hBox4.add(Box.createHorizontalStrut(10));
        sp.condVariables.add(sp.newCondBox.getSelectedItem());
        hBox4.add(new JLabel(((Node)sp.newCondBox.getSelectedItem()).getName() + ": "));
        //hBox4.add(Box.createHorizontalStrut(10));
        ((JCheckBox)sp.boxes.get(i)).addActionListener(new ScatterListener(sp));
        sp.granularity.add(new JTextField(5));
        ((JTextField)sp.granularity.get(i)).setText("1");
        ScatterPlotEditorPanel.setPreferredAsMax((JTextField)sp.granularity.get(i));
        hBox4.add(new JLabel("Set granularity of slider: "));
        hBox4.add((JTextField)sp.granularity.get(i));

        ((JTextField)sp.granularity.get(i)).addFocusListener(new GranularityListener(sp, i));
        ((JTextField)sp.granularity.get(i)).addActionListener(new GranularityListener(sp, i));

        hBox4.add(Box.createHorizontalGlue());
        main.add(hBox4);

        double min, max;
        int varIndex = sp.dataSet.getColumn(((Node)sp.newCondBox.getSelectedItem()));
        min = max = sp.dataSet.getDouble(0 , varIndex);

        for(int j = 0; j < sp.dataSet.getNumRows(); j++)
        {
            double temp = sp.dataSet.getDouble(j, varIndex);
            if(temp < min) min = temp;
            if(temp > max) max = temp;
        }

        sp.scrollers.add(new JScrollBar(JScrollBar.HORIZONTAL, (int)Math.floor(min), 1, (int)Math.floor(min), (int)Math.ceil(max)));

        Box hBox10 = Box.createHorizontalBox();
        hBox10.add(Box.createHorizontalStrut(10));
        hBox10.add((JScrollBar)sp.scrollers.get(i));
        main.add(hBox10);

        ((JScrollBar)sp.scrollers.get(i)).addAdjustmentListener(new SliderListener(sp, i));

        Box hBox12 = Box.createHorizontalBox();
        hBox12.add(Box.createHorizontalStrut(10));
        hBox12.add(removeButton);
        main.add(hBox12);

        sp.slideLabels.add(new JLabel("Viewing Range: [" + ((JScrollBar)sp.scrollers.get(i)).getValue() + ", " + (((JScrollBar)sp.scrollers.get(i)).getValue() + ((JScrollBar)sp.scrollers.get(i)).getVisibleAmount()) + "]"));
        Box hBox11 = Box.createHorizontalBox();
        hBox11.add(Box.createHorizontalStrut(10));
        hBox11.add((JLabel)sp.slideLabels.get(i));
        main.add(hBox11);

        JComponent[] toRemove = new JComponent[4];
        toRemove[0] = hBox4;
        toRemove[1] = hBox10;
        toRemove[2]  = hBox11;
        toRemove[3] = hBox12;
        removeButton.addActionListener(new RemovalListener(sp, main, toRemove, i));

        sp.redrawScatterPlot();

        this.main.revalidate();
        this.main.repaint();
    }
}

class RemovalListener implements ActionListener
{
    JComponent container;
    JComponent contained[];
    int index;
    ScatterPlotEditorPanel sp;

    public RemovalListener(ScatterPlotEditorPanel sp, JComponent container, JComponent[] contained, int index)
    {
        this.container = container;
        this.contained = contained;
        this.index = index;
        this.sp = sp;
    }

    public void actionPerformed(ActionEvent e)
    {
        sp.boxes.remove(index);
        sp.granularity.remove(index);
        sp.slideLabels.remove(index);
        sp.scrollers.remove(index);
        sp.condVariables.remove(index);
        sp.redrawScatterPlot();
        for(int i = 0; i < contained.length; i++)
            this.container.remove(contained[i]);
        this.container.revalidate();
        this.container.repaint();
    }    
}



