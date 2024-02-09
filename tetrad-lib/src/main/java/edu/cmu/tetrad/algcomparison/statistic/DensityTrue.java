package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both the true and estimated graphs.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DensityTrue implements Statistic {
    private static final long serialVersionUID = 23L;

    /** {@inheritDoc} */
    @Override
    public String getAbbreviation() {
        return "DensTrue";
    }

    /** {@inheritDoc} */
    @Override
    public String getDescription() {
        return "Density of True Graph";
    }

    /** {@inheritDoc} */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return new AverageDegreeTrue().getValue(trueGraph, estGraph, dataModel)
                / (double) (trueGraph.getNumNodes() - 1);
    }

    /** {@inheritDoc} */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
