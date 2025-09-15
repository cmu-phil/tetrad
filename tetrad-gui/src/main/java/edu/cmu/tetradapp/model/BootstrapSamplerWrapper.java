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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.prefs.Preferences;

/**
 * Wraps a data model so that a random sample will automatically be drawn on construction from a BayesIm.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BootstrapSamplerWrapper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The output data set.
     */
    private final DataSet outputDataSet;

    //=============================CONSTRUCTORS===========================//

    /**
     * <p>Constructor for BootstrapSamplerWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BootstrapSamplerWrapper(DataWrapper wrapper,
                                   Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }

        if (params == null) {
            throw new NullPointerException();
        }

        DataModelList bootstraps = new DataModelList();
        DataModelList oldDataSets = wrapper.getDataModelList();

        for (DataModel dataModel : oldDataSets) {
            DataSet dataSet = (DataSet) dataModel;
            BootstrapSampler sampler = new BootstrapSampler();
            sampler.setWithReplacement(Preferences.userRoot().getBoolean("withReplacement", true));
            DataSet bootstrap = sampler.sample(dataSet, Preferences.userRoot().getInt("bootstrapSampleSize",
                    1000));
            bootstraps.add(bootstrap);
            if (oldDataSets.getSelectedModel() == dataModel) {
                bootstraps.setSelectedModel(bootstrap);
            }
        }

        setDataModel(bootstraps);
        setSourceGraph(wrapper.getSourceGraph());

        DataModel dataModel = wrapper.getSelectedDataModel();
        DataSet dataSet = (DataSet) dataModel;
        BootstrapSampler sampler = new BootstrapSampler();
        sampler.setWithReplacement(params.getBoolean("withReplacement", true));
        this.outputDataSet = sampler.sample(dataSet,
                params.getInt("sampleSize", 1000));

        LogDataUtils.logDataModelList("Bootstrap sample of data in the parent node.", getDataModelList());

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //=============================PUBLIC METHODS=========================//

    /**
     * <p>getOutputDataset.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getOutputDataset() {
        return this.outputDataSet;
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}







