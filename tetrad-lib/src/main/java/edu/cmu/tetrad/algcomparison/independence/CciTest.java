package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.General;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.ConditionalCorrelationIndependence;
import edu.cmu.tetrad.search.test.IndTestConditionalCorrelation;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

// Can't change the name of this yet.

/**
 * Wrapper for Daudin Conditional Independence test.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "CCI-Test (Conditional Correlation Independence Test)",
        command = "cci-test",
        dataType = DataType.Continuous
)
@General
public class CciTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the CciTest class.
     */
    public CciTest() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestConditionalCorrelation cci = new IndTestConditionalCorrelation(SimpleDataLoader.getContinuousDataSet(dataSet),
                parameters.getDouble(Params.ALPHA));

        if (parameters.getInt(Params.BASIS_TYPE) == 1) {
            cci.setBasis(ConditionalCorrelationIndependence.Basis.Polynomial);
        } else if (parameters.getInt(Params.BASIS_TYPE) == 2) {
            cci.setBasis(ConditionalCorrelationIndependence.Basis.Cosine);
        } else {
            throw new IllegalStateException("Basis not configured.");
        }

        cci.setNumFunctions(parameters.getInt(Params.NUM_BASIS_FUNCTIONS));
        cci.setKernelMultiplier(parameters.getDouble(Params.KERNEL_MULTIPLIER));

        return cci;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "CCI Test";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.NUM_BASIS_FUNCTIONS);
        params.add(Params.KERNEL_MULTIPLIER);
        params.add(Params.BASIS_TYPE);
        params.add(Params.KERNEL_REGRESSION_SAMPLE_SIZE);
        return params;
    }
}
