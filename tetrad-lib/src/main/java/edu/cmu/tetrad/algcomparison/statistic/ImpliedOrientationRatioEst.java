package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * The Implied Arrow Orientation Ratio Est statistic calculates the ratio of the number of implied arrows to the number of arrows in unshielded colliders in the estimated graph.
 * Implied Arrow Orientation Ratio in the Estimated Graph = (numImpliedArrows - numArrowsInUnshieldedColliders) / numArrowsInUnshieldedColliders.
 * It implements the Statistic interface.
 */
public class ImpliedOrientationRatioEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public ImpliedOrientationRatioEst() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "IOR";
    }

    /**A
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Implied Arrow Orientation Ratio in the Estimated Graph (# implied arrow and tail orientions / # edges in unshielded colliders)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        double n1 = new NumberEdgesInUnshieldedCollidersEst().getValue(trueGraph, estGraph, dataModel, new Parameters());
        double n2 = new NumberArrowsEst().getValue(trueGraph, estGraph, dataModel, new Parameters());
        double n3 = new NumberTailsEst().getValue(trueGraph, estGraph, dataModel, new Parameters());
        return (n2 + n3 - n1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1 - value;
    }
}
