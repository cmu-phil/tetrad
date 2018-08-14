package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.algcomparison.statistic.utils.AncestorConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class AncestorPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "ANCP";
    }

    @Override
    public String getDescription() {
        return "Ancestor Precision";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        AncestorConfusion adjConfusion = new AncestorConfusion(trueGraph, estGraph);
        int tp = adjConfusion.getTp();
        int fp = adjConfusion.getFp();
        return tp / (double) (tp + fp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
