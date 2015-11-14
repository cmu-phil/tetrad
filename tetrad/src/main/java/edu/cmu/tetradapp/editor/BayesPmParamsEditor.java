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

import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.BayesPmParams;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class BayesPmParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private BayesPmParams params = null;

    /**
     * Lets the user edit the number of nodes.
     */
    private IntTextField lowerBoundField;

    /**
     * Lets the user edit the number of nodes.
     */
    private IntTextField upperBoundField;

    /**
     * Constructs a dialog to edit the given workbench Bayes simulation
     * getMappings object.
     */
    public BayesPmParamsEditor() {
    }

    public void setParams(Params params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = (BayesPmParams) params;
    }

    public void setParentModels(Object[] parentModels) {
        // Do nothing.
    }

    public void setup() {
        lowerBoundField = new IntTextField(getParams().getLowerBoundNumVals(), 4);
        lowerBoundField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    getParams().setLowerBoundNumVals(value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        upperBoundField = new IntTextField(getParams().getUpperBoundNumVals(), 4);
        upperBoundField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    getParams().setUpperBoundNumVals(value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        // construct the workbench.
        setLayout(new BorderLayout());

        JRadioButton setUpManually =
                new JRadioButton("<html>" + "Set up manually.</html>");

        JRadioButton automaticallyAssigned =
                new JRadioButton("<html>" + "Automatically assigned.</html>");
        ButtonGroup group = new ButtonGroup();
        group.add(setUpManually);
        group.add(automaticallyAssigned);

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Categories for variables should be:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));

        Box b3 = Box.createHorizontalBox();
        b3.add(setUpManually);
        b3.add(Box.createHorizontalGlue());
        //        b3.add(fixedField);
        b1.add(b3);

        Box b4 = Box.createHorizontalBox();
        b4.add(Box.createHorizontalStrut(25));
        b4.add(new JLabel("<html>" +
                "All variables will initially have 2 categories, '0' and '1', " +
                "<br>which can then be changed variable by variable in the editor." +
                "</html>"));
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);
        b1.add(Box.createVerticalStrut(10));

        Box b5 = Box.createHorizontalBox();
        b5.add(automaticallyAssigned);
        b5.add(Box.createHorizontalGlue());
        b1.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createHorizontalStrut(25));
        b6.add(new JLabel("<html>" +
                "Each variable will be automatically be assigned a number" +
                "<br>of categories (for simulation, e.g.). " + "</html>"));
        b6.add(Box.createHorizontalGlue());
        b1.add(b6);
        b1.add(Box.createVerticalStrut(10));

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalStrut(25));
        b7.add(new JLabel("Least number of categories for each variable:  "));
        b7.add(Box.createHorizontalGlue());
        b7.add(lowerBoundField);
        b1.add(b7);

        Box b8 = Box.createHorizontalBox();
        b8.add(Box.createHorizontalStrut(25));
        b8.add(new JLabel(
                "Greatest number of categories for each variable:  "));
        b8.add(Box.createHorizontalGlue());
        b8.add(upperBoundField);
        b1.add(b8);

        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);

        if (getParams().getInitializationMode() == BayesPmParams.MANUAL) {
            setUpManually.setSelected(true);
            lowerBoundField.setEnabled(false);
            upperBoundField.setEnabled(false);
        }
        else {
            automaticallyAssigned.setSelected(true);
            lowerBoundField.setEnabled(true);
            upperBoundField.setEnabled(true);
        }

        setUpManually.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setInitializationMode(BayesPmParams.MANUAL);
                lowerBoundField.setEnabled(false);
                upperBoundField.setEnabled(false);
            }
        });

        automaticallyAssigned.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().setInitializationMode(BayesPmParams.AUTOMATIC);
                lowerBoundField.setEnabled(true);
                upperBoundField.setEnabled(true);
            }
        });
    }

    public boolean mustBeShown() {
        return false;
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     *
     * @return the stored simulation parameters model.
     */
    private synchronized BayesPmParams getParams() {
        return this.params;
    }
}





