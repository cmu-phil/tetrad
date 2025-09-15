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
import org.apache.commons.math3.util.FastMath;

/**
 * <p>BoxCoxWrapper class.</p>
 *
 * @author Tyler
 * @version $Id: $Id
 */
public class BoxCoxWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new time series dataset.
     *
     * @param data   - Previous data (from the parent node)
     * @param params - The parameters.
     */
    private BoxCoxWrapper(DataWrapper data, Parameters params) {
        DataModelList list = data.getDataModelList();
        DataModelList convertedList = new DataModelList();
        DataModelList dataSets = data.getDataModelList();

        for (int i = 0; i < list.size(); i++) {
            DataModel selectedModel = dataSets.get(i);

            if (!(selectedModel instanceof DataSet)) {
                continue;
            }

//            DataModel model = boxCox((DataSet) selectedModel, params.getLambda());
            DataModel model = yeoJohnson((DataSet) selectedModel, params.getDouble("lambda", 0));
            convertedList.add(model);
            setSourceGraph(data.getSourceGraph());
        }

        setDataModelList(convertedList);


        LogDataUtils.logDataModelList("Result data from an AR residual calculation.", getDataModelList());

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

    private DataModel boxCox(DataSet dataSet, double lambda) {
        DataSet transformedData = new BoxDataSet(new VerticalDoubleDataBox(dataSet.getNumRows(), dataSet.getVariables().size()),
                dataSet.getVariables());

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                double y = dataSet.getDouble(i, j);
                double d2;

                if (lambda == 0.0) {
                    d2 = FastMath.log(y);
                } else {
                    d2 = (FastMath.pow(y, lambda) - 1.0) / lambda;
                }

                transformedData.setDouble(i, j, d2);
            }
        }

        return transformedData;
    }

    private DataModel yeoJohnson(DataSet dataSet, double lambda) {
        DataSet transformedData = new BoxDataSet(new DoubleDataBox(dataSet.getNumRows(), dataSet.getVariables().size()),
                dataSet.getVariables());

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                double y = dataSet.getDouble(i, j);
                double d2;

                if (lambda != 0 && y >= 0.0) {
                    d2 = (FastMath.pow(y + 1.0, lambda) - 1.0) / lambda;
                } else if (lambda == 0 && y >= 0.0) {
                    d2 = FastMath.log(y + 1.0);
                } else if (lambda != 2 && y < 0.0) {
                    d2 = (FastMath.pow(1.0 - y, 2.0 - lambda) - 1) / (lambda - 2.0);
                } else if (lambda == 2 && y < 0.0) {
                    d2 = -FastMath.log(1.0 - y);
                } else {
                    throw new IllegalStateException("Impossible state.");
                }

                transformedData.setDouble(i, j, d2);
            }
        }

        return transformedData;
    }
}



