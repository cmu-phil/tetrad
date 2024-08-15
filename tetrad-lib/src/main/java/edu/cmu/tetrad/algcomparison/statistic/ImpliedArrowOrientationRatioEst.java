package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

import java.io.Serial;

/**
 * The Implied Arrow Orientation Ratio Est statistic calculates the ratio of the number of implied arrows to the number of arrows in unshielded colliders in the estimated graph.
 * Implied Arrow Orientation Ratio in the Estimated Graph = (numImpliedArrows - numArrowsInUnshieldedColliders) / numArrowsInUnshieldedColliders.
 * It implements the Statistic interface.
 */
public class ImpliedArrowOrientationRatioEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public ImpliedArrowOrientationRatioEst() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "IAOR";
    }

    /**A
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Implied Arrow Orientation Ratio in the Estimated Graph (# implied arrows / # arrows in unshielded colliders)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double n1 = new NumberEdgesInUnshieldedCollidersEst().getValue(trueGraph, estGraph, dataModel);
        double n2 = new NumberArrowsEst().getValue(trueGraph, estGraph, dataModel);
        double n3 = new NumberTailsEst().getValue(trueGraph, estGraph, dataModel);
        return n1 == 0 ? Double.NaN : (n2 - n1) / n1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1 - value;
    }
}
