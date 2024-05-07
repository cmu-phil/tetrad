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

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author josephramsey
 * @author Frank Wimberly based on similar classes by Joe Ramsey
 * @version $Id: $Id
 */
public class EMBayesEstimatorParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params;

    /**
     * <p>Constructor for EMBayesEstimatorParamsEditor.</p>
     */
    public EMBayesEstimatorParamsEditor() {

    }

    /**
     * <p>setParentModels.</p>
     *
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    public void setParentModels(Object[] parentModels) {
        // Ignore.
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return false;
    }

    /**
     * Constructs the Gui used to edit properties; called from each constructor. Constructs labels and text fields for
     * editing each property and adds appropriate listeners.
     */
    public void setup() {
        setLayout(new BorderLayout());

        DoubleTextField toleranceField =
                new DoubleTextField(
                        this.params.getDouble("tolerance", 0.0001), 8, NumberFormatUtil.getInstance().getNumberFormat());
        toleranceField.setFilter((value, oldValue) -> {
            try {
                EMBayesEstimatorParamsEditor.this.params.set("tolerance", value);
                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("<html>" +
                          "The dataset will be used to iteratively estmate the parameters " +
                          "<br>of a Bayes IM until the distance between the vectors of parameters" +
                          "<br>of successive iterates is less than a tolerance set by the user" +
                          "</html>"));

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("<html>" + "<i>The default value is 0.0001</i>" +
                          "</html>"));
        b7.add(toleranceField);

        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b7);
        b1.add(Box.createHorizontalGlue());
        add(b1, BorderLayout.CENTER);
    }

    /**
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return the getMappings object being edited. (This probably should not be public, but it is needed so that the
     * textfields can edit the model.)
     */
    protected synchronized Parameters getParams() {
        return this.params;
    }

    /**
     * {@inheritDoc}
     */
    public void setParams(Parameters params) {
        this.params = params;
    }
}





