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

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.datamanip.DataSplitter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

/**
 * Parameter editor for the Split Data tool: hosts a {@link DataSplitEditor}, restores a previously stored spec into
 * it, and on OK validates the spec against the parent data and stores it in the parameters. The model
 * ({@link edu.cmu.tetradapp.model.datamanip.DataSplitModel}) re-applies the stored spec to the parent data, so
 * nothing but the spec is stored.
 */
public class DataSplitParamsEditor extends JPanel implements FinalizingParameterEditor {
    private DataSplitEditor editor;
    private Parameters parameters;
    private DataSet dataSet = new BoxDataSet(new DoubleDataBox(0, 0), new ArrayList<>());
    private DataSplitter.Spec initialSpec;

    /**
     * Constructs the editor.
     */
    public DataSplitParamsEditor() {
        setLayout(new BorderLayout());
        setup();
    }

    /**
     * Validates the current spec against the parent data and, if it yields at least one non-empty split, stores it
     * in the parameters.
     *
     * @return true if the spec was valid and stored; false otherwise, in which case the edit is treated as canceled.
     */
    @Override
    public boolean finalizeEdit() {
        if (editor == null) return false;

        DataSplitter.Spec spec = editor.getSpec();

        try {
            DataSplitter.split(dataSet, spec);
        } catch (IllegalArgumentException e) {
            editor.preview();
            JOptionPane.showMessageDialog(this, e.getMessage(), "Cannot split the data",
                    JOptionPane.ERROR_MESSAGE);
            return false;
        }

        spec.storeIn(parameters);
        return true;
    }

    /**
     * Sets the parameters, remembering any stored spec so that it can be shown once the editor is built.
     *
     * @param params the parameters.
     */
    @Override
    public void setParams(Parameters params) {
        this.parameters = params;
        this.initialSpec = DataSplitter.Spec.fromParameters(params);
        if (editor != null && initialSpec != null) editor.applySpec(initialSpec);
    }

    /**
     * Sets the parent models: a {@link DataWrapper} whose first data model is a tabular data set, and the
     * parameters.
     *
     * @param parentModels the parent models.
     * @throws IllegalArgumentException if the parents are not as described.
     */
    @Override
    public void setParentModels(Object[] parentModels) {
        DataWrapper wrapper = null;
        for (Object o : parentModels) {
            if (o instanceof DataWrapper w) wrapper = w;
            else if (o instanceof Parameters p) this.parameters = p;
        }
        if (wrapper == null) {
            throw new IllegalArgumentException("The parent must be a data box.");
        }

        DataModel first = wrapper.getDataModelList().getFirst();
        if (!(first instanceof DataSet ds)) {
            throw new IllegalArgumentException("The data to be split must be a tabular data set.");
        }
        this.dataSet = ds;
    }

    /**
     * Builds the GUI against the current data set, restoring any stored spec. Called from the constructor against a
     * placeholder data set and again by the framework after the parents are set, so it first clears the panel.
     */
    @Override
    public void setup() {
        removeAll();

        Box box = Box.createVerticalBox();
        box.setBorder(new EmptyBorder(5, 5, 5, 5));

        editor = new DataSplitEditor(dataSet);
        if (initialSpec != null) editor.applySpec(initialSpec);

        box.add(editor);
        add(box, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * There is no sensible default split, so the editor is always shown.
     *
     * @return true.
     */
    @Override
    public boolean mustBeShown() {
        return true;
    }
}
