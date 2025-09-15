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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * <p>TimeSeriesParamsEditor class.</p>
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class TimeSeriesParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The params.
     */
    private Parameters params;


    /**
     * Empty constructor that does nothing, call <code>setup()</code> to build panel.
     */
    public TimeSeriesParamsEditor() {
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
        SpinnerNumberModel model = new SpinnerNumberModel(this.params.getInt("numTimeLags", 1),
                0, Integer.MAX_VALUE, 1);
        JSpinner jSpinner = new JSpinner(model);
        jSpinner.setPreferredSize(jSpinner.getPreferredSize());

        model.addChangeListener(e -> {
            SpinnerNumberModel model1 = (SpinnerNumberModel) e.getSource();
            TimeSeriesParamsEditor.this.params.set("numTimeLags", model1.getNumber().intValue());
        });

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Number of time lags: "));
        b1.add(Box.createHorizontalGlue());
        b1.add(Box.createHorizontalStrut(15));
        b1.add(jSpinner);
        b1.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(b1, BorderLayout.CENTER);
    }

    /**
     * <p>mustBeShown.</p>
     *
     * @return a boolean
     */
    public boolean mustBeShown() {
        return true;
    }
}





