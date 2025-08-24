package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.test.IndTestBlocksLemma10;
import edu.cmu.tetrad.search.test.IndTestBlocksTs;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.ejml.simple.SimpleMatrix;

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
        name = "Blocks-Test-TS (Trek separation) Requires blocks)",
        command = "blocks-test-ts",
        dataType = DataType.Mixed
)
@Mixed
public class BlocksIndTestTs implements BlockIndependenceWrapper {

    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * Represents the specification for block structures required in the
     * "Blocks Test Lemma 10" independence test implementation. This variable
     * holds the configuration or details defining the nature of the blocks to be
     * used in the test.
     */
    private BlockSpec blockSpec;

    /**
     * Initializes a new instance of the DegenerateGaussianLrt class.
     */
    public BlocksIndTestTs() {
    }

    /**
     * Sets the specification for block structures required in the "Blocks Test Lemma 10"
     * independence test implementation. This method updates the configuration or details
     * defining the nature of the blocks to be used in the test.
     *
     * @param blockSpec the specification for the block structure to be set
     */
    public void setBlockSpec(BlockSpec blockSpec) {
        this.blockSpec = blockSpec;
    }

    /**
     * Creates and returns an instance of an independence test based on the specified block structure
     * and parameters provided.
     *
     * @param dataModel The data model which contains the dataset for performing the test.
     * @param parameters A collection of parameters including test-specific configuration such as the alpha value.
     * @return An instance of the {@link IndependenceTest} configured with the provided block specification and parameters.
     */
    @Override
    public IndependenceTest getTest(DataModel dataModel, Parameters parameters) {
        IndTestBlocksTs test = new IndTestBlocksTs(blockSpec);
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        return test;
    }

    /**
     * Returns a description of the "Blocks Test Lemma 10 (Requires blocks)" independence test.
     *
     * @return A string containing the description of the test.
     */
    @Override
    public String getDescription() {
        return "Blocks Test Lemma 10 (Requires blocks)";
    }

    /**
     * Retrieves the data type associated with this independence test implementation.
     *
     * @return the data type, which is {@link DataType#Mixed}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Retrieves a list of parameter names required for the "Blocks Test Lemma 10" independence test.
     * These parameters define the test's configuration and behavior.
     *
     * @return a list of parameter names as strings.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        return parameters;
    }
}
