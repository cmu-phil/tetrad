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

import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/**
 * Edits the parameters for generating random graphs.
 *
 * @author Joseph Ramsey
 */
class RandomMimParamsEditor extends JPanel {

    /**
     * Constructs a dialog to edit the given workbench randomization
     * parameters.
     */
    public RandomMimParamsEditor() {
        final Preferences preferences = Preferences.userRoot();

        final IntTextField numStructuralEdges = new IntTextField(
                preferences.getInt("numStructuralEdges", 3), 4);
        numStructuralEdges.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    int n = preferences.getInt("numStructuralNodes", 3);
                    int maxNumLatentEdges = n * (n - 1) / 2;

                    if (value > maxNumLatentEdges) {
                        value = maxNumLatentEdges;
                    }

                    preferences.putInt("numStructuralEdges", value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        IntTextField numStructuralNodes = new IntTextField(
                preferences.getInt("numStructuralNodes", 3), 4);
        numStructuralNodes.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    if (value < 1) {
                        throw new IllegalArgumentException(
                                "Number of structural " +
                                        "nodes Must be greater than 0: " + value);
                    }

                    preferences.putInt("numStructuralNodes", value);
                    numStructuralEdges.setValue(numStructuralEdges.getValue());
                    return value;
                }
                catch (Exception e) {
                    numStructuralEdges.setValue(numStructuralEdges.getValue());
                    return oldValue;
                }

            }
        });

        IntTextField numMeasurementsPerLatent = new IntTextField(
                preferences.getInt("measurementModelDegree", 1), 4);
        numMeasurementsPerLatent.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    if (value < 2) {
                        throw new IllegalArgumentException();
                    }

                    preferences.putInt("measurementModelDegree", value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        IntTextField numLatentMeasuredImpureParents = new IntTextField(
                preferences.getInt("latentMeasuredImpureParents", 0), 4);
        numLatentMeasuredImpureParents.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    if (value < 0) {
                        throw new IllegalArgumentException();
                    }

                    preferences.putInt("latentMeasuredImpureParents", value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        IntTextField numMeasuredMeasuredImpureParents = new IntTextField(
                preferences.getInt("measuredMeasuredImpureParents", 0), 4);
        numMeasuredMeasuredImpureParents.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    if (value < 0) {
                        throw new IllegalArgumentException();
                    }

                    preferences.putInt("measuredMeasuredImpureParents", value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        IntTextField numMeasuredMeasuredImpureAssociations = new IntTextField(
                preferences.getInt("measuredMeasuredImpureAssociations", 0),
                4);
        numMeasuredMeasuredImpureAssociations.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    if (value < 0) {
                        throw new IllegalArgumentException();
                    }

                    preferences.putInt("measuredMeasuredImpureAssociations",
                            value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        // construct the workbench.
        setLayout(new BorderLayout());

        Box b1 = Box.createVerticalBox();

        Box b10 = Box.createHorizontalBox();
        b10.add(new JLabel("Number of structural nodes:"));
//        b10.add(Box.createRigidArea(new Dimension(10, 0)));
        b10.add(Box.createHorizontalGlue());
        b10.add(numStructuralNodes);
        b1.add(b10);

        Box b12 = Box.createHorizontalBox();
        b12.add(new JLabel("Number of structural edges:"));
        b12.add(Box.createHorizontalGlue());
        b12.add(numStructuralEdges);
        b1.add(b12);

        Box b15 = Box.createHorizontalBox();
        b15.add(new JLabel("Number of measurements per latent:"));
//        b15.add(Box.createHorizontalStrut(10));
        b15.add(Box.createHorizontalGlue());
        b15.add(numMeasurementsPerLatent);
        b1.add(b15);
        b1.add(Box.createVerticalStrut(10));

        Box b16 = Box.createHorizontalBox();
        b16.add(new JLabel("Add impure edges:"));
        b16.add(Box.createHorizontalGlue());
        b1.add(b16);
//        b1.add(Box.createVerticalStrut(5));

        Box b17 = Box.createHorizontalBox();
        b17.add(new JLabel("Latent --> Measured"));
        b17.add(Box.createHorizontalGlue());
        b17.add(numLatentMeasuredImpureParents);
        b1.add(b17);

        Box b18 = Box.createHorizontalBox();
        b18.add(new JLabel("Measured --> Measured"));
        b18.add(Box.createHorizontalGlue());
        b18.add(numMeasuredMeasuredImpureParents);
        b1.add(b18);

        Box b19 = Box.createHorizontalBox();
        b19.add(new JLabel("Measured <-> Measured"));
        b19.add(Box.createHorizontalGlue());
        b19.add(numMeasuredMeasuredImpureAssociations);
        b1.add(b19);

        b1.add(Box.createVerticalGlue());
        add(b1, BorderLayout.CENTER);
    }
}





