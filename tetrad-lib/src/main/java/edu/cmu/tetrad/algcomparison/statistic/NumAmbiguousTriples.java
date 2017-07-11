package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

import static java.lang.Math.tanh;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class NumAmbiguousTriples implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AMB";
    }

    @Override
    public String getDescription() {
        return "Number of Ambiguous Triples";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        return estGraph.getAmbiguousTriples().size();
    }

    @Override
    public double getNormValue(double value) {
        return 1 - tanh(value);
    }
}
