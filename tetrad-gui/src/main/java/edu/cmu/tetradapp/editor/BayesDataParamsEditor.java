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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Edits the parameters for simulating new datasets from a Bayes net.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public final class BayesDataParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The objects storing values of parameters needed to simulate data.
     */
    private Parameters params;

    /**
     * Blank constructor.
     */
    public BayesDataParamsEditor() {
    }

    /**
     * Sets the parameter-storing object. This is a separate method because
     * a blank constructor is needed.
     * @param params
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * A method required by the interface that does nothing.
     */
    public void setParentModels(Object[] parentModels) {
        // Do nothing.
    }

    /**
     * Constructs the GUI.
     */
    public void setup() {
        // set up text and ties them to the parameters object being edited.
        IntTextField sampleSizeField = new IntTextField(getParams().getInt("sampleSize", 1000), 8);
        sampleSizeField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    getParams().set("sampleSize", value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });

        IntTextField numDataSetsField = new IntTextField(getParams().getInt("numDataSets", 1), 8);

        numDataSetsField.setFilter(new IntTextField.Filter() {
            public int filter(int value, int oldValue) {
                try {
                    getParams().set("numDataSets", value);
                    return value;
                }
                catch (Exception e) {
                    return oldValue;
                }
            }
        });



//        JCheckBox latentDataSaved = new JCheckBox("Include Latent Variables",
//                Preferences.userRoot().getBoolean("latentDataSaved", getParameters().isIncludeLatents()));
        JCheckBox latentDataSaved = new JCheckBox("Include Latent Variables",
                getParams().getBoolean("latentDataSaved", false));

        latentDataSaved.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                params.set("latentDataSaved", checkBox.isSelected());
            }
        });


        // construct the workbench.

        setLayout(new BorderLayout());

        // continue workbench construction.
        Box b = Box.createVerticalBox();

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Sample size:  "));
        b1.add(Box.createHorizontalGlue());
        b1.add(sampleSizeField);
        b.add(b1);

        Box b1a = Box.createHorizontalBox();
        b1a.add(new JLabel("Num data sets:  "));
        b1a.add(Box.createHorizontalGlue());
        b1a.add(numDataSetsField);
        b.add(b1a);

        Box b2 = Box.createHorizontalBox();
        b2.add(latentDataSaved);
        b.add(b2);

        add(b, BorderLayout.CENTER);
        setBorder(new EmptyBorder(5, 5, 5, 5));
    }

    public boolean mustBeShown() {
        return true;
    }

    /**
     * @return the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     */
    private synchronized Parameters getParams() {

        // Unused.
        //        String ret = (this.getMappings == null)
        //                ? "null"
        //                : this.getMappings.toString();

        return this.params;
    }
}





