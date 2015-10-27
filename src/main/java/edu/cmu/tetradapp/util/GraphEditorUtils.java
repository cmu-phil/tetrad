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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;

/**
 * Stores some static methods useful for graph editing.
 *
 * @author Joseph Ramsey
 */
public class GraphEditorUtils {

    /**
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

        randomCombo.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JComboBox combo = (JComboBox) e.getSource();
                String selection = (String) combo.getSelectedItem();
                if ("No".equals(selection)) {
                    Preferences.userRoot().putBoolean(
                            "kamadaKawaiLayoutInitializeRandomly", false);
                }
                else {
                    Preferences.userRoot().putBoolean(
                            "kamadaKawaiLayoutInitializeRandomly", true);
                }
            }
        });

        DoubleTextField naturalEdgeLengthField = new DoubleTextField(
                naturalEdgeLength, 4, NumberFormatUtil.getInstance().getNumberFormat());
        naturalEdgeLengthField.setFilter(
                new DoubleTextField.Filter() {
                    public double filter(double value, double oldValue) {
                        if (value <= 0.0) {
                            return oldValue;
                        }

                        Preferences.userRoot().putDouble(
                                "kamadaKawaiLayoutNaturalEdgeLength", value);
                        return value;
                    }
                });

        DoubleTextField springConstantField = new DoubleTextField(
                springConstant, 4, NumberFormatUtil.getInstance().getNumberFormat());
        springConstantField.setFilter(
                new DoubleTextField.Filter() {
                    public double filter(double value, double oldValue) {
                        if (value < 0.0) {
                            return oldValue;
                        }


                        Preferences.userRoot().putDouble(
                                "kamadaKawaiLayoutSpringConstant", value);
                        return value;
                    }
                });

        DoubleTextField stopEnergyField =
                new DoubleTextField(stopEnergy, 4, NumberFormatUtil.getInstance().getNumberFormat());
        stopEnergyField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                if (value < 0.0) {
                    return oldValue;
                }

                Preferences.userRoot().putDouble("kamadaKawaiLayoutStopEnergy",
                        value);
                return value;
            }
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





