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
import edu.cmu.tetrad.search.score.PoissonPriorScore;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.ScoreIndTest;
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
        name = "Poisson Prior Test",
        command = "poisson-prior-test",
        dataType = {DataType.Continuous, DataType.Covariance}
)
@LinearGaussian
public class PoissonBicTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the SEM BIC test.
     */
    public PoissonBicTest() {
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
        boolean precomputeCovariances = parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES);

        PoissonPriorScore score;

        if (dataSet instanceof DataSet) {
            score = new edu.cmu.tetrad.search.score.PoissonPriorScore((DataSet) dataSet, parameters.getBoolean(Params.PRECOMPUTE_COVARIANCES));
        } else if (dataSet instanceof ICovarianceMatrix) {
            score = new edu.cmu.tetrad.search.score.PoissonPriorScore((ICovarianceMatrix) dataSet);
        } else {
            throw new IllegalArgumentException("Expecting either a dataset or a covariance matrix.");
        }

        score.setLambda(parameters.getDouble(Params.POISSON_LAMBDA));
        score.setSingularityLambda(parameters.getDouble(Params.SINGULARITY_LAMBDA));

        return new ScoreIndTest(score, dataSet);
    }

    /**
     * Returns a short description of the test.
     *
     * @return A short description of the test.
     */
    @Override
    public String getDescription() {
        return "Poisson Prior Test";
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
        params.add(Params.PRECOMPUTE_COVARIANCES);
        params.add(Params.POISSON_LAMBDA);
        params.add(Params.SINGULARITY_LAMBDA);
        return params;
    }
}

