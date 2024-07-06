package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.io.Serial;

import static java.lang.Math.tanh;

/**
 * The number of induced adjacencies in an estimated PAG compared to the true PAG.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumInducedAdjacenciesInPag implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public NumInducedAdjacenciesInPag() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "NumInducedAdj";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of Induced Adjacencies in PAG (adjacencies in estimated graph but not in true graph that are not covering colliders or non-colliders)";
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
