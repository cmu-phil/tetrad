package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AncestorConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class AncestorRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "ANCR";
    }

    @Override
    public String getDescription() {
        return "Ancestor Recall";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        AncestorConfusion adjConfusion = new AncestorConfusion(trueGraph, estGraph);
        int tp = adjConfusion.getTp();
        int fn = adjConfusion.getFn();
        return tp / (double) (tp + fn);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
