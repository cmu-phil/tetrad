///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.data.missing.MiceLiteImputer;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MultipleImputer;
import edu.cmu.tetrad.data.missing.MvnImputer;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.List;

/**
 * A data manipulation that fills in missing values by multiple imputation, producing m completed datasets rather
 * than one. The imputers themselves are the existing ones in
 * {@link edu.cmu.tetrad.data.missing}; this wrapper exposes them in a session box.
 *
 * <h2>Which method applies</h2>
 *
 * <p>{@link MvnImputer} estimates a saturated multivariate normal by EM and then draws each missing block from its
 * conditional normal given that row's observed entries. It requires a continuous dataset and refuses anything
 * else, so it is not an option for mixed data.</p>
 *
 * <p>{@link MiceLiteImputer} handles continuous, discrete and mixed data by chained equations with predictive mean
 * matching: each incomplete variable is regressed on the others in turn, and each missing cell takes the value of a
 * randomly chosen near donor. It is not EM. Because every imputed value is copied from an observed row, discrete
 * imputations are automatically valid categories, which is the property a general-location EM would have to be
 * built to guarantee.</p>
 *
 * <p>The AUTO method picks MVN for a continuous dataset and MICE for anything else.</p>
 *
 * <h2>Things worth knowing before using the output</h2>
 *
 * <p><b>These draw, they do not average.</b> Imputing each cell at its conditional mean would shrink residual
 * variance and pull correlations toward the imputation model's own, which downstream shows up as deflated variance,
 * spurious determinism findings, and independence tests that are confident about structure the imputation put
 * there. Both imputers draw instead, which preserves second moments.</p>
 *
 * <p><b>The output is m datasets, not one.</b> That is deliberate: a single completed dataset presents imputed
 * values as though they were measured, and any analysis run on it understates uncertainty. Both imputers are
 * "improper" in Rubin's sense -- parameters are fixed at their estimates rather than drawn from a posterior -- so
 * even across m datasets the between-imputation variability is somewhat understated.</p>
 *
 * <p><b>Validity requires MAR.</b> Under MNAR, imputation is not merely noisy but biased, and no setting here
 * repairs that.</p>
 *
 * <p><b>For a continuous dataset feeding a score-based search, consider not imputing at all.</b> The EM covariance
 * matrix is the maximum likelihood estimate under MAR and can be handed to a search directly, which avoids
 * fabricating rows entirely. Imputation earns its place when the downstream method needs cases rather than moments.</p>
 *
 * @author josephramsey
 * @see MvnImputer
 * @see MiceLiteImputer
 */
public class ImputeMissingDataWrapper extends DataWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The imputation methods offered.
     */
    public enum Method {

        /**
         * MVN for a continuous dataset, MICE otherwise.
         */
        AUTO,

        /**
         * EM under a saturated multivariate normal, then conditional-normal draws. Continuous data only.
         */
        MVN_EM,

        /**
         * Chained equations with predictive mean matching. Continuous, discrete or mixed.
         */
        MICE
    }

    /**
     * Constructs the wrapper, running the imputation.
     *
     * @param wrapper The parent data.
     * @param params  The parameters: {@code imputationMethod}, {@code numImputations}, {@code imputationSeed}, and
     *                for MVN the EM controls {@code emRidge}, {@code emTolerance}, {@code emMaxIterations}.
     * @throws IllegalArgumentException if the parent data has no missing values, or the chosen method cannot handle
     *                                  the data. The message says which.
     */
    public ImputeMissingDataWrapper(DataWrapper wrapper, Parameters params) {
        DataSet dataSet = (DataSet) wrapper.getSelectedDataModel();

        if (!dataSet.existsMissingValue()) {
            throw new IllegalArgumentException("This dataset has no missing values; there is nothing to impute.");
        }

        Method method = resolveMethod(params, dataSet);
        int m = Math.max(2, params.getInt("numImputations", 5));
        long seed = params.getLong("imputationSeed", 0L);

        MultipleImputer imputer = switch (method) {
            case MVN_EM -> new MvnImputer(MissingDataSpec.multipleImputation(m)
                    .withEmRidge(params.getDouble("emRidge", MissingDataSpec.emCovariance().getEmRidge()))
                    .withEmTolerance(params.getDouble("emTolerance",
                            MissingDataSpec.emCovariance().getEmTolerance()))
                    .withEmMaxIterations(params.getInt("emMaxIterations",
                            MissingDataSpec.emCovariance().getEmMaxIterations())));
            case MICE -> new MiceLiteImputer(
                    Math.max(1, params.getInt("miceNumDonors", 5)),
                    Math.max(1, params.getInt("miceNumSweeps", 5)));
            case AUTO -> throw new IllegalStateException("AUTO should have been resolved.");
        };

        List<DataSet> imputed = imputer.impute(dataSet, m, seed);

        DataModelList list = new DataModelList();
        list.addAll(imputed);

        setDataModelList(list);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Parent data with missing values imputed.", getDataModelList());
        TetradLogger.getInstance().log("Imputation: method=" + method + ", m=" + imputed.size()
                                       + ", seed=" + seed + ". Draws, not conditional means; valid under MAR;"
                                       + " improper MI, so between-imputation variability is understated.");
    }

    /**
     * Resolves AUTO against the data, and checks that an explicit choice can actually run on it. MVN is rejected
     * for anything but continuous data rather than silently coerced: treating discrete category codes as normal
     * would impute fractional categories and then round them, which is not the same model and is worse than the
     * donor method that handles this properly.
     */
    private static Method resolveMethod(Parameters params, DataSet dataSet) {
        String name = params.getString("imputationMethod", Method.AUTO.name());

        Method method;
        try {
            method = Method.valueOf(name);
        } catch (IllegalArgumentException e) {
            method = Method.AUTO;
        }

        if (method == Method.AUTO) {
            return dataSet.isContinuous() ? Method.MVN_EM : Method.MICE;
        }

        if (method == Method.MVN_EM && !dataSet.isContinuous()) {
            throw new IllegalArgumentException(
                    "The EM multivariate normal imputer requires a continuous dataset. This dataset is not"
                    + " continuous; use the MICE method, which handles discrete and mixed data.");
        }

        return method;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return The exemplar.
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }
}
