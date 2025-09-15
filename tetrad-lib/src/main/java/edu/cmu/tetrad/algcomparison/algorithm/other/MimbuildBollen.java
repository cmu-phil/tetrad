///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

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

    /**
     * Represents a specification for blocks used in the Mimbuild Bollen algorithm. This variable manages the
     * configuration of block-related information, such as the grouping of variables into blocks or other related
     * structural considerations.
     */
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

        if (blockSpec == null) {
            throw new IllegalArgumentException("Expecting a block specification.");
        }

        if (!dataSet.equals(blockSpec.dataSet())) {
            throw new IllegalArgumentException("Expecting the same dataset in the block specification.");
        }

        try {
            edu.cmu.tetrad.search.MimbuildBollen mimbuildBollen
                    = new edu.cmu.tetrad.search.MimbuildBollen(blockSpec);
            mimbuildBollen.setPenaltyDiscount(parameters.getDouble(Params.PENALTY_DISCOUNT));

            return mimbuildBollen.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the data type required by the algorithm.
     *
     * @return The data type required by the algorithm, which in this case is {@code DataType.Mixed}.
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
     * Returns a description of the MimbuildBollen algorithm.
     *
     * @return The description of the algorithm.
     */
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

    /**
     * Retrieves the block specification associated with the algorithm.
     *
     * @return The current block specification of type {@code BlockSpec}.
     */
    public BlockSpec getBlockSpec() {
        return blockSpec;
    }

    /**
     * Sets the block specification to be used by the MimbuildBollen algorithm.
     *
     * @param blockSpec the block specification of type {@code BlockSpec} to set
     */
    public void setBlockSpec(BlockSpec blockSpec) {
        this.blockSpec = blockSpec;
    }
}

