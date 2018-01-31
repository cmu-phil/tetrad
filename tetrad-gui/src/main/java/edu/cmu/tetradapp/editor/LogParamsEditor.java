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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @author Frank Wimberly based on similar classes by Joe Ramsey
 */
public class LogParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params = null;


    public void setParams(Parameters params) {
        this.params = params;
    }

    public void setParentModels(Object[] parentModels) {
        for (Object parentModel : parentModels) {
            //            System.out.println(parentModel);
            //
            if (parentModel instanceof DataWrapper) {
                DataModel dataModel = ((DataWrapper) parentModel).getSelectedDataModel();
                //
                if (dataModel instanceof DataSet) {
                    DataSet parentDataSet = (DataSet) dataModel;
                }
            }
        }
    }

    public void setup() {
        buildGui();
    }

    public boolean mustBeShown() {
        return true;
    }

    //================================= Private Methods ===============================//

    /**
     * Constructs the Gui used to edit properties; called from each constructor.
     * Constructs labels and text fields for editing each property and adds
     * appropriate listeners.
     */
    private void buildGui() {
        setLayout(new BorderLayout());

        final DoubleTextField aField = new DoubleTextField(params.getDouble("a", 10.0), 6, NumberFormatUtil.getInstance().getNumberFormat());
        aField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.set("a", value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final IntTextField baseField = new IntTextField(params.getInt("base", 0), 4);
        baseField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    params.set("base", value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("<html>" +
                "The input dataset will be logarithmically transformed by applying f(x) = ln(a + x) to each data point x." +
                "<br> Can also 'unlog' the data i.e., apply g(x) = exp(x) - a, or override the base"));



        Box b9 = Box.createHorizontalBox();
        b9.add(Box.createHorizontalGlue());
        b9.add(new JLabel("<html> base (use 0 for natural log and base <i>e</i>): </html>"));
        b9.add(baseField);

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("<html><i>a =  </i></html>"));
        b7.add(aField);


        JCheckBox unlog = new JCheckBox();
        unlog.setSelected(params.getBoolean("unlog", false));
        unlog.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox box = (JCheckBox) e.getSource();
                params.set("unlog", box.isSelected());
            }
        });

        Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalGlue());
        b8.add(new JLabel("<html>Unlog: </html>"));
        b8.add(unlog);


        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b7);
        b1.add(Box.createHorizontalGlue());
        b1.add(b8);
        b1.add(Box.createHorizontalGlue());
        b1.add(b9);
        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
    }

}