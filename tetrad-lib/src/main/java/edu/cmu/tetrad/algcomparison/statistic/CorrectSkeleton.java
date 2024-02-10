package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

/**
 * Outputs 1 if the skeleton is correct, 0 if not..
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CorrectSkeleton implements Statistic {
    private static final long serialVersionUID = 23L;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "CorrSk";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Correct Skeleton";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return GraphUtils.undirectedGraph(trueGraph).equals(GraphUtils.undirectedGraph(estGraph)) ?
                1 : 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}
