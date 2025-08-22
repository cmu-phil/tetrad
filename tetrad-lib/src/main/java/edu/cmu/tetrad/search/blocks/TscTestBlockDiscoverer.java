package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.TscScored;

import java.util.List;

/**
 * Adapter: TSC → BlockSpec.
 */
public class TscTestBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final double alpha; // or whatever hyperparams TSC needs
    private final SingleClusterPolicy policy;

    public TscTestBlockDiscoverer(DataSet dataSet, double alpha, SingleClusterPolicy policy) {
        this.dataSet = dataSet;
        this.alpha = alpha;
        this.policy = policy;
    }

    @Override
    public BlockSpec discover() {
        if (!(alpha > 0 && alpha < 1)) {
            throw new IllegalArgumentException("alpha must be in (0,1)");
        }

        TscScored tsc = new TscScored(dataSet.getVariables(), new CorrelationMatrix(dataSet));
        tsc.setAlpha(alpha);
        tsc.setIncludeAllNodes(true);
        tsc.setExpectedSampleSize(dataSet.getNumRows());
        tsc.setMode(TscScored.Mode.Testing);

        List<List<Integer>> blocks = tsc.findClusters();

        // OK for blocks to be empty; just ensure indices are sane and canonicalize.
        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        blocks = BlocksUtil.applySingleClusterPolicy(policy, blocks, dataSet);

        return BlocksUtil.toSpec(blocks, dataSet);
    }
}