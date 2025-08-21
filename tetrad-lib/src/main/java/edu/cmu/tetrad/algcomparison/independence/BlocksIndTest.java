package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionBlocks;
import edu.cmu.tetrad.search.test.IndTestBlocks;
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
        name = "Blocks-Test (Requires blocks)",
        command = "blocks-test",
        dataType = DataType.Mixed
)
@Mixed
public class BlocksIndTest implements BlockIndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the DegenerateGaussianLrt class.
     */
    public BlocksIndTest() {
    }

    /**
     * {@inheritDoc}x
     */
    @Override
    public IndependenceTest getTest(BlockSpec spec, Parameters parameters) {
        IndTestBlocks test = new IndTestBlocks(spec);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Blocks Test (Requires blocks)";
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
