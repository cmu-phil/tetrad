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
 * Splits continuous data sets by collinear columns.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class CovMatrixWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * Splits the given data set by collinear columns.
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CovMatrixWrapper(DataWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException("The given data must not be null");
        }

        DataModelList models = wrapper.getDataModelList();
        DataModelList out = new DataModelList();

        for (DataModel model : models) {
            if (model instanceof DataSet dataSet) {

                if (!(dataSet.isContinuous())) {
                    throw new IllegalArgumentException("The data must be continuous");
                }

                ICovarianceMatrix covarianceMatrix = new CovarianceMatrix(dataSet);
                out.add(covarianceMatrix);
            } else if (model instanceof ICovarianceMatrix) {
                ICovarianceMatrix covarianceMatrix = new CovarianceMatrix((CovarianceMatrix) model);
                out.add(covarianceMatrix);
            }
        }

        setDataModel(out);

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





