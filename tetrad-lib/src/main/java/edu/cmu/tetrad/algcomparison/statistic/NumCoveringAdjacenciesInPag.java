package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

import static java.lang.Math.tanh;

/**
 * The number of covering adjacencies in an estimated PAG compared to the true PAG.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumCoveringAdjacenciesInPag implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public NumCoveringAdjacenciesInPag() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#CoveringAdj";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of Covering Adjacencies in PAG (adjacencies in estimated graph that are not in true graph and are covering colliders or noncolliders)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        return GraphUtils.getNumCoveringAdjacenciesInPag(trueGraph, estGraph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return tanh(value / 5000.0);
    }
}
