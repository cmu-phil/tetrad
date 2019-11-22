package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.UnshieldedTripleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The number of triangle true positives.
 *
 * @author jdramsey
 */
public class UnshieldedTripleInvolvedEdges implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "UTInvolved";
    }

    @Override
    public String getDescription() {
        return "Unshielded triple involved edges";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        UnshieldedTripleConfusion confusion = new UnshieldedTripleConfusion(trueGraph, estGraph);
        return (double) confusion.getInvolvedUtFp() / 2.;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
