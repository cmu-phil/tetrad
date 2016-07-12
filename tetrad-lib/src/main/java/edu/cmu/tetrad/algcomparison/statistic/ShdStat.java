package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.interfaces.Statistic;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.SearchGraphUtils;

/**
 * Created by jdramsey on 7/10/16.
 */
public class ShdStat implements Statistic {

    @Override
    public String getAbbreviation() {
        return "SHD ";
    }

    @Override
    public String getDescription() {
        return "Structural Hamming Distance";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph) {
        GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison3(estGraph, trueGraph, System.out);
        return comparison.getShd();
    }

    @Override
    public double getUtility(double value) {
        return -value;
    }
}
