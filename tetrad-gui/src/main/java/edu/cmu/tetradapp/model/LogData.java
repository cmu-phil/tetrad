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
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.Serial;

/**
 * Applies a logarithmic transform.
 *
 * @author Jeremy Espino
 * @version $Id: $Id
 */
public class LogData extends DataWrapper {
    @Serial
    private static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS==============================//

    /**
     * Applies a logarithmic transform to the data.
     *
     * @param wrapper The data to transform.
     * @param params  The parameters for the transformation.
     */
    public LogData(DataWrapper wrapper, Parameters params) {
        DataModelList inList = wrapper.getDataModelList();
        DataModelList outList = new DataModelList();

        for (DataModel model : inList) {
            if (!(model instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Not a data set: " + model.getName());
            }

            double a = params.getDouble("a");
            boolean isUnlog = params.getBoolean("unlog");
            int base = params.getInt("base");

            DataSet dataSet2 = DataTransforms.logData(dataSet, a, isUnlog, base);
            outList.add(dataSet2);
        }

        setDataModel(outList);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Logarithmic conversion of data.", getDataModelList());

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

}




