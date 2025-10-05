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
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

/**
 * GUI model for the permute rows function in RectangularDataSet.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class FirstDifferencesWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the wrapper given some data and the params.
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public FirstDifferencesWrapper(DataWrapper wrapper, Parameters params) {
        LogDataUtils.logDataModelList("Parent data in which rows have been randomly permuted.", getDataModelList());

        DataModelList inList = wrapper.getDataModelList();
        DataModelList outList = new DataModelList();

        for (DataModel model : inList) {
            if (!(model instanceof DataSet data)) {
                throw new IllegalArgumentException("Not a data set: " + model.getName());
            }

            if (!(data.isContinuous())) {
                throw new IllegalArgumentException("Not a continuous data set: " + data.getName());
            }

            DataSet firstDiff = new BoxDataSet(new DoubleDataBox(data.getNumRows() - 1, data.getVariables().size()),
                    data.getVariables());

            for (int j = 0; j < data.getNumColumns(); j++) {
                for (int i = 0; i < data.getNumRows() - 1; i++) {
                    double d2 = data.getDouble(i + 1, j);
                    double d1 = data.getDouble(i, j);
                    firstDiff.setDouble(i, j, d2 - d1);
                }
            }

            outList.add(firstDiff);
        }

        setDataModel(outList);
        setSourceGraph(wrapper.getSourceGraph());


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




