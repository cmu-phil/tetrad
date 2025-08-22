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

    public TscTestBlockDiscoverer(DataSet dataSet, double alpha) {
        this.dataSet = dataSet;
        this.alpha = alpha;
    }

    @Override
    public BlockSpec discover() {
        TscScored tsc = new TscScored(dataSet.getVariables(), new CorrelationMatrix(dataSet),
                dataSet.getNumRows());
        tsc.setAlpha(alpha);
        tsc.setIncludeAllNodes(true);
        tsc.setMode(TscScored.Mode.Testing);
        List<List<Integer>> blocks = tsc.findClusters();
        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        return BlocksUtil.toSpec(blocks, dataSet);
    }
}