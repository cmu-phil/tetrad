package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.util.Parameters;

import java.util.List;
import java.util.Objects;

/**
 * Runner for the Block Search session node.
 * Requires an upstream BlockSpec (from a ClusterRunner) and ensures it is present before running.
 */
public class LatentStructureRunner extends GeneralAlgorithmRunner {

    /** Upstream block source (your ClusterRunner should implement this). */
    public interface BlockSpecProvider {
        /** Must return non-null after clusters have been searched/applied. */
        BlockSpec getBlockSpec();
    }

    private final LatentClustersRunner source;

    public LatentStructureRunner(DataWrapper data, LatentClustersRunner latentClustersRunner, Parameters parameters) {
        super(data, parameters);
        this.source = Objects.requireNonNull(latentClustersRunner, "ClusterRunner required");
    }

    /** Called by editor when user presses “Run”. */
    @Override
    public void execute() {
        // 1) Fetch BlockSpec from upstream
        BlockSpec spec = source.getBlockSpec();
        if (spec == null) {
            throw new IllegalStateException(
                    "No BlockSpec is available. Run a clustering algorithm first and/or click Apply in the blocks editor.");
        }

        // 2) Defensive validations
        BlocksUtil.validateBlocks(spec.blocks(), spec.dataSet());
        ensureSpecMatchesRunnerData(spec, getDataModel());

        // 3) Cache for factories/wrappers
        setBlockSpec(spec);

        // 4) Run via the parent (algorithm instance will be created downstream)
        super.execute();
    }

    /** For factories/wrappers to access the current spec during construction. */
    public BlockSpec getCurrentSpecOrThrow() {
        if (getBlockSpec() == null) {
            throw new IllegalStateException("BlockSpec not initialized—call execute() first.");
        }
        return getBlockSpec();
    }

    // ---------- internal helpers ----------

    private static void ensureSpecMatchesRunnerData(BlockSpec spec, DataModel runnerData) {
        if (spec.dataSet() == runnerData) return; // exact same object

        // Fallback: compare by variable names and count
        List<String> specNames = spec.dataSet().getVariableNames();
        List<String> runNames  = runnerData.getVariableNames();

        if (specNames.size() != runNames.size() || !specNames.equals(runNames)) {
            throw new IllegalStateException(
                    "BlockSpec dataset does not match the runner’s dataset. " +
                    "Ensure the clusters were derived from the same data used for this search node.");
        }
    }
}