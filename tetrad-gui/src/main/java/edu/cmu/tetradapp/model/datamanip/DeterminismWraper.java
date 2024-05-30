/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Feb 11, 2019 4:19:04 PM
 *
 * @author Zhou Yuan zhy19@pitt.edu
 * @version $Id: $Id
 */
public class DeterminismWraper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = -5573234622763285581L;

    /**
     * <p>Constructor for DeterminismWraper.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params      a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public DeterminismWraper(DataWrapper dataWrapper, Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException("The given data must not be null");
        }
        if (params == null) {
            throw new NullPointerException("The given parameters must not be null");
        }

        // Put together with added interventions to get the final combined dataset
        // Create a new class in tetrad-lib data package to handle the data processing
        // Kepp the origional data unchanged, use copies for combinging.

        // Display the merged dataset in the data editor 
        // when users click the "OK" button on the Interventions editor panel

        // Get the merged data through parameter set by the editor
        // Kepp the origional data unchanged, use copies for merging.
        DataModel mergedDataset = (DataModel) params.get("DeterminisedDataset");
        mergedDataset.setName("Determinised");
        setDataModel(mergedDataset);
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }
}
