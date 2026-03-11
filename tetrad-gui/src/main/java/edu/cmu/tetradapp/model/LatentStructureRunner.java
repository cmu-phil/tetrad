/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.util.Parameters;

import java.text.ParseException;
import java.util.List;
import java.util.Objects;

/**
 * Runner for the Block Search session node. Requires an upstream BlockSpec (from a ClusterRunner) and ensures it is
 * present before running.
 */
public class LatentStructureRunner extends GeneralAlgorithmRunner {

    private final LatentClustersRunner runner;

    /**
     * Constructs a LatentStructureRunner instance, which requires a data wrapper, a latent clusters runner, and search
     * parameters. The latent clusters runner must provide an upstream BlockSpec, which is necessary for running this
     * class.
     *
     * @param data                 the data wrapper containing the dataset to be used
     * @param latentClustersRunner the instance of LatentClustersRunner providing the upstream BlockSpec; must not be
     *                             null
     * @param parameters           parameters for the block search session node
     * @throws NullPointerException if the latentClustersRunner argument is null
     * @throws ParseException       if any
     */
    public LatentStructureRunner(DataWrapper data, LatentClustersRunner latentClustersRunner, Parameters parameters)
            throws ParseException {
        super(data, parameters);
        this.runner = Objects.requireNonNull(latentClustersRunner, "ClusterRunner required");
        super.blockSpec = latentClustersRunner.getBlockSpec();
    }

    /**
     * Constructs a LatentStructureRunner instance, utilizing a data wrapper, a latent clusters runner,
     * a latent structure runner, and search parameters. The latent clusters runner is required to provide
     * an upstream BlockSpec, and the algorithm is set based on the provided latent structure runner.
     *
     * @param data                 the data wrapper containing the dataset to be processed
     * @param latentClustersRunner the instance of LatentClustersRunner providing the upstream BlockSpec;
     *                              must not be null
     * @param latentStructureRunner the instance of LatentStructureRunner providing the algorithm to be used;
     *                               must not be null
     * @param parameters           parameters for the block search session node
     * @throws NullPointerException if the latentClustersRunner or latentStructureRunner argument is null
     * @throws ParseException       if a parsing error occurs during initialization
     */
    public LatentStructureRunner(DataWrapper data, LatentClustersRunner latentClustersRunner,
                                 LatentStructureRunner latentStructureRunner, Parameters parameters)
    throws ParseException{
        super(data, parameters);
        this.runner = Objects.requireNonNull(latentClustersRunner, "ClusterRunner required");
        super.blockSpec = latentClustersRunner.getBlockSpec();
        setAlgorithm(latentStructureRunner.getAlgorithm());
    }

    /**
     * Constructs a LatentStructureRunner instance, which is responsible for executing the latent structure
     * analysis using the provided data, latent clusters runner, and parameters. This constructor ensures
     * that an upstream BlockSpec is provided by the latentClustersRunner, and initializes the object accordingly.
     *
     * @param data                 the DataWrapper containing the dataset to be processed
     * @param latentClustersRunner the instance of LatentClustersRunner providing the upstream BlockSpec;
     *                              must not be null
     * @param knowledge            the KnowledgeBoxModel containing background knowledge relevant to the process
     * @param parameters           the Parameters object defining the search parameters for the run
     * @throws NullPointerException if the latentClustersRunner argument is null
     * @throws ParseException       if an error occurs while parsing the provided parameters or during initialization
     */
    public LatentStructureRunner(DataWrapper data, LatentClustersRunner latentClustersRunner, KnowledgeBoxModel knowledge,
                                 Parameters parameters) throws ParseException {
        super(data, knowledge, parameters);
        this.runner = Objects.requireNonNull(latentClustersRunner, "ClusterRunner required");
        super.blockSpec = latentClustersRunner.getBlockSpec();
    }

    /**
     * Constructs a LatentStructureRunner instance, which is responsible for executing
     * the latent structure analysis using the provided data wrapper, latent clusters
     * runner, latent structure runner, knowledge box model, and parameters. This constructor
     * ensures proper initialization by validating the required dependencies and setting
     * the appropriate algorithm.
     *
     * @param data                 the DataWrapper containing the dataset to be processed
     * @param latentClustersRunner the instance of LatentClustersRunner providing the upstream BlockSpec;
     *                              must not be null
     * @param latentStructureRunner the instance of LatentStructureRunner providing the algorithm to be used;
     *                               must not be null
     * @param knowledge            the KnowledgeBoxModel containing background knowledge relevant to the process
     * @param parameters           the Parameters object defining the search parameters for the run
     * @throws NullPointerException if the latentClustersRunner or latentStructureRunner argument is null
     * @throws ParseException       if any
     */
    public LatentStructureRunner(DataWrapper data, LatentClustersRunner latentClustersRunner,
                                 LatentStructureRunner latentStructureRunner, KnowledgeBoxModel knowledge,
                                 Parameters parameters) throws ParseException{
        super(data, knowledge, parameters);
        this.runner = Objects.requireNonNull(latentClustersRunner, "ClusterRunner required");
        super.blockSpec = latentClustersRunner.getBlockSpec();
        setAlgorithm(latentStructureRunner.getAlgorithm());
    }

    private static void ensureSpecMatchesRunnerData(BlockSpec spec, DataModel runnerData) {
        if (spec.dataSet() == runnerData) return; // exact same object

        // Fallback: compare by variable names and count
        List<String> specNames = spec.dataSet().getVariableNames();
        List<String> runNames = runnerData.getVariableNames();

        if (specNames.size() != runNames.size() || !specNames.equals(runNames)) {
            throw new IllegalStateException(
                    "BlockSpec dataset does not match the runnerâs dataset. " +
                            "Ensure the clusters were derived from the same data used for this search node.");
        }
    }

    /**
     * Called by editor when user presses âRun.â
     */
    @Override
    public void execute() {
        // 1) Fetch BlockSpec from upstream
        BlockSpec spec = runner.getBlockSpec();
        if (spec == null) {
            throw new IllegalStateException(
                    "No BlockSpec is available. Run a clustering algorithm first and/or click Apply in the blocks editor.");
        }

        if (!spec.dataSet().equals(getDataModel())) {
            throw new IllegalStateException("The dataset for the supplied latent clusters is not the " +
                    "dataset given as a parent to this box.");
        }

        // 2) Defensive validations
        BlocksUtil.validateBlocks(spec.blocks(), spec.dataSet());
        ensureSpecMatchesRunnerData(spec, getDataModel());

        // 3) Cache for factories/wrappers
        setBlockSpec(spec);

        // 4) Run via the parent (algorithm instance will be created downstream)
        super.execute();

        Graph graph = getGraph();

        if (graph == null) {
            return;
        }

        for (int i = 0; i < spec.blocks().size(); i++) {
            Node var = spec.blockVariables().get(i);

            for (int j : spec.blocks().get(i)) {
                Node node2 = spec.dataSet().getVariables().get(j);
                graph.addNode(node2);
                graph.addDirectedEdge(var, node2);
            }
        }

        LayoutUtil.fruchtermanReingoldLayout(graph);

        super.graphList.clear();
        super.graphList.add(graph);
    }
}
