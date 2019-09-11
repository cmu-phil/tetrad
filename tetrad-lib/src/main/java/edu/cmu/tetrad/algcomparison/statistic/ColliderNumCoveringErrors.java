package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ColliderConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The number of collider errors due to false covering (shielding) of colliders.
 *
 * @author jdramsey
 */
public class ColliderNumCoveringErrors implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "CollCovErr";
    }

    @Override
    public String getDescription() {
        return "Number of collider errors due to false covering";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        ColliderConfusion confusion = new ColliderConfusion(trueGraph, estGraph);
        return confusion.getNumCoveringErrors();
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
