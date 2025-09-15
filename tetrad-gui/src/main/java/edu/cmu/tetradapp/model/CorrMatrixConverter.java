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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Converts a continuous data set to a correlation matrix.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CorrMatrixConverter extends DataWrapper {
    private static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS==============================//

    /**
     * <p>Constructor for CorrMatrixConverter.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CorrMatrixConverter(DataWrapper wrapper, Parameters params) {
        ICovarianceMatrix covMatrix;

        if (wrapper.getSelectedDataModel() instanceof DataSet dataSet) {

            if (!(dataSet.isContinuous())) {
                throw new RuntimeException("Only continuous data sets can be " +
                                           "converted to correlation matrices.");
            }

            covMatrix = new CorrelationMatrix(dataSet);
        } else if (wrapper.getSelectedDataModel() instanceof ICovarianceMatrix covOrig) {
            covMatrix = new CorrelationMatrix(covOrig);
        } else {
            throw new IllegalArgumentException("Expecting a continuous data set or a covariance matrix.");
        }

        setDataModel(covMatrix);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Conversion of parent data to correlation matrix form.", getDataModelList());

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







