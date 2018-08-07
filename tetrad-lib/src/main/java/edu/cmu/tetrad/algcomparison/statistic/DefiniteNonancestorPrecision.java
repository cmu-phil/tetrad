package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.DefiniteAncestorConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class DefiniteNonancestorPrecision implements Statistic {
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
        DefiniteAncestorConfusion adjConfusion = new DefiniteAncestorConfusion(trueGraph, estGraph);
        int tp = adjConfusion.getTpna();
        int fp = adjConfusion.getFpna();
        return tp / (double) (tp + fp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}