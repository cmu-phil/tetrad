package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionBlocks;
import edu.cmu.tetrad.search.test.IndTestBlocksTs;
import edu.cmu.tetrad.search.test.IndTestTsBasisFunctions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for BF-Blocks-Test.
 *
 * @author josephramsey
 * @author bryanandrews
 * @version $Id: $Id
 */
@TestOfIndependence(
        name = "BF-Test-TS",
        command = "bf-test-ts",
        dataType = DataType.Mixed
)
@Mixed
public class BasisFunctionIndTestTs implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the DegenerateGaussianLrt class.
     */
    public BasisFunctionIndTestTs() {
    }

    /**
     * {@inheritDoc}x
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestTsBasisFunctions test = new IndTestTsBasisFunctions(SimpleDataLoader.getMixedDataSet(dataSet),
                parameters.getInt(Params.TRUNCATION_LIMIT));
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "BF Test TS";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        parameters.add(Params.TRUNCATION_LIMIT);
        return parameters;
    }
}
