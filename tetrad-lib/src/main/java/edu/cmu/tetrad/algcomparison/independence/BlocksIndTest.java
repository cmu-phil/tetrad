package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
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
     * Stores the specification for blocks used in the BlocksIndTest. This object determines how data is divided into
     * blocks for statistical independence testing.
     */
    private BlockSpec blockSpec;

    /**
     * Initializes a new instance of the DegenerateGaussianLrt class.
     */
    public BlocksIndTest() {
        System.out.println();
    }

    /**
     * Sets the BlockSpec for this instance. The BlockSpec determines how data is divided into blocks for statistical
     * independence testing.
     *
     * @param blockSpec the BlockSpec object to set, specifying the configuration for dividing data into blocks for
     *                  analysis.
     */
    @Override
    public void setBlockSpec(BlockSpec blockSpec) {
        this.blockSpec = blockSpec;
    }

    /**
     * Creates and returns an instance of the {@link IndTestBlocks} initialized with a specific block specification and
     * a significance level extracted from the provided parameters.
     *
     * @param dataSet    A {@link DataModel} object representing the dataset to be used for the independence test.
     * @param parameters A {@link Parameters} object containing the configuration and attributes, including the alpha
     *                   level for the test.
     * @return An {@link IndependenceTest} instance configured with the block specification and the significance level
     * set from the passed parameters.
     */
    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestBlocks test = new IndTestBlocks(blockSpec);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        return test;
    }

    /**
     * Returns a description of the test.
     *
     * @return A string describing the test as "Blocks Test (Requires blocks)".
     */
    @Override
    public String getDescription() {
        return "Blocks Test (Requires blocks)";
    }

    /**
     * Retrieves the data type associated with this test.
     *
     * @return The data type of this test, which is {@link DataType#Mixed}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Retrieves the parameters used by the test. This includes configuration keys that determine the behavior or
     * thresholds of the test.
     *
     * @return A list of parameter names as strings, indicating the configurable attributes for the test.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        return parameters;
    }
}
