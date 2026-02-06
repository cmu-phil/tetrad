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
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.GcmIndependenceTest;
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
        name = "GCM (Generalized Covariance Measure)",
        command = "gcm-test",
        dataType = DataType.Continuous
)
@General
public class Gcm implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * `Kci` constructor.
     */
    public Gcm() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a KCI test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {

        GcmIndependenceTest gcm = new GcmIndependenceTest((DataSet) dataSet,
                parameters.getDouble(Params.ALPHA));
        // start with something modest:
        gcm.setVerbose(false);
        gcm.setRegressorType(GcmIndependenceTest.RegressorType.RFF_RIDGE);
        gcm.setRidge(1e-2);
        gcm.setRffFeatures(400);   // try 100, 200, 400
        gcm.setRffSigma(2);      // try 0.5, 1.0, 2.0

        return new CachedIndependenceQueries(gcm);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the test.
     */
    @Override
    public String getDescription() {
        return "GCM";
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
//        params.add(Params.KCI_USE_APPROXIMATION);
        params.add(Params.ALPHA);
//        params.add(Params.SCALING_FACTOR);
//        params.add(Params.KCI_NUM_BOOTSTRAPS);
//        params.add(Params.KCI_EPSILON);
//        params.add(Params.KERNEL_TYPE);
//        params.add(Params.POLYNOMIAL_DEGREE);
//        params.add(Params.POLYNOMIAL_CONSTANT);
        return params;
    }
}

