package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runner for the Block Search session node. Requires an upstream BlockSpec (from a ClusterRunner) and ensures it is
 * present before running.
 */
public class LatentStructureRunner extends GeneralAlgorithmRunner {

    private final LatentClustersRunner runner;
    private Graph graph = new EdgeListGraph();

    public LatentStructureRunner(DataWrapper data, LatentClustersRunner latentClustersRunner, Parameters parameters) {
        super(data, parameters);
        this.runner = Objects.requireNonNull(latentClustersRunner, "ClusterRunner required");
    }

    private static void ensureSpecMatchesRunnerData(BlockSpec spec, DataModel runnerData) {
        if (spec.dataSet() == runnerData) return; // exact same object

        // Fallback: compare by variable names and count
        List<String> specNames = spec.dataSet().getVariableNames();
        List<String> runNames = runnerData.getVariableNames();

        if (specNames.size() != runNames.size() || !specNames.equals(runNames)) {
            throw new IllegalStateException(
                    "BlockSpec dataset does not match the runner’s dataset. " +
                    "Ensure the clusters were derived from the same data used for this search node.");
        }
    }

    /**
     * Called by editor when user presses “Run”.
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

        for (Node node : spec.dataSet().getVariables()) {
            graph.addNode(node);
        }

        for (int i = 0; i < spec.blocks().size(); i++) {
            Node var = spec.blockVariables().get(i);

            for (int j : spec.blocks().get(i)) {
                graph.addDirectedEdge(var, spec.dataSet().getVariables().get(j));
            }
        }

        LayoutUtil.fruchtermanReingoldLayout(graph);

        super.graphList.clear();
        super.graphList.add(graph);
    }

    /**
     * For factories/wrappers to access the current spec during construction.
     */
    public BlockSpec getCurrentSpecOrThrow() {
        if (getBlockSpec() == null) {
            throw new IllegalStateException("BlockSpec not initialized—call execute() first.");
        }
        return getBlockSpec();
    }

    // ---------- internal helpers ----------

    /**
     * Upstream block source (your ClusterRunner should implement this).
     */
    public interface BlockSpecProvider {
        /**
         * Must return non-null after clusters have been searched/applied.
         */
        BlockSpec getBlockSpec();
    }
}