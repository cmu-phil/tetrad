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

import edu.cmu.tetrad.util.Parameters;
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
    private Parameters params;

    /**
     * Constructs a dialog to edit the given workbench SEM simulation
     * getMappings object.
     */
    public SemImParamsEditor() {
    }

    public void setParams(final Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }

    public void setParentModels(final Object[] parentModels) {
        // Do nothing.
    }

    /**
     * Constructs the Gui used to edit properties; called from each constructor.
     * Constructs labels and text fields for editing each property and adds
     * appropriate listeners.
     */
    public void setup() {
        setLayout(new BorderLayout());

//        final JCheckBox randomEveryTime = new JCheckBox();
//        randomEveryTime.setSelected(!params.getBoolean("retainPreviousValues", false));
        final DecimalFormat decimalFormat = new DecimalFormat("0.0######");

        final DoubleTextField coefLowField = new DoubleTextField(this.params.getDouble("coefLow"),
                6, decimalFormat);

        coefLowField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    SemImParamsEditor.this.getParams().set("coefLow", value);
                    SemImParamsEditor.this.getParams().set("coefHigh", params.getDouble("coefHigh"));
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });


        DoubleTextField coefHighField = new DoubleTextField(params.getDouble("coefHigh"),
                6, decimalFormat);

        coefHighField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    SemImParamsEditor.this.getParams().set("coefLow", params.getDouble("coefLow"));
                    SemImParamsEditor.this.getParams().set("coefHigh", value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        DoubleTextField covLowField = new DoubleTextField(params.getDouble("covLow", 0.0),
                6, decimalFormat);

        covLowField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.set("covLow", value);
                    params.set("covHigh", params.getDouble("covHigh"));
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        DoubleTextField covHighField = new DoubleTextField(params.getDouble("covHigh", 0.0),
                6, decimalFormat);

        covHighField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.set("covLow", params.getDouble("covLow"));
                    params.set("covHigh", value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        DoubleTextField varLowField = new DoubleTextField(params.getDouble("varLow", 1),
                6, decimalFormat);

        varLowField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.set("varLow", value);
                    params.set("varHigh", params.getDouble("varHigh"));
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        DoubleTextField varHighField = new DoubleTextField(params.getDouble("varHigh"),
                6, decimalFormat);

        varHighField.setFilter(new DoubleTextField.Filter() {
            public double filter(final double value, final double oldValue) {
                try {
                    SemImParamsEditor.this.params.set("varLow", SemImParamsEditor.this.params.getDouble("varLow"));
                    SemImParamsEditor.this.params.set("varHigh", value);
                    return value;
                } catch (final IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final JCheckBox coefSymmetric = new JCheckBox("Symmetric about zero.");
        final JCheckBox covSymmetric = new JCheckBox("Symmetric about zero.");

        coefSymmetric.setSelected(this.params.getBoolean("coefSymmetric", true));
        covSymmetric.setSelected(this.params.getBoolean("covSymmetric", true));

        coefSymmetric.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final JCheckBox checkBox = (JCheckBox) e.getSource();
                SemImParamsEditor.this.params.set("coefSymmetric", checkBox.isSelected());
            }

        });

        covSymmetric.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final JCheckBox checkBox = (JCheckBox) e.getSource();
                SemImParamsEditor.this.params.set("covSymmetric", checkBox.isSelected());
            }
        });

//        randomEveryTime.setText("Pick new random values each time this SEM IM is reinitialized.");
//        randomEveryTime.setVerticalTextPosition(SwingConstants.TOP);
//
//        randomEveryTime.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                JCheckBox checkBox = (JCheckBox) e.getSource();
//                boolean retainPreviousValues = !checkBox.isSelected();
//                getParams().set("retainPreviousValues", retainPreviousValues);
//            }
//        });

        // continue workbench construction.
        final Box b1 = Box.createVerticalBox();

        final Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel(
                "Unfixed parameter values for this SEM IM are drawn as follows:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));

        final Box b4a = Box.createHorizontalBox();
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

        final Box b4b = Box.createHorizontalBox();
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

        final Box b4c = Box.createHorizontalBox();
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

//        Box b5 = Box.createHorizontalBox();
//        b5.add(Box.createHorizontalStrut(10));
//        b5.add(randomEveryTime);
//        b5.add(Box.createHorizontalGlue());
//        b1.add(b5);

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
     */
    private synchronized Parameters getParams() {
        return this.params;
    }

    static final class BigLabel extends JLabel {
        private static final Font FONT = new Font("Dialog", Font.BOLD, 20);

        public BigLabel(final String text) {
            super(text);
            setFont(BigLabel.FONT);
        }
    }
}


