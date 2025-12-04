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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.FinalizingParameterEditor;
import edu.cmu.tetradapp.model.DataWrapper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

public class DataSubsetParamsEditor extends JPanel implements FinalizingParameterEditor {

    private DataSubsetEditor dataSubsetEditor;
    private Parameters parameters;
    private DataSet dataSet = new BoxDataSet(new DoubleDataBox(0, 0), new ArrayList<>());
    private java.util.List<String> initialSelectedVarNames;
    private String initialRowSpec;
    private DataSubsetEditor.SamplingMode initialSamplingMode;
    private Integer initialSampleSize;
    private String initialSeedText;

    public DataSubsetParamsEditor() {
        setLayout(new BorderLayout());
//        setPreferredSize(new Dimension(600, 600));
        setup();
    }

//    @Override
//    public boolean finalizeEdit() {
//        if (dataSubsetEditor != null) {
//            DataSet subset = dataSubsetEditor.createSubset();
//            if (subset != null) {
//                parameters.set("dataSubsetParamsEditorSubset", subset);
//                return true;
//            }
//        }
//
//        return false;
//    }

    @Override
    public boolean finalizeEdit() {
        if (dataSubsetEditor != null) {
            DataSet subset = dataSubsetEditor.createSubset();
            if (subset != null) {
                // Keep your existing subset result.
                parameters.set("dataSubsetParamsEditorSubset", subset);

                // NEW: persist UI state so it can be restored later.
                parameters.set("dataSubsetSelectedVarNames",
                        dataSubsetEditor.getSelectedVariableNames());
                parameters.set("dataSubsetRowSpec",
                        dataSubsetEditor.getRowSpec());

                DataSubsetEditor.SamplingMode mode = dataSubsetEditor.getSamplingMode();
                if (mode != null) {
                    parameters.set("dataSubsetSamplingMode", mode.name());
                }

                parameters.set("dataSubsetSampleSize",
                        dataSubsetEditor.getSampleSize());
                parameters.set("dataSubsetSeed",
                        dataSubsetEditor.getSeedText());

                return true;
            }
        }

        return false;
    }

//    @Override
//    public void setParams(Parameters params) {
//        this.parameters = params;
//    }

    @Override
    public void setParams(Parameters params) {
        this.parameters = params;

        Object selNamesObj = params.get("dataSubsetSelectedVarNames");
        if (selNamesObj instanceof java.util.List) {
            @SuppressWarnings("unchecked")
            java.util.List<String> names = (java.util.List<String>) selNamesObj;
            this.initialSelectedVarNames = new java.util.ArrayList<>(names);
        }

        Object rowSpecObj = params.get("dataSubsetRowSpec");
        if (rowSpecObj instanceof String) {
            this.initialRowSpec = (String) rowSpecObj;
        }

        Object modeObj = params.get("dataSubsetSamplingMode");
        if (modeObj instanceof String) {
            try {
                this.initialSamplingMode =
                        DataSubsetEditor.SamplingMode.valueOf((String) modeObj);
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
            dataSubsetEditor.applyState(
                    initialSelectedVarNames,
                    initialRowSpec,
                    initialSamplingMode,
                    initialSampleSize,
                    initialSeedText
            );
        }
    }

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
            throw  new IllegalArgumentException("First data model must be of type DataSet.");
        }

        this.dataSet = (DataSet) first;
        this.parameters = (Parameters) parentModels[1];
    }

    @Override
    public void setup() {
        Box box = Box.createVerticalBox();
        box.setBorder(new EmptyBorder(5, 5, 5, 5));

        dataSubsetEditor = new DataSubsetEditor(dataSet);

        // NEW: restore previous state if we have any.
        if (initialSelectedVarNames != null ||
            initialRowSpec != null ||
            initialSamplingMode != null ||
            initialSampleSize != null ||
            initialSeedText != null) {

            dataSubsetEditor.applyState(
                    initialSelectedVarNames,
                    initialRowSpec,
                    initialSamplingMode,
                    initialSampleSize,
                    initialSeedText
            );
        }

        box.add(dataSubsetEditor);
        add(box, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    @Override
    public boolean mustBeShown() {
        return false;
    }
}
