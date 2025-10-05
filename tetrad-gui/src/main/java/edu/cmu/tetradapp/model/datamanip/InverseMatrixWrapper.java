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
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

/**
 * Splits continuous data sets by collinear columns.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class InverseMatrixWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * Splits the given data set by collinear columns.
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public InverseMatrixWrapper(DataWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException("The given data must not be null");
        }
        DataModel model = wrapper.getSelectedDataModel();
        if (model instanceof DataSet dataSet) {
            if (!(dataSet.isContinuous())) {
                throw new IllegalArgumentException("The data must be continuous");
            }

            Matrix _data = dataSet.getDoubleData();
            Matrix _data2 = _data.inverse();
            DataSet inverse = new BoxDataSet(new DoubleDataBox(_data2.toArray()), dataSet.getVariables());
            setDataModel(inverse);
            setSourceGraph(wrapper.getSourceGraph());
        } else if (model instanceof ICovarianceMatrix cov) {
            Matrix _data = cov.getMatrix();
            Matrix _data2 = _data.inverse();
            DataSet inverse = new BoxDataSet(new DoubleDataBox(_data2.toArray()), cov.getVariables());
            setDataModel(inverse);
            setSourceGraph(wrapper.getSourceGraph());
        } else {
            throw new IllegalArgumentException("Must be a dataset or a covariance  matrix");
        }

        LogDataUtils.logDataModelList("Conversion of data to covariance matrix form.", getDataModelList());

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





