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
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.EmCovarianceEstimator;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.data.missing.CensoredMvnCovarianceEstimator;
import edu.cmu.tetrad.data.missing.PairwiseCovarianceEstimator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.Serial;

/**
 * A Data box transform that replaces a continuous data set by one covariance matrix estimated from all of its
 * rows, incomplete ones included, by one of three estimators: EM under a multivariate normal (consistent when
 * values are missing at random, positive semidefinite); pairwise-complete (each entry from the rows complete on
 * its own pair; need not be positive semidefinite); or censored EM, which in addition treats every cell at a
 * stated ceiling as "at least the ceiling" rather than as an exact value (see
 * {@link CensoredMvnCovarianceEstimator}). The matrix is what the rest of the session then sees, so an Estimator
 * box or a score-based search downstream runs on it directly -- which is how the censored and EM covariances
 * reach BOSS and the SEM estimator from the interface rather than only from code.
 * <p>
 * The sample size carried by the matrix is chosen by the effective-sample-size mode: the full row count, the mean
 * of the pairwise-complete counts over all pairs, or the smallest of them. With missing values, the full count
 * overstates the evidence; mean pairwise is the usual choice.
 * <p>
 * Censoring applies one ceiling to every column (a value of 100 for grades), as the RLCD option does. Columns
 * that never reach it are unaffected. The estimator's EM is deterministic by default, so the same settings give
 * the same matrix.
 *
 * @author josephramsey
 */
public class CovarianceEstimateDataWrapper extends DataWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Parameter: which estimator. One of "em", "pairwise", "censored".
     */
    public static final String METHOD = "covarianceEstimateMethod";

    /**
     * Parameter: the ceiling for the censored estimator.
     */
    public static final String CEILING = "covarianceEstimateCeiling";

    /**
     * Parameter: the effective-sample-size mode. One of "fullN", "meanPairwise", "minPairwise".
     */
    public static final String ESS_MODE = "covarianceEstimateEssMode";

    /**
     * The estimators offered.
     */
    public enum Method {
        /**
         * EM under a multivariate normal; missing values at random.
         */
        EM,
        /**
         * Pairwise-complete covariances.
         */
        PAIRWISE,
        /**
         * EM under a multivariate normal with cells at the ceiling right-censored; other missing values at random.
         */
        CENSORED
    }

    /**
     * Constructs the covariance estimate of the parent's selected data set.
     *
     * @param wrapper the parent Data box.
     * @param params  the parameters: method, ceiling, effective-sample-size mode.
     */
    public CovarianceEstimateDataWrapper(DataWrapper wrapper, Parameters params) {
        DataModel model = wrapper.getSelectedDataModel();

        if (!(model instanceof DataSet dataSet)) {
            throw new IllegalArgumentException("The parent must hold a tabular data set, not a covariance matrix.");
        }

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Covariance estimation needs continuous data; discrete or mixed "
                                               + "columns were found.");
        }

        Method method = method(params);
        String essMode = params.getString(ESS_MODE, "meanPairwise");
        int n = dataSet.getNumRows();
        ICovarianceMatrix cov;
        int[][] counts;
        String note;

        switch (method) {
            case EM -> {
                EmCovarianceEstimator estimator = new EmCovarianceEstimator(dataSet);
                cov = estimator.estimate();
                counts = estimator.getPairwiseCounts();
                note = "EM covariance: " + estimator.getNumIterations() + " iterations, converged "
                       + estimator.isConverged() + ", min pairwise count " + estimator.getMinPairwiseCount() + ".";
            }
            case PAIRWISE -> {
                cov = PairwiseCovarianceEstimator.estimate(dataSet);
                counts = pairwiseCounts(dataSet);
                note = "Pairwise-complete covariance (need not be positive semidefinite).";
            }
            case CENSORED -> {
                double ceiling = params.getDouble(CEILING, 100.0);
                CensoredMvnCovarianceEstimator estimator = new CensoredMvnCovarianceEstimator(dataSet,
                        CensoredMvnCovarianceEstimator.commonCeiling(dataSet.getNumColumns(), ceiling), null);
                cov = estimator.estimate();
                counts = estimator.getPairwiseCounts();
                note = "Censored EM covariance at ceiling " + ceiling + ": " + estimator.getCensoredCells()
                       + " censored cells, " + estimator.getMissingCells() + " missing cells (missing at random), "
                       + estimator.getNumIterationsRun() + " iterations.";
            }
            default -> throw new IllegalStateException("Unknown method " + method);
        }

        int ess = effectiveSampleSize(counts, n, essMode);
        CovarianceMatrix result = new CovarianceMatrix(dataSet.getVariables(), cov.getMatrix(), ess);
        result.setName(dataSet.getName() == null ? "Covariance estimate" : dataSet.getName() + " (" + method.name()
                .toLowerCase() + " covariance)");

        DataModelList list = new DataModelList();
        list.add(result);
        setDataModelList(list);
        setSourceGraph(wrapper.getSourceGraph());

        TetradLogger.getInstance().log(note + " Sample size carried: " + ess + " (" + essMode + "; full N = " + n
                                       + ").");
        LogDataUtils.logDataModelList("Covariance matrix estimated from incomplete data.", getDataModelList());
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
     * The method named by the parameters, EM if unset or unrecognized.
     *
     * @param params the parameters.
     * @return the method.
     */
    public static Method method(Parameters params) {
        try {
            return Method.valueOf(params.getString(METHOD, Method.EM.name()).trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Method.EM;
        }
    }

    /**
     * The number of rows complete on each pair of columns.
     */
    private static int[][] pairwiseCounts(DataSet dataSet) {
        int p = dataSet.getNumColumns();
        int n = dataSet.getNumRows();
        int[][] counts = new int[p][p];

        for (int a = 0; a < p; a++) {
            for (int b = a; b < p; b++) {
                int c = 0;

                for (int i = 0; i < n; i++) {
                    if (!Double.isNaN(dataSet.getDouble(i, a)) && !Double.isNaN(dataSet.getDouble(i, b))) c++;
                }

                counts[a][b] = c;
                counts[b][a] = c;
            }
        }

        return counts;
    }

    /**
     * The sample size to carry: the full row count, the mean of the off-diagonal pairwise counts, or their minimum.
     */
    private static int effectiveSampleSize(int[][] counts, int n, String essMode) {
        String mode = essMode == null ? "" : essMode.trim().toLowerCase();
        int p = counts.length;

        if (mode.isEmpty() || mode.equals("fulln") || mode.equals("full_n") || p < 2) return n;

        double sum = 0;
        int min = Integer.MAX_VALUE;
        int pairs = 0;

        for (int a = 0; a < p; a++) {
            for (int b = a + 1; b < p; b++) {
                sum += counts[a][b];
                min = Math.min(min, counts[a][b]);
                pairs++;
            }
        }

        if (mode.equals("minpairwise") || mode.equals("min_pairwise")) return Math.max(1, min);
        return Math.max(1, (int) Math.round(sum / pairs));
    }
}
