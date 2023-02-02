package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataPersistence;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

// Can't change the name of this yet.

/**
 * Wrapper for Daudin Conditional Independence test.
 *
 * @author jdramsey
 */
@TestOfIndependence(
        name = "CCI-Lingam-Test (Conditional Correlation Independence Lingam Test)",
        command = "cci-lingam-test",
        dataType = DataType.Continuous
)
@General
public class CciLingamTest implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestConditionalCorrelationLingam cci = new IndTestConditionalCorrelationLingam(DataPersistence.getContinuousDataSet(dataSet),
                parameters.getDouble(Params.ALPHA));

        if (parameters.getInt(Params.BASIS_TYPE) == 1) {
            cci.setBasis(ConditionalCorrelationIndependenceLingam.Basis.Polynomial);
        } else if (parameters.getInt(Params.BASIS_TYPE) == 2) {
            cci.setBasis(ConditionalCorrelationIndependenceLingam.Basis.Cosine);
        } else {
            throw new IllegalStateException("Basis not configured.");
        }

        cci.setNumFunctions(parameters.getInt(Params.NUM_BASIS_FUNCTIONS));

        return cci;
    }

    @Override
    public String getDescription() {
        return "CCI Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.NUM_BASIS_FUNCTIONS);
        params.add(Params.BASIS_TYPE);
        return params;
    }
}