package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Bpc;
import edu.cmu.tetrad.search.ntad_test.NtadTest;

import java.util.List;

/**
 * Adapter: BPC -> BlockSpec (blocks of observed indices + one Node per block).
 * Assumes Bpc#findClusters() returns List<List<Integer>> (indices).
 */
public class BpcBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final NtadTest ntadTest;
    private final double alpha;
    private final int ess;
    private final SingleClusterPolicy policy;

    public BpcBlockDiscoverer(DataSet dataSet, NtadTest ntadTest, double alpha, int ess, SingleClusterPolicy policy) {
        this.dataSet = dataSet;
        this.ntadTest = ntadTest;
        this.alpha = alpha;
        this.ess = ess;
        this.policy = policy;
    }

    @Override
    public BlockSpec discover() {
        Bpc bpc = new Bpc(ntadTest, dataSet, alpha, ess);
        List<List<Integer>> blocks = bpc.getClusters();
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        blocks = BlocksUtil.applySingleClusterPolicy(policy, blocks, dataSet);
        return BlocksUtil.toSpec(blocks, dataSet);
    }
}
