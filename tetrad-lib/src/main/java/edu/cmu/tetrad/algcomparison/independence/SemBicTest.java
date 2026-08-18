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

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.LinearGaussian;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.ScoreIndTest;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * The SemBicTest class implements the IndependenceWrapper interface and represents a test for independence based on SEM
 * BIC algorithm. It is annotated with the TestOfIndependence and LinearGaussian annotations.
 */
@TestOfIndependence(
        name = "SEM BIC Test",
        command = "sem-bic-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class SemBicTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the SEM BIC test.
     */
    public SemBicTest() {
    }

    /**
     * Returns an instance of IndependenceTest for the SEM BIC test.
     *
     * @param dataSet    The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return An instance of IndependenceTest for the SEM BIC test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        dataSet = MissingDataUtils.gate(dataSet, parameters, true, "SEM BIC Test");
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        SemBicScore score;

        if (dataSet instanceof ICovarianceMatrix) {
            score = new SemBicScore((ICovarianceMatrix) dataSet);
        } else {
            score = new SemBicScore((DataSet) dataSet, precomputeCovariances,
                    MissingDataUtils.fromParameters(parameters));
        }
        score.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
        score.setStructurePrior(parameters.getDouble(Params.STRUCTURE_PRIOR));
        score.setLambda(parameters.getDouble(Params.SINGULARITY_LAMBDA));

        ScoreIndTest test = new ScoreIndTest(score, dataSet);

        // Optional: report likelihood-ratio p-values instead of raw score differences. Does not change the
        // test's accept/reject decisions; see ScoreIndTest.setCalibratePValues.
        test.setCalibratePValues(parameters.getBoolean(Params.SCORE_TEST_CALIBRATED_P_VALUES));

        return test;
    }

    /**
     * Returns a short description of the test.
     *
     * @return A short description of the test.
     */
    @Override
    public String getDescription() {
        return "SEM-BIC-Test";
    }

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return The data type required for the search.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the parameters required for the SEM BIC test.
     *
     * @return A list of strings representing the parameters required for the SEM BIC test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.PENALTY_DISCOUNT);
        params.add(Params.STRUCTURE_PRIOR);
        params.add(Params.SCORE_TEST_CALIBRATED_P_VALUES);
        params.add(Params.PRECOMPUTE_COVARIANCES);
        params.add(Params.SINGULARITY_LAMBDA);
        params.add(Params.MISSING_DATA_POLICY);
        params.add(Params.MISSING_EM_RIDGE);
        params.add(Params.MISSING_EM_TOLERANCE);
        params.add(Params.MISSING_EM_MAX_ITERATIONS);
        params.add(Params.MISSING_ESS_MODE);
        return params;
    }
}

