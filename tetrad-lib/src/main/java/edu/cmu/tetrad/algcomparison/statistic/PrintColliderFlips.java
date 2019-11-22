package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.TriangleConfusion;
import edu.cmu.tetrad.algcomparison.statistic.utils.UnshieldedTripleCounts;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The number of triangle false positives.
 *
 * @author jdramsey
 */
public class PrintColliderFlips implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "PrintFlips";
    }

    @Override
    public String getDescription() {
        return "Printing the collider flips";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        UnshieldedTripleCounts confusion = new UnshieldedTripleCounts(trueGraph, estGraph, 0, 0);
        return 100;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
