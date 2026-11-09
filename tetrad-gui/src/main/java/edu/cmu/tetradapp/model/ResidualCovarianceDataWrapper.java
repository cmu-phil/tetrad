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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.sem.ResidualCovariance;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.Serial;

/**
 * A Data box model, with an Estimator box as parent, holding the covariance among the measured variables that the
 * estimated SEM leaves unexplained: the sample covariance minus the implied covariance, with the model's error
 * variances on the diagonal. See {@link ResidualCovariance}.
 * <p>
 * The companion of {@link ImpliedCovarianceDataWrapper}, which holds what the model explains; this holds what it
 * does not. The intended use is to estimate a measurement model found by a latent-variable search (RLCD, FOFC),
 * take this residual, and run an observed-variable search (BOSS, say) on it, which then shows the structure among
 * the indicators that the common factors were hiding: on a block of checkpoints under one completion factor, the
 * module-to-module and module-to-unit links that the same search on the raw covariance buries under a hairball.
 * <p>
 * What a search on this residual can and cannot say. It reports edges the model lacks, not the ones it has; if
 * the graph already carries a measured-to-measured edge, its effect is subtracted here. The residual is only as
 * good as the measurement model: an indicator placed under the wrong latent, or a latent that should be two,
 * leaves its misfit in the residual, where a search will read it as structure. Look at the estimator's fit first.
 * Variables that were estimated on very different subsamples (a variable two thirds missing against the rest)
 * contribute residuals that do not belong to the same matrix as everyone else's; prefer a single covariance
 * estimate (EM or censored) as the Estimator's input.
 *
 * @author josephramsey
 */
public class ResidualCovarianceDataWrapper extends DataWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The estimated SEM the residual was taken from.
     */
    private SemIm semIm;

    /**
     * Constructs the residual covariance of the given estimate.
     *
     * @param wrapper the Estimator box.
     * @param params  the parameters (unused).
     */
    public ResidualCovarianceDataWrapper(SemEstimatorWrapper wrapper, Parameters params) {
        SemIm estimated = wrapper.getSemEstimator().getEstimatedSem();

        if (estimated == null) {
            throw new IllegalArgumentException("The Estimator box has no estimated model yet.");
        }

        CovarianceMatrix residual = ResidualCovariance.residual(estimated);
        residual.setName("Residual covariance");
        setDataModel(residual);
        setSourceGraph(estimated.getSemPm().getGraph());
        this.semIm = estimated;

        TetradLogger.getInstance().log("Residual covariance over " + residual.getDimension()
                                       + " measured variables, sample size " + residual.getSampleSize()
                                       + ": the sample covariance minus the estimated model's implied "
                                       + "covariance, with the model's error variances on the diagonal.");
        LogDataUtils.logDataModelList("Residual covariance of an estimated SEM.", getDataModelList());
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

    /**
     * The estimated SEM the residual was taken from.
     *
     * @return the SEM IM.
     */
    public SemIm getSemIm() {
        return this.semIm;
    }
}
