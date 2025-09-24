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

import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import java.awt.*;

/**
 * Edits the parameters for simulating data from Bayes nets.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BayesEstimatorParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The parameters object being edited.
     */
    private Parameters params;

    /**
     * Constructs a dialog to edit the given workbench Bayes simulation getMappings object.
     */
    public BayesEstimatorParamsEditor() {
    }

    /**
     * <p>setParentModels.</p>
     *
     * @param parentModels an array of {@link Object} objects
     */
    public void setParentModels(Object[] parentModels) {
        // Does nothing.
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

        DoubleTextField priorField = new DoubleTextField(
                this.params.getDouble("bayesEstimatorCellPrior", 1.0), 5, NumberFormatUtil.getInstance().getNumberFormat());
        priorField.setFilter((value, oldValue) -> {
            try {
                BayesEstimatorParamsEditor.this.params.set("bayesEstimatorCellPrior", value);
                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b7 = Box.createHorizontalBox();
        b7.add(Box.createHorizontalGlue());
        b7.add(new JLabel("<html>" +
                          "<i>Estimate using a prior for each cell in each CPT =</i>" +
                          "</html>"));
        b7.add(priorField);

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
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
    }
}






