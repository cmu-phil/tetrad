package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.FfCiContinuous;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for FFCI test with a reduced parameter set.
 *
 * Exposed parameters:
 *   - alpha
 *   - permutations
 *   - numFeatures (applied to XY and Z)
 *   - lambda
 *   - bandwidthMultiplier
 *   - verbose
 */
@TestOfIndependence(
        name = "FFCI (Fourier Features Conditional Independence) - Simple",
        command = "ffci-simple",
        dataType = DataType.Mixed
)
@General
@Mixed
public class FfCi implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 24L;

    // ---- fixed defaults (not exposed as GUI params) ----
    private static final int DEFAULT_BW_MAX_ROWS = 100;

    // If you have strong preferences, set these explicitly.
    // Otherwise, the first enum value is the safest “always exists” default.
    private static final FfCiContinuous.FeatureType DEFAULT_FEATURE_TYPE = FfCiContinuous.FeatureType.ORF;

    // Only relevant when categories are present; pick your house default.
    private static final double DEFAULT_CAT_RHO = 0.5;

    public FfCi() { }

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        edu.cmu.tetrad.search.test.FfCi test = new edu.cmu.tetrad.search.test.FfCi((DataSet) dataSet);

        test.setAlpha(parameters.getDouble(Params.ALPHA));

        // Single “numFeatures” knob: apply to XY and Z.
        test.setNumFeaturesXY(parameters.getInt(Params.RCIT_NUM_FEATURES_XY));
        test.setNumFeaturesZ(parameters.getInt(Params.RCIT_NUM_FEATURES_Z));

        test.setPermutations(parameters.getInt(Params.RCIT_PERMUTATIONS));
        test.setBandwidthMultiplier(parameters.getDouble(Params.KML_BANDWIDTH_MULTIPLIER));
        test.setLambda(parameters.getDouble(Params.KML_LAMBDA));
        FfCiContinuous.Approx[] approxes
                = FfCiContinuous.Approx.values();
        test.setApproximation(approxes[parameters.getInt(Params.RCIT_APPROX) - 1]);

        // Fixed / hidden knobs
        test.setBwMaxRows(DEFAULT_BW_MAX_ROWS);
        test.setFeatureType(DEFAULT_FEATURE_TYPE);
        test.setCatRho(DEFAULT_CAT_RHO);

        test.setVerbose(parameters.getBoolean(Params.VERBOSE));
        return test;
    }

    @Override
    public String getDescription() {
        return "FFCI";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.RCIT_PERMUTATIONS);
        params.add(Params.RCIT_NUM_FEATURES_Z);
        params.add(Params.RCIT_NUM_FEATURES_XY);
        params.add(Params.KML_LAMBDA);
        params.add(Params.KML_BANDWIDTH_MULTIPLIER);
        params.add(Params.RCIT_APPROX);
        params.add(Params.VERBOSE);
        return params;
    }
}