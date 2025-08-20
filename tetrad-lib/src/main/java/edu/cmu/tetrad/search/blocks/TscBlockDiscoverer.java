package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.TrekSeparationClusters;

import java.util.ArrayList;
import java.util.List;

/** Adapter: TSC → BlockSpec. */
public class TscBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final double alpha; // or whatever hyperparams TSC needs

    public TscBlockDiscoverer(DataSet dataSet, double alpha) {
        this.dataSet = dataSet;
        this.alpha = alpha;
    }

    @Override
    public BlockSpec discover() {
        TrekSeparationClusters tsc = new TrekSeparationClusters(dataSet.getVariables(), new CorrelationMatrix(dataSet),
                dataSet.getNumRows());
        List<List<Integer>> blocks = tsc.findClusters();
        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        return BlocksUtil.toSpec(blocks, dataSet);
    }
}