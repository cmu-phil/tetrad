package edu.cmu.tetrad.algcomparison.statistic.utilities;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.graph.*;

/**
 * Created by jdramsey on 7/10/16.
 */
public class EdgeStats {
    private Graph est;
    private Graph truth;
    private long elapsed;

    public EdgeStats(Graph est, Graph truth, long elapsed) {
        this.est = est;
        this.truth = GraphUtils.replaceNodes(truth, est.getNodes());
        this.elapsed = elapsed;
    }

    public double getStat(String stat) {
        switch (stat) {
            case "AP":
                return new AdjacencyPrecisionStat().getValue(truth, est);
            case "AR":
                return new AdjacencyRecallStat().getValue(truth, est);
            case "OP":
                return new ArrowPrecisionStat().getValue(truth, est);
            case "OR":
                return new ArrowRecallStat().getValue(truth, est);
            case "McAdj":
                return new MathewsCorrAdjStat().getValue(truth, est);
            case "McOr":
                return new MathewsCorrArrowStat().getValue(truth, est);
            case "F1Adj":
                return new F1AdjStat().getValue(truth, est);
            case "F1Or":
                return new F1ArrowStat().getValue(truth, est);
            case "SHD":
                return new ShdStat().getValue(truth, est);
            case "E":
                return new ElapsedTimeStat().getValue(truth, est);
            case "W":
                return Double.NaN;
            default:
                throw new IllegalArgumentException("No such stat: " + stat);
        }
    }
}
