package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import org.apache.commons.math3.util.FastMath;

/**
 * Calculates the structural Hamming distance (SHD) between the estimated graph and the true graph.
 *
 * @author josephramsey
 */
public class StructuralHammingDistance implements Statistic {
    private static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "SHD";
    }

    @Override
    public String getDescription() {
        return "Structural Hamming Distance";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        GraphUtils.GraphComparison comparison = GraphSearchUtils.getGraphComparison(trueGraph, estGraph);
        return comparison.getShd();
    }

    @Override
    public double getNormValue(double value) {
        return 1.0 - FastMath.tanh(0.001 * value);
    }
}
