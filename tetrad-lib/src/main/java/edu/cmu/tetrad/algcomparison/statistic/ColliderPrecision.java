package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.algcomparison.statistic.utils.ColliderConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The collider precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class ColliderPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "CollP";
    }

    @Override
    public String getDescription() {
        return "Collider Precision";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        ColliderConfusion confusion = new ColliderConfusion(trueGraph, estGraph);
        int adjTp = confusion.getAdjTp();
        int adjFp = confusion.getAdjFp();
//        int adjFn = confusion.getAdjFn();
//        int adjTn = confusion.getAdjTn();
        return adjTp / (double) (adjTp + adjFp);
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
