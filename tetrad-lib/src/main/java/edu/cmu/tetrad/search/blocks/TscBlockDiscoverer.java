package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;

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
        // TODO: call your TSC pipeline and get List<List<Integer>>.
        // Example (pseudo):
        // Tsc tsc = new Tsc(dataSet, alpha, ...);
        // List<List<Integer>> blocks = tsc.getClustersAsIndices();

        List<List<Integer>> blocks = new ArrayList<>(); // <— replace with real call

        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        return BlocksUtil.toSpec(blocks, dataSet);
    }
}