package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.DefiniteAncestorConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class AncestorTrueNegatives implements Statistic {
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
        DefiniteAncestorConfusion adjConfusion = new DefiniteAncestorConfusion(trueGraph, estGraph);
        return adjConfusion.getAtn();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}