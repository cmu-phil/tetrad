package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents the NumberEdgesEst statistic, which calculates the number of arrows not in unshielded colliders in the estimated graph.
 */
public class NumberArrowsNotInUnshieldedCollidersEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public NumberArrowsNotInUnshieldedCollidersEst() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#ArrowsNotInUCEst";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of Arrows Not in Unshielded Colliders in the Estimated Graph";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double n1 = new NumberEdgesInUnshieldedCollidersEst().getValue(trueGraph, estGraph, dataModel);
        double n2 = new NumberArrowsEst().getValue(trueGraph, estGraph, dataModel);
        return n2 - n1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - FastMath.tanh(value / 10.);
    }
}
