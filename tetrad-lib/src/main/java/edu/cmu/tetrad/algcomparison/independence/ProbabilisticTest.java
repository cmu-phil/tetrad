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

import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.test.IndTestProbabilistic;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Dec 17, 2018 3:44:46 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "Probabilistic Test",
        command = "prob-test",
        dataType = DataType.Discrete
)
public class ProbabilisticTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the ProbabilisticTest class.
     */
    public ProbabilisticTest() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestProbabilistic test = new IndTestProbabilistic(SimpleDataLoader.getDiscreteDataSet(dataSet));
        test.setThreshold(parameters.getBoolean(Params.NO_RANDOMLY_DETERMINED_INDEPENDENCE));
        test.setCutoff(parameters.getDouble(Params.CUTOFF_IND_TEST));
        test.setPriorEquivalentSampleSize(parameters.getDouble(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE));
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Probabilistic Conditional Independence Test";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.NO_RANDOMLY_DETERMINED_INDEPENDENCE);
        parameters.add(Params.CUTOFF_IND_TEST);
        parameters.add(Params.PRIOR_EQUIVALENT_SAMPLE_SIZE);
        return parameters;
    }

}

