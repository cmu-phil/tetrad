///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

/**
 * Splits continuous data sets by collinear columns.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class InvertCovMatrixWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * Splits the given data set by collinear columns.
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public InvertCovMatrixWrapper(DataWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException("The given data must not be null");
        }
        DataModel model = wrapper.getSelectedDataModel();
        if (model instanceof ICovarianceMatrix dataSet) {
            Matrix data = dataSet.getMatrix();
            Matrix inverse = data.inverse();
            String[] varNames = dataSet.getVariableNames().toArray(new String[0]);
            ICovarianceMatrix covarianceMatrix = new CovarianceMatrix(DataUtils.createContinuousVariables(varNames), inverse,
                    ((ICovarianceMatrix) model).getSampleSize());
            setDataModel(covarianceMatrix);
            setSourceGraph(wrapper.getSourceGraph());
        } else {
            throw new IllegalArgumentException("Must be a covariance matrix");
        }

        LogDataUtils.logDataModelList("Inverts a parent covaraince matrix.", getDataModelList());

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



