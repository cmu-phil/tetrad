package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.interfaces.Statistic;
import edu.cmu.tetrad.algcomparison.statistic.utilities.AdjacencyConfusion;
import edu.cmu.tetrad.graph.Graph;

/**
 * Created by jdramsey on 7/10/16.
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
