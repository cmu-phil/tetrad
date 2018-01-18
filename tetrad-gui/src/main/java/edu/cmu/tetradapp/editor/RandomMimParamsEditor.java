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
import edu.cmu.tetradapp.util.IntTextField;
import java.awt.BorderLayout;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Edits the parameters for generating random graphs.
 *
 * @author Joseph Ramsey
 */
class RandomMimParamsEditor extends JPanel {

    private static final long serialVersionUID = -1478898170626611725L;

    private static final Logger LOGGER = LoggerFactory.getLogger(RandomMimParamsEditor.class);

    /**
     * Constructs a dialog to edit the given workbench randomization parameters.
     */
    public RandomMimParamsEditor(final Parameters parameters) {
        final JComboBox<String> numFactors = new JComboBox<>();

        numFactors.addItem("1");
        numFactors.addItem("2");

        numFactors.addActionListener((e) -> {
            if (numFactors.getSelectedItem().equals("1")) {
                parameters.set("randomMimNumFactors", 1);
            } else if (numFactors.getSelectedItem().equals("2")) {
                parameters.set("randomMimNumFactors", 2);
            }
        });

        numFactors.setSelectedItem(Integer.toString(parameters.getInt("randomMimNumFactors", 1)));

        numFactors.setMaximumSize(numFactors.getPreferredSize());

        final IntTextField numStructuralEdges = new IntTextField(
                parameters.getInt("numStructuralEdges", 3), 4);
        numStructuralEdges.setFilter((value, oldValue) -> {
            try {
                int n = parameters.getInt("numStructuralNodes", 3);
                int maxNumLatentEdges = n * (n - 1) / 2;

                if (value > maxNumLatentEdges) {
                    value = maxNumLatentEdges;
                }

                parameters.set("numStructuralEdges", value);
                return value;
            } catch (Exception exception) {
                LOGGER.error("", exception);

                return oldValue;
            }
        });

        IntTextField numStructuralNodes = new IntTextField(
                parameters.getInt("numStructuralNodes", 3), 4);
        numStructuralNodes.setFilter((value, oldValue) -> {
            try {
                if (value < 1) {
                    throw new IllegalArgumentException(
                            "Number of structural "
                            + "nodes Must be greater than 0: " + value);
                }

                parameters.set("numStructuralNodes", value);
                numStructuralEdges.setValue(numStructuralEdges.getValue());
                return value;
            } catch (Exception exception) {
                LOGGER.error("", exception);

                numStructuralEdges.setValue(numStructuralEdges.getValue());
                return oldValue;
            }
        });

        IntTextField numMeasurementsPerLatent = new IntTextField(
                parameters.getInt("measurementModelDegree", 5), 4);
        numMeasurementsPerLatent.setFilter((value, oldValue) -> {
            try {
                if (value < 2) {
                    throw new IllegalArgumentException();
                }

                parameters.set("measurementModelDegree", value);
                return value;
            } catch (Exception exception) {
                LOGGER.error("", exception);

                return oldValue;
            }
        });

        IntTextField numLatentMeasuredImpureParents = new IntTextField(
                parameters.getInt("latentMeasuredImpureParents", 0), 4);
        numLatentMeasuredImpureParents.setFilter((value, oldValue) -> {
            try {
                if (value < 0) {
                    throw new IllegalArgumentException();
                }

                parameters.set("latentMeasuredImpureParents", value);
                return value;
            } catch (Exception exception) {
                LOGGER.error("", exception);

                return oldValue;
            }
        });

        IntTextField numMeasuredMeasuredImpureParents = new IntTextField(
                parameters.getInt("measuredMeasuredImpureParents", 0), 4);
        numMeasuredMeasuredImpureParents.setFilter((value, oldValue) -> {
            try {
                if (value < 0) {
                    throw new IllegalArgumentException();
                }

                parameters.set("measuredMeasuredImpureParents", value);
                return value;
            } catch (Exception exception) {
                LOGGER.error("", exception);

                return oldValue;
            }
        });

        IntTextField numMeasuredMeasuredImpureAssociations = new IntTextField(
                parameters.getInt("measuredMeasuredImpureAssociations", 0), 4);
        numMeasuredMeasuredImpureAssociations.setFilter((value, oldValue) -> {
            try {
                if (value < 0) {
                    throw new IllegalArgumentException();
                }

                parameters.set("measuredMeasuredImpureAssociations",
                        value);
                return value;
            } catch (Exception exception) {
                LOGGER.error("", exception);

                return oldValue;
            }
        });

        // construct the workbench.
        setLayout(new BorderLayout());

        Box b1 = Box.createVerticalBox();

        Box b9 = Box.createHorizontalBox();
        b9.add(new JLabel("Number of Factors:"));
        b9.add(Box.createHorizontalGlue());
        b9.add(numFactors);
        b1.add(b9);

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
