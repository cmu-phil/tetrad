package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionBlocks;
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
        name = "BF-Blocks-Test",
        command = "bf-blocks-test",
        dataType = DataType.Mixed
)
@Mixed
public class BasisFunctionBlocksIndTest implements IndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the DegenerateGaussianLrt class.
     */
    public BasisFunctionBlocksIndTest() {
    }

    /**
     * {@inheritDoc}x
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestBasisFunctionBlocks test = new IndTestBasisFunctionBlocks(SimpleDataLoader.getMixedDataSet(dataSet),
                parameters.getInt(Params.TRUNCATION_LIMIT));
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        test.setEffectiveSampleSize(parameters.getInt(Params.EFFECTIVE_SAMPLE_SIZE));
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "BF Blocks Test";
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
        parameters.add(Params.EFFECTIVE_SAMPLE_SIZE);
        return parameters;
    }
}
