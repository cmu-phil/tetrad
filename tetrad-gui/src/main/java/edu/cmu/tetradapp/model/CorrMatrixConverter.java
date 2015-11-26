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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Converts a continuous data set to a correlation matrix.
 *
 * @author Joseph Ramsey
 */
public class CorrMatrixConverter extends DataWrapper {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS==============================//

    public CorrMatrixConverter(DataWrapper wrapper) {
        ICovarianceMatrix covMatrix;

        if (wrapper.getSelectedDataModel() instanceof DataSet) {
            DataSet dataSet = (DataSet) wrapper.getSelectedDataModel();

            if (!(dataSet.isContinuous())) {
                throw new RuntimeException("Only continuous data sets can be " +
                        "converted to correlation matrices.");
            }

            covMatrix = new CorrelationMatrix(dataSet);
        }
        else if (wrapper.getSelectedDataModel() instanceof ICovarianceMatrix) {
            ICovarianceMatrix covOrig = (ICovarianceMatrix) wrapper.getSelectedDataModel();
            covMatrix = new CorrelationMatrix(covOrig);
        }
        else {
            throw new IllegalArgumentException("Expecting a continuous data set or a covariance matrix.");
        }

        setDataModel(covMatrix);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Conversion of parent data to correlation matrix form.", getDataModelList());

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        DataWrapper wrapper =
                new DataWrapper(DataUtils.continuousSerializableInstance());
        return new CorrMatrixConverter(wrapper);
    }
}






