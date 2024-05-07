package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * Prints the number of edges in the estimated graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumberOfEdgesEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NumberOfEdgesEst() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "EdgesEst";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of Edges in the Estimated Graph";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return estGraph.getNumEdges();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return FastMath.tanh(value);
    }
}
