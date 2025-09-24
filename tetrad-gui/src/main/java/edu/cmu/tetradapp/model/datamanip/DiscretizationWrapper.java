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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Tyler
 * @version $Id: $Id
 */
public class DiscretizationWrapper extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the <code>DiscretizationWrapper</code> by discretizing the select
     * <code>DataModel</code>.
     *
     * @param data   a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public DiscretizationWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The given data must not be null");
        }
        if (params == null) {
            throw new NullPointerException("The given parameters must not be null");
        }

        DataModelList dataSets = data.getDataModelList();
        DataModelList discretizedDataSets = new DataModelList();

        for (DataModel dataModel : dataSets) {
            if (!(dataModel instanceof DataSet originalData)) {
                throw new IllegalArgumentException("Only tabular data sets can be converted to time lagged form.");
            }

            Map<Node, DiscretizationSpec> discretizationSpecs = (Map<Node, DiscretizationSpec>) params.get("discretizationSpecs", new HashMap<Node, DiscretizationSpec>());
            Discretizer discretizer = new Discretizer(originalData, discretizationSpecs);
            discretizer.setVariablesCopied(Preferences.userRoot().getBoolean("copyUnselectedColumns", true));

            discretizedDataSets.add(discretizer.discretize());
        }


        setDataModel(discretizedDataSets);
        setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Discretization of data in the parent node.", getDataModelList());

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




