package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Fofc;
import edu.cmu.tetrad.search.ntad_test.NtadTest;

import java.util.List;

/** Adapter: FOFC → BlockSpec. */
public class FofcBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final NtadTest ntadTest;
    private final double alpha;
    private final int ess;
    private final SingleClusterPolicy policy;

    public FofcBlockDiscoverer(DataSet dataSet, NtadTest ntadTest, double alpha, int ess,
                               SingleClusterPolicy policy) {
        this.dataSet = dataSet;
        this.ntadTest = ntadTest;
        this.alpha = alpha;
        this.ess = ess;
        this.policy = policy;
    }

    @Override
    public BlockSpec discover() {
        Fofc fofc = new Fofc(dataSet, ntadTest, alpha, ess);
        List<List<Integer>> blocks = fofc.findClusters();

        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        blocks = BlocksUtil.applySingleClusterPolicy(policy, blocks, dataSet);

        return BlocksUtil.toSpec(blocks, dataSet);
    }
}