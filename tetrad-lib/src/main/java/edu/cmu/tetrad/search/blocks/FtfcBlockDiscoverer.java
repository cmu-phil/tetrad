package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.Fofc;
import edu.cmu.tetrad.search.Ftfc;
import edu.cmu.tetrad.search.ntad_test.NtadTest;

import java.util.List;

/**
 * Adapter: FTFC (two-factor clustering) -> BlockSpec (blocks of observed indices + one latent Node per block).
 * <p>
 * This mirrors the BPC adapter and keeps the return type consistent with FOFC/TSC adapters.
 */
public class FtfcBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final NtadTest ntadTest;
    private final double alpha;

    public FtfcBlockDiscoverer(DataSet dataSet, NtadTest ntadTest, double alpha) {
        this.dataSet = dataSet;
        this.ntadTest = ntadTest;
        this.alpha = alpha;
    }

    @Override
    public BlockSpec discover() {
        Ftfc ftfc = new Ftfc(dataSet, ntadTest, alpha);
        List<List<Integer>> blocks = ftfc.findClusters();

        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);

        return BlocksUtil.toSpec(blocks, dataSet);
    }
}
