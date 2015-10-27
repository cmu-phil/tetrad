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

import edu.cmu.tetradapp.model.GesIndTestParams;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Edits the properties of a GesSearch.
 *
 * @author Ricardo Silva
 */

class GesIndTestParamsEditor extends JComponent {
    private GesIndTestParams params;
    private DoubleTextField cellPriorField, structurePriorField;
//    private JButton defaultStructurePrior;
    private JButton uniformStructurePrior;
    private boolean discreteData;
    private DoubleTextField penaltyDiscount;
    private IntTextField numPatternsToSave;

    public GesIndTestParamsEditor(GesIndTestParams simulator, boolean discreteData) {
        this.params = simulator;
        this.discreteData = discreteData;

        NumberFormat nf = new DecimalFormat("0.0####");

        if (this.discreteData) {
            this.cellPriorField = new DoubleTextField(
                    getGesIndTestParams().getSamplePrior(), 5, nf);

            this.cellPriorField.setFilter(new DoubleTextField.Filter() {
                public double filter(double value, double oldValue) {
                    try {
                        getGesIndTestParams().setSamplePrior(value);
                        return value;
                    }
                    catch (IllegalArgumentException e) {
                        return oldValue;
                    }
                }
            });

            this.structurePriorField = new DoubleTextField(
                    getGesIndTestParams().getStructurePrior(), 5, nf);
            this.structurePriorField.setFilter(new DoubleTextField.Filter() {
                public double filter(double value, double oldValue) {
                    try {
                        getGesIndTestParams().setStructurePrior(value);
                        return value;
                    }
                    catch (IllegalArgumentException e) {
                        return oldValue;
                    }
                }
            });

//            this.defaultStructurePrior =
//                    new JButton("Default structure prior = 0.05");
            Font font = new Font("Dialog", Font.BOLD, 10);
//            this.defaultStructurePrior.setFont(font);
//            this.defaultStructurePrior.setBorder(null);
//            this.defaultStructurePrior.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    structurePriorField.setValue(0.05);
//                }
//            });

            this.uniformStructurePrior =
                    new JButton("Default structure prior = 1.0");
            this.uniformStructurePrior.setFont(font);
            this.uniformStructurePrior.setBorder(null);
            this.uniformStructurePrior.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    structurePriorField.setValue(1.0);
                }
            });
        } else {
            this.penaltyDiscount = new DoubleTextField(
                    getGesIndTestParams().getPenaltyDiscount(), 5, nf);
            this.penaltyDiscount.setFilter(new DoubleTextField.Filter() {
                public double filter(double value, double oldValue) {
                    try {
                        getGesIndTestParams().setPenaltyDiscount(value);
                        return value;
                    }
                    catch (IllegalArgumentException e) {
                        return oldValue;
                    }
                }
            });
        }

        this.numPatternsToSave = new IntTextField(
                getGesIndTestParams().getNumPatternsToSave(), 5);
        this.numPatternsToSave.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    getGesIndTestParams().setNumPatternsToSave(value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        buildGui();
    }

    private void buildGui() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        if (discreteData) {
            Box b0 = Box.createHorizontalBox();
            b0.add(new JLabel("BDeu:"));
            b0.add(Box.createHorizontalGlue());
            add(b0);
            add(Box.createVerticalStrut(5));

            Box b2 = Box.createHorizontalBox();
            b2.add(Box.createHorizontalStrut(5));
            b2.add(new JLabel("Sample prior:"));
            b2.add(Box.createHorizontalGlue());
            b2.add(this.cellPriorField);
            add(b2);
            add(Box.createVerticalStrut(5));

            Box b3 = Box.createHorizontalBox();
            b3.add(Box.createHorizontalStrut(5));
            b3.add(new JLabel("Structure prior:"));
            b3.add(Box.createHorizontalGlue());
            b3.add(this.structurePriorField);
            add(b3);

//            Box b4 = Box.createHorizontalBox();
//            b4.add(Box.createHorizontalGlue());
//            b4.add(this.defaultStructurePrior);
//            add(b4);

            Box b5 = Box.createHorizontalBox();
            b5.add(Box.createHorizontalGlue());
            b5.add(this.uniformStructurePrior);
            add(b5);
            add(Box.createVerticalStrut(10));
        } else {
            Box b7 = Box.createHorizontalBox();
            b7.add(new JLabel("Penalty Discount"));
            b7.add(Box.createHorizontalGlue());
            b7.add(this.penaltyDiscount);
            add(b7);
        }

        Box b8 = Box.createHorizontalBox();
        b8.add(new JLabel("Num Patterns to Save"));
        b8.add(Box.createHorizontalGlue());
        b8.add(this.numPatternsToSave);
        add(b8);
    }

    private GesIndTestParams getGesIndTestParams() {
        return params;
    }

}





