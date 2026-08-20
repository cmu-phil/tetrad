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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Converts a continuous data set to its Gaussian copula ("nonparanormal") correlation matrix: Kendall's tau-b for
 * each pair, mapped to the latent Gaussian correlation by rho = sin(pi * tau / 2).
 * <p>
 * Use this in place of Convert to Correlation Matrix when the variables are plausibly monotone transformations of
 * an underlying Gaussian -- skewed, log-scaled or otherwise reshaped margins. Pearson correlation is biased toward
 * zero by such transformations and rank correlation is not, so the linear-Gaussian scores and tests downstream are
 * given the correlation structure they are actually written against.
 * <p>
 * This is a different operation from Nonparanormal Transform, which rank-transforms the data set and returns a data
 * set. This returns a correlation matrix, so the row-level data is gone and anything needing rows (residuals,
 * bootstrapping, nonlinear tests) cannot run downstream of it.
 * <p>
 * The invariance is to monotone MARGINAL transformation only; nonlinearity in a conditional mean is outside the
 * model and is not addressed here. Warnings about non-continuous columns, missing values and loss of positive
 * semidefiniteness are written to the log; see {@link DataTransforms#covarianceGaussianCopula(DataSet)}.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GaussianCopulaCorrelationConverter extends DataWrapper {

    private static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS==============================//

    /**
     * <p>Constructor for GaussianCopulaCorrelationConverter.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GaussianCopulaCorrelationConverter(DataWrapper wrapper, Parameters params) {
        if (!(wrapper.getSelectedDataModel() instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("Expecting a tabular data set. The Gaussian copula correlation is "
                                               + "estimated from ranks, so it needs the rows; a covariance matrix "
                                               + "no longer has them.");
        }

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Expecting a continuous data set. For ordinal variables the "
                                               + "corresponding estimator is the polychoric correlation, which "
                                               + "this is not.");
        }

        ICovarianceMatrix covMatrix = DataTransforms.covarianceGaussianCopula(dataSet);
        covMatrix.setKnowledge(dataSet.getKnowledge().copy());

        setDataModel(covMatrix);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Conversion of parent data to Gaussian copula correlation matrix form.",
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
