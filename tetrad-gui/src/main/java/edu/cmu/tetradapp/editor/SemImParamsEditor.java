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
import edu.cmu.tetradapp.model.SemImParams;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;


/**
 * Edits the parameters for simulating data from SEMs.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class SemImParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private SemImParams params = null;

    /**
     * Constructs a dialog to edit the given workbench SEM simulation
     * getMappings object.
     */
    public SemImParamsEditor() {
    }

    public void setParams(Params params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = (SemImParams) params;
    }

    public void setParentModels(Object[] parentModels) {
        // Do nothing.
    }

    /**
     * Constructs the Gui used to edit properties; called from each constructor.
     * Constructs labels and text fields for editing each property and adds
     * appropriate listeners.
     */
    public void setup() {
        setLayout(new BorderLayout());

        final JCheckBox randomEveryTime = new JCheckBox();
        randomEveryTime.setSelected(!params.isRetainPreviousValues());
        DecimalFormat decimalFormat = new DecimalFormat("0.0######");

        final DoubleTextField coefLowField = new DoubleTextField(params.getCoefLow(),
                6, decimalFormat);

        coefLowField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.setCoefRange(value, params.getCoefHigh());
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });


        final DoubleTextField coefHighField = new DoubleTextField(params.getCoefHigh(),
                6, decimalFormat);

        coefHighField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.setCoefRange(params.getCoefLow(), value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final DoubleTextField covLowField = new DoubleTextField(params.getCovLow(),
                6, decimalFormat);

        covLowField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.setCovRange(value, params.getCovHigh());
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final DoubleTextField covHighField = new DoubleTextField(params.getCovHigh(),
                6, decimalFormat);

        covHighField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.setCovRange(params.getCovLow(), value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final DoubleTextField varLowField = new DoubleTextField(params.getVarLow(),
                6, decimalFormat);

        varLowField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.setVarRange(value, params.getVarHigh());
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final DoubleTextField varHighField = new DoubleTextField(params.getVarHigh(),
                6, decimalFormat);

        varHighField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.setVarRange(params.getVarLow(), value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final JCheckBox coefSymmetric = new JCheckBox("Symmetric about zero.");
        final JCheckBox covSymmetric = new JCheckBox("Symmetric about zero.");

        coefSymmetric.setSelected(params.isCoefSymmetric());
        covSymmetric.setSelected(params.isCovSymmetric());

        coefSymmetric.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                params.setCoefSymmetric(checkBox.isSelected());
            }

        });

        covSymmetric.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                params.setCovSymmetric(checkBox.isSelected());
            }
        });

        randomEveryTime.setText("Pick new random values each time this SEM IM is reinitialized.");
        randomEveryTime.setVerticalTextPosition(SwingConstants.TOP);

        randomEveryTime.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                getParams().setRetainPreviousValues(!checkBox.isSelected());
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel(
                "Unfixed parameter values for this SEM IM are drawn as follows:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));

        Box b4a = Box.createHorizontalBox();
//        b4a.add(Box.createHorizontalStrut(10));
        b4a.add(new JLabel("Coefficient values are drawn from "));
        b4a.add(new BigLabel("("));
        b4a.add(coefLowField);
        b4a.add(new BigLabel(", "));
        b4a.add(coefHighField);
        b4a.add(new BigLabel(") "));
        b4a.add(coefSymmetric);
        b4a.add(Box.createHorizontalGlue());
        b1.add(b4a);

        Box b4b = Box.createHorizontalBox();
//        b4b.add(Box.createHorizontalStrut(10));
        b4b.add(new JLabel("Error covariance values are drawn from "));
        b4b.add(new BigLabel("("));
        b4b.add(covLowField);
        b4b.add(new BigLabel(", "));
        b4b.add(covHighField);
        b4b.add(new BigLabel(") "));
        b4b.add(covSymmetric);
        b4b.add(Box.createHorizontalGlue());
        b1.add(b4b);

        Box b4c = Box.createHorizontalBox();
//        b4c.add(Box.createHorizontalStrut(10));
        b4c.add(new JLabel("Error standard deviation values are drawn from "));
        b4c.add(new BigLabel("("));
        b4c.add(varLowField);
        b4c.add(new BigLabel(", "));
        b4c.add(varHighField);
        b4c.add(new BigLabel(")"));
        b4c.add(new JLabel("."));
        b4c.add(Box.createHorizontalGlue());
        b1.add(b4c);

        Box b5 = Box.createHorizontalBox();
//        b5.add(Box.createHorizontalStrut(10));
        b5.add(randomEveryTime);
        b5.add(Box.createHorizontalGlue());
        b1.add(b5);

        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
        setBorder(new EmptyBorder(5, 5, 5, 5));        
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
    private synchronized SemImParams getParams() {
        return this.params;
    }

    final static class BigLabel extends JLabel {
        private static Font FONT = new Font("Dialog", Font.BOLD, 20);

        public BigLabel(String text) {
            super(text);
            setFont(FONT);
        }
    }
}


