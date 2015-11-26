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

import edu.cmu.tetrad.search.BpcAlgorithmType;
import edu.cmu.tetrad.search.TestType;
import edu.cmu.tetradapp.model.BuildPureClustersIndTestParams;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Edits the properties of a measurement paramsPureClusters. See
 * BasicIndTestParamsEditor for more explanations.
 *
 * @author Ricardo Silva
 */
class BuildPureClustersIndTestParamsEditor2 extends JComponent {
    private BuildPureClustersIndTestParams paramsPureClusters;

    public BuildPureClustersIndTestParamsEditor2(
            BuildPureClustersIndTestParams paramsPureClusters,
            boolean discreteData) {
        this.paramsPureClusters = paramsPureClusters;

        NumberFormat smallNumberFormat = new DecimalFormat("0E00");
        final DoubleTextField alphaField = new DoubleTextField(getParams().getAlpha(), 8,
                new DecimalFormat("0.0########"), smallNumberFormat, 1e-4);

        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    getParams().setAlpha(value);
                    return value;
                } catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final JComboBox testSelector = new JComboBox();

        if (!discreteData) {
            final TestType[] descriptions = TestType.getTestDescriptions();
            testSelector.removeAllItems();
            for (int i = 0; i < descriptions.length; i++) {
                testSelector.addItem(descriptions[i]);
            }

            TestType tetradTestType = getParams().getTetradTestType();
            testSelector.setSelectedItem(tetradTestType);

            testSelector.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JComboBox combo = (JComboBox) e.getSource();
                    TestType index = (TestType) combo.getSelectedItem();
                    if (index != null) {
                        getParams().setTetradTestType(index);
                    }
                }
            });
        }

        JComboBox algorithmSelector = null;

        final BpcAlgorithmType[] descriptions = BpcAlgorithmType.getAlgorithmDescriptions();
        algorithmSelector = new JComboBox(descriptions);
        algorithmSelector.setSelectedItem(getParams().getAlgorithmType());

        if (getParams().getAlgorithmType() == BpcAlgorithmType.FIND_TWO_FACTOR_CLUSTERS) {
            testSelector.removeAllItems();
            testSelector.addItem(TestType.SAG);
            testSelector.addItem(TestType.GAP);
            testSelector.setSelectedItem(TestType.GAP);
        } else {
            TestType type1 = (TestType) testSelector.getItemAt(0);
            TestType type2 = (TestType) testSelector.getItemAt(1);

            if (!(type1 == TestType.TETRAD_WISHART && type2 == TestType.TETRAD_DELTA)) {
                testSelector.removeAllItems();
                testSelector.addItem(TestType.TETRAD_WISHART);
                testSelector.addItem(TestType.TETRAD_DELTA);
            }
        }

        TestType tetradTestType = getParams().getTetradTestType();
        testSelector.setSelectedItem(tetradTestType);

        if (paramsPureClusters.getTetradTestType() == TestType.TETRAD_WISHART) {
            testSelector.setSelectedItem(TestType.TETRAD_WISHART);
            getParams().setTetradTestType(TestType.TETRAD_WISHART);
        } else {
            testSelector.setSelectedItem(TestType.TETRAD_DELTA);
            getParams().setTetradTestType(TestType.TETRAD_DELTA);
        }

        algorithmSelector.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox combo = (JComboBox) e.getSource();
                BpcAlgorithmType type = (BpcAlgorithmType) combo.getSelectedItem();
                getParams().setAlgorithmType(type);

                if (type == BpcAlgorithmType.FIND_TWO_FACTOR_CLUSTERS) {
                    testSelector.removeAllItems();
                    testSelector.addItem(TestType.SAG);
                    testSelector.addItem(TestType.GAP);
                    testSelector.setSelectedItem(TestType.GAP);
                } else {
                    testSelector.removeAllItems();
                    testSelector.addItem(TestType.TETRAD_WISHART);
                    testSelector.addItem(TestType.TETRAD_DELTA);

                    if (getParams().getTetradTestType() == TestType.TETRAD_WISHART) {
                        testSelector.setSelectedItem(TestType.TETRAD_WISHART);
                        getParams().setTetradTestType(TestType.TETRAD_WISHART);
                    } else {
                        testSelector.setSelectedItem(TestType.TETRAD_DELTA);
                        getParams().setTetradTestType(TestType.TETRAD_DELTA);
                    }
                }
            }
        });

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        if (!discreteData) {
            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Test:"));
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
        b3.add(Box.createHorizontalGlue());
        b3.add(alphaField);
        add(b3);
        add(Box.createHorizontalGlue());

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Algorithm:"));
        b4.add(Box.createHorizontalGlue());
        b4.add(algorithmSelector);
        add(b4);
        add(Box.createHorizontalGlue());

//        if (discreteData) {
//            paramsPureClusters.setPurifyTestType(
//                    BuildPureClusters.PURIFY_TEST_DISCRETE_LRT);
//            paramsPureClusters.setTetradTestType(BuildPureClusters.TEST_DISCRETE);
//
//        }
    }

    private BuildPureClustersIndTestParams getParams() {
        return this.paramsPureClusters;
    }


}





