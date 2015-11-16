///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;

/**
 * Add description
 *
 * @author Tyler Gibson
 */
public class ConvertNumericalDiscreteToContinuousWrapper extends DataWrapper {
    static final long serialVersionUID = 23L;

    public ConvertNumericalDiscreteToContinuousWrapper(DataWrapper data) {
        if (data == null) {
            throw new NullPointerException("The given data must not be null");
        }

        DataModelList dataSets = data.getDataModelList();
        DataModelList convertedDataSets = new DataModelList();

        for (DataModel dataModel : dataSets) {
            if (!(dataModel instanceof DataSet)) {
                throw new IllegalArgumentException("Only tabular data sets can be converted to time lagged form.");
            }

            DataSet originalData = (DataSet) dataModel;
            DataSet convertedData;

            try {
                convertedData = DataUtils.convertNumericalDiscreteToContinuous(originalData);
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
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static ConvertNumericalDiscreteToContinuousWrapper serializableInstance() {
        return new ConvertNumericalDiscreteToContinuousWrapper(DataWrapper.serializableInstance());
    }


}


