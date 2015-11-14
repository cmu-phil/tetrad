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

import edu.cmu.tetradapp.model.JpcIndTestParams;
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
class JpcIndTestParamsEditor extends JComponent {

    /**
     * The parameters object being edited.
     */
    private JpcIndTestParams params = null;

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

    private IntTextField maxAdjacenciesField;

    private IntTextField maxDescendantPathField;

    private JCheckBox useCpcBox;

    private JCheckBox startFromEmptyBox;

    private IntTextField maxIterationsField;


    /**
     * Constructs a dialog to edit the given gene simulation parameters object.
     */
    public JpcIndTestParamsEditor(JpcIndTestParams params) {
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
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        depthField = new IntTextField(indTestParams().getDepth(), 4);
        depthField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    indTestParams().setDepth(value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        maxAdjacenciesField = new IntTextField(indTestParams().getMaxAdjacencies(), 4);
        maxAdjacenciesField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    indTestParams().setMaxAdjacencies(value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        maxDescendantPathField = new IntTextField(indTestParams().getMaxDescendantPath(), 4);
        maxDescendantPathField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    indTestParams().setMaxDescendantPath(value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

//
//        skipSepsetBiggerThanField = new IntTextField(indTestParams().getSkipSepsetsBiggerThan(), 4);
//        skipSepsetBiggerThanField.setFilter(new IntTextField.Filter() {
//            public int filter(int value, int oldValue) {
//                try {
//                    indTestParams().setSkipSepsetsBiggerThan(value);
//                    return value;
//                }
//                catch (IllegalArgumentException e) {
//                    return oldValue;
//                }
//            }
//        });

        useCpcBox = new JCheckBox();
        useCpcBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                indTestParams().setUseCpc(checkBox.isSelected());
            }
        });
        useCpcBox.setSelected(indTestParams().isUseCpc());

        startFromEmptyBox = new JCheckBox();
        startFromEmptyBox.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                indTestParams().setStartFromEmptyGraph(checkBox.isSelected());
            }
        });
        startFromEmptyBox.setSelected(indTestParams().isStartFromEmptyGraph());

        maxIterationsField = new IntTextField(indTestParams().getMaxIterations(), 4);
        maxIterationsField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    indTestParams().setMaxIterations(value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
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

        if (alphaField != null) {
            Box b1 = Box.createHorizontalBox();
            b1.add(new JLabel("Alpha:"));
            b1.add(Box.createHorizontalStrut(10));
            b1.add(Box.createHorizontalGlue());
            b1.add(alphaField);
            add(b1);
        }

//        Box b2 = Box.createHorizontalBox();
//        b2.add(new JLabel("Depth Limit:"));
//        b2.add(Box.createHorizontalStrut(10));
//        b2.add(Box.createHorizontalGlue());
//        b2.add(depthField);
//        add(b2);

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Adjacencies Softmax:"));
        b3.add(Box.createHorizontalStrut(10));
        b3.add(Box.createHorizontalGlue());
        b3.add(maxAdjacenciesField);
        add(b3);

//        Box b4 = Box.createHorizontalBox();
//        b4.add(new JLabel("Max Descendant Path:"));
//        b4.add(Box.createHorizontalStrut(10));
//        b4.add(Box.createHorizontalGlue());
//        b4.add(maxDescendantPathField);
//        add(b4);

//        Box b5 = Box.createHorizontalBox();
//        b5.add(new JLabel("Use CPC"));
//        b5.add(Box.createHorizontalGlue());
//        b5.add(useCpcBox);
//        add(b5);

//        Box b6 = Box.createHorizontalBox();
//        b6.add(new JLabel("Start from empty graph"));
//        b6.add(Box.createHorizontalGlue());
//        b6.add(startFromEmptyBox);
//        add(b6);

        Box b7 = Box.createHorizontalBox();
        b7.add(new JLabel("Max Iterations:"));
        b7.add(Box.createHorizontalStrut(10));
        b7.add(Box.createHorizontalGlue());
        b7.add(maxIterationsField);
        add(b7);

        add(Box.createHorizontalGlue());
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     *
     * @return the stored simulation parameters model.
     */
    private JpcIndTestParams indTestParams() {
        return params;
    }
}


