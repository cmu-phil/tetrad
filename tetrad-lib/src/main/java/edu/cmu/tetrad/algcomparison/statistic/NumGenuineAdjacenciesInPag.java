package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

import static java.lang.Math.tanh;

/**
 * The number of genuine adjacencies in an estimated PAG compared to the true PAG. These are edges that are not induced edges
 * or covering colliders or non-colliders.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumGenuineAdjacenciesInPag implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public NumGenuineAdjacenciesInPag() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "NumGenuineAdj";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of Genuine Adjacencies in PAG (not induced adjacencies and not covering colliders or non-colliders)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int numInducedAdjacenciesInPag = GraphUtils.getNumInducedAdjacenciesInPag(trueGraph, estGraph);
        int numCoveringAdjacenciesInPag = GraphUtils.getNumCoveringAdjacenciesInPag(trueGraph, estGraph);
        int numEdges = estGraph.getNumEdges();
        return numEdges - numInducedAdjacenciesInPag - numCoveringAdjacenciesInPag;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return tanh(value / 5000.0);
    }
}
