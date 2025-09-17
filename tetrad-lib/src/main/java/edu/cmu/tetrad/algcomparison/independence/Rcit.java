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
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for KCI test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "RCIT (Random Conditional Independence Test)",
        command = "rcit-test",
        dataType = DataType.Continuous
)
@General
public class Rcit implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * `Kci` constructor.
     */
    public Rcit() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a KCI test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.test.IndTestRcit test = new edu.cmu.tetrad.search.test.IndTestRcit((DataSet) dataSet);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setVerbose(parameters.getBoolean(Params.VERBOSE));
        return test;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the test.
     */
    @Override
    public String getDescription() {
        return "RCIT";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the data type of the test, which is continuous.
     *
     * @see DataType
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the parameters of the test.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.SEED);
        params.add(Params.ALPHA);
        params.add(Params.RCIT_LAMBDA);
        params.add(Params.RCIT_MODE);
        params.add(Params.RCIT_PERMUTATIONS);
        params.add(Params.RCIT_APPROX);
        params.add(Params.RCIT_CENTER_FEATURES);
        params.add(Params.RCIT_NUM_FEATURES);
        params.add(Params.RCIT_NUM_FEATURES_XY);
        params.add(Params.RCIT_NUM_FEATURES_Z);
        return params;
    }
}

