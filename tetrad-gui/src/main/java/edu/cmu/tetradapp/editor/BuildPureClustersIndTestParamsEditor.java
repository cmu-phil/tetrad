///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.search.utils.BpcAlgorithmType;
import edu.cmu.tetrad.search.utils.BpcTestType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Edits the properties of a measurement paramsPureClusters. See BasicIndTestParamsEditor for more explanations.
 *
 * @author Ricardo Silva
 */
class BuildPureClustersIndTestParamsEditor extends JComponent {

    /**
     * The parameters to edit.
     */
    private final Parameters paramsPureClusters;

    /**
     * <p>Constructor for BuildPureClustersIndTestParamsEditor.</p>
     *
     * @param paramsPureClusters a {@link edu.cmu.tetrad.util.Parameters} object
     * @param discreteData       a boolean
     */
    public BuildPureClustersIndTestParamsEditor(
            Parameters paramsPureClusters,
            boolean discreteData) {
        this.paramsPureClusters = paramsPureClusters;

        NumberFormat smallNumberFormat = new DecimalFormat("0E00");
        DoubleTextField alphaField = new DoubleTextField(getParams().getDouble("alpha", 0.001), 8,
                new DecimalFormat("0.0########"), smallNumberFormat, 1e-4);

        alphaField.setFilter((value, oldValue) -> {
            try {
                getParams().set("alpha", 0.001);
                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        JComboBox testSelector = new JComboBox();

        if (!discreteData) {
            BpcTestType[] descriptions = BpcTestType.getTestDescriptions();
            testSelector.removeAllItems();
            for (BpcTestType description : descriptions) {
                testSelector.addItem(description);
            }

            testSelector.setSelectedItem(getParams().get("tetradTestType", BpcTestType.TETRAD_WISHART));

            testSelector.addActionListener(e -> {
                JComboBox combo = (JComboBox) e.getSource();
                BpcTestType index = (BpcTestType) combo.getSelectedItem();
                getParams().set("tetradTestType", index);
            });
        }

        JComboBox algorithmSelector;

//        final BpcAlgorithmType[] descriptions = BpcAlgorithmType.getAlgorithmDescriptions();
        BpcAlgorithmType[] descriptions = {BpcAlgorithmType.BUILD_PURE_CLUSTERS};
        algorithmSelector = new JComboBox(descriptions);
        algorithmSelector.setSelectedItem(getParams().get("bpcAlgorithmthmType", BpcAlgorithmType.FIND_ONE_FACTOR_CLUSTERS));

        algorithmSelector.addActionListener(e -> {
            JComboBox combo = (JComboBox) e.getSource();
            BpcAlgorithmType type = (BpcAlgorithmType) combo.getSelectedItem();
            getParams().set("bpcAlgorithmType", type);

            if (type == BpcAlgorithmType.FIND_TWO_FACTOR_CLUSTERS) {
                testSelector.removeAllItems();
                testSelector.addItem(BpcTestType.SAG);
                testSelector.addItem(BpcTestType.GAP);
            } else {
                testSelector.removeAllItems();
                testSelector.addItem(BpcTestType.TETRAD_WISHART);
                testSelector.addItem(BpcTestType.TETRAD_DELTA);

            }
            testSelector.revalidate();


            if (paramsPureClusters.get("tetradTestType", BpcTestType.TETRAD_WISHART) == BpcTestType.TETRAD_WISHART) {
                testSelector.setSelectedItem(BpcTestType.TETRAD_WISHART);
            } else {
                testSelector.setSelectedItem(BpcTestType.TETRAD_DELTA);
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

    }

    private Parameters getParams() {
        return this.paramsPureClusters;
    }


}






