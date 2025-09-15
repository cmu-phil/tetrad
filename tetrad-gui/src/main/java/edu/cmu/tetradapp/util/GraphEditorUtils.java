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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * Stores some static methods useful for graph editing.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraphEditorUtils {

    /**
     * <p>editkamadaKawaiLayoutParams.</p>
     */
    public static void editkamadaKawaiLayoutParams() {
        boolean initializeRandomly = Preferences.userRoot().getBoolean(
                "kamadaKawaiLayoutInitializeRandomly", false);
        double naturalEdgeLength = Preferences.userRoot().getDouble(
                "kamadaKawaiLayoutNaturalEdgeLength", 80.0);
        double springConstant = Preferences.userRoot().getDouble(
                "kamadaKawaiLayoutSpringConstant", 0.2);
        double stopEnergy = Preferences.userRoot().getDouble(
                "kamadaKawaiLayoutStopEnergy", 1.0);

        JComboBox randomCombo = new JComboBox(new String[]{"No", "Yes"});
        randomCombo.setMaximumSize(randomCombo.getPreferredSize());

        if (initializeRandomly) {
            randomCombo.setSelectedItem("Yes");
        }

        randomCombo.addActionListener(e -> {
            JComboBox combo = (JComboBox) e.getSource();
            String selection = (String) combo.getSelectedItem();
            Preferences.userRoot().putBoolean(
                    "kamadaKawaiLayoutInitializeRandomly", !"No".equals(selection));
        });

        DoubleTextField naturalEdgeLengthField = new DoubleTextField(
                naturalEdgeLength, 4, NumberFormatUtil.getInstance().getNumberFormat());
        naturalEdgeLengthField.setFilter(
                (value, oldValue) -> {
                    if (value <= 0.0) {
                        return oldValue;
                    }

                    Preferences.userRoot().putDouble(
                            "kamadaKawaiLayoutNaturalEdgeLength", value);
                    return value;
                });

        DoubleTextField springConstantField = new DoubleTextField(
                springConstant, 4, NumberFormatUtil.getInstance().getNumberFormat());
        springConstantField.setFilter(
                (value, oldValue) -> {
                    if (value < 0.0) {
                        return oldValue;
                    }


                    Preferences.userRoot().putDouble(
                            "kamadaKawaiLayoutSpringConstant", value);
                    return value;
                });

        DoubleTextField stopEnergyField =
                new DoubleTextField(stopEnergy, 4, NumberFormatUtil.getInstance().getNumberFormat());
        stopEnergyField.setFilter((value, oldValue) -> {
            if (value < 0.0) {
                return oldValue;
            }

            Preferences.userRoot().putDouble("kamadaKawaiLayoutStopEnergy",
                    value);
            return value;
        });

        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Initialize randomly? "));
        b1.add(Box.createHorizontalGlue());
        b1.add(randomCombo);
        b.add(b1);

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Natural edge length: "));
        b2.add(Box.createHorizontalGlue());
        b2.add(naturalEdgeLengthField);
        b.add(b2);

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Strength of springs: "));
        b3.add(Box.createHorizontalGlue());
        b3.add(springConstantField);
        b.add(b3);

        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Stop at energy = "));
        b4.add(Box.createHorizontalGlue());
        b4.add(stopEnergyField);
        b.add(b4);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(b, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(JOptionUtils.centeringComp(), panel,
                "Spring Layout Parameters", JOptionPane.PLAIN_MESSAGE);
    }

}






