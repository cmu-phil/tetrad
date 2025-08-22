package edu.cmu.tetrad.search.blocks;

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.TscScored;

import java.util.List;

/**
 * Adapter: TSC → BlockSpec.
 */
public class TscScoreBlockDiscoverer implements BlockDiscoverer {
    private final DataSet dataSet;
    private final double alpha;
    private final double ebicGamma;
    private final double ridge;
    private final double penaltyDiscount;

    public TscScoreBlockDiscoverer(DataSet dataSet, double alpha, double ebicGamma, double ridge, double penaltyDiscount) {
        this.dataSet = dataSet;
        this.alpha = alpha;
        this.ebicGamma = ebicGamma;
        this.ridge = ridge;
        this.penaltyDiscount = penaltyDiscount;
    }

    @Override
    public BlockSpec discover() {
        TscScored tsc = new TscScored(dataSet.getVariables(), new CorrelationMatrix(dataSet),
                dataSet.getNumRows());
        tsc.setAlpha(alpha);
        tsc.setEbicGamma(ebicGamma);
        tsc.setRidge(ridge);
        tsc.setIncludeAllNodes(true);
        tsc.setPenaltyDiscount(penaltyDiscount);
        tsc.setMode(TscScored.Mode.Scoring);
        List<List<Integer>> blocks = tsc.findClusters();
        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);
        return BlocksUtil.toSpec(blocks, dataSet);
    }
}