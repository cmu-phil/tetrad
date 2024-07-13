package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.io.Serial;

import static java.lang.Math.tanh;

/**
 * The number of adjacencies in the estimated graph but not in the true graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumEdgeInEstInTrue implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public NumEdgeInEstInTrue() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#EdgesEstInTrue";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of Adjacencies in the Estimated Graph that are Also in the True Graph";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return GraphUtils.getNumInducedAdjacenciesInPag(trueGraph, estGraph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return tanh(value / 5000.0);
    }
}
