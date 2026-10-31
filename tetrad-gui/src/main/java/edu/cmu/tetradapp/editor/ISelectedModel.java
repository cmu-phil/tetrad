package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataModel;

/** Implemented by components that have a currently selected data model. */
public interface ISelectedModel {
    /**
     * Returns the currently selected data model.
     *
     * @return the selected data model
     */
    DataModel getSelectedDataModel();
}
