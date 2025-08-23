package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ExtraLatentStructureAlgorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Mimbuild PCA.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Mimbuild (PCA)",
        command = "mimbuild-pca",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class MimbuildPca implements Algorithm, ExtraLatentStructureAlgorithm {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Represents the block specification used within the MimbuildPca algorithm. This variable defines the structure of
     * blocks and the block variables to be used during the execution of the PCA-based search for latent structure.
     */
    private BlockSpec blockSpec;

    /**
     * Constructs a new instance of the algorithm.
     */
    public MimbuildPca() {
    }

    /**
     * Executes a factor analysis search on the given data model using the provided parameters.
     *
     * @param dataModel  The data model to perform the factor analysis on.
     * @param parameters The parameters for the factor analysis.
     * @return The resulting graph after performing the factor analysis.
     * @throws IllegalArgumentException If the data model is not a continuous dataset.
     */
    public Graph search(DataModel dataModel, Parameters parameters) {
        if (!(dataModel instanceof DataSet dataSet && dataModel.isContinuous())) {
            throw new IllegalArgumentException("Expecting a continuous dataset.");
        }

        if (blockSpec == null) {
            throw new IllegalArgumentException("Expecting a block specification.");
        }

        if (!dataSet.equals(blockSpec.dataSet())) {
            throw new IllegalArgumentException("Expecting the same dataset in the block specification.");
        }

        try {
            edu.cmu.tetrad.search.MimbuildPca mimbuildPca
                    = new edu.cmu.tetrad.search.MimbuildPca(blockSpec);
            mimbuildPca.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));
            return mimbuildPca.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the data type associated with this algorithm.
     *
     * @return the data type used by this algorithm, represented as {@link DataType}.
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    /**
     * Returns an undirected graph used for comparison.
     *
     * @param graph The true directed graph, if there is one.
     * @return The undirected graph for comparison.
     */
    public Graph getComparisonGraph(Graph graph) {
        return GraphUtils.undirectedGraph(graph);
    }

    /**
     * Provides a brief description of the "Mimbuild Pca" algorithm.
     *
     * @return A one-line description of the algorithm as a string.
     */
    @Override
    public String getDescription() {
        return "Mimbuild Pca";
    }

    /**
     * Retrieves the parameters for the current instance of the {@code FactorAnalysis} class.
     *
     * @return a list of strings representing the parameters for the factor analysis.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.PENALTY_DISCOUNT);
        return parameters;
    }

    /**
     * Retrieves the block specification for the current instance.
     *
     * @return the block specification as an instance of {@link BlockSpec}.
     */
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    /**
     * Sets the block specification for the current instance.
     *
     * @param blockSpec the block specification to be set, represented as an instance of {@link BlockSpec}
     */
    public void setBlockSpec(BlockSpec blockSpec) {
        this.blockSpec = blockSpec;
    }
}
