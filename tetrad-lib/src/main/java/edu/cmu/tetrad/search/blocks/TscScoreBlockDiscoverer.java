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
        // sanity checks (choose clamp-or-throw style you prefer)
        if (!(alpha > 0 && alpha < 1)) throw new IllegalArgumentException("alpha must be in (0,1)");
        if (ebicGamma < 0) throw new IllegalArgumentException("ebicGamma must be ≥ 0");
        if (ridge < 0) throw new IllegalArgumentException("ridge must be ≥ 0");
        if (penaltyDiscount <= 0) throw new IllegalArgumentException("penaltyDiscount must be > 0");

        TscScored tsc = new TscScored(dataSet.getVariables(), new CorrelationMatrix(dataSet));
        tsc.setAlpha(alpha);
        tsc.setEbicGamma(ebicGamma);
        tsc.setRidge(ridge);
        tsc.setIncludeAllNodes(true);
        tsc.setPenaltyDiscount(penaltyDiscount);
        tsc.setExpectedSampleSize(dataSet.getNumRows());
        tsc.setMode(TscScored.Mode.Scoring);

        List<List<Integer>> blocks = tsc.findClusters();

        // Defensive: empty result -> empty spec is fine; validate the rest.
        BlocksUtil.validateBlocks(blocks, dataSet);
        blocks = BlocksUtil.canonicalizeBlocks(blocks);

        return BlocksUtil.toSpec(blocks, dataSet);
    }
}