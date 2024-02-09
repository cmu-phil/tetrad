package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import org.apache.commons.math3.util.FastMath;

/**
 * Prints the number of edges in the true graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumberOfEdgesTrue implements Statistic {
    private static final long serialVersionUID = 23L;

    /** {@inheritDoc} */
    @Override
    public String getAbbreviation() {
        return "EdgesT";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Number of Edges in the True Graph";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return trueGraph.getNumEdges();
    }

    /** {@inheritDoc} */
    @Override
    public double getNormValue(double value) {
        return FastMath.tanh(value);
    }
}
