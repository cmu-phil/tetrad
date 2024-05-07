///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

/**
 * Edits the parameters for simulating new datasets from a Bayes net.
 *
 * @author josephramsey
 * @version $Id: $Id
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
     * A method required by the interface that does nothing.
     *
     * @param parentModels an array of {@link java.lang.Object} objects
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
        sampleSizeField.setFilter((value, oldValue) -> {
            try {
                BayesDataParamsEditor.this.getParams().set("sampleSize", value);
                return value;
            } catch (Exception e) {
                return oldValue;
            }
        });

        IntTextField numDataSetsField = new IntTextField(this.getParams().getInt("numDataSets", 1), 8);

        numDataSetsField.setFilter((value, oldValue) -> {
            try {
                getParams().set("numDataSets", value);
                return value;
            } catch (Exception e) {
                return oldValue;
            }
        });


        JCheckBox latentDataSaved = new JCheckBox("Include Latent Variables",
                getParams().getBoolean("latentDataSaved", false));

        latentDataSaved.addActionListener(e -> {
            JCheckBox checkBox = (JCheckBox) e.getSource();
            BayesDataParamsEditor.this.params.set("latentDataSaved", checkBox.isSelected());
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

    /**
     * <p>mustBeShown.</p>
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return true;
    }

    /**
     * @return the getMappings object being edited. (This probably should not be public, but it is needed so that the
     * textfields can edit the model.)
     */
    private synchronized Parameters getParams() {

        return this.params;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the parameter-storing object. This is a separate method because a blank constructor is needed.
     */
    public void setParams(Parameters params) {
        this.params = params;
    }
}





