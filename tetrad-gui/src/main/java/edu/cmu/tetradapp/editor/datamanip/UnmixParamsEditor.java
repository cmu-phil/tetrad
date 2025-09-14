/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.UnmixSpec;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.util.IntTextField;

import javax.swing.*;
import java.awt.*;

/**
 * Allows the user to specify how a selected list of columns should be discretized.
 *
 * @author Tyler Gibson
 * @author josephramsey
 * @version $Id: $Id
 */
public class UnmixParamsEditor extends JPanel implements FinalizingParameterEditor {

    private final IntTextField numComponentsField;
    /**
     * The parameters that will be returned by this editor.
     */
    private Parameters parameters;
    private final UnmixSpec unmixSpec = new UnmixSpec();


    /**
     * Constructs a new editor that will allow the user to specify how to discretize each of the columns in the given
     * list. The editor will return the discretized data set.
     */
    public UnmixParamsEditor() {
        setLayout(new BorderLayout());

        Box v1 = Box.createVerticalBox();
        add(v1, BorderLayout.CENTER);

        Box v2 = Box.createHorizontalBox();

        v2.add(new JLabel("Number of components:"));

        numComponentsField = new IntTextField(
                unmixSpec.getNumComponents(), 3);
        numComponentsField.setFilter((value, oldValue) -> {
            try {
                unmixSpec.setNumComponents(value);

                if (parameters != null) {
                    parameters.set("unmixNumComponents", value);
                }

                return value;
            } catch (IllegalArgumentException e) {
                return oldValue;
            }
        });

        v2.add(numComponentsField);

        v1.add(v2);

    }

    //============================= Public Methods ===================================//

    /**
     * Sets up the GUI.
     */
    public void setup() {
    }

    @Override
    public boolean mustBeShown() {
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the previous params, must be <code>DiscretizationParams</code>.
     */
    public void setParams(Parameters params) {
        this.parameters = params;
        this.parameters.set("unmixSpec", unmixSpec);
        this.numComponentsField.setText(Integer.toString(parameters.getInt("unmixNumComponents", 2)));
    }

    /**
     * The parant model should be a <code>DataWrapper</code>.
     *
     * @param parentModels an array of {@link Object} objects
     */
    public void setParentModels(Object[] parentModels) {
        if (parentModels == null || parentModels.length == 0) {
            throw new IllegalArgumentException("There must be parent model");
        }
        DataWrapper data = null;
        for (Object parent : parentModels) {
            if (parent instanceof DataWrapper) {
                data = (DataWrapper) parent;
            }
        }
        if (data == null) {
            throw new IllegalArgumentException("Should have have a data wrapper as a parent");
        }
        DataModel model = data.getSelectedDataModel();
        if (!(model instanceof DataSet)) {
            throw new IllegalArgumentException("The dataset must be a rectangular dataset");
        }
    }

    @Override
    public boolean finalizeEdit() {
        parameters.set("unmixSpec", unmixSpec);
        return true;
    }
}






