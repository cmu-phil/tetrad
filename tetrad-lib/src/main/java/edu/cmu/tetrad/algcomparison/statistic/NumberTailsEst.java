package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * Represents the NumberEdgesEst statistic, which calculates the number of tails in the estimated graph.
 */
public class NumberTailsEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public NumberTailsEst() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#TailsEst";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of Tails in the Estimated Graph";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int count = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.TAIL) {
                count++;
            }

            if (edge.getEndpoint2() == Endpoint.TAIL) {
                count++;
            }
        }

        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - FastMath.tanh(value / 1000.);
    }
}
