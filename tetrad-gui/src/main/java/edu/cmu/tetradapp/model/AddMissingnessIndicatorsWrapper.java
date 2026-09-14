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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.search.utils.MissingnessIndicatorAdder;
import edu.cmu.tetrad.search.utils.MissingnessIndicatorAdder.Spec;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Wraps a data model so that a random sample will automatically be drawn on construction from a BayesIm.
 *
 * @author josephramsey
 * @author Frank Wimberly based on similar class by Ramsey
 * @version $Id: $Id
 */
public class AddMissingnessIndicatorsWrapper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private final DataSet outputDataSet;

    /**
     * A record of which indicators were added and which variables were skipped, for display and logging.
     *
     * @serial Can be null in sessions saved before this field existed.
     */
    private final String summary;

    //============================CONSTRUCTORS=============================//

    /**
     * <p>Constructor for MissingDataInjectorWrapper.</p>
     *
     * @param wrapper a {@link DataWrapper} object
     * @param params  a {@link Parameters} object
     */
    public AddMissingnessIndicatorsWrapper(DataWrapper wrapper,
                                           Parameters params) {
        DataSet dataSet = (DataSet) wrapper.getSelectedDataModel();

        Spec spec = new Spec(
                params.getDouble("missingnessIndicatorMinRate", 0.02),
                params.getDouble("missingnessIndicatorMaxRate", 0.98),
                params.getBoolean("missingnessIndicatorDiscrete", true),
                params.getBoolean("missingnessIndicatorKnowledge", true),
                params.getBoolean("missingnessIndicatorForbidSelfMasking", true),
                "_missing");

        MissingnessIndicatorAdder.Result result = MissingnessIndicatorAdder.add(dataSet, spec);

        this.outputDataSet = result.data();
        this.summary = result.summary();

        setDataModel(this.outputDataSet);
        setSourceGraph(wrapper.getSourceGraph());

        TetradLogger.getInstance().log(this.summary);
        LogDataUtils.logDataModelList("Parent data with missingness indicators added.", getDataModelList());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //==========================PUBLIC METHODS============================//

    /**
     * <p>getOutputDataset.</p>
     *
     * @return a {@link DataSet} object
     */
    public DataSet getOutputDataset() {
        return this.outputDataSet;
    }

    /**
     * <p>getSummary.</p>
     *
     * @return a description of which indicators were added and which variables were skipped, or null for sessions
     * saved before this field existed.
     */
    public String getSummary() {
        return this.summary;
    }

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






