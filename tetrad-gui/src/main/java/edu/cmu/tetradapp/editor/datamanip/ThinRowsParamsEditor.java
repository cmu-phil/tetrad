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
 * Edits the parameters for the thin rows (keep every kth row) data transform: the thinning
 * interval k and the offset of the first row kept.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ThinRowsParamsEditor extends JPanel implements ParameterEditor {

    private static final long serialVersionUID = 23L;

    /**
     * The params.
     */
    private Parameters params;

    /**
     * Empty constructor that does nothing, call <code>setup()</code> to build panel.
     */
    public ThinRowsParamsEditor() {
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
        SpinnerNumberModel kModel = new SpinnerNumberModel(this.params.getInt("thinRowsK", 2),
                1, Integer.MAX_VALUE, 1);
        JSpinner kSpinner = new JSpinner(kModel);
        kSpinner.setPreferredSize(kSpinner.getPreferredSize());

        kModel.addChangeListener(e -> {
            SpinnerNumberModel model = (SpinnerNumberModel) e.getSource();
            ThinRowsParamsEditor.this.params.set("thinRowsK", model.getNumber().intValue());
        });

        SpinnerNumberModel offsetModel = new SpinnerNumberModel(this.params.getInt("thinRowsOffset", 0),
                0, Integer.MAX_VALUE, 1);
        JSpinner offsetSpinner = new JSpinner(offsetModel);
        offsetSpinner.setPreferredSize(offsetSpinner.getPreferredSize());

        offsetModel.addChangeListener(e -> {
            SpinnerNumberModel model = (SpinnerNumberModel) e.getSource();
            ThinRowsParamsEditor.this.params.set("thinRowsOffset", model.getNumber().intValue());
        });

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Keep every kth row of each data set; k: "));
        b1.add(Box.createHorizontalGlue());
        b1.add(Box.createHorizontalStrut(15));
        b1.add(kSpinner);
        b1.setBorder(new EmptyBorder(10, 10, 5, 10));

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Offset of first row kept (0 = first row): "));
        b2.add(Box.createHorizontalGlue());
        b2.add(Box.createHorizontalStrut(15));
        b2.add(offsetSpinner);
        b2.setBorder(new EmptyBorder(5, 10, 10, 10));

        Box vert = Box.createVerticalBox();
        vert.add(b1);
        vert.add(b2);
        add(vert, BorderLayout.CENTER);
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
