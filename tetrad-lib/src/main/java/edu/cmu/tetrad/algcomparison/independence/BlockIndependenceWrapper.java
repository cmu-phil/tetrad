package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.utils.HasParameters;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.List;

/**
 * An interface that extends functionality of the {@link IndependenceWrapper} and {@link HasParameters}
 * interfaces by adding support for block-specific configuration in independence testing.
 *
 * <p>Implementations of this interface provide the ability to configure block structures,
 * perform independence tests based on these structures, and retrieve descriptions, data types,
 * and parameter requirements of the respective tests.</p>
 *
 * Defines methods for configuring block-specific independence tests and retrieving meta-details
 * about the test such as its description, required data type, and associated parameters.
 */
public interface BlockIndependenceWrapper extends IndependenceWrapper, HasParameters {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    @Serial
    long serialVersionUID = 23L;

    /**
     * Configures the block specification for the independence test.
     * The block specification describes the structure and attributes
     * of the blocks relevant for performing a block-specific
     * independence test.
     *
     * @param blockSpec the block specification to be applied, which
     *                  defines the dataset, block structure, and
     *                  block variables.
     */
    void setBlockSpec(BlockSpec blockSpec);

    /**
     * Returns a short of this independence test.
     *
     * @return This description.
     */
    String getDescription();

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return This type.
     */
    DataType getDataType();

    /**
     * Returns the parameters that this search uses.
     *
     * @return A list of String names of parameters.
     */
    List<String> getParameters();
}
