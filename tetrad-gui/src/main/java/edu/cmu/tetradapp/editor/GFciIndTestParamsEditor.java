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

import edu.cmu.tetradapp.model.GFciIndTestParams;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;


/**
 * Edits the properties of a measurement params.
 *
 * @author Joseph Ramsey
 */
class GFciIndTestParamsEditor extends JComponent {

    /**
     * The parameters object being edited.
     */
    private GFciIndTestParams params = null;

    /**
     * A text field to allow the user to enter the number of dishes to
     * generate.
     */
    private DoubleTextField alphaField;

    /**
     * A text field to allow the user to enter the number of dishes to
     * generate.
     */
    private IntTextField depthField;

    private DoubleTextField penaltyDiscount;
    private DoubleTextField samplePrior;
    private DoubleTextField structurePrior;

    /**
     * A checkbox to allow the user to specify whether the complete rule set should be used.
     */
    private JCheckBox completeRuleSetCheckBox;

    /**
     * A checkbox to allow the user to specify whether possible DSEP should be done.
     */
    private JCheckBox possibleDsepCheckBox;

    /**
     * An int field to specify the maximum length of reachable undirectedPaths (in discriminating path orientation and
     * possible dsep).
     */
    private IntTextField maxReachablePathLengthField;

    /**
     * A checkbox to allow the user to specify whether to use RFCI
     */
    private JCheckBox faithfulnessAssumed;

    /**
     * Constructs a dialog to edit the given gene simulation parameters object.
     */
    public GFciIndTestParamsEditor(GFciIndTestParams params) {
        this.params = params;

        NumberFormat smallNumberFormat = new DecimalFormat("0E00");

        // set up text and ties them to the parameters object being edited.
        alphaField = new DoubleTextField(indTestParams().getAlpha(), 8,
                new DecimalFormat("0.0########"), smallNumberFormat, 1e-4);
        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    indTestParams().setAlpha(value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        depthField = new IntTextField(indTestParams().getDepth(), 5);
        depthField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    indTestParams().setDepth(value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });


        penaltyDiscount = new DoubleTextField(indTestParams().getPenaltyDiscount(), 8,
                new DecimalFormat("0.0########"), smallNumberFormat, 1e-4);
        penaltyDiscount.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    indTestParams().setPenaltyDiscount(value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        samplePrior = new DoubleTextField(indTestParams().getSamplePrior(), 8,
                new DecimalFormat("0.0########"), smallNumberFormat, 1e-4);
        samplePrior.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    indTestParams().setSamplePrior(value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        structurePrior = new DoubleTextField(indTestParams().getStructurePrior(), 8,
                new DecimalFormat("0.0########"), smallNumberFormat, 1e-4);
        structurePrior.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    indTestParams().setStructurePrior(value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });


        completeRuleSetCheckBox = new JCheckBox();
        completeRuleSetCheckBox.setSelected(indTestParams().isCompleteRuleSetUsed());
        completeRuleSetCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JCheckBox source = (JCheckBox) actionEvent.getSource();
                indTestParams().setCompleteRuleSetUsed(source.isSelected());
            }
        });

        possibleDsepCheckBox = new JCheckBox();
        possibleDsepCheckBox.setSelected(indTestParams().isPossibleDsepDone());
        possibleDsepCheckBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JCheckBox source = (JCheckBox) actionEvent.getSource();
                indTestParams().setPossibleDsepDone(source.isSelected());
            }
        });

        maxReachablePathLengthField = new IntTextField(indTestParams().getMaxReachablePathLength(), 3);
        maxReachablePathLengthField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    indTestParams().setMaxReachablePathLength(value);
                    return value;
                } catch (Exception e) {
                    return oldValue;
                }
            }
        });


        faithfulnessAssumed = new JCheckBox();
        faithfulnessAssumed.setSelected(indTestParams().isFaithfulnessAssumed());
        faithfulnessAssumed.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                JCheckBox source = (JCheckBox) actionEvent.getSource();
                indTestParams().setFaithfulnessAssumed(source.isSelected());
            }
        });

        buildGui();
    }

    /**
     * Constructs the Gui used to edit properties; called from each constructor.
     * Constructs labels and text fields for editing each property and adds
     * appropriate listeners.
     */
    private void buildGui() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Alpha:"));
        b1.add(Box.createHorizontalStrut(10));
        b1.add(Box.createHorizontalGlue());
        b1.add(alphaField);
        add(b1);

        Box b1a = Box.createHorizontalBox();
        b1a.add(new JLabel("Penaty Discount (Continuous):"));
        b1a.add(Box.createHorizontalStrut(10));
        b1a.add(Box.createHorizontalGlue());
        b1a.add(penaltyDiscount);
        add(b1a);

        Box b1b = Box.createHorizontalBox();
        b1b.add(new JLabel("Sample Prior (Discrete):"));
        b1b.add(Box.createHorizontalStrut(10));
        b1b.add(Box.createHorizontalGlue());
        b1b.add(samplePrior);
        add(b1b);

        Box b1c = Box.createHorizontalBox();
        b1c.add(new JLabel("Structure Prior (Discrete):"));
        b1c.add(Box.createHorizontalStrut(10));
        b1c.add(Box.createHorizontalGlue());
        b1c.add(structurePrior);
        add(b1c);

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Depth:"));
        b2.add(Box.createHorizontalStrut(10));
        b2.add(Box.createHorizontalGlue());
        b2.add(depthField);
        add(b2);

        Box b5 = Box.createHorizontalBox();
        b5.add(new JLabel("Max reachable path length: "));
        b5.add(Box.createHorizontalGlue());
        b5.add(maxReachablePathLengthField);
        add(b5);

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Use complete rule set: "));
        b3.add(completeRuleSetCheckBox);
        add(b3);

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Do possible DSEP search: "));
        b4.add(possibleDsepCheckBox);
        add(b4);

        Box b4a = Box.createHorizontalBox();
        b4a.add(new JLabel("Length 1 faithfulness assumed "));
        b4a.add(faithfulnessAssumed);
        add(b4a);

        add(Box.createHorizontalGlue());
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     */
    private GFciIndTestParams indTestParams() {
        return params;
    }
}


