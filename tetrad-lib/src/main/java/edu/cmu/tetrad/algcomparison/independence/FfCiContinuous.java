/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

///**
// * Wrapper for FF-CI-Continuous test.
// *
// * @author josephramsey
// * @version $Id: $Id
// */
//@TestOfIndependence(
//        name = "FFCI-Continuous (Fourier Features Conditional Independence)",
//        command = "ffci-continuous",
//        dataType = DataType.Continuous
//)
//@General
public class FfCiContinuous implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * `Kci` constructor.
     */
    public FfCiContinuous() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a RCIT test.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.test.FfCiContinuous test = new edu.cmu.tetrad.search.test.FfCiContinuous((DataSet) dataSet);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setNumFeatXY(parameters.getInt(Params.RCIT_NUM_FEATURES_XY));
        test.setNumFeatZ(parameters.getInt(Params.RCIT_NUM_FEATURES_Z));
        test.setPermutations(parameters.getInt(Params.RCIT_PERMUTATIONS));
//        test.setCenterFeatures(parameters.getBoolean(Params.RCIT_CENTER_FEATURES));
//        test.setBandwidthMultiplier(parameters.getDouble(Params.KML_BANDWIDTH_MULTIPLIER));
        test.setBwMaxRows(parameters.getInt(Params.KML_BW_MAX_ROWS));
        test.setLambda(parameters.getDouble(Params.KML_LAMBDA));

        edu.cmu.tetrad.search.test.FfCiContinuous.FeatureType[] values
                = edu.cmu.tetrad.search.test.FfCiContinuous.FeatureType.values();
        test.setFeatureType(values[parameters.getInt(Params.KML_FEATURE_TYPE) - 1]);

        edu.cmu.tetrad.search.test.FfCiContinuous.Approx[] approxes
                = edu.cmu.tetrad.search.test.FfCiContinuous.Approx.values();
        test.setApproximation(approxes[parameters.getInt(Params.RCIT_APPROX) - 1]);

//        test.setDoRcit(parameters.getBoolean(Params.RCIT_MODE));
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
        return "FFCI-Continuous";
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
        params.add(Params.KML_LAMBDA);
        params.add(Params.RCIT_PERMUTATIONS);
//        params.add(Params.KML_BANDWIDTH_MULTIPLIER);
        params.add(Params.KML_BW_MAX_ROWS);
        params.add(Params.RCIT_APPROX);
//        params.add(Params.RCIT_CENTER_FEATURES);
        params.add(Params.RCIT_NUM_FEATURES_XY);
        params.add(Params.RCIT_NUM_FEATURES_Z);
        params.add(Params.KML_FEATURE_TYPE);
//        params.add(Params.RCIT_MODE);
        params.add(Params.VERBOSE);
        return params;
    }
}

