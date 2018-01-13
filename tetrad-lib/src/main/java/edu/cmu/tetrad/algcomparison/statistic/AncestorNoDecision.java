package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AncestorConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class AncestorNoDecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "ANND";
    }

    @Override
    public String getDescription() {
        return "Ancestor No Decision";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        AncestorConfusion adjConfusion = new AncestorConfusion(trueGraph, estGraph);
        int tn = adjConfusion.getTn();
        return tn;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}