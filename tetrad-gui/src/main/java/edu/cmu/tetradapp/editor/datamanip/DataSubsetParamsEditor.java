/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

/**
 * The {@code DataSubsetParamsEditor} class is a visual component extending {@code JPanel} and implementing the
 * {@code FinalizingParameterEditor} interface. It serves as a user interface for configuring and editing parameters
 * related to a subset of a dataset using an instance of {@code DataSubsetEditor}. This editor allows users to configure
 * data selection, sampling options, and other subset-specific parameters, persist those settings, and restore them when
 * necessary.
 * <p>
 * This class provides functionality for initializing the editor state, handling user inputs, and finalizing any
 * modifications made.
 */
public class DataSubsetParamsEditor extends JPanel implements FinalizingParameterEditor {
    private DataSubsetEditor dataSubsetEditor;
    private Parameters parameters;
    private DataSet dataSet = new BoxDataSet(new DoubleDataBox(0, 0), new ArrayList<>());
    private java.util.List<String> initialSelectedVarNames;
    private String initialRowSpec;
    private DataSubsetEditor.SamplingMode initialSamplingMode;
    private Integer initialSampleSize;
    private String initialSeedText;

    /**
     * Constructs a DataSubsetParamsEditor with default settings.
     */
    public DataSubsetParamsEditor() {
        setLayout(new BorderLayout());
        setup();
    }

    /**
     * Finalizes the editing of a data subset by creating a subset using the data subset editor and persisting the
     * editor's state in the parameters. This includes the selected variable names, row specifications, sampling mode,
     * sample size, and seed text. The method returns whether the process was successfully completed.
     *
     * @return true if the subset was created and the state was successfully persisted; false otherwise
     */
    @Override
    public boolean finalizeEdit() {
        if (dataSubsetEditor != null) {
            DataSet subset = dataSubsetEditor.createSubset();
            if (subset != null) {
                // Keep your existing subset result.
                parameters.set("dataSubsetParamsEditorSubset", subset);

                // NEW: persist UI state so it can be restored later.
                parameters.set("dataSubsetSelectedVarNames", dataSubsetEditor.getSelectedVariableNames());
                parameters.set("dataSubsetRowSpec", dataSubsetEditor.getRowSpec());

                DataSubsetEditor.SamplingMode mode = dataSubsetEditor.getSamplingMode();
                if (mode != null) {
                    parameters.set("dataSubsetSamplingMode", mode.name());
                }

                parameters.set("dataSubsetSampleSize", dataSubsetEditor.getSampleSize());
                parameters.set("dataSubsetSeed", dataSubsetEditor.getSeedText());

                return true;
            }
        }

        return false;
    }

    /**
     * Sets the parameters for the DataSubsetParamsEditor, updating the internal state based on the provided
     * {@code Parameters} object. This includes initializing the selected variable names, row specification, sampling
     * mode, sample size, and seed text. If the editor instance is already available, the state is applied immediately.
     *
     * @param params the parameters containing configuration values, where: - "dataSubsetSelectedVarNames" should be a
     *               list of strings representing selected variable names. - "dataSubsetRowSpec" should be a string
     *               defining the row specification. - "dataSubsetSamplingMode" should be a string matching the
     *               SamplingMode enum. - "dataSubsetSampleSize" should be a number representing the sample size. -
     *               "dataSubsetSeed" should be a string defining the sample seed.
     */
    @Override
    public void setParams(Parameters params) {
        this.parameters = params;

        Object selNamesObj = params.get("dataSubsetSelectedVarNames");
        if (selNamesObj instanceof java.util.List) {
            @SuppressWarnings("unchecked") java.util.List<String> names = (java.util.List<String>) selNamesObj;
            this.initialSelectedVarNames = new java.util.ArrayList<>(names);
        }

        Object rowSpecObj = params.get("dataSubsetRowSpec");
        if (rowSpecObj instanceof String) {
            this.initialRowSpec = (String) rowSpecObj;
        }

        Object modeObj = params.get("dataSubsetSamplingMode");
        if (modeObj instanceof String) {
            try {
                this.initialSamplingMode = DataSubsetEditor.SamplingMode.valueOf((String) modeObj);
            } catch (IllegalArgumentException ignored) {
                // unknown enum value; leave null
            }
        }

        Object sizeObj = params.get("dataSubsetSampleSize");
        if (sizeObj instanceof Number) {
            this.initialSampleSize = ((Number) sizeObj).intValue();
        }

        Object seedObj = params.get("dataSubsetSeed");
        if (seedObj instanceof String) {
            this.initialSeedText = (String) seedObj;
        }

        // If the editor already exists, apply immediately.
        if (dataSubsetEditor != null) {
            dataSubsetEditor.applyState(initialSelectedVarNames, initialRowSpec, initialSamplingMode, initialSampleSize, initialSeedText);
        }
    }

    /**
     * Sets the parent models for the DataSubsetParamsEditor. This method validates that the input array contains
     * exactly two elements, where the first element must be a {@code DataWrapper} containing a {@code DataSet} as the
     * first data model, and the second element must be an instance of {@code Parameters}. If the validation succeeds,
     * the respective fields of the editor are initialized with these values.
     *
     * @param parentModels an array of {@code Object} containing two elements: - The first element must be a
     *                     {@code DataWrapper}, where the first data model in its list is a {@code DataSet}. - The
     *                     second element must be of type {@code Parameters}.
     * @throws IllegalArgumentException if the array does not have exactly two elements, if the first element is not a
     *                                  {@code DataWrapper} containing a {@code DataSet}, or if the second element is
     *                                  not an instance of {@code Parameters}.
     */
    @Override
    public void setParentModels(Object[] parentModels) {
        if (parentModels.length != 2) {
            if (!(parentModels[0] instanceof DataWrapper)) {
                throw new IllegalArgumentException("Parent model must be of type DataSet.");
            }
            if (!(parentModels[1] instanceof Parameters)) {
                throw new IllegalArgumentException("Expected the second parent model to be of type Parameters.");
            }
        }

        DataModel first = ((DataWrapper) parentModels[0]).getDataModelList().getFirst();

        if (!(first instanceof DataSet)) {
            throw new IllegalArgumentException("First data model must be of type DataSet.");
        }

        this.dataSet = (DataSet) first;
        this.parameters = (Parameters) parentModels[1];
    }

    /**
     * Initializes and configures the layout and components of the DataSubsetParamsEditor. This method
     * sets up a vertical box layout with a border and initializes the data subset editor with the
     * provided dataset. If there is previously saved state available for the editor (e.g., selected
     * variable names, row specifications, sampling mode, sample size, or seed text), it restores the
     * editor's state accordingly. After adding the editor to the layout, the method refreshes the UI
     * to reflect the changes.
     */
    @Override
    public void setup() {
        Box box = Box.createVerticalBox();
        box.setBorder(new EmptyBorder(5, 5, 5, 5));

        dataSubsetEditor = new DataSubsetEditor(dataSet);

        // NEW: restore previous state if we have any.
        if (initialSelectedVarNames != null || initialRowSpec != null || initialSamplingMode != null || initialSampleSize != null || initialSeedText != null) {

            dataSubsetEditor.applyState(initialSelectedVarNames, initialRowSpec, initialSamplingMode, initialSampleSize, initialSeedText);
        }

        box.add(dataSubsetEditor);
        add(box, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Determines whether the editor for the data subset parameters should be shown.
     * This method allows conditional display of the editor based on specific criteria,
     * which is defined in the overriding implementation.
     *
     * @return false indicating that this editor does not need to be shown.
     */
    @Override
    public boolean mustBeShown() {
        return false;
    }
}
