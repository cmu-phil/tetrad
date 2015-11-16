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
public class CorrMatrixConverterSpecial extends DataWrapper {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS==============================//

    public CorrMatrixConverterSpecial(DataWrapper wrapper) {
        ICovarianceMatrix covMatrix;

        if (wrapper.getSelectedDataModel() instanceof CorrelationMatrix) {
            CorrelationMatrix cov = (CorrelationMatrix) wrapper.getSelectedDataModel();
            CovarianceMatrix cov2 = new CovarianceMatrix(cov);

            for (int i = 0; i < cov.getDimension(); i++) {
                for (int j = i + 1; j < cov.getDimension(); j++) {
                    double value = cov.getValue(i, j);
                    double delta = 0.4;
                    double value2 = value - delta;
                    if (value2 < -1.0) value2 = -1.0;
//                double value2 = Math.abs(value) > delta ? Math.signum(value) * (Math.abs(value) - delta) : 0.0;
                    cov2.setValue(i, j, value2);
//                System.out.println(value + " " + value2 + " " + cov.getValue(i, j));
                }
            }

            covMatrix = cov2;
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
        return new CorrMatrixConverterSpecial(wrapper);
    }
}






