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

package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.ParameterEditor;
import edu.cmu.tetradapp.util.DoubleTextField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.DecimalFormat;

/**
 * <p>BoxCoxParamsEditor class.</p>
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class BoxCoxParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The params.
     */
    private Parameters params;


    /**
     * Empty constructor that does nothing, call <code>setup()</code> to build panel.
     */
    public BoxCoxParamsEditor() {
        super(new BorderLayout());
    }


    /**
     * {@inheritDoc}
     * <p>
     * Sets the parameters.
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * Does nothing
     *
     * @param parentModels an array of {@link java.lang.Object} objects
     */
    public void setParentModels(Object[] parentModels) {

    }

    /**
     * Builds the panel.
     */
    public void setup() {
        DoubleTextField lambda = new DoubleTextField(this.params.getDouble("lambda", 0), 8, new DecimalFormat("0.0"));

        lambda.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                if (value >= 0) {
                    BoxCoxParamsEditor.this.params.set("lambda", value);
                    return value;
                } else {
                    return oldValue;
                }
            }
        });


        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Lambda: "));
        b1.add(Box.createHorizontalGlue());
        b1.add(Box.createHorizontalStrut(15));
        b1.add(lambda);
        b1.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(b1, BorderLayout.CENTER);
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return true.
     */
    public boolean mustBeShown() {
        return true;
    }
}




