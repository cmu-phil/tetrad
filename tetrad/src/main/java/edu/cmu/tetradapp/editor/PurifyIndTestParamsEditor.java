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

import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetradapp.model.PurifyIndTestParams;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Edits the properties of a measurement params. See BasicIndTestParamsEditor
 * for more explanations.
 *
 * @author Ricardo Silva
 */
class PurifyIndTestParamsEditor extends JPanel {
    private PurifyIndTestParams params;

    public PurifyIndTestParamsEditor(PurifyIndTestParams params,
            boolean discreteData) {
        this.params = params;

        NumberFormat smallNumberFormat = new DecimalFormat("0E00");
        final DoubleTextField alphaField = new DoubleTextField(getParams().getAlpha(), 8,
                new DecimalFormat("0.0########"), smallNumberFormat, 1e-4);

        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    getParams().setAlpha(value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        JComboBox testSelector = null;
//        JComboBox purifySelector = null;

        if (!discreteData) {
            final TestType[] descriptions = TestType.getTestDescriptions();
            testSelector = new JComboBox(descriptions);
            testSelector.setSelectedItem(getParams().getTetradTestType());

            testSelector.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JComboBox combo = (JComboBox) e.getSource();
                    TestType index = (TestType) combo.getSelectedItem();
                    getParams().setTetradTestType(index);
                }
            });

//            final TestType[] purifyDescriptions = TestType.getPurifyTestDescriptions();
//            purifySelector = new JComboBox(purifyDescriptions);
//            purifySelector.setSelectedItem(getParams().getPurifyTestType());
//
//            purifySelector.addActionListener(new ActionListener() {
//                public void actionPerformed(ActionEvent e) {
//                    JComboBox combo = (JComboBox) e.getSource();
//                    TestType index = (TestType) combo.getSelectedItem();
//                    getParams().setPurifyTestType(index);
//                }
//            });
        }

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        Box b = Box.createVerticalBox();

        if (!discreteData) {
            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Tetrad Test:"));
            b1.add(Box.createHorizontalGlue());
            b1.add(testSelector);
            add(b1);
            add(Box.createHorizontalGlue());

//            Box b2 = Box.createHorizontalBox();
//            b2.add(new JLabel("Purify:"));
//            b2.add(Box.createHorizontalGlue());
//            b2.add(purifySelector);
//            add(b2);
//            add(Box.createHorizontalGlue());
        }


        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Alpha:"));
        b3.add(Box.createHorizontalStrut(10));
        b3.add(Box.createHorizontalGlue());
        b3.add(alphaField);
        b.add(b3);

        add(b, BorderLayout.CENTER);
    }

    private PurifyIndTestParams getParams() {
        return this.params;
    }
}





