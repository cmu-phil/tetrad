package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utilities.AdjacencyConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 * @author jdramsey
 */
public class AdjacencyPrecisionStat implements Statistic {

    @Override
    public String getAbbreviation() {
        return "AP";
    }

    @Override
    public String getDescription() {
        return "Adjacency Precision";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        int adjTp = adjConfusion.getAdjTp();
        int adjFp = adjConfusion.getAdjFp();
        int adjFn = adjConfusion.getAdjFn();
        int adjTn = adjConfusion.getAdjTn();
        return adjTp / (double) (adjTp + adjFp);
    }

    @Override
    public double getUtility(double value) {
        return value;
    }
}
