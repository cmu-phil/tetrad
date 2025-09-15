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
 * Add description
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class ConvertNumericalDiscreteToContinuousWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for ConvertNumericalDiscreteToContinuousWrapper.</p>
     *
     * @param data   a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public ConvertNumericalDiscreteToContinuousWrapper(DataWrapper data, Parameters params) {
        if (data == null) {
            throw new NullPointerException("The given data must not be null");
        }

        DataModelList dataSets = data.getDataModelList();
        DataModelList convertedDataSets = new DataModelList();

        for (DataModel dataModel : dataSets) {
            if (!(dataModel instanceof DataSet originalData)) {
                throw new IllegalArgumentException("Only tabular data sets can be converted to time lagged form.");
            }

            DataSet convertedData;

            try {
                convertedData = DataTransforms.convertNumericalDiscreteToContinuous(originalData);
            } catch (NumberFormatException e) {
                throw new RuntimeException("There were some non-numeric values in that dataset.");
            }

            convertedDataSets.add(convertedData);
        }

        setDataModel(convertedDataSets);
        setSourceGraph(data.getSourceGraph());

        LogDataUtils.logDataModelList("Data in which numerical discrete columns of parent node data have been expanded.",
                getDataModelList());

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



