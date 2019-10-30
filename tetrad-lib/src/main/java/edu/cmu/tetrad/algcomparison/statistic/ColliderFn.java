package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ColliderConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The number of collider errors false negatives.
 *
 * @author jdramsey
 */
public class ColliderFn implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "CollFn";
    }

    @Override
    public String getDescription() {
        return "Number of collider false negatives";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        ColliderConfusion confusion = new ColliderConfusion(trueGraph, estGraph);
        return confusion.getFn();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
