package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

import java.io.Serial;

/**
 * Outputs 1 if the skeleton is correct, 0 if not..
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CorrectSkeleton implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the CorrectSkeleton class.
     */
    public CorrectSkeleton() {

    }

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
