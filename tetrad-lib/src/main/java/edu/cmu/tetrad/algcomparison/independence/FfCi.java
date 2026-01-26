package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.test.IndTestFfCi;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

@TestOfIndependence(
        name = "FF-CI (Fourier Features Conditional Independence)",
        command = "ff-ci",
        dataType = DataType.Mixed
)
@General
@Mixed
public class FfCi implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    public FfCi() { }

    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        IndTestFfCi test = new IndTestFfCi((DataSet) dataModel, parameters);

        // Core
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setVerbose(parameters.getBoolean(Params.VERBOSE));

        // RCIT knobs
//        test.setDoRcit(parameters.getBoolean(Params.RCIT_MODE));
        test.setLambda(parameters.getDouble(Params.RCIT_LAMBDA));
        test.setCenterFeatures(parameters.getBoolean(Params.RCIT_CENTER_FEATURES));
        test.setNumFeaturesXY(parameters.getInt(Params.RCIT_NUM_FEATURES_XY));
        test.setNumFeaturesZ(parameters.getInt(Params.RCIT_NUM_FEATURES_Z));

        // Seed (optional; IndTestRcit2 ctor already reads rcit.seed from Parameters)
        // Keeping this line makes the wrapper explicit and ensures consistency with algcomparison seed.
        test.setSeed(parameters.getLong(Params.SEED));

        // Permutations (if your Params has RCIT_PERMUTATIONS; it was set in the old wrapper)
        test.setPermutations(parameters.getInt(Params.RCIT_PERMUTATIONS));

        edu.cmu.tetrad.search.test.ffci_utils.PValueMethod[] approxes =
                edu.cmu.tetrad.search.test.ffci_utils.PValueMethod.values();
        test.setApproximation(approxes[parameters.getInt(Params.RCIT_APPROX) - 1]);

        return test;
    }

    @Override
    public String getDescription() {
        return "FF-CI";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        params.add(Params.SEED);
        params.add(Params.ALPHA);
        params.add(Params.RCIT_LAMBDA);
//        params.add(Params.RCIT_MODE);
        params.add(Params.RCIT_APPROX);
        params.add(Params.RCIT_CENTER_FEATURES);
        params.add(Params.RCIT_NUM_FEATURES_XY);
        params.add(Params.RCIT_NUM_FEATURES_Z);
        params.add(Params.RCIT_PERMUTATIONS);
        params.add(Params.VERBOSE);
        return params;
    }
}