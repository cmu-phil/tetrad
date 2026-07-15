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

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndTestCordEric;
import edu.cmu.tetrad.search.test.IndTestCord;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the CORD conditional independence test backed by Eric's engine
 * ({@link IndTestCordEric}): an omnibus, orthogonal rank-score test of Y &perp; Z | X that is
 * sensitive to higher-moment, tail, and co-volatility dependence which a covariance-based test
 * (e.g., Fisher Z) cannot see. CORD is nonparametric and cross-fitted, so it requires the raw
 * continuous sample rather than a covariance matrix.
 *
 * <p>This is the sibling of {@code Cord} that dispatches to {@link IndTestCordEric} rather than the
 * self-contained {@code IndTestCord}. The two share identical parameters and defaults, so they use the
 * same {@code ParamDescriptions} entries and can be A/B compared by swapping the command.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see IndTestCordEric
 */
@TestOfIndependence(
        name = "CORD",
        command = "cord-test",
        dataType = {DataType.Continuous}
)
@General
public class Cord implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    // CORD-specific parameter names. These are local so the wrapper compiles and runs against stock
    // Tetrad; they match the constants used by {@code Cord}, so the same Params + ParamDescriptions
    // entries render both tests in the GUI parameter grid.
    private static final String CORD_SYMMETRIC = "cordSymmetric";
    private static final String CORD_NUM_THRESHOLDS = "cordNumThresholds";
    private static final String CORD_NUM_ESTIMATORS = "cordNumEstimators";
    private static final String CORD_LEARNING_RATE = "cordLearningRate";
    private static final String CORD_MAX_LEAF_NODES = "cordMaxLeafNodes";
    private static final String CORD_SEED = "cordSeed";

    /**
     * Constructs a new instance of the algorithm.
     */
    public Cord() {
    }

    /**
     * Gets a CORD (Eric's engine) independence test for the given data model and parameters.
     *
     * @param dataModel  The data set to test independence against.
     * @param parameters The parameters of the test.
     * @return An IndependenceTest object.
     * @throws IllegalArgumentException if the dataModel is not a tabular dataset.
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet)) {
            throw new IllegalArgumentException(
                    "CORD requires a tabular (continuous) dataset, not a covariance matrix.");
        }

        double alpha = parameters.getDouble(Params.ALPHA);
        IndTestCord test = new IndTestCord(dataSet, alpha);

        // Defaults below mirror IndTestCordEric's own defaults, so an unset parameter leaves the test
        // exactly at its native configuration.
        test.setSymmetric(parameters.getBoolean(CORD_SYMMETRIC, true));
        test.setNLevels(parameters.getInt(CORD_NUM_THRESHOLDS, 9));
        test.setNEstimators(parameters.getInt(CORD_NUM_ESTIMATORS, 300));
        test.setLearningRate(parameters.getDouble(CORD_LEARNING_RATE, 0.1));
        test.setMaxLeafNodes(parameters.getInt(CORD_MAX_LEAF_NODES, 31));
        test.setSeed(parameters.getLong(CORD_SEED, -1L));

        test.setVerbose(parameters.getBoolean(Params.VERBOSE, false));

        return test;
    }

    /**
     * Retrieves the description of the CORD test.
     *
     * @return The description of the CORD test.
     */
    @Override
    public String getDescription() {
        return "CORD (orthogonal rank-score omnibus CI test)";
    }

    /**
     * Retrieves the data type of the independence test.
     *
     * @return The data type of the independence test.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Retrieves the parameters of the CORD test.
     *
     * @return A list of strings representing the parameters of the CORD test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(CORD_SYMMETRIC);
        params.add(CORD_NUM_THRESHOLDS);
        params.add(CORD_NUM_ESTIMATORS);
        params.add(CORD_LEARNING_RATE);
        params.add(CORD_MAX_LEAF_NODES);
        params.add(CORD_SEED);
        params.add(Params.VERBOSE);
        return params;
    }
}
