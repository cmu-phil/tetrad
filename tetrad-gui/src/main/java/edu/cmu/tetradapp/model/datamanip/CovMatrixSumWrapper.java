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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.LogDataUtils;
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
public class CovMatrixSumWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * Splits the given data set by collinear columns.
     *
     * @param wrapper1 a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param wrapper2 a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public CovMatrixSumWrapper(DataWrapper wrapper1, DataWrapper wrapper2) {
        if (wrapper1 == null || wrapper2 == null) {
            throw new NullPointerException("The data must not be null");
        }
        DataModel model1 = wrapper1.getSelectedDataModel();
        DataModel model2 = wrapper2.getSelectedDataModel();

        if (!(model1 instanceof ICovarianceMatrix)) {
            throw new IllegalArgumentException("Expecting corrariance matrices.");
        }

        if (!(model2 instanceof ICovarianceMatrix)) {
            throw new IllegalArgumentException("Expecting corrariance matrices.");
        }

        Matrix corr1 = ((ICovarianceMatrix) model1).getMatrix();
        Matrix corr2 = ((ICovarianceMatrix) model2).getMatrix();

        Matrix corr3 = calcSum(corr1, corr2);

        ICovarianceMatrix covWrapper = new CovarianceMatrix(model1.getVariables(), corr3,
                ((ICovarianceMatrix) model1).getSampleSize());

        setDataModel(covWrapper);
        setSourceGraph(wrapper1.getSourceGraph());
        LogDataUtils.logDataModelList("Difference of matrices.", getDataModelList());

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

    private Matrix calcSum(Matrix corr1, Matrix corr2) {
        if (corr1.getNumRows() != corr2.getNumRows()) {
            throw new IllegalArgumentException("Covariance matrices must be the same size.");
        }

        Matrix corr3 = new Matrix(corr2.getNumRows(), corr2.getNumRows());

        for (int i = 0; i < corr3.getNumRows(); i++) {
            for (int j = 0; j < corr3.getNumRows(); j++) {
                double v = corr1.get(i, j) + corr2.get(i, j);
                corr3.set(i, j, v);
                corr3.set(j, i, v);
            }
        }

        return corr3;
    }


}




