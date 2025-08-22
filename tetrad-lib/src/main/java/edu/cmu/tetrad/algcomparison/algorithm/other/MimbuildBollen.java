package edu.cmu.tetrad.algcomparison.algorithm.other;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.ExtraLatentStructureAlgorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Mimbuild Bollen.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Mimbuild (Bollen)",
        command = "mimbuild-bollen",
        algoType = AlgType.search_for_structure_over_latents
)
@Bootstrapping
public class MimbuildBollen implements Algorithm, ExtraLatentStructureAlgorithm {
    @Serial
    private static final long serialVersionUID = 23L;

    private BlockSpec blockSpec;

    /**
     * Constructs a new instance of the algorithm.
     */
    public MimbuildBollen() {
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

        try {
            edu.cmu.tetrad.search.MimbuildBollen mimbuildBollen
                    = new edu.cmu.tetrad.search.MimbuildBollen(dataSet, blockSpec.blocks(), blockSpec.blockVariables());
            mimbuildBollen.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

            return mimbuildBollen.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

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

    @Override
    public String getDescription() {
        return "Mimbuild Bollen";
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

    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    public void setBlockSpec(BlockSpec blockSpec) {
        this.blockSpec = blockSpec;
    }
}
