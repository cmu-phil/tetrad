package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;

/**
 * Outputs 1 if the skeleton is correct, 0 if not..
 *
 * @author jdramsey
 */
public class CorrectSkeleton implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "CorrSk";
    }

    @Override
    public String getDescription() {
        return "Correct Skeleton";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return GraphUtils.undirectedGraph(trueGraph).equals(GraphUtils.undirectedGraph(estGraph)) ?
                1 : 0;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
