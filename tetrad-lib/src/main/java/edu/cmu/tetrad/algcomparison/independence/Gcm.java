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

        edu.cmu.tetrad.search.test.Gcm gcm = new edu.cmu.tetrad.search.test.Gcm((DataSet) dataSet,
                parameters.getDouble(Params.ALPHA));
        gcm.setVerbose(parameters.getBoolean(Params.VERBOSE));
        edu.cmu.tetrad.search.test.Gcm.RegressorType[] types = edu.cmu.tetrad.search.test.Gcm.RegressorType.values();
        gcm.setRegressorType(types[parameters.getInt(Params.GCM_REGRESSOR_TYPE) - 1]);
        gcm.setRidge(parameters.getDouble(Params.GCM_RIDGE));
        gcm.setRffFeatures(parameters.getInt(Params.GCM_RFF_FEATURES));   // try 100, 200, 400
        gcm.setRffSigma(parameters.getDouble(Params.GCM_RFF_SIGMA));

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
        params.add(Params.ALPHA);
        params.add(Params.GCM_REGRESSOR_TYPE);
        params.add(Params.GCM_RIDGE);
        params.add(Params.GCM_RFF_FEATURES);
        params.add(Params.GCM_RFF_SIGMA);
        return params;
    }
}

